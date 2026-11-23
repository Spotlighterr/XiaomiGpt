# XiaomiGpt Link development integration

This standalone Android build combines the XiaoLink glasses transport/UI with XiaomiGpt's native ChatGPT account login, offline Vietnamese/English recognition and verified Bluetooth speech playback. The existing app in the repository root is preserved.

Upstream snapshot: `mahdi-malv/xiaolink@f74fa8d034cdfce3fe9d3baadf4b756763a29676`. XiaoLink's GPL-3.0 license remains in `LICENSE`; imported XiaomiGpt files retain MIT attribution in `third_party/XIAOMIGPT_MIT_LICENSE`. See `THIRD_PARTY_INTEGRATION.md`.

Application ID: `io.github.spotlighterr.xiaomigpt.integrated`. Display name: **XiaomiGpt Link Dev**. This installs separately from the legacy XiaomiGpt and XiaoLink apps. No public release is authorized until physical glasses invocation/microphone/speaker validation and user confirmation.

The user confirmed on 2026-11-23 that the current phone is **Sony Xperia 1 V**, replacing the earlier Xiaomi 15 Ultra. Android version/current glasses firmware are pending device verification. The integrated assistant uses SPP/public Android APIs and does not require HyperOS or Android's default-assistant role.

## What is wired

- Xiaomi account key fetch and WearAuthV2/SPP connection from XiaoLink.
- AIVS voice/touch wake events feed the account assistant; suspect/inhibition events do not activate it.
- mSBC from the glasses microphone is decoded locally, then transcribed with a verified Vosk model.
- Optional new JPEG from the glasses camera is included in the selected provider request. No fallback to a cached image.
- Native browser-based ChatGPT sign-in with PKCE/state/ID-token checks, account model discovery, streaming Responses and image input.
- Gemini account access through the private Python account bridge and authenticated Antigravity ACP on the user's server. This is a Google account signed in to Antigravity CLI, not an embedded Gemini web session or an API-key substitute.
- Reply synthesis uses an offline Android TTS voice; speech requires the matched glasses communication route. Loss of the route cancels playback.
- In-memory turn generations reject stale inference results. Stop/disconnect cancels the mobile request and exits image mode. A server-side CLI request can finish before the bridge releases its inference slot; its stale reply is not spoken by the app.
- Raw audio and camera JPEGs are not saved. App credential strings are encrypted with Android Keystore, and backup/transfer exclusions are explicit.

## Build

From the XiaomiGpt repository root on the existing Windows toolchain:

```powershell
& .\scripts\build-integrated.ps1
```

Or build this standalone directory with its Gradle wrapper and a configured Android SDK. It uses Android SDK 37, AGP 9.5.0-alpha07 and the upstream Gradle/JDK daemon toolchain. No API key is required by the account assistant.

APK: `app/build/outputs/apk/debug/app-debug.apk`. A successful build is not hardware acceptance.

## Initial setup

1. Install the local development APK. Pair the glasses in Android Bluetooth Settings.
2. In Device/Connection, sign in to the Xiaomi account already owning the glasses, select the glasses key and connect. Do not reset/rebind working glasses for an initial test.
3. Open **Trợ lý AI**. For ChatGPT, use **Đăng nhập ChatGPT**, complete browser consent and load/select an account model.
4. For Gemini, the server operator signs in through official Antigravity CLI, starts the private account bridge, and enters its URL/bridge pairing token in the app. The token authenticates this private bridge; it is not a Gemini API key.
5. Download the Vietnamese or English recognition model in the app. Install the corresponding offline Android Text-to-speech voice in system settings.
6. Choose whether a new glasses-camera photo accompanies each question. First try the explicit phone-side question button, then validate the actual glasses gesture separately.

The server bridge defaults to loopback. Explicit private-LAN binding or a private encrypted network is needed for a phone on another device. Do not publish an unauthenticated public endpoint.

## Honest limits

- Neither sign-in eligibility for every account nor M2442G1 firmware compatibility is implied by build/test results. Provider limits and permission failures are shown to the user.
- This workflow transcribes audio locally and generates TTS locally. It does not use ChatGPT's native voice mode or the Gemini web app's Live session.
- Native ChatGPT account sign-in must be completed on the phone. Google account credentials remain under the official CLI's control on the private server.
- The exact physical gesture, locked-screen behavior, glasses mic/loa and camera freshness need a real device check. There was no phone attached to ADB when implementation began.
- The available JPEG voice packets do not echo a verified capture request ID. Bounded assembly and cancellation are implemented, but a very late FIRST_IMAGE packet overlapping a new capture still needs firmware-level testing. No arbitrary gesture enum is written to mask this limitation.
- User-visible diagnostics and short conversation context are held in memory. No automatic disk history is enabled.
- Existing gallery/live stream/developer features are inherited from XiaoLink and have separate hardware limits described by upstream. This integration does not establish their runtime compatibility.
