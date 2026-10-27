package io.github.spotlighterr.xiaomigpt

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.os.SystemClock
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class VoiceAssistant(context: Context, private val client: ChatGptClient) {
    private val context = context.applicationContext
    private val audio = context.getSystemService(AudioManager::class.java)
    private val output = SpokenReply(context)
    private val worker = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    private val cancelled = AtomicBoolean(false)
    private val endSpeech = AtomicBoolean(false)
    private val history = mutableListOf<Pair<String, String>>()
    private var model: Model? = null
    private var loadedLanguage: SpeechLanguage? = null
    @Volatile var listening = false; private set

    fun start(device: AudioDeviceInfo, choice: String, typed: String?, modelFiles: SpeechModel, status: (String) -> Unit, transcript: (String) -> Unit,
              answer: (String) -> Unit, finished: () -> Unit) {
        if (!busy.compareAndSet(false, true)) return
        cancelled.set(false); endSpeech.set(false)
        worker.execute {
            var ownedRoute = false; var focusOwned = false
            val previousMode = audio.mode
            val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setOnAudioFocusChangeListener { if (it < 0) cancel() }.build()
            try {
                check(context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) { "Hãy cấp quyền Bluetooth trước." }
                if (typed == null) {
                    check(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { "Hãy cấp quyền micro trước." }
                    check(modelFiles.ready()) { "Hãy tải model ${modelFiles.language.label} trước." }
                    if (model == null || loadedLanguage != modelFiles.language) {
                        status("Đang chuẩn bị nhận dạng ${modelFiles.language.label}…")
                        model?.close(); model = null; loadedLanguage = null
                        model = Model(modelFiles.directory.absolutePath); loadedLanguage = modelFiles.language
                    }
                }
                checkActive()
                check(previousMode == AudioManager.MODE_NORMAL) { "Hãy kết thúc cuộc gọi hoặc phiên Voice khác trước." }
                focusOwned = audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                check(focusOwned) { "Chưa lấy được quyền âm thanh." }
                audio.mode = AudioManager.MODE_IN_COMMUNICATION; ownedRoute = true
                check(audio.setCommunicationDevice(device)) { "Không chọn được kính Bluetooth." }
                val deadline = SystemClock.elapsedRealtime() + 5000
                while (audio.communicationDevice?.id != device.id) {
                    checkActive(); check(SystemClock.elapsedRealtime() < deadline) { "Kính chưa sẵn sàng nhận âm thanh." }; SystemClock.sleep(30)
                }
                val question = typed ?: recognize(device, modelFiles.language, status, transcript)
                checkActive(); check(question.isNotBlank()) { "Chưa nghe rõ câu hỏi. Hãy thử nói lại." }
                transcript(question)
                status("ChatGPT đang trả lời…")
                val reply = client.answer(choice, history.toList(), question, modelFiles.language) { !cancelled.get() }
                checkActive()
                history.add("user" to question); history.add("assistant" to reply)
                while (history.size > 12) history.removeAt(0)
                answer(reply)
                status("Đang đọc câu trả lời qua kính…")
                output.speak(reply, device, modelFiles.language, { !cancelled.get() }, { audio.communicationDevice?.id == device.id })
                checkActive(); status("Đã trả lời. Bấm Nói với trợ lý để hỏi tiếp.")
            } catch (e: Exception) {
                status(if (cancelled.get()) "Đã dừng trợ lý." else (e.message?.takeIf { it.length < 300 && !it.contains("https://") } ?: "Chưa hoàn tất hội thoại. Kiểm tra kết nối rồi thử lại."))
            } finally {
                listening = false
                if (ownedRoute) { runCatching { audio.clearCommunicationDevice() }; if (audio.mode == AudioManager.MODE_IN_COMMUNICATION) audio.mode = previousMode }
                if (focusOwned) audio.abandonAudioFocusRequest(focus)
                busy.set(false); finished()
            }
        }
    }
    private fun recognize(device: AudioDeviceInfo, language: SpeechLanguage, status: (String) -> Unit, transcript: (String) -> Unit): String {
        check(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { "Chưa có quyền micro." }
        val format = AudioFormat.Builder().setSampleRate(16000).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build()
        val minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(minimum > 0) { "Không khởi tạo được định dạng micro." }
        val recorder = AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            .setAudioFormat(format).setBufferSizeInBytes(maxOf(4096, minimum * 2)).build()
        try {
            check(recorder.state == AudioRecord.STATE_INITIALIZED) { "Không khởi tạo được micro." }
            recorder.startRecording()
            val chunk = ByteArray(2048)
            val routeDeadline = SystemClock.elapsedRealtime() + 3000
            while (recorder.routedDevice?.let { AudioProbe.isHeadset(it.type) } != true) {
                checkActive(); check(SystemClock.elapsedRealtime() < routeDeadline) { "Micro chưa chuyển sang kính; đã hủy." }
                check(recorder.read(chunk, 0, chunk.size, AudioRecord.READ_NON_BLOCKING) >= 0)
                SystemClock.sleep(20)
            }
            val input = checkNotNull(recorder.routedDevice)
            // Audible ready cue for use without looking at the phone.
            runCatching {
                val tone = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 50)
                try { tone.startTone(ToneGenerator.TONE_PROP_BEEP, 100); SystemClock.sleep(120) }
                finally { tone.release() }
            }
            checkActive(); listening = true
            status("Đang nghe qua kính · ${language.label}. App gửi khi bạn ngừng nói, tối đa 20 giây.")
            Recognizer(checkNotNull(model), 16000f).use { recognizer ->
                val deadline = SystemClock.elapsedRealtime() + 20_000
                var nextUpdate = 0L
                while (!endSpeech.get() && SystemClock.elapsedRealtime() < deadline) {
                    checkActive()
                    check(audio.communicationDevice?.id == device.id && recorder.routedDevice?.id == input.id) { "Đường micro đã thay đổi; không gửi câu hỏi." }
                    val count = recorder.read(chunk, 0, chunk.size, AudioRecord.READ_NON_BLOCKING)
                    check(count >= 0) { "Không đọc được micro kính." }
                    if (count > 0) {
                        if (recognizer.acceptWaveForm(chunk, count)) {
                            val result = JSONObject(recognizer.result).optString("text").trim()
                            if (result.isNotBlank()) return result
                        }
                        if (SystemClock.elapsedRealtime() > nextUpdate) {
                            transcript(JSONObject(recognizer.partialResult).optString("partial")); nextUpdate = SystemClock.elapsedRealtime() + 250
                        }
                    } else SystemClock.sleep(10)
                }
                checkActive()
                return JSONObject(recognizer.finalResult).optString("text").trim()
            }
        } finally { listening = false; runCatching { recorder.stop() }; recorder.release() }
    }
    fun finishSpeech() { endSpeech.set(true) }
    fun cancel() { cancelled.set(true); client.cancel() }
    private fun checkActive() { check(!cancelled.get()) { "Đã dừng." } }
    fun clearHistory() { check(!busy.get()); history.clear() }
    fun shutdown() { cancel(); worker.execute { model?.close(); model = null; output.shutdown() }; worker.shutdown() }
}
