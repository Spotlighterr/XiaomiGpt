# Source provenance and licensing

The standalone Android project was imported from https://github.com/mahdi-malv/xiaolink at commit `f74fa8d034cdfce3fe9d3baadf4b756763a29676`. Upstream source remains subject to its GPL-3.0 license. This development integration is kept with that license; no claim is made that the combined source is solely MIT.

The native account and speech files under `app/src/main/java/io/github/spotlighterr/xiaomigpt/` originate from the MIT-licensed XiaomiGpt repository at `6f975fa948351db888de25ccbc74bd9b1eec4dfc`. A copy of that license is included as `third_party/XIAOMIGPT_MIT_LICENSE`. Their corresponding protocol/SSE/language tests are imported with the same attribution. `VisualInput.kt` and the `accounts/` implementation are integration additions.

The original XiaomiGpt project and its root MIT license are preserved as a separate build. The integration application ID is different, so its private Android Keystore and app data do not replace the original app's data.

Vosk, JNA, AndroidX, OkHttp and other dependencies retain their respective upstream licenses. Vosk speech-model attribution and license notices are preserved in assets/licenses. No Xiaomi APK, decompiled proprietary code or OAuth credential is added to source control.

The Python account bridge was contributed through the user's explicitly requested Antigravity CLI session and reviewed by Codex. Its contribution notes document the CLI capability checks and intentional limitations. No CLI credential files are part of the integration.
