# Vietnamese and Android assistant activation

Target: M2442G1 firmware 1.8.12.0, Xiaomi 15 Ultra / Android 16. Version: 0.3.0-assistant-preview.

The user confirmed that sign-in and an assistant response work in 0.2.1. The exact reason for 0.2.0's response-format failure was not captured. This update preserves that working authentication/transport and the selected model; it adds Vietnamese and a system-assistant entry point.

## Vietnamese setup

1. Install over the previous version without clearing app data.
2. Select **Tiếng Việt** (the new default). Download its recognition model once, approximately 34 MB / 32 MiB. The previous English model remains available when selecting **English**.
3. Use **Cài giọng đọc Tiếng Việt / English** to install an offline Vietnamese voice in the phone's TTS engine. If no matching offline voice exists, the app reports it instead of speaking Vietnamese using an English voice. The written answer remains visible.
4. Ask a short Vietnamese question. The selected language configures Vosk recognition, ChatGPT instructions and Android TTS together. Changing language clears in-memory history. This version uses an explicit language selector, not automatic mixed-language recognition.

Official model: `vosk-model-small-vn-0.4`, 33,656,337 bytes. SHA-256 verified from the official HTTPS download on 2026-10-27:

```text
efe5c8494212110471a79befc48c79da679e5b1fc52a4ffb500222ff86d622e5
```

The model catalog lists it under Apache 2.0. It uses the same bounded, checksum-verified, private-storage installer as English. Recognition quality and actual Vietnamese TTS on this phone need user-device testing.

## Test activation in order

1. Finish account, model, language and Bluetooth setup. Try a normal in-app question first. The app saves the model used for a turn for that ChatGPT registration, so subsequent cold starts can restore it when it remains in the account catalog.
2. Tap **Đặt XiaomiGpt làm trợ lý mặc định**. Select XiaomiGpt. If the previous Activity-only implementation was already selected, open Android assistant settings and select XiaomiGpt again. The status must say **Dịch vụ trợ lý XiaomiGpt: đang được Android chọn**.
3. Tap **Thử gọi qua trợ lý Android**. This requests a session through XiaomiGpt's selected VoiceInteractionService and should open a listening turn. An audible ready cue is attempted on the Bluetooth communication route. This is an app-generated test, not proof of a glasses gesture.
4. Return to the phone's home screen while keeping the phone **unlocked**. Try the glasses' assistant gesture. If XiaomiGpt opens, wait for the ready cue and ask a short question. A system invocation can cold-launch the app, load models and start listening once setup is ready.
5. If the glasses still invoke Xiao AI, return to XiaomiGpt and inspect **Nhật ký gọi trợ lý**. Copy the log after the attempt. Report whether Xiao AI spoke and whether XiaomiGpt opened. A missing event means this app did not observe the activation; it does not prove Android received no event.

Assistant/headset-hook key events delivered while the Activity is visible also trigger a turn. A second distinct activation finishes speech while listening or cancels the current request/playback. Duplicate deliveries within 750 ms are ignored. Other observed media/volume/camera key codes are logged while foreground; they retain their normal behavior.

## What this implements

- A system-bound `VoiceInteractionService`, protected by `BIND_VOICE_INTERACTION`, in a lightweight process separate from the conversation UI and speech models.
- A protected `VoiceInteractionSessionService` that launches the assistant Activity through `startAssistantActivity`. The Activity has a single-task launch mode to avoid separate concurrent conversation instances.
- Required service metadata with `supportsAssist=true`. The required `recognitionService` field refers to a non-exported placeholder; AOSP documents that this metadata field is unused from Android 12 onward. The app's minimum is Android 12, and its own routed PCM/Vosk code performs recognition. It does not register as the phone's general STT engine.
- Assist structure and screenshot context are disabled. No screenshot or other app's content is used.
- A bounded local activation journal: at most 12 timestamp/event-kind/flags-or-key entries. It separates `APP_TEST_SESSION` from `SYSTEM_SESSION`, records incoming assist intents and foreground keys, and can be copied or cleared explicitly. It does not identify the physical source of a system or key event and does not include speech, transcripts, accounts, MAC addresses or tokens.

## Limits

This is the standard Android assistant route. It does not change the glasses' firmware, capture Xiaomi cloud queries, register an always-listening wake word, or rewrite Xiao AI's internal gesture handling. A gesture consumed inside the glasses may never invoke Android's selected assistant. In that case Xiaomi protocol work is still needed.

Phone-lock activation and screen-off conversation are **not implemented** in this iteration. The descriptor explicitly declines launch-from-keyguard support, the Activity does not show over the lock screen, and leaving it cancels the turn as before. HyperOS system binding, cold launch and the actual glasses gesture remain hardware-validation tasks.

## Sources

- [Android VoiceInteractionService](https://developer.android.com/reference/android/service/voice/VoiceInteractionService)
- [Android VoiceInteractionSession](https://developer.android.com/reference/android/service/voice/VoiceInteractionSession)
- [AOSP service metadata validation](https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/service/voice/VoiceInteractionServiceInfo.java)
- [AOSP recognitionService attribute: ignored since Android 12 but still required](https://android.googlesource.com/platform/frameworks/base/+/ce27eff99247ce0cc9fe185db79c07553f24a1b7/core/res/res/values/attrs.xml)
- [Official Vosk model catalog](https://alphacephei.com/vosk/models)
