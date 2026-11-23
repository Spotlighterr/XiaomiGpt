# XiaomiGpt working instructions

## Release gate: actual glasses-button activation

User instruction, 2026-10-27: do not publish another release until holding the
button on the user's glasses actually invokes the assistant and the complete
voice turn works. Version 0.3.0's Android assistant entry point is not proof.

- Keep implementation changes and candidate builds in development. Local
  builds, tests, research and source commits may continue.
- Do not create another release tag, GitHub release/pre-release or upload
  release assets until the hardware gate below passes, unless the user
  explicitly changes this requirement.
- Do not count the in-app Talk button, app-generated assistant-session test,
  phone power-button gesture, unit tests or manifest validation as glasses
  activation success.
- Preserve existing releases and artifacts; this instruction does not request
  deleting or unpublishing 0.3.0 or any older version.

Required hardware evidence on the user's M2442G1 / firmware 1.8.12.0 and Xiaomi
15 Ultra / Android 16:

1. Record the actual glasses gesture used. Start from the phone home screen,
   with XiaomiGpt outside the foreground; record whether the phone is locked
   or unlocked.
2. Holding the glasses button invokes XiaomiGpt without a phone-side tap.
3. The question is captured from the glasses microphone and the response is
   heard from the glasses speakers.
4. Obtain the user's explicit confirmation of that complete flow, alongside
   available activation diagnostics. An event log alone cannot identify a
   physical glasses button.

State any remaining lock-screen or firmware limitations plainly. Do not claim
that Xiao AI's internal button handling has been replaced merely because
XiaomiGpt is selected as Android's default assistant.

## Current phone correction — PO update 2026-11-23

The user now uses a **Sony Xperia 1 V**, replacing the Xiaomi 15 Ultra. Apply
the physical glasses invocation/microphone/speaker release gate above to the
Sony phone. Do not require or assume HyperOS. The Sony Android version and
current glasses firmware remain unverified until read from the actual device.
Older Xiaomi 15 Ultra / Android 16 entries describe historical tests only.

Authorized development integration is on `feature/xiaolink-account-integration`
under `integrations/xiaolink/`, with Antigravity CLI contributing the Gemini
account bridge. Keep legacy app and releases intact. Do not claim camera or
glasses-button success from synthetic vision tests or phone-side test buttons.
