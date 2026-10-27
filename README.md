# XiaomiGpt

An experimental Android app written in Kotlin for Xiaomi AI Glasses.

The goal is hands-free English conversations with ChatGPT, using the glasses' microphone, camera and speakers.

## Current status

**[v0.1.0-probe — pre-release](https://github.com/Spotlighterr/XiaomiGpt/releases/tag/v0.1.0-probe)**

This first build checks camera access, Bluetooth audio, local Wi-Fi connectivity and button events. It can also share a test photo with the official ChatGPT app.

Automatic AI conversations, ChatGPT sign-in and glasses controls are not implemented yet. Compatibility with the glasses has not been verified on hardware.

Target setup: **M2442G1 / firmware 1.8.12.0 / Xiaomi 15 Ultra / Android 16**.

## Build

Requires JDK 17 and Android SDK 36.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

On Windows, use `gradlew.bat`. Build and tests pass; lint has no errors.

[Testing guide](docs/PROBE_TEST_GUIDE_v1.md) · [Implementation status](docs/IMPLEMENTATION_STATUS_v1.md) · [Research notes](docs/RESEARCH_v1_2026-10-27.md)

## Credits

Thanks to these authors and communities for their research, tools and architecture references:

- **idootop / Del Wang** — [mi-gpt](https://github.com/idootop/mi-gpt), [MigicCube](https://github.com/idootop/MigicCube) and [HyperSight](https://del.wang/projects/product/ase).
- **Mentra Community** — [Xiaomi glasses research in MentraOS](https://github.com/Mentra-Community/MentraOS/blob/dev/agents/xiaomi_glasses_reverse_engineering.md).
- **moyslav** — [xiaomi-ai-glasses](https://github.com/moyslav/xiaomi-ai-glasses).
- **jacuboffsky** — [xiaomi-glasses-network-fix](https://github.com/jacuboffsky/xiaomi-glasses-network-fix).
- **zero2005x** — [RokidAIAssistant](https://github.com/zero2005x/RokidAIAssistant).
- **morluto and REA contributors** — [REA](https://github.com/morluto/rea).

These are references, not claims of endorsement. Xiaomi APKs, decompiled code, credentials and local toolchains are not included in this repository.
