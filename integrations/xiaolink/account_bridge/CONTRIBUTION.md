# Account Bridge Contribution Notes

This directory implements a Python 3 standard-library local HTTP bridge (`account_bridge`) so the private Android app can call Gemini through an existing, officially authenticated Antigravity CLI (`~/.local/bin/agy`) without ever reading, extracting, or returning provider OAuth tokens.

## Prerequisites and Honest Limitations

1. **Installed and logged-in CLI required**: The bridge does not handle Google/OpenAI OAuth and never reads credential files. The host operator must install and sign in to `~/.local/bin/agy` beforehand.
2. **Gemini via Antigravity CLI (`agy acp`)**:
   - Verified against installed `agy` v1.5.15 (`agy --help`, `agy help acp`, official `antigravity.google/docs/cli/reference.md`, and bundled `hooks.md`).
   - `agy --print` has no `--image` flag, and `--input-format stream-json` documents `"text"` as the only supported content block type. Because `agy --print` cannot receive camera JPEG bytes without file-reading tools, the bridge uses `agy acp --stdio` instead.
   - `agy acp` advertises `promptCapabilities.image: true` and natively accepts base64 JPEG image blocks in `session/prompt`, so camera bytes reach Gemini without exposing filesystem tools.
   - Tool execution is blocked fail-closed per request by creating an isolated `0700` working directory containing `.agents/hooks.json` with a `PreToolUse` wildcard (`"matcher": "*"`) hook returning `{"decision": "deny"}`, passing `--sandbox --include-default-customizations=false --disable-telemetry`, sending `mcpServers: []`, and cancelling any ACP `session/request_permission` callback. Verified empirically on `agy` v1.5.15 against `run_command`, `read_file`, and `search_web`.
3. **ChatGPT via Codex CLI (`codex`) fails closed**:
   - Verified against installed `codex-cli 0.162.1`. Even with `--ignore-user-config --ignore-rules -s read-only --ephemeral --skip-git-repo-check -c 'web_search="disabled"' -c 'mcp_servers={}'` and `--disable` flags (`shell_tool`, `unified_exec`, `view_image`, `multi_agent`, `plugins`, `apps`, `browser_use`, `computer_use`, `image_generation`, `sleep_tool`, `memories`, `skill_search`, `tool_suggest`, `hooks`, `worktrees`), `codex exec` still declares `functions.exec` and `collaboration.*` tools in the model schema.
   - Because untrusted prompt/camera text must not reach an agent with active execution tools, the `chatgpt` provider fails closed (`501 unsupported_provider`, `available: false` in `/v1/status`). The Android app retains its native ChatGPT login.
4. **Gemini CLI (`gemini`)**: Not installed on this host, so no unverified `gemini` adapter is enabled.

## HTTP Contract

- `GET /health` (unauthenticated): returns `200` with `{"ok":true}` only.
- `GET /v1/status` (Bearer auth): returns `{"ok":true,"busy":bool,"providers":{...}}` with no PII or tokens.
- `POST /v1/ask` (Bearer auth, `Content-Type: application/json`):
  - Request: `{"provider":"gemini"|"chatgpt","question":"...","image_base64":"...","model":"...","history":[{"role":"user"|"assistant","text":"..."}]}`
  - Response: `200` with `{"answer":"..."}` (or `503` `{"error":"busy",...}` when another inference is in progress, `501` for fail-closed `chatgpt`).

## Security Controls

- High-entropy bridge token required via `ACCOUNT_BRIDGE_TOKEN` or owner-only (`0600`/`0400`, non-symlink) `--token-file` / `ACCOUNT_BRIDGE_TOKEN_FILE`; compared with `hmac.compare_digest`; never logged and scrubbed from child environments.
- Loopback bind (`127.0.0.1`) by default; pass `--host` explicitly for private LAN.
- No CORS headers, no redirects, no `shell=True`, fixed `Content-Length` required.
- Limits: max request 3 MiB, decoded JPEG max 2 MiB with SOI/marker/EOI magic validation, bounded question/history/output, max 120s timeout with process-group termination, single-flight inference lock.
- Server-side allowlist for model IDs (`--gemini-models`) and executable path (`--agy-bin`).
- Isolated `0700` per-request working directory (`IsolatedRequestDir`) with `0600` temp JPEG helper (`write_jpeg`), always removed on exit/error/timeout.

## Running

```bash
export ACCOUNT_BRIDGE_TOKEN="$(python3 -c 'import secrets; print(secrets.token_urlsafe(32))')"
python3 -m account_bridge --port 8765
```

## Tests

Codex integration review added a deadline for large stdin writes (a provider that stops reading must not hang an image request), reduced maximum output to 8,000 characters to fit the Android JSON cap, removed raw ACP error details from HTTP responses, and added a regression test. The reviewed suite has 14 tests.

The bridge removes its per-request directory, camera bytes, hook and log files. It does not claim to disable the official CLI's own account/session storage or Google's retention. An isolated `--app-data-dir` was tested but made the existing account unavailable, so it is not enabled and no credentials were copied to work around it. Keep provider-side storage/retention settings under the account owner's control.

```bash
python3 -m unittest discover -s account_bridge/tests -v
```
