"""Security, validation, token handling, and per-request filesystem isolation."""

from __future__ import annotations

import base64
import binascii
import hmac
import math
import os
import shutil
import stat
import tempfile
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Mapping, Sequence

MAX_REQUEST_BYTES = 3 * 1024 * 1024  # 3 MiB
MAX_JPEG_BYTES = 2 * 1024 * 1024  # 2 MiB
MAX_QUESTION_CHARS = 8_000
MAX_HISTORY_ITEMS = 20
MAX_HISTORY_ITEM_CHARS = 4_000
MAX_HISTORY_TOTAL_CHARS = 24_000
MAX_OUTPUT_CHARS = 8_000  # Fits the Android client's 64 KiB JSON response cap even when escaped.
MAX_TIMEOUT_SECONDS = 120.0

MIN_TOKEN_LENGTH = 32
MIN_TOKEN_UNIQUE_CHARS = 12
MIN_TOKEN_ENTROPY_BITS = 100.0

ALLOWED_PROVIDERS = frozenset({"gemini", "chatgpt"})
ALLOWED_ASK_KEYS = frozenset({"provider", "question", "image_base64", "model", "history"})
ALLOWED_HISTORY_KEYS = frozenset({"role", "text"})
ALLOWED_HISTORY_ROLES = frozenset({"user", "assistant"})

# Only pass minimal non-secret runtime environment variables to provider CLIs.
SAFE_ENV_KEYS = frozenset(
    {
        "PATH",
        "HOME",
        "USER",
        "LOGNAME",
        "LANG",
        "LC_ALL",
        "LC_CTYPE",
        "TERM",
        "TMPDIR",
        "XDG_CONFIG_HOME",
        "XDG_CACHE_HOME",
        "XDG_DATA_HOME",
        "XDG_STATE_HOME",
        "XDG_RUNTIME_DIR",
        "SSL_CERT_FILE",
        "SSL_CERT_DIR",
        "HTTP_PROXY",
        "HTTPS_PROXY",
        "NO_PROXY",
        "http_proxy",
        "https_proxy",
        "no_proxy",
    }
)


class ValidationError(ValueError):
    """Raised when client input or server security configuration is invalid."""

    def __init__(self, message: str, *, status_code: int = 400, error_code: str = "invalid_request"):
        super().__init__(message)
        self.message = message
        self.status_code = status_code
        self.error_code = error_code


@dataclass(frozen=True)
class HistoryEntry:
    role: str
    text: str


@dataclass(frozen=True)
class AskRequest:
    provider: str
    question: str
    image_bytes: bytes | None = None
    model: str | None = None
    history: tuple[HistoryEntry, ...] = ()


def _shannon_entropy_bits(value: str) -> float:
    if not value:
        return 0.0
    counts = Counter(value)
    length = len(value)
    entropy_per_char = -sum((c / length) * math.log2(c / length) for c in counts.values())
    return entropy_per_char * length


def validate_bridge_token(token: str) -> str:
    """Validate that a bridge token has high entropy and safe characters."""
    if not isinstance(token, str) or not token:
        raise ValidationError("Bridge token is required", error_code="invalid_token_config")
    if any(ch.isspace() or ord(ch) < 0x21 or ord(ch) > 0x7E for ch in token):
        raise ValidationError(
            "Bridge token must contain only printable ASCII non-whitespace characters",
            error_code="invalid_token_config",
        )
    if len(token) < MIN_TOKEN_LENGTH:
        raise ValidationError(
            f"Bridge token must be at least {MIN_TOKEN_LENGTH} characters",
            error_code="invalid_token_config",
        )
    if len(set(token)) < MIN_TOKEN_UNIQUE_CHARS:
        raise ValidationError(
            "Bridge token has too few distinct characters",
            error_code="invalid_token_config",
        )
    if _shannon_entropy_bits(token) < MIN_TOKEN_ENTROPY_BITS:
        raise ValidationError(
            "Bridge token entropy is too low; generate with secrets.token_urlsafe(32)",
            error_code="invalid_token_config",
        )
    return token


