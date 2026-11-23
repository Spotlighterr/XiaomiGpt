# Combined glasses development build

The user explicitly authorizes combining XiaomiGpt and XiaoLink, with Antigravity CLI contributing code. Required outcome: account-based ChatGPT and Gemini, actual glasses invocation, microphone, speakers, and fresh camera image input. Preserve legacy app and releases.

Keep this standalone Android project in development. No tags, public APK assets, publishing, or claim of hardware success until the parent project's physical device gate passes. Upstream XiaoLink code is GPL-3.0; imported XiaomiGpt source retains its MIT attribution in third_party/.

Never log credentials or raw authentication packets. Do not save raw voice or AI camera frames by default. Keep tokens in Android Keystore-protected no-backup storage. Reject stale camera/AI callbacks and stop output when the glasses audio route disappears. Account capabilities must be checked rather than inferred from sign-in success.
