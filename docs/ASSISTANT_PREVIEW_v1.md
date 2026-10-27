# XiaomiGpt assistant preview

Date: 2026-10-27. Target: M2442G1 firmware 1.8.12.0, Xiaomi 15 Ultra, Android 16.

## Confirmed device feedback

The user confirmed that audio works with the existing probe. This does not yet establish that the new speech recognizer, ChatGPT authorization, spoken replies, or assistant activation work end to end.

## Try it

1. Install the assistant-preview APK over the previous app. Open XiaomiGpt and tap **Continue with ChatGPT**. The system browser handles your credentials; XiaomiGpt does not ask for your password or an API key. Finish authorization and return to XiaomiGpt. An eligible Plus/Pro account and consent to use its plan are required; sign-in alone does not guarantee inference access.
2. Wait for the account's model list. Select a model. **Manage Usage** opens ChatGPT's usage settings. Requests use the selected account's plan allowance.
3. Tap **Tải nhận dạng tiếng Anh · 40 MB** once. Allow storage headroom for the extracted model. The app downloads the official English Vosk model, verifies its SHA-256, and installs it into private app storage.
4. Tap **Cấp quyền / Làm mới kết nối kính**. Allow Nearby devices and microphone. If several Bluetooth headsets are connected, select the glasses.
5. Tap **Nói với trợ lý**, wait for **Đang nghe qua kính**, and ask a short English question. A detected speech endpoint sends it automatically; **Tôi nói xong · Gửi ngay** ends recording sooner. Recording is limited to 20 seconds.
6. Check **Bạn nói**, then the answer text. The app reads the completed reply through the selected Bluetooth route. If the phone lacks an offline English TTS voice, use **Cài giọng đọc tiếng Anh** to install one and retry. The written answer remains available when playback fails.

Use **Gửi câu đã nhập** to isolate authentication/inference/playback from speech recognition. Use **Cuộc trò chuyện mới** to clear the in-memory conversation.

Keep the assistant screen open. Leaving it, locking the phone, losing audio focus, or tapping Stop cancels the turn. This version does not provide background listening, a wake word, or full-duplex interruption.

## Activation

**Đặt XiaomiGpt làm trợ lý mặc định** requests Android's assistant role. The Activity handles `ACTION_ASSIST` and `ACTION_VOICE_COMMAND`. Assistant/headset-hook events delivered while this Activity is visible can also start a turn or finish speech. No service intercepts all Bluetooth buttons.

Android assistant-role selection, system invocation on HyperOS, and the glasses' temple gesture are unverified. The glasses may consume their Xiao AI gesture internally. This preview does not reprogram that gesture or replace the firmware's assistant backend.

## Authentication and inference

- OAuth authorization-code flow in the system browser, fresh PKCE S256/state/nonce per attempt, listener bound only to `127.0.0.1` at an available port with `/auth/callback`.
- Registers as XiaomiGpt with `dynamic_agent_client` only for new registrations. Persists the issued client ID and an opaque stable host ID, reuses registrations for returning accounts, and separates saved account records.
- Verifies ID-token RS256 signatures against OpenAI's HTTPS JWKS, issuer, audience, expiration, nonce and returning subject before activating credentials. Tokens are encrypted with an Android Keystore AES-GCM key and stored atomically under `noBackupFilesDir`.
- Refreshes expiring sessions serially and preserves rotating refresh tokens. Sign-out attempts revocation before clearing local tokens; unconfirmed remote revocation is reported.
- Loads models from the account's catalog. Uses the public Responses API with `store:false`, `stream:true`, inline context and no backend-api endpoints. A stream must reach `response.completed`; partial/failed replies are not spoken or committed as successful turns.
- No client secret, borrowed OAuth client ID, ChatGPT cookie extraction, or API key is used. No credentials were available here to complete a real sign-in or inference test.

This uses ChatGPT plan inference with local speech processing. It does not embed the official ChatGPT Voice experience or import ChatGPT conversation history.

## Audio and privacy

`AudioRecord` PCM input is checked for a Bluetooth communication route before Vosk receives samples. A route change cancels recording. Audio is processed locally, not uploaded or retained. Question text and up to six prior exchanges are sent to OpenAI only for the active conversation.

English TTS must have a local voice. It synthesizes private temporary audio; MediaPlayer selects the headset and checks its active route. Temporary reply audio is deleted after playback or cancellation. As with any Android routing request, selection is not proof before playback starts; the app stops if the observed route differs. Validate this on the actual phone/glasses.

History and displayed transcripts are not saved to Activity instance state or included in probe reports. Tokens and authorization URLs are not logged. Existing diagnostic photo captures remain a separate, explicit flow.

## Model and sources

Vosk Android `0.3.75`, JNA `5.18.1`, model `vosk-model-small-en-us-0.15` (41,205,931 compressed bytes). Download SHA-256 pinned from the official HTTPS source:

```text
30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498
```

The app limits archive size, extracted size and entry count, rejects extraction paths outside its staging directory, and marks installation complete only after checking required model files. The small model's recognition accuracy, especially over Bluetooth and for names, needs device testing.

- [Official ChatGPT registration](https://developers.openai.com/siwc/token-sharing-open-source/sign-in)
- [Accounts and sessions](https://developers.openai.com/siwc/token-sharing-open-source/profiles-and-sessions)
- [Models and inference](https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference)
- [Preview limitations: no audio input/transcription in this flow](https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations)
- [Official Vosk Android demo](https://github.com/alphacep/vosk-android-demo)
- [Vosk model catalog](https://alphacephei.com/vosk/models)
- [Android assistant role](https://developer.android.com/reference/androidx/core/role/RoleManagerCompat#ROLE_ASSISTANT)

Camera integration is deferred. The probe remains available under **Kiểm tra kết nối / Camera đang phát triển**. Independent Xiaomi pairing and automatic photo import are not implemented.

## Validation boundary

The JVM suite covers the PKCE reference vector, callback state/path/client binding, consent denial, signed/tampered RSA ID tokens, identity claims, and successful/interrupted/failed SSE completion, plus the existing probe policies and audio-confirmation state (16 tests total). Android lint and debug assembly are required before packaging. Native ELF LOAD segments and APK zip alignment are checked for 16 KB pages on arm64-v8a and x86_64. APK signature verification is required by the packaging script.

These checks do not validate Android Keystore behavior, browser round-trip/process survival, real account eligibility, Vosk accuracy, English TTS availability, Bluetooth routing, or gesture activation on the physical phone. Those are the next user-device checks, in the order given above.
