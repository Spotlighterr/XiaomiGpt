# Unreleased development: Android assistant setup

Current version: 0.3.2-dev. Date: 2026-10-28. This is not a release or a passed glasses-button gate. The older 0.3.1-dev candidate is preserved separately.

## User feedback

On the target Xiaomi 15 Ultra / Android 16, the user reports both assistant setup/test buttons are clickable but do not open anything. The TTS button does open the phone's Text-to-speech settings. We have not observed the actual RoleManager result, selected service, or HyperOS assistant list yet.

The latest clarification is **no page opens from the button**. An earlier multiple-choice answer seemed to indicate a chooser without XiaomiGpt; that interpretation was withdrawn after the user clarified. Do not treat this as evidence that HyperOS has filtered XiaomiGpt out of its assistant list.

The phone's TTS page is intentional: the phone synthesizes speech in the selected language, and XiaomiGpt routes playback to the glasses over Bluetooth. No TTS model is installed on the glasses. The button and its adjacent explanation now state this explicitly.

## Code findings and development changes

- AOSP's assistant-role definition explicitly has `requestable=false`. `isRoleAvailable()` does not establish requestability. 0.3.0 relied on `createRequestRoleIntent`, and 0.3.1-dev only handled its cancellation. 0.3.2-dev removes that request path entirely. This corrects a documented API mismatch; it does not prove this was the only cause of the observed phone behavior.
- **Chọn trợ lý mặc định trong Cài đặt** directly opens the public Default Apps settings action, with Voice Input and general Settings fallbacks if an action is rejected. **Thử trang cài đặt giọng nói khác** gives an explicit alternate entry that starts with Voice Input instead. The app no longer invokes the role-specific manage action used internally by system Settings.
- Service-state reads remain guarded. Setup does not change secure settings or assume an OEM component name. An accepted settings intent is reported as a request to open the page, not proof that a chooser appeared. No conclusion about OEM filtering or an invalid service declaration is made without device evidence.
- The test button explicitly explains when native-service selection has not been confirmed. When selected, a ResultReceiver acknowledges whether the service is waiting for readiness, accepted the show-session request, rejected it, or shut down. A ten-second timeout distinguishes an accepted request from an actually observed incoming assistant-session intent.
- Service-test state is scoped to a request ID. Old callbacks/timeouts cannot overwrite a newer test, and late failures cannot overwrite an opened-session result. Background timeout notices are deferred until the Activity is visible.
- Setup and TTS buttons stay available during model discovery/download, except while a voice turn owns audio. OAuth/model changes still obey their existing busy-state rules.
- **Nhật ký gọi trợ lý** includes live role availability/ownership, native-service selection (`unknown` if the read fails), public-settings route and service-test phase, plus bounded event metadata. It includes no account tokens, conversation text or other apps' contents.

New `SETTINGS_PUBLIC_ROUTE` indices: 0 = Default Apps; 1 = Voice Input; 2 = general Settings. Older `SETTINGS_ROUTE` entries belong to the earlier dev implementation and use its older route numbering. Neither event proves the correct page appeared; that still requires device feedback.

## Check on the device

First establish that a Settings page actually opens, using the public Default Apps entry or the alternate Voice Input entry. Only after a chooser is visible should its candidate list be examined. Determine whether XiaomiGpt appears there. This separates an intent-opening issue from service eligibility or an OEM policy limitation.

The local development APK may be installed for this check without creating a public release. Select XiaomiGpt if the chooser offers it, return to the app and copy the activation/setup report. If XiaomiGpt is absent or there is no chooser, report that exact observation rather than repeatedly pressing the same test button.

Only after native setup works should the existing hold-button test proceed. The release gate in [AGENTS.md](../AGENTS.md) remains unchanged: physical glasses hold → glasses microphone → assistant response through the glasses, explicitly confirmed by the user. No new release tag, release, or release-asset upload is permitted before that gate passes.

## Sources

- [Android RoleManager role requests and result codes](https://developer.android.com/reference/android/app/role/RoleManager)
- [AOSP default-assistant settings controller and role-specific manage action](https://android.googlesource.com/platform/packages/apps/Settings/+/acf789f35a/src/com/android/settings/applications/assist/DefaultAssistPreferenceController.java)
- [AOSP role definitions: ASSISTANT is non-requestable](https://android.googlesource.com/platform/packages/modules/Permission/+/refs/heads/main/PermissionController/res/xml/roles.xml)
- [AOSP assistant qualification checks](https://android.googlesource.com/platform/packages/modules/Permission/+/b9a93e0e9285e16b729294fee7005e64473edb68/PermissionController/src/com/android/permissioncontroller/role/model/AssistantRoleBehavior.java)

## Local validation

33 JVM tests passed, including service-test timeout, stale callback and opened-session regression cases. Android lint: 0 errors, 16 style/dependency/localization warnings. Debug assembly, APK signature and 16 KB native/APK alignment checks passed. The APK is signed with the same local project key as 0.3.0.

Local candidate only: `_archive/dev-builds/v0.3.2-dev/xiaomigpt-v0.3.2-dev.apk`, with `SHA256SUMS.txt` beside it. No release tag, GitHub release or release asset is created. This validation does not confirm the phone's settings pages, native binding, glasses gesture or physical audio route.
