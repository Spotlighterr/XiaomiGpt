# XiaomiGpt

An experimental Android app written in Kotlin for Xiaomi AI Glasses.

The goal is hands-free Vietnamese and English conversations with ChatGPT, using the glasses' microphone, camera and speakers.

## Current status

**[v0.3.0-assistant-preview — pre-release](https://github.com/Spotlighterr/XiaomiGpt/releases/tag/v0.3.0-assistant-preview)**

The assistant preview implements official ChatGPT sign-in, account-specific model selection, offline Vietnamese/English recognition with Vosk, and spoken replies through Bluetooth. It requires an eligible ChatGPT account and plan-use consent. Select a language, download its speech model once (Vietnamese: 34 MB; English: 41 MB), and install an offline Android TTS voice for that language.

The user confirmed Bluetooth audio, ChatGPT sign-in and an assistant response on the target setup. Vietnamese speech and the new native Android assistant service still need device testing. Select XiaomiGpt as the default assistant to test system activation when the phone is unlocked. A local event log helps determine whether an invocation reaches this app. The glasses may still handle their Xiao AI gesture internally.

Screen-off/locked conversations, camera access, automatic photo import and independent Xiaomi pairing are not implemented.

**Release gate:** further releases are on hold until holding the button on the user's glasses is confirmed to invoke XiaomiGpt and complete a voice turn through the glasses. v0.3.0 provides an Android activation route for testing; it has not yet passed that hardware check.

Target setup: **M2442G1 / firmware 1.8.12.0 / Xiaomi 15 Ultra / Android 16**.

## Build

Requires JDK 17 and Android SDK 36.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

On Windows, use `gradlew.bat`. Build and tests pass; lint has no errors.

[Vietnamese and glasses activation](docs/GLASSES_ACTIVATION_v1.md) · [Assistant setup and limitations](docs/ASSISTANT_PREVIEW_v1.md) · [Connection testing](docs/PROBE_TEST_GUIDE_v2.md)

Project code is [MIT licensed](LICENSE). Third-party speech licenses are included in `app/src/main/assets/licenses/`.

## Credits

Thanks to these authors and communities for their research, tools and architecture references:

- **idootop / Del Wang** — [mi-gpt](https://github.com/idootop/mi-gpt), [MigicCube](https://github.com/idootop/MigicCube) and [HyperSight](https://del.wang/projects/product/ase).
- **Mentra Community** — [Xiaomi glasses research in MentraOS](https://github.com/Mentra-Community/MentraOS/blob/dev/agents/xiaomi_glasses_reverse_engineering.md).
- **moyslav** — [xiaomi-ai-glasses](https://github.com/moyslav/xiaomi-ai-glasses).
- **jacuboffsky** — [xiaomi-glasses-network-fix](https://github.com/jacuboffsky/xiaomi-glasses-network-fix).
- **zero2005x** — [RokidAIAssistant](https://github.com/zero2005x/RokidAIAssistant).
- **morluto and REA contributors** — [REA](https://github.com/morluto/rea).
- **macedonga** — [Visor](https://github.com/macedonga/visor).
- **FerSaiyan** — [CyanBridge](https://github.com/FerSaiyan/Alternative-HeyCyan-App-and-SDK).
- **Alpha Cephei and contributors** — [Vosk](https://github.com/alphacep/vosk-api) and the [English speech model](https://alphacephei.com/vosk/models).
- **Java Native Access contributors** — [JNA](https://github.com/java-native-access/jna).

These are references, not claims of endorsement. Xiaomi APKs, decompiled code, credentials and local toolchains are not included in this repository.