def load_bridge_token(
    *,
    token_env: str = "ACCOUNT_BRIDGE_TOKEN",
    token_file: str | Path | None = None,
    token_file_env: str = "ACCOUNT_BRIDGE_TOKEN_FILE",
    environ: Mapping[str, str] | None = None,
) -> str:
    """Load and validate the bridge bearer token from a protected file or environment."""
    env = os.environ if environ is None else environ
    resolved_file = token_file or env.get(token_file_env)
    env_token = env.get(token_env)

    if resolved_file and env_token:
        raise ValidationError(
            "Specify either bridge token environment variable or token file, not both",
            error_code="invalid_token_config",
        )

    if resolved_file:
        path = Path(resolved_file)
        flags = os.O_RDONLY
        if hasattr(os, "O_NOFOLLOW"):
            flags |= os.O_NOFOLLOW
        try:
            fd = os.open(os.fspath(path), flags)
        except OSError as exc:
            raise ValidationError(
                f"Unable to open token file safely: {exc.strerror or type(exc).__name__}",
                error_code="invalid_token_config",
            ) from None
        try:
            st = os.fstat(fd)
            if not stat.S_ISREG(st.st_mode):
                raise ValidationError(
                    "Token file must be a regular file",
                    error_code="invalid_token_config",
                )
            if os.name == "posix":
                if (st.st_mode & 0o077) != 0:
                    raise ValidationError(
                        "Token file permissions are too open; require owner-only mode (0600 or 0400)",
                        error_code="invalid_token_config",
                    )
                if hasattr(os, "getuid") and st.st_uid != os.getuid():
                    raise ValidationError(
                        "Token file must be owned by the current user",
                        error_code="invalid_token_config",
                    )
            if st.st_size > 4096:
                raise ValidationError(
                    "Token file is unexpectedly large",
                    error_code="invalid_token_config",
                )
            raw = os.read(fd, 4097)
        finally:
            os.close(fd)
        try:
            token = raw.decode("utf-8").rstrip("\r\n")
        except UnicodeDecodeError:
            raise ValidationError(
                "Token file must be valid UTF-8 text",
                error_code="invalid_token_config",
            ) from None
        return validate_bridge_token(token)

    if env_token:
        return validate_bridge_token(env_token)

    raise ValidationError(
        f"Bridge token not configured; set {token_env} or --token-file / {token_file_env}",
        error_code="invalid_token_config",
    )


def verify_bearer_token(auth_header: str | None, expected_token: str) -> bool:
    """Constant-time check for an Authorization: Bearer <token> header."""
    if not auth_header or not isinstance(auth_header, str):
        return False
    parts = auth_header.split(" ")
    if len(parts) != 2 or parts[0] != "Bearer" or not parts[1]:
        return False
    return hmac.compare_digest(parts[1].encode("utf-8"), expected_token.encode("utf-8"))


