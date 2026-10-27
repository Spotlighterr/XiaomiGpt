# XiaomiGpt

An experimental Android app written in Kotlin for Xiaomi AI Glasses.

The goal is hands-free English conversations with ChatGPT, using the glasses' microphone, camera and speakers.

## Current status

**[v0.1.1-probe — pre-release](https://github.com/Spotlighterr/XiaomiGpt/releases/tag/v0.1.1-probe)**

This build provides a guided Bluetooth microphone test. Camera, local Wi-Fi and button diagnostics are under Advanced. It can also share a test photo with the official ChatGPT app when supported.

Automatic AI conversations, ChatGPT sign-in and independent Xiaomi pairing are not implemented yet. Initial user testing found Bluetooth audio, but glasses-camera access is not working yet.

Target setup: **M2442G1 / firmware 1.8.12.0 / Xiaomi 15 Ultra / Android 16**.

## Build

Requires JDK 17 and Android SDK 36.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

On Windows, use `gradlew.bat`. Build and tests pass; lint has no errors.

[Testing guide](docs/PROBE_TEST_GUIDE_v2.md) · [Companion-app direction](docs/COMPANION_DIRECTION_v2_2026-10-27.md) · [Research notes](docs/RESEARCH_v1_2026-10-27.md)

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

These are references, not claims of endorsement. Xiaomi APKs, decompiled code, credentials and local toolchains are not included in this repository.
