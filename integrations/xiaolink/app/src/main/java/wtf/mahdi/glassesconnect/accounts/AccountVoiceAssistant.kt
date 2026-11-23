package wtf.mahdi.glassesconnect.accounts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.SystemClock
import io.github.spotlighterr.xiaomigpt.SpokenReply
import io.github.spotlighterr.xiaomigpt.VisualInput
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import wtf.mahdi.glassesconnect.ai.AiTurn
import wtf.mahdi.glassesconnect.ai.MsbcDecoder
import wtf.mahdi.glassesconnect.protocol.GlassesCommands
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** SPP mic -> local Vosk -> optional fresh SPP image -> account model -> verified Bluetooth speech.
 * This class never opens the phone microphone and never persists raw voice or camera frames.
 */
class AccountVoiceAssistant(
    context: Context, private val hub: AccountHub,
    private val connected: () -> Boolean, private val address: () -> String,
    private val glassesBridge: DeviceActionRouter.GlassesBridge,
    private val send: (ByteArray) -> Unit, private val onState: (String) -> Unit,
    private val onTurn: (AiTurn) -> Unit,
) {
    private val worker = Executors.newSingleThreadScheduledExecutor { Thread(it, "glasses-account-turn") }
    private val requests = Executors.newSingleThreadExecutor { Thread(it, "glasses-account-http") }
    private val speech = Executors.newSingleThreadExecutor { Thread(it, "glasses-account-speech") }
    private val gate = TurnGate()
    private val decoder = MsbcDecoder()
    private val audio = context.getSystemService(AudioManager::class.java)
    private val speaker = SpokenReply(context)
    private val androidSpeech = AndroidPipeSpeech(context)
    private val appContext = context.applicationContext
    private var pipeActive = false
    private var model: Model? = null
    private var modelPath = ""
    private var recognizer: Recognizer? = null
    private var vad = PcmUtteranceGate()
    private var phase = "Idle"
    private var ticket = 0L
    private var cameraOpen = false
    private var hdCameraOpen = false
    private var fallbackJpeg: ByteArray? = null
    private var timeout: ScheduledFuture<*>? = null
    private val heard = StringBuilder()

    init { hub.onChanged = ::stop }
    fun onWake(cmd: Int) {
        if (!TurnGate.acceptsWake(cmd)) return
        // Instant barge-in: cut off any ongoing TTS playback or in-flight AI request before queuing the new turn.
        gate.cancel(); speaker.stopNow(); hub.cancelInference()
        worker.execute {
            stopInternal(stopMic = false, notifyIdle = false)
            if (!connected()) return@execute fail("Kính chưa kết nối.")
            val settings = hub.state.value
            val hasAndroidSpeech = AndroidPipeSpeech.available(appContext)
            if (!settings.speechReady && !hasAndroidSpeech) return@execute fail("Tải bộ nhận dạng giọng nói trong Tài khoản AI trước.")
            val chatgptReady = settings.model.isNotBlank()
            val geminiReady = settings.bridgeReady
            val deepseekReady = settings.deepseekReady
            val anyBackup = settings.autoFallback && (chatgptReady || geminiReady || deepseekReady)
            if (settings.provider == "chatgpt" && !chatgptReady && !anyBackup)
                return@execute fail("Đăng nhập ChatGPT và tải model trước.")
            if (settings.provider == "gemini" && !geminiReady && !anyBackup)
                return@execute fail("Kiểm tra kết nối tài khoản Gemini trước.")
            if (settings.provider == "deepseek" && !deepseekReady && !anyBackup)
                return@execute fail("Nhập DeepSeek API Key trước.")
            try {
                ticket = gate.begin(); decoder.reset(); vad = PcmUtteranceGate(); heard.clear()
                state("Listening"); hub.status("Đang nghe micro kính…")
                val captured = ticket
                pipeActive = androidSpeech.start(settings.language)
                if (pipeActive) {
                    // Battery & RAM optimization: skip loading ~110 MB Vosk native model when Google Speech Pipe is active.
                    if (model != null) { runCatching { model?.close() }; model = null; modelPath = "" }
                } else if (settings.speechReady) {
                    val path = hub.speechModel().directory.absolutePath
                    if (modelPath != path) { model?.close(); model = Model(path); modelPath = path }
                    recognizer = Recognizer(checkNotNull(model), 16000f)
                }
                if (!pipeActive && recognizer == null) return@execute fail("Không mở được bộ nhận dạng giọng nói.")
                timeout = worker.schedule({ if (gate.active(captured) && phase == "Listening") finishListening() }, 30, TimeUnit.SECONDS)
            } catch (_: Exception) { fail("Không mở được bộ nhận dạng. Kiểm tra model giọng nói.") }
        }
    }
    fun onVoice(data: ByteArray) {
        if (data.size <= 12 || data[1].toInt() != 2) return
        val type = ((data[4].toInt() and 255) shl 8) or (data[5].toInt() and 255)
        if (type !in 1..2) return
        worker.execute {
            if (phase != "Listening" || !gate.active(ticket)) return@execute
            try {
                val pcm = decoder.decode(data.copyOfRange(12, data.size))
                if (pcm.isEmpty()) return@execute
                if (pipeActive) androidSpeech.feed(pcm)
                if (recognizer?.acceptWaveForm(pcm, pcm.size) == true) appendText(recognizer!!.result)
                if (vad.feed(pcm)) finishListening()
            } catch (_: Exception) { fail("Không giải mã được âm thanh kính.") }
        }
    }
    fun onStopCapture() = worker.execute { if (phase == "Listening" && gate.active(ticket)) finishListening() }
    private fun appendText(result: String) {
        val text = JSONObject(result).optString("text").trim()
        if (text.isNotEmpty()) { if (heard.isNotEmpty()) heard.append(' '); heard.append(text) }
    }
    private fun finishListening() {
        if (phase != "Listening") return
        timeout?.cancel(false); timeout = null
        runCatching { send(GlassesCommands.aivsStopCapture()) }
        val googleText = if (pipeActive) { pipeActive = false; androidSpeech.finishAndGet(3000) } else null
        recognizer?.let { appendText(it.finalResult); it.close() }; recognizer = null
        val question = googleText?.takeIf { it.isNotBlank() } ?: heard.toString().trim()
        if (question.isEmpty()) return fail("Chưa nghe rõ câu hỏi từ kính.")
        val useCamera = when (hub.state.value.cameraMode) {
            "always" -> true
            "auto" -> SmartCameraIntent.shouldCapture(question)
            else -> false
        }
        android.util.Log.i("GlassesConnect", "STT source=${if (!googleText.isNullOrBlank()) "google" else "vosk"} cam=$useCamera")
        beginQuestion(question, useCamera)
    }
    /** Explicit phone-side test is separate from physical-glasses acceptance. */
    fun askTyped(question: String, camera: Boolean) {
        worker.execute {
            stopInternal(); if (!connected()) return@execute fail("Kết nối kính trước.")
            if (question.isBlank() || question.length > 8000) return@execute fail("Nhập câu hỏi tối đa 8000 ký tự.")
            ticket = gate.begin(); beginQuestion(question.trim(), camera)
        }
    }
    private fun beginQuestion(question: String, camera: Boolean) {
        heard.clear(); heard.append(question); hub.transcript(question)
        if (hub.state.value.deviceActions) {
            val reply = DeviceActionRouter.tryExecute(appContext, question, glassesBridge)
            if (reply != null) {
                hub.recordDeviceTurn(question, reply)
                val captured = ticket; val began = System.currentTimeMillis()
                hub.answered(reply); state("Speaking")
                val language = hub.state.value.language
                speech.execute {
                    val spoken = runCatching { speak(reply, language, captured) }
                    worker.execute {
                        if (!gate.active(captured)) return@execute
                        spoken.fold({
                            onTurn(AiTurn(began, began, question, reply))
                            stopInternal(); hub.status("Đã thực hiện lệnh trên điện thoại.")
                        }, { e -> fail(e.message ?: "Đã thực hiện lệnh nhưng chưa phát được tiếng.") })
                    }
                }
                return
            }
        }
        if (!camera) return request(question, null)
        state("Thinking"); hub.status("Đang lấy ảnh mới từ camera kính…")
        cameraOpen = true
        val captured = ticket
        try { send(GlassesCommands.aivsMultiModal(1)) } catch (_: Exception) { return fail("Không gửi được lệnh camera.") }
        timeout = worker.schedule({
            if (gate.active(captured) && cameraOpen) fail("Kính chưa trả ảnh mới. Câu hỏi chưa được gửi tới AI.")
        }, 8, TimeUnit.SECONDS)
    }
    fun onImage(jpeg: ByteArray) = worker.execute {
        if ((!cameraOpen && !hdCameraOpen) || !gate.active(ticket)) return@execute
        timeout?.cancel(false); timeout = null
        if (cameraOpen) {
            endCamera()
            try { VisualInput.content(heard.toString(), jpeg) } catch (_: Exception) { return@execute fail("Ảnh kính không hợp lệ hoặc chưa đầy đủ.") }
            if (hub.state.value.hdCamera && SmartCameraIntent.needsHd(heard.toString())) {
                fallbackJpeg = jpeg
                hdCameraOpen = true
                val captured = ticket
                hub.status("Đang tải ảnh nét cao (960×720) từ kính…")
                runCatching { send(GlassesCommands.aivsMultiModal(3)) }.onFailure {
                    hdCameraOpen = false; fallbackJpeg = null
                    return@execute request(heard.toString(), jpeg)
                }
                timeout = worker.schedule({
                    if (gate.active(captured) && hdCameraOpen) {
                        val fb = fallbackJpeg
                        endCamera()
                        if (fb != null) request(heard.toString(), fb)
                    }
                }, 4, TimeUnit.SECONDS)
                return@execute
            }
            return@execute request(heard.toString(), jpeg)
        }
        if (hdCameraOpen) {
            val fb = fallbackJpeg
            endCamera()
            val chosen = runCatching { VisualInput.content(heard.toString(), jpeg); jpeg }.getOrElse { fb }
                ?: return@execute fail("Ảnh kính không hợp lệ hoặc chưa đầy đủ.")
            request(heard.toString(), chosen)
        }
    }
    private fun request(question: String, jpeg: ByteArray?) {
        val captured = ticket; val began = System.currentTimeMillis()
        state("Thinking"); hub.status(if (jpeg != null) "Đang hỏi AI kèm ảnh camera kính…" else "Đang hỏi AI…")
        requests.execute {
            val result = runCatching { hub.ask(question, jpeg) { gate.active(captured) && connected() } }
            worker.execute {
                if (!gate.active(captured) || !connected()) return@execute
                result.fold({ answer ->
                    hub.answered(answer); state("Speaking")
                    val language = hub.state.value.language
                    speech.execute {
                        val spoken = runCatching { speak(answer, language, captured) }
                        worker.execute {
                            if (!gate.active(captured)) return@execute
                            spoken.fold({
                                onTurn(AiTurn(began, began, question, answer))
                                stopInternal(); hub.status("Đã phát câu trả lời qua đường loa kính được xác minh.")
                            }, { e -> fail(e.message ?: "Chưa phát được qua loa kính.") })
                        }
                    }
                }, { e -> fail(e.message ?: "Yêu cầu AI chưa thành công.") })
            }
        }
    }
    private fun speak(text: String, language: io.github.spotlighterr.xiaomigpt.SpeechLanguage, captured: Long) {
        val targetAddress = address()
        val a2dpDevice = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull {
            it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP &&
                it.address.equals(targetAddress, ignoreCase = true)
        }
        if (a2dpDevice != null) {
            val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setOnAudioFocusChangeListener { change -> if (change < 0) stop() }.build()
            var focused = false
            try {
                check(gate.active(captured) && connected()) { "Đã dừng." }
                focused = audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                check(focused) { "Không nhận được quyền phát âm thanh." }
                speaker.speak(text.take(12000), a2dpDevice, language, { gate.active(captured) && connected() }, {
                    audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.id == a2dpDevice.id }
                }, hub.state.value.speechRate)
            } finally {
                if (focused) audio.abandonAudioFocusRequest(focus)
            }
            return
        }
        val device = audio.availableCommunicationDevices.firstOrNull {
            it.type in setOf(android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO, android.media.AudioDeviceInfo.TYPE_BLE_HEADSET) &&
                it.address.equals(targetAddress, ignoreCase = true)
        } ?: error("Chưa tìm thấy loa Bluetooth của kính. Kiểm tra kết nối âm thanh Bluetooth.")
        val previousMode = audio.mode
        val previousDevice = audio.communicationDevice
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).build())
            .setOnAudioFocusChangeListener { change -> if (change < 0) stop() }.build()
        var focused = false; var ownsRoute = false
        try {
            check(gate.active(captured) && connected()) { "Đã dừng." }
            focused = audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            check(focused) { "Không nhận được quyền phát âm thanh." }
            audio.mode = AudioManager.MODE_IN_COMMUNICATION
            check(audio.setCommunicationDevice(device)) { "Không chọn được loa kính." }; ownsRoute = true
            val deadline = SystemClock.elapsedRealtime() + 2000
            while (audio.communicationDevice?.id != device.id) {
                check(gate.active(captured) && connected() && SystemClock.elapsedRealtime() < deadline) { "Chưa xác minh đường âm thanh kính." }
                SystemClock.sleep(25)
            }
            speaker.speak(text.take(12000), device, language, { gate.active(captured) && connected() }, { audio.communicationDevice?.id == device.id }, hub.state.value.speechRate)
        } finally {
            if (ownsRoute && audio.communicationDevice?.id == device.id) {
                if (previousDevice != null) audio.setCommunicationDevice(previousDevice) else audio.clearCommunicationDevice()
                if (audio.mode == AudioManager.MODE_IN_COMMUNICATION) audio.mode = previousMode
            }
            if (focused) audio.abandonAudioFocusRequest(focus)
        }
    }
    private fun endCamera() {
        if (cameraOpen) runCatching { send(GlassesCommands.aivsMultiModal(2)) }
        if (hdCameraOpen) runCatching { send(GlassesCommands.aivsMultiModal(4)) }
        cameraOpen = false
        hdCameraOpen = false
        fallbackJpeg = null
    }
    private fun state(value: String) {
        phase = value; onState(value)
        val (voice, tts) = when (value) { "Listening" -> 2 to 0; "Thinking" -> 3 to 2; "Speaking" -> 3 to 1; else -> 0 to 0 }
        runCatching { send(GlassesCommands.aivsStatus(voice, tts)) }
    }
    private fun fail(message: String) { stopInternal(); hub.status(message) }
    private fun stopInternal(stopMic: Boolean = true, notifyIdle: Boolean = true) {
        gate.cancel(); hub.cancelInference(); timeout?.cancel(false); timeout = null
        if (pipeActive) { pipeActive = false; androidSpeech.cancel() }
        recognizer?.close(); recognizer = null; endCamera()
        if (stopMic) runCatching { send(GlassesCommands.aivsStopCapture()) }
        if (notifyIdle) state("Idle") else { phase = "Idle"; onState("Idle") }
    }
    fun stop() { gate.cancel(); speaker.stopNow(); hub.cancelInference(); worker.execute { stopInternal() } }
    fun resetSession() = stop()
}
