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
    private val tts = TextToSpeech(context.applicationContext) { status -> initStatus = status; initialized.countDown() }
    fun speak(text: String, device: AudioDeviceInfo, language: SpeechLanguage, active: () -> Boolean, routed: () -> Boolean) {
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
        val limit = minOf(TextToSpeech.getMaxSpeechInputLength(), 3500)
        for (part in text.chunked(limit)) {
            check(active()) { "Đã dừng." }
            val file = File.createTempFile("reply-", ".wav", context.cacheDir)
            val id = file.name
            val done = CountDownLatch(1)
            var success = false
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) { if (utteranceId == id) { success = true; done.countDown() } }
                @Deprecated("Required by framework") override fun onError(utteranceId: String?) { if (utteranceId == id) done.countDown() }
            })
            try {
                check(tts.synthesizeToFile(part, Bundle(), file, id) == TextToSpeech.SUCCESS) { "Không tạo được giọng đọc." }
                val synthesisDeadline = SystemClock.elapsedRealtime() + 60_000
                while (!done.await(100, TimeUnit.MILLISECONDS)) {
                    check(active()) { "Đã dừng." }; check(SystemClock.elapsedRealtime() < synthesisDeadline) { "Bộ đọc trả lời quá chậm." }
                }
                check(success && active()) { "Chưa tạo được giọng đọc, hoặc đã dừng." }
                val player = MediaPlayer()
                try {
                    player.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
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
                            check(output.id == device.id && AudioProbe.isHeadset(output.type)) { "Đường phát không còn là kính; đã dừng." }
                            verified = true
                        }
                        check(verified || SystemClock.elapsedRealtime() < routeDeadline) { "Không xác minh được loa Bluetooth." }
                        SystemClock.sleep(25)
                    }
                    check(verified) { "Chưa xác minh được tiếng trả lời qua Bluetooth." }
                } finally { player.release() }
            } finally { tts.stop(); file.delete() }
        }
    }
    fun shutdown() { tts.stop(); tts.shutdown() }
}
