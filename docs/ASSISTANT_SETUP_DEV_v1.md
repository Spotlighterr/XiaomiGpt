# Unreleased development: Android assistant setup

Version: 0.3.1-dev. Date: 2026-10-27. This is not a release or a passed glasses-button gate.

## User feedback

On the target Xiaomi 15 Ultra / Android 16, the user reports both assistant setup/test buttons are clickable but do not open anything. The TTS button does open the phone's Text-to-speech settings. We have not observed the actual RoleManager result, selected service, or HyperOS assistant list yet.

The phone's TTS page is intentional: the phone synthesizes speech in the selected language, and XiaomiGpt routes playback to the glasses over Bluetooth. No TTS model is installed on the glasses. The button and its adjacent explanation now state this explicitly.

## Code findings and development changes

- 0.3.0 did not handle `onActivityResult` for the assistant-role request. A canceled or ungranted request could return without useful feedback. The dev build records the result and checks actual native-service selection when the Activity resumes; it does not treat `RESULT_OK` or role ownership alone as a working native assistant session.
- A separate **Mở cài đặt trợ lý mặc định của Android** button tries the role-specific default-app page, then Default Apps, Voice Input, and general Settings if individual entry points are absent. It uses standard intents and explicit exception handling, without changing secure settings or assuming an OEM component name.
- The role request and service-state reads are guarded. Returning from a denied/canceled request gives a visible explanation and a route to manual settings. It does not infer that HyperOS forbids third-party assistants merely because a request fails.
- The test button explicitly explains when native-service selection has not been confirmed. When selected, a ResultReceiver acknowledges whether the service is waiting for readiness, accepted the show-session request, rejected it, or shut down. A ten-second timeout distinguishes an accepted request from an actually observed incoming assistant-session intent.
- Service-test state is scoped to a request ID. Old callbacks/timeouts cannot overwrite a newer test, and late failures cannot overwrite an opened-session result. Background timeout notices are deferred until the Activity is visible.
- Setup and TTS buttons stay available during model discovery/download, except while a voice turn owns audio. OAuth/model changes still obey their existing busy-state rules.
- **Nhật ký gọi trợ lý** includes live role availability/ownership, native-service selection (`unknown` if the read fails), last role result, settings-route index and service-test phase, plus bounded event metadata. It includes no account tokens, conversation text or other apps' contents.

Settings-route indices: 0 = role-specific default app; 1 = Default Apps; 2 = Voice Input; 3 = general Settings. An intent being accepted does not prove the correct page appeared; that still requires device feedback.

## Check on the device

First use the phone's Settings search for Digital assistant / Trợ lý số / Ứng dụng trợ lý. Determine whether there is a default-assistant chooser and whether XiaomiGpt appears in it. This separates an intent-opening issue from service eligibility or an OEM policy limitation.

The local development APK may be installed for this check without creating a public release. Select XiaomiGpt if the chooser offers it, return to the app and copy the activation/setup report. If XiaomiGpt is absent or there is no chooser, report that exact observation rather than repeatedly pressing the same test button.

Only after native setup works should the existing hold-button test proceed. The release gate in [AGENTS.md](../AGENTS.md) remains unchanged: physical glasses hold → glasses microphone → assistant response through the glasses, explicitly confirmed by the user. No new release tag, release, or release-asset upload is permitted before that gate passes.

## Sources

- [Android RoleManager role requests and result codes](https://developer.android.com/reference/android/app/role/RoleManager)
- [AOSP default-assistant settings controller and role-specific manage action](https://android.googlesource.com/platform/packages/apps/Settings/+/acf789f35a/src/com/android/settings/applications/assist/DefaultAssistPreferenceController.java)

## Local validation

33 JVM tests passed, including service-test timeout, stale callback and opened-session regression cases. Android lint: 0 errors, 16 style/dependency/localization warnings. Debug assembly, APK signature and 16 KB native/APK alignment checks passed. The APK is signed with the same local project key as 0.3.0.

Local candidate only: `_archive/dev-builds/v0.3.1-dev/xiaomigpt-v0.3.1-dev.apk`, with `SHA256SUMS.txt` beside it. No release tag, GitHub release or release asset was created. This validation does not confirm the phone's role picker, native binding, glasses gesture or physical audio route.
