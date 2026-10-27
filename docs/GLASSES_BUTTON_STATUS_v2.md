# Glasses hold-button status and release gate

2026-10-27. Target: M2442G1 / firmware 1.8.12.0, Xiaomi 15 Ultra / Android 16.

## Confirmed state

- The user confirmed Bluetooth audio, ChatGPT sign-in and an assistant response in the preceding builds.
- 0.3.0 implements Vietnamese and an Android VoiceInteractionService entry point. It does not establish that the glasses' hold-button action invokes that entry point.
- The user has not yet tested 0.3.0's glasses activation. There is no physical hold-button success or failure result to diagnose yet.
- At the user's explicit instruction, no further release, pre-release, release tag or release-asset upload is allowed until actual glasses-button invocation completes a voice turn. See [project instructions](../AGENTS.md).

The existing 0.3.0 remains available for this check; no new diagnostic release is needed or created.

## Additional static APK check using REA

Input: the supplied `eco-andriod-glass.apk`, SHA-256 `8757e57eb498071daccf4ff09b98be8072a2cc5c2f293a638605887c1a89d7a8`. Its APK-declared package/version are `com.xiaomi.superhexa` / 3.1.27. The earlier signature-integrity failure remains unresolved, so this is evidence about that file, not proof of an authentic Xiaomi build or behavior on firmware 1.8.12.0.

The prior class inventory contains separate audio-glasses and AI-glasses gesture classes. Audio-glasses `LongPress*` names must not be treated as M2442G1 support.

The class `com.superhexa.supervision.feature.miwearglasses.presentation.setting.subpages.gestureControl.GestureSettingViewModel` is linked by its method signatures to `IDeviceOperator<O95StateLiveData>`. REA returned 15 methods for this class.

Its `a1(GestureSettingState, GestureSettingEvent)` handler was inspected as **smali fallback**; Java decompilation was unavailable. The observed branches:

- Load settings using `GetSystemSettings`.
- Construct `SetHeadSet` while preserving `wearDetection`, `volumeAdaptive` and `preventSoundLeak`; the changing value is associated with `musicControl` in the state update.
- Construct `SetMediaSetting`, with branches changing values associated with `recordControl` and `shootingMode` while preserving the other media fields.

This handler does not provide a demonstrated switch from a glasses hold-button action to Android's assistant. That is a bounded observation about this method, not proof that no such switch exists anywhere in the APK or firmware. It also does not identify command enum meanings, framing, pairing, or safe firmware writes. No command was sent to the glasses.

Evidence records and hashes: [REA gesture evidence index](REA_GESTURE_EVIDENCE_v2.json). Full JSON records and the derived smali remain under ignored `_archive/rea/`; no decompiled implementation or Xiaomi APK is published.

## Required next hardware check

Use the existing 0.3.0, finish setup, select XiaomiGpt as Android's assistant, then return to the phone home screen with the phone unlocked. Hold the actual glasses button without tapping the phone. Report whether XiaomiGpt opens, Xiao AI responds, or neither happens, and copy **Nhật ký gọi trợ lý** after the attempt.

Success requires the same glasses-triggered turn to hear the question from the glasses microphone and speak the response through the glasses. An app-generated test session, phone-side gesture or log entry alone does not satisfy that requirement. Record locked/unlocked state; locked/screen-off support is not implemented in 0.3.0.

If the hold action reaches XiaomiGpt, finish the end-to-end route checks before releasing. If it remains inside Xiao AI, use that evidence to continue firmware/app-protocol tracing instead of assuming another Android intent declaration remaps it.
