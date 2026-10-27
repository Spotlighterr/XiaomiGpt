# XiaomiGpt

An experimental Android app written in Kotlin for Xiaomi AI Glasses.

The goal is hands-free English conversations with ChatGPT, using the glasses' microphone, camera and speakers.

## Current status

**[v0.2.0-assistant-preview — pre-release](https://github.com/Spotlighterr/XiaomiGpt/releases/tag/v0.2.0-assistant-preview)**

The assistant preview implements official ChatGPT sign-in, account-specific model selection, offline English recognition with Vosk, and spoken replies through Bluetooth. It requires an eligible ChatGPT account and plan-use consent. Download the English model once (40 MB) and install an offline English Android TTS voice.

The user confirmed Bluetooth audio on the target setup. The new assistant has not yet been validated end to end on the glasses or with a real ChatGPT account. Keep the app open during conversations. Android assistant-role activation and the glasses gesture need device testing. Camera access, automatic photo import and independent Xiaomi pairing are not implemented.

Target setup: **M2442G1 / firmware 1.8.12.0 / Xiaomi 15 Ultra / Android 16**.

## Build

Requires JDK 17 and Android SDK 36.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

On Windows, use `gradlew.bat`. Build and tests pass; lint has no errors.

[Assistant setup and limitations](docs/ASSISTANT_PREVIEW_v1.md) · [Connection testing](docs/PROBE_TEST_GUIDE_v2.md) · [Companion-app direction](docs/COMPANION_DIRECTION_v2_2026-10-27.md)

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
