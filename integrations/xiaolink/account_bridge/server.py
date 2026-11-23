"""HTTP server and CLI entrypoint for the local account bridge."""

from __future__ import annotations

import argparse
import json
import os
import sys
import threading
from dataclasses import dataclass, field
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any, Mapping
from urllib.parse import urlsplit

from .providers import (
    DEFAULT_CODEX_UNSUPPORTED_REASON,
    DEFAULT_GEMINI_ALLOWED_MODELS,
    DEFAULT_GEMINI_MODEL,
    AgyAcpProvider,
    ProviderAdapter,
    ProviderError,
    UnsupportedProvider,
)
from .security import (
    MAX_REQUEST_BYTES,
    MAX_TIMEOUT_SECONDS,
    ValidationError,
    load_bridge_token,
    validate_ask_payload,
    validate_bridge_token,
    verify_bearer_token,
)

DEFAULT_HOST = "127.0.0.1"
DEFAULT_PORT = 8765
DEFAULT_TIMEOUT_SECONDS = 90.0
REQUEST_IO_TIMEOUT_SECONDS = 15.0


@dataclass
class BridgeConfig:
    bridge_token: str
    host: str = DEFAULT_HOST
    port: int = DEFAULT_PORT
    timeout_seconds: float = DEFAULT_TIMEOUT_SECONDS
    providers: dict[str, ProviderAdapter] = field(default_factory=dict)

    def __post_init__(self) -> None:
        validate_bridge_token(self.bridge_token)
        if not (0 < self.timeout_seconds <= MAX_TIMEOUT_SECONDS):
            raise ValidationError(
                f"timeout_seconds must be > 0 and <= {MAX_TIMEOUT_SECONDS}",
                error_code="invalid_config",
            )
        if not self.providers:
            self.providers = {
                "gemini": AgyAcpProvider(),
                "chatgpt": UnsupportedProvider(
                    name="chatgpt",
                    backend="codex-exec",
                    reason=DEFAULT_CODEX_UNSUPPORTED_REASON,
                ),
            }


class BridgeHTTPServer(ThreadingHTTPServer):
    """Multi-threaded HTTP server with single-flight inference lock."""

    allow_reuse_address = True
    daemon_threads = True

    def __init__(self, server_address: tuple[str, int], config: BridgeConfig):
        self.config = config
        self.inference_lock = threading.Lock()
        super().__init__(server_address, BridgeHTTPRequestHandler)


class BridgeHTTPRequestHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    server_version = "account-bridge"
    sys_version = ""

    server: BridgeHTTPServer

    def setup(self) -> None:
        super().setup()
        self.connection.settimeout(REQUEST_IO_TIMEOUT_SECONDS)

    def log_message(self, format: str, *args: Any) -> None:
        # Never log Authorization headers, tokens, prompts, answers, or base64 payloads.
        sys.stderr.write(
            f"[account-bridge] {self.command} {urlsplit(self.path).path}\n"
        )

    def _send_json(
        self,
        status: int,
        payload: dict[str, Any],
        *,
        extra_headers: Mapping[str, str] | None = None,
    ) -> None:
        body = json.dumps(payload, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Connection", "close")
        if extra_headers:
            for k, v in extra_headers.items():
                self.send_header(k, v)
        self.end_headers()
        self.wfile.write(body)
        self.close_connection = True

    def _require_auth(self) -> bool:
        header = self.headers.get("Authorization")
        if not verify_bearer_token(header, self.server.config.bridge_token):
            self._send_json(
                HTTPStatus.UNAUTHORIZED,
                {"error": "unauthorized", "message": "Missing or invalid Bearer token"},
                extra_headers={"WWW-Authenticate": "Bearer"},
            )
            return False
        return True

    def _read_bounded_json_body(self) -> Any:
        transfer_encoding = self.headers.get("Transfer-Encoding")
        if transfer_encoding is not None:
            raise ValidationError(
                "Transfer-Encoding is not supported; send a fixed Content-Length",
                status_code=HTTPStatus.BAD_REQUEST,
                error_code="invalid_transfer_encoding",
            )

        content_type = (self.headers.get("Content-Type") or "").split(";", 1)[0].strip().lower()
        if content_type != "application/json":
            raise ValidationError(
                "Content-Type must be application/json",
                status_code=HTTPStatus.UNSUPPORTED_MEDIA_TYPE,
                error_code="unsupported_media_type",
            )

        raw_length = self.headers.get("Content-Length")
        if raw_length is None:
            raise ValidationError(
                "Content-Length header is required",
                status_code=HTTPStatus.LENGTH_REQUIRED,
                error_code="length_required",
            )
        if not raw_length.isdigit():
            raise ValidationError(
                "Invalid Content-Length header",
                status_code=HTTPStatus.BAD_REQUEST,
                error_code="invalid_content_length",
            )
        length = int(raw_length)
        if length == 0:
            raise ValidationError("Request body must not be empty")
        if length > MAX_REQUEST_BYTES:
            raise ValidationError(
                f"Request body exceeds maximum size of {MAX_REQUEST_BYTES} bytes",
                status_code=HTTPStatus.REQUEST_ENTITY_TOO_LARGE,
                error_code="payload_too_large",
            )

        chunks: list[bytes] = []
        remaining = length
        try:
            while remaining > 0:
                chunk = self.rfile.read(min(remaining, 65536))
                if not chunk:
                    raise ValidationError("Truncated request body")
                chunks.append(chunk)
                remaining -= len(chunk)
        except TimeoutError:
            raise ValidationError(
                "Timed out reading request body",
                status_code=HTTPStatus.REQUEST_TIMEOUT,
                error_code="request_timeout",
            ) from None

        try:
            return json.loads(b"".join(chunks).decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError):
            raise ValidationError("Request body must be valid UTF-8 JSON") from None

    def do_GET(self) -> None:
        route = urlsplit(self.path).path
        if route == "/health":
            self._send_json(HTTPStatus.OK, {"ok": True})
            return

        if route == "/v1/status":
            if not self._require_auth():
                return
            providers_status = {
                name: adapter.status().to_dict()
                for name, adapter in self.server.config.providers.items()
            }
            self._send_json(
                HTTPStatus.OK,
                {
                    "ok": True,
                    "busy": self.server.inference_lock.locked(),
                    "providers": providers_status,
                },
            )
            return

        self._send_json(HTTPStatus.NOT_FOUND, {"error": "not_found", "message": "Route not found"})

    def do_POST(self) -> None:
        route = urlsplit(self.path).path
        if route != "/v1/ask":
            self._send_json(HTTPStatus.NOT_FOUND, {"error": "not_found", "message": "Route not found"})
            return

        if not self._require_auth():
            return

        try:
            raw_payload = self._read_bounded_json_body()
            allowed_models = {
                name: adapter.allowed_models
                for name, adapter in self.server.config.providers.items()
            }
            ask_req = validate_ask_payload(raw_payload, allowed_models_by_provider=allowed_models)
        except ValidationError as exc:
            self._send_json(exc.status_code, {"error": exc.error_code, "message": exc.message})
            return

        adapter = self.server.config.providers.get(ask_req.provider)
        if adapter is None:
            self._send_json(
                HTTPStatus.NOT_IMPLEMENTED,
                {"error": "unsupported_provider", "message": f"Provider '{ask_req.provider}' is not enabled"},
            )
            return

        # Fail fast for unsupported providers without acquiring or contesting the inference slot.
        if isinstance(adapter, UnsupportedProvider):
            self._send_json(
                HTTPStatus.NOT_IMPLEMENTED,
                {"error": "unsupported_provider", "message": adapter.reason},
            )
            return

        # ponytail: single global inference lock; switch to bounded queue only if multi-client latency matters.
        if not self.server.inference_lock.acquire(blocking=False):
            self._send_json(
                HTTPStatus.SERVICE_UNAVAILABLE,
                {"error": "busy", "message": "Another inference is currently in progress"},
                extra_headers={"Retry-After": "2"},
            )
            return

        try:
            answer = adapter.ask(ask_req, timeout=self.server.config.timeout_seconds)
        except ProviderError as exc:
            self._send_json(exc.status_code, {"error": exc.error_code, "message": exc.message})
            return
        except Exception:
            self._send_json(
                HTTPStatus.INTERNAL_SERVER_ERROR,
                {"error": "internal_error", "message": "Unexpected bridge error"},
            )
            return
        finally:
            self.server.inference_lock.release()

        self._send_json(HTTPStatus.OK, {"answer": answer})

    def _method_not_allowed(self) -> None:
        self._send_json(
            HTTPStatus.METHOD_NOT_ALLOWED,
            {"error": "method_not_allowed", "message": "Method not allowed"},
        )

    do_OPTIONS = _method_not_allowed
    do_PUT = _method_not_allowed
    do_DELETE = _method_not_allowed
    do_PATCH = _method_not_allowed
    do_HEAD = _method_not_allowed


def create_server(config: BridgeConfig) -> BridgeHTTPServer:
    """Create a configured BridgeHTTPServer without starting its loop."""
    return BridgeHTTPServer((config.host, config.port), config)


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        prog="account_bridge",
        description="Local stdlib HTTP bridge for officially authenticated CLI providers.",
    )
    parser.add_argument(
        "--host",
        default=DEFAULT_HOST,
        help=f"Bind address (default: {DEFAULT_HOST}; set explicitly for private LAN).",
    )
    parser.add_argument(
        "--port",
        type=int,
        default=DEFAULT_PORT,
        help=f"TCP port (default: {DEFAULT_PORT}).",
    )
    parser.add_argument(
        "--token-file",
        default=None,
        help="Path to owner-only (0600/0400) file containing high-entropy bridge token.",
    )
    parser.add_argument(
        "--timeout",
        type=float,
        default=DEFAULT_TIMEOUT_SECONDS,
        help=f"Per-request inference timeout in seconds (max {int(MAX_TIMEOUT_SECONDS)}).",
    )
    parser.add_argument(
        "--agy-bin",
        default=os.path.expanduser("~/.local/bin/agy"),
        help="Path to the installed Antigravity CLI executable.",
    )
    parser.add_argument(
        "--gemini-models",
        default=",".join(DEFAULT_GEMINI_ALLOWED_MODELS),
        help="Comma-separated allowlist of Gemini model IDs accepted by /v1/ask.",
    )
    parser.add_argument(
        "--gemini-default-model",
        default=DEFAULT_GEMINI_MODEL,
        help="Default Gemini model ID when client omits 'model'.",
    )
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    try:
        token = load_bridge_token(token_file=args.token_file)
        allowed_models = tuple(m.strip() for m in args.gemini_models.split(",") if m.strip())
        if not allowed_models:
            raise ValidationError("At least one Gemini allowed model must be configured")
        default_model = args.gemini_default_model.strip() if args.gemini_default_model else None
        if default_model and default_model not in allowed_models:
            raise ValidationError("--gemini-default-model must be included in --gemini-models")

        config = BridgeConfig(
            bridge_token=token,
            host=args.host,
            port=args.port,
            timeout_seconds=args.timeout,
            providers={
                "gemini": AgyAcpProvider(
                    executable=Path(args.agy_bin),
                    allowed_models=allowed_models,
                    default_model=default_model,
                ),
                "chatgpt": UnsupportedProvider(
                    name="chatgpt",
                    backend="codex-exec",
                    reason=DEFAULT_CODEX_UNSUPPORTED_REASON,
                ),
            },
        )
    except ValidationError as exc:
        sys.stderr.write(f"Configuration error: {exc.message}\n")
        return 2

    server = create_server(config)
    bound_host, bound_port = server.server_address[:2]
    sys.stderr.write(
        f"[account-bridge] Listening on http://{bound_host}:{bound_port} "
        "(Ctrl-C to stop)\n"
    )
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
    return 0
