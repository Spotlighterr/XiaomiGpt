# XiaomiGpt Link

**English** | [Tiếng Việt](README.vi.md)

An open-source Android companion app for **Xiaomi AI Glasses**, built as a fork of [XiaoLink](https://github.com/mahdi-malv/xiaolink). It lets you have hands-free voice conversations in English and Vietnamese using the glasses' microphone, camera, and speakers, with multiple AI providers and direct phone/glasses voice commands.

## Key Features

- **Direct Glasses Protocol (from XiaoLink):** WearAuthV2 over Bluetooth SPP, glasses mSBC microphone decoding, photo/video capture, battery monitoring, and Wi-Fi Direct gallery sync.
- **4 AI Providers in One App:**
  - **ChatGPT:** Native browser sign-in with your ChatGPT account, model picker, and glasses camera vision input.
  - **Gemini Live (API Key):** Direct audio-in and natural AI voice-out over WebSocket, with automatic camera capture when needed.
  - **Gemini Bridge:** Connects to your own self-hosted Python bridge (`integrations/xiaolink/account_bridge`) using an authenticated CLI.
  - **DeepSeek (API Key):** Fast text replies (`deepseek-chat`, `deepseek-reasoner`), with automatic fallback to ChatGPT or Gemini when a photo is attached.
- **Accurate Voice & Fast Playback:** Uses Android 13+ Google Speech via `EXTRA_AUDIO_SOURCE` directly from the glasses microphone (with offline Vosk fallback), sentence-by-sentence TTS playback over Bluetooth A2DP, adjustable reading speed, and instant barge-in when holding the glasses temple.
- **Smart Auto-Camera:** Auto mode only takes a photo when your question asks to look, read, or translate; upgrades to `960x720` HD for small text; keeps recent photo context for 3 minutes for follow-up questions; and displays zoomable photos inline in chat.
- **Hands-Free Commands:** Check time, glasses battery/status, take photos, start/stop video, sync gallery, start Google Maps navigation, call contacts, open installed apps, set timers/alarms, control music, and start a new chat.
- **Chat Workspace & Bilingual UI:** Saves multi-thread chat history locally, auto-starts a new chat after 15 minutes idle, supports custom system context, and switches between **English** and **Tiếng Việt** in one tap.

---

## Setup & Usage Guide

### Step 1: Build and Install

Requires JDK 17+ and Android SDK.

```sh
cd integrations/xiaolink
./gradlew assembleDebug
```

On Windows, you can run from the repository root:

```powershell
.\scripts\build-integrated.ps1 -Tasks assembleDebug
```

Install the generated APK from `integrations/xiaolink/app/build/outputs/apk/debug/app-debug.apk` on your Android phone (Android 13+ recommended for direct Google Speech piping).

### Step 2: Connect Your Xiaomi AI Glasses

1. Pair the glasses in Android **Bluetooth Settings** first.
2. Open **XiaomiGpt Link**, go to the **Glasses** tab (or **More -> Glasses connection**).
3. Sign in to the Xiaomi account bound to your glasses to fetch the connection key, select your glasses, and tap **Connect**.
4. If the official Xiaomi Glasses app is holding the SPP connection, **Force Stop** it and tap Connect again (no need to uninstall it or reset your glasses).

### Step 3: Configure AI & Voice

1. Open the **Assistant** tab and tap the **Settings** icon in the top-right corner.
2. Set up one or more AI providers:
   - **ChatGPT:** Tap **Sign in ChatGPT**, finish browser sign-in, then tap **Models** and pick a model.
   - **Gemini Live:** Enter your Gemini API Key (from Google AI Studio), pick an AI voice, and tap **Save**.
   - **Gemini Bridge:** Run `python -m account_bridge` on your private server, enter its URL and bearer token, then tap **Save and connect**.
   - **DeepSeek:** Enter your DeepSeek API Key, choose `deepseek-chat` or `deepseek-reasoner`, and tap **Save**.
3. *(Recommended)* Enable **Auto-fallback across configured AIs** if you configured more than one provider.
4. *(For ChatGPT / DeepSeek / Gemini Bridge)* Tap **TTS Settings** to make sure your offline Android TTS voice (English/Vietnamese) is installed. Optionally tap **Download Vosk fallback** for offline speech recognition.
5. Adjust **TTS reading speed** (default `1.15x`) and optionally add a **Custom Context** prompt.

### Step 4: Daily Use

- **Ask AI:** Hold the glasses temple until the wake tone plays, speak your question, and pause for ~1.6 seconds to send. The answer plays through the glasses speakers.
- **Interrupt (Barge-in):** While an answer is playing, hold the glasses temple again to stop playback immediately and ask a new question.
- **Switch App Language:** Tap the **EN / VI** button in the top-right corner of the **Glasses** or **Assistant** tab.
- **Chat History:** In the **Assistant** tab, tap the top-left menu icon to switch or delete saved chats, or tap **+** to start a new conversation.

### Step 5: Built-in Hands-Free Commands

These run locally on your phone and glasses without waiting for an AI round-trip:

- **Glasses:** *"How much glasses battery is left?"*, *"Glasses status"*, *"Take a photo"*, *"Start recording"*, *"Stop recording"*, *"Sync gallery"*.
- **Chat:** *"New chat"*, *"New topic"*.
- **Phone:** *"What time is it?"*, *"Navigate to [place]"*, *"Open YouTube"*, *"Call [contact or number]"*, *"Set timer 5 minutes"*, *"Set alarm 7:30 am"*.
- **Media & Volume:** *"Pause music"*, *"Play music"*, *"Next song"*, *"Previous song"*, *"Volume up"*, *"Volume down"*.

### Tips for Screen-Off Use & Battery Life

- **Reliable background mode:** Exempt the app from Android battery optimization. To allow commands like opening Google Maps or apps while the screen is off, enable Android **Extend Unlock (Smart Lock)** and add your glasses as a **Trusted Device**.
- **Save glasses battery:** Keep Camera mode on **Auto** (only captures when visual intent is detected) and turn off **HD photo (960x720)** when you do not need to read small text.

---

## Project Structure

- `integrations/xiaolink/`: Main Android application (Kotlin + Jetpack Compose) and optional `account_bridge/` (Python).
- `app/`: Legacy standalone XiaomiGpt prototype (kept intact).
- `scripts/build-integrated.ps1`: Windows build helper script.

## License

- The forked app in `integrations/xiaolink/` is licensed under **GPL-3.0** (`integrations/xiaolink/LICENSE`), preserving upstream XiaoLink attribution and MIT attribution for imported XiaomiGpt code (`integrations/xiaolink/THIRD_PARTY_INTEGRATION.md`).
- The legacy root prototype remains **MIT licensed** (`LICENSE`).

## References & Credits

Built on the open-source work and research of:

- **mahdi-malv**: [XiaoLink](https://github.com/mahdi-malv/xiaolink) (upstream fork base: WearAuthV2, Bluetooth SPP, mSBC decoder, AIVS events/camera, gallery sync).
- **idootop / Del Wang**: [mi-gpt](https://github.com/idootop/mi-gpt), [MigicCube](https://github.com/idootop/MigicCube), and [HyperSight](https://del.wang/projects/product/ase).
- **Mentra Community**: [Xiaomi glasses reverse engineering in MentraOS](https://github.com/Mentra-Community/MentraOS/blob/dev/agents/xiaomi_glasses_reverse_engineering.md).
- **moyslav**: [xiaomi-ai-glasses](https://github.com/moyslav/xiaomi-ai-glasses).
- **jacuboffsky**: [xiaomi-glasses-network-fix](https://github.com/jacuboffsky/xiaomi-glasses-network-fix).
- **zero2005x**: [RokidAIAssistant](https://github.com/zero2005x/RokidAIAssistant).
- **Alpha Cephei**: [Vosk](https://github.com/alphacep/vosk-api).
- **Java Native Access**: [JNA](https://github.com/java-native-access/jna).