def decode_and_validate_jpeg(image_base64: str) -> bytes:
    """Strictly decode base64 and verify JPEG size limit and magic bytes."""
    if not isinstance(image_base64, str) or not image_base64:
        raise ValidationError("image_base64 must be a non-empty string")
    if image_base64.startswith("data:"):
        raise ValidationError("image_base64 must contain raw base64 bytes, not a data URI")

    cleaned = "".join(image_base64.split())
    if not cleaned:
        raise ValidationError("image_base64 must not be empty")

    # Fast size bound before decoding: ceil(2 MiB / 3) * 4 = 2,796,204 chars.
    max_b64_len = ((MAX_JPEG_BYTES + 2) // 3) * 4
    if len(cleaned) > max_b64_len:
        raise ValidationError(
            f"Decoded JPEG exceeds maximum allowed size of {MAX_JPEG_BYTES} bytes",
            status_code=413,
            error_code="image_too_large",
        )

    try:
        raw = base64.b64decode(cleaned, validate=True)
    except (binascii.Error, ValueError):
        raise ValidationError("image_base64 is not valid base64") from None

    if len(raw) > MAX_JPEG_BYTES:
        raise ValidationError(
            f"Decoded JPEG exceeds maximum allowed size of {MAX_JPEG_BYTES} bytes",
            status_code=413,
            error_code="image_too_large",
        )

    # Require JPEG SOI (FF D8), valid marker byte (FF C0..FE), and EOI (FF D9).
    if len(raw) < 4 or raw[0:3] != b"\xff\xd8\xff" or not (0xC0 <= raw[3] <= 0xFE):
        raise ValidationError("image_base64 does not contain valid JPEG magic bytes")
    if raw[-2:] != b"\xff\xd9":
        raise ValidationError("image_base64 JPEG is missing EOI trailer (FFD9)")

    return raw


def validate_ask_payload(
    payload: Any,
    *,
    allowed_models_by_provider: Mapping[str, Sequence[str]],
) -> AskRequest:
    """Validate and normalize a POST /v1/ask JSON payload."""
    if not isinstance(payload, dict):
        raise ValidationError("Request body must be a JSON object")

    unknown = set(payload.keys()) - ALLOWED_ASK_KEYS
    if unknown:
        raise ValidationError(f"Unknown field(s): {', '.join(sorted(unknown))}")

    provider = payload.get("provider")
    if not isinstance(provider, str) or provider not in ALLOWED_PROVIDERS:
        raise ValidationError("provider must be 'gemini' or 'chatgpt'")

    question = payload.get("question")
    if not isinstance(question, str):
        raise ValidationError("question must be a string")
    if "\x00" in question:
        raise ValidationError("question must not contain NUL bytes")
    if not question.strip():
        raise ValidationError("question must not be empty")
    if len(question) > MAX_QUESTION_CHARS:
        raise ValidationError(f"question exceeds maximum length of {MAX_QUESTION_CHARS} characters")

    model = payload.get("model")
    if model is not None:
        if not isinstance(model, str) or not model:
            raise ValidationError("model must be a non-empty string when provided")
        allowed_models = allowed_models_by_provider.get(provider, ())
        if model not in allowed_models:
            raise ValidationError(
                "Requested model is not in the server-configured allowlist for this provider"
            )

    history_entries: list[HistoryEntry] = []
    raw_history = payload.get("history")
    if raw_history is not None:
        if not isinstance(raw_history, list):
            raise ValidationError("history must be a list")
        if len(raw_history) > MAX_HISTORY_ITEMS:
            raise ValidationError(f"history exceeds maximum of {MAX_HISTORY_ITEMS} entries")
        total_chars = 0
        for idx, item in enumerate(raw_history):
            if not isinstance(item, dict):
                raise ValidationError(f"history[{idx}] must be an object")
            extra_keys = set(item.keys()) - ALLOWED_HISTORY_KEYS
            if extra_keys or not ALLOWED_HISTORY_KEYS.issubset(item.keys()):
                raise ValidationError(f"history[{idx}] must contain only 'role' and 'text'")
            role = item["role"]
            text = item["text"]
            if not isinstance(role, str) or role not in ALLOWED_HISTORY_ROLES:
                raise ValidationError(f"history[{idx}].role must be 'user' or 'assistant'")
            if not isinstance(text, str) or "\x00" in text or not text.strip():
                raise ValidationError(f"history[{idx}].text must be a non-empty string")
            if len(text) > MAX_HISTORY_ITEM_CHARS:
                raise ValidationError(
                    f"history[{idx}].text exceeds {MAX_HISTORY_ITEM_CHARS} characters"
                )
            total_chars += len(text)
            if total_chars > MAX_HISTORY_TOTAL_CHARS:
                raise ValidationError(
                    f"history total length exceeds {MAX_HISTORY_TOTAL_CHARS} characters"
                )
            history_entries.append(HistoryEntry(role=role, text=text))

    image_bytes: bytes | None = None
    raw_image = payload.get("image_base64")
    if raw_image is not None:
        image_bytes = decode_and_validate_jpeg(raw_image)

    return AskRequest(
        provider=provider,
        question=question,
        image_bytes=image_bytes,
        model=model,
        history=tuple(history_entries),
    )


def build_safe_env(base_env: Mapping[str, str] | None = None) -> dict[str, str]:
    """Return a minimal subprocess environment excluding bridge secrets."""
    source = os.environ if base_env is None else base_env
    return {k: v for k, v in source.items() if k in SAFE_ENV_KEYS}


class IsolatedRequestDir:
    """Per-request isolated 0700 working directory with 0600 temp JPEG helper and guaranteed cleanup."""

    def __init__(self, *, parent_dir: str | Path | None = None):
        self._parent_dir = None if parent_dir is None else os.fspath(parent_dir)
        self.path: Path | None = None

    def __enter__(self) -> IsolatedRequestDir:
        created = tempfile.mkdtemp(prefix="account_bridge_req_", dir=self._parent_dir)
        os.chmod(created, 0o700)
        self.path = Path(created)
        return self

    def write_jpeg(self, jpeg_bytes: bytes, *, filename: str = "input.jpg") -> Path:
        """Write validated JPEG bytes inside the isolated dir with strict 0600 permissions."""
        if self.path is None:
            raise RuntimeError("IsolatedRequestDir is not active")
        if "/" in filename or "\\" in filename or filename in {"", ".", ".."}:
            raise ValueError("Invalid temp image filename")
        target = self.path / filename
        flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL
        if hasattr(os, "O_NOFOLLOW"):
            flags |= os.O_NOFOLLOW
        fd = os.open(os.fspath(target), flags, 0o600)
        try:
            os.fchmod(fd, 0o600)
            view = memoryview(jpeg_bytes)
            written = 0
            while written < len(view):
                written += os.write(fd, view[written:])
        finally:
            os.close(fd)
        return target

    def __exit__(self, exc_type: Any, exc: Any, tb: Any) -> None:
        if self.path is not None:
            try:
                shutil.rmtree(self.path)
            except FileNotFoundError:
                pass
            finally:
                self.path = None
