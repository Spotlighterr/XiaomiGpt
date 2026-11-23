package io.github.spotlighterr.xiaomigpt

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.MediaPlayer
import android.os.Bundle
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Synthesize to private temporary audio, then play through a route we can verify. */
class SpokenReply(private val context: Context) {
    private val initialized = CountDownLatch(1)
    @Volatile private var initStatus = TextToSpeech.ERROR
    @Volatile private var activePlayer: MediaPlayer? = null
    private val tts = TextToSpeech(context.applicationContext) { status -> initStatus = status; initialized.countDown() }

    /** Immediately interrupts any active TTS synthesis or Bluetooth audio playback (barge-in). */
    fun stopNow() {
        runCatching { tts.stop() }
        val p = activePlayer
        activePlayer = null
        runCatching { if (p?.isPlaying == true) p.stop() }
    }
    fun speak(text: String, device: AudioDeviceInfo, language: SpeechLanguage, active: () -> Boolean, routed: () -> Boolean, speechRate: Float = 1.15f) {
        val waitUntil = SystemClock.elapsedRealtime() + 15_000
        while (!initialized.await(100, TimeUnit.MILLISECONDS)) {
            check(active()) { "Đã dừng." }; check(SystemClock.elapsedRealtime() < waitUntil) { "Bộ đọc văn bản chưa sẵn sàng." }
        }
        check(initStatus == TextToSpeech.SUCCESS) { "Điện thoại chưa có bộ đọc văn bản. Cài/bật Text-to-speech trong Cài đặt Android." }
        check(tts.setLanguage(language.locale) >= TextToSpeech.LANG_AVAILABLE) { "Hãy tải giọng đọc ${language.label} trong cài đặt Text-to-speech Android." }
        // Require a local voice so reply text isn't silently sent to a second cloud service.
        val voice = tts.voices?.filter { it.locale.language == language.locale.language && !it.isNetworkConnectionRequired }
            ?.sortedByDescending { it.locale.toLanguageTag() == language.tag }?.firstOrNull()
        check(voice != null) { "Hãy tải giọng đọc ${language.label} ngoại tuyến trong Text-to-speech Android." }
        tts.voice = voice
        tts.setSpeechRate(speechRate.coerceIn(0.6f, 2.0f))
        val parts = splitSentences(text, minOf(TextToSpeech.getMaxSpeechInputLength(), 3500))
        val synthExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { Thread(it, "tts-synth") }
        try {
            var nextFuture: java.util.concurrent.Future<File?>? = synthExecutor.submit<File?> {
                synthesizeChunk(parts.first(), active)
            }
            for (i in parts.indices) {
                check(active()) { "Đã dừng." }
                val wav = try {
                    nextFuture?.get()
                } catch (e: java.util.concurrent.ExecutionException) {
                    throw (e.cause ?: e)
                }
                nextFuture = if (i + 1 < parts.size) {
                    val nextPart = parts[i + 1]
                    synthExecutor.submit<File?> { synthesizeChunk(nextPart, active) }
                } else null
                if (wav == null) continue
                try {
                    playWav(wav, device, active, routed)
                } finally {
                    wav.delete()
                }
            }
        } finally {
            synthExecutor.shutdownNow()
            tts.stop()
        }
    }

    private fun synthesizeChunk(part: String, active: () -> Boolean): File? {
        if (part.isBlank() || !active()) return null
        val file = File.createTempFile("reply-", ".wav", context.cacheDir)
        val id = file.name
        val done = CountDownLatch(1)
        var success = false
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) { if (utteranceId == id) { success = true; done.countDown() } }
            @Deprecated("Required by framework") override fun onError(utteranceId: String?) { if (utteranceId == id) done.countDown() }
        })
        if (tts.synthesizeToFile(part, Bundle(), file, id) != TextToSpeech.SUCCESS) {
            file.delete(); error("Không tạo được giọng đọc.")
        }
        val synthesisDeadline = SystemClock.elapsedRealtime() + 60_000
        while (!done.await(50, TimeUnit.MILLISECONDS)) {
            if (!active()) { tts.stop(); file.delete(); error("Đã dừng.") }
            if (SystemClock.elapsedRealtime() >= synthesisDeadline) { tts.stop(); file.delete(); error("Bộ đọc trả lời quá chậm.") }
        }
        if (!success || !active()) { file.delete(); error("Chưa tạo được giọng đọc, hoặc đã dừng.") }
        return file
    }

    private fun playWav(file: File, device: AudioDeviceInfo, active: () -> Boolean, routed: () -> Boolean) {
        val player = MediaPlayer()
        activePlayer = player
        val usage = if (device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) AudioAttributes.USAGE_MEDIA else AudioAttributes.USAGE_VOICE_COMMUNICATION
        val allowedTypes = setOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET)
        try {
            player.setAudioAttributes(AudioAttributes.Builder().setUsage(usage).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            player.setDataSource(file.absolutePath)
            check(player.setPreferredDevice(device)) { "Không chọn được loa kính." }
            player.prepare(); check(active() && routed()) { "Kính đã ngắt kết nối." }
            player.start()
            val routeDeadline = SystemClock.elapsedRealtime() + 1500
            var verified = false
            while (player.isPlaying) {
                check(active() && routed()) { "Đã dừng hoặc mất kết nối kính." }
                val output = player.routedDevice
                if (output != null) {
                    check(output.id == device.id && output.type in allowedTypes) { "Đường phát không còn là kính; đã dừng." }
                    verified = true
                }
                check(verified || SystemClock.elapsedRealtime() < routeDeadline) { "Không xác minh được loa Bluetooth." }
                SystemClock.sleep(25)
            }
            check(verified) { "Chưa xác minh được tiếng trả lời qua Bluetooth." }
        } finally {
            if (activePlayer === player) activePlayer = null
            player.release()
        }
    }

    private fun splitSentences(text: String, maxLen: Int): List<String> {
        val clean = text.trim()
        if (clean.length <= 140) return listOf(clean)
        val out = mutableListOf<String>()
        val buf = StringBuilder()
        val rawParts = clean.split(Regex("(?<=[.!?\\n;])\\s+"))
        for (p in rawParts) {
            val s = p.trim()
            if (s.isEmpty()) continue
            if (buf.isNotEmpty() && buf.length + s.length + 1 > 180) {
                out.add(buf.toString().take(maxLen))
                buf.clear()
            }
            if (buf.isNotEmpty()) buf.append(' ')
            buf.append(s)
            if (out.isEmpty() && buf.length >= 40) {
                // Flush the first sentence early so playback begins as quickly as possible.
                out.add(buf.toString().take(maxLen))
                buf.clear()
            }
        }
        if (buf.isNotEmpty()) out.add(buf.toString().take(maxLen))
        return out.ifEmpty { listOf(clean.take(maxLen)) }
    }
    fun shutdown() { tts.stop(); tts.shutdown() }
}
