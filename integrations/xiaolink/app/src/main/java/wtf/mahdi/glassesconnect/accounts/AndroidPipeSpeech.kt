package wtf.mahdi.glassesconnect.accounts

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import io.github.spotlighterr.xiaomigpt.SpeechLanguage
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Streams 16 kHz mono 16-bit PCM from the glasses microphone into Android 13+'s official
 * [RecognizerIntent.EXTRA_AUDIO_SOURCE] pipe, so Google Speech Recognition transcribes the
 * glasses mic directly without opening the phone microphone.
 */
class AndroidPipeSpeech(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var readEnd: ParcelFileDescriptor? = null
    @Volatile private var writeStream: OutputStream? = null
    @Volatile private var partialText = ""
    @Volatile private var finalText: String? = null
    private var doneLatch: CountDownLatch? = null
    private val finished = AtomicBoolean(false)

    companion object {
        fun available(context: Context): Boolean =
            Build.VERSION.SDK_INT >= 33 && SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun start(language: SpeechLanguage, onReadyError: () -> Unit = {}): Boolean {
        if (!available(context)) return false
        cancel()
        val pipe = runCatching { ParcelFileDescriptor.createPipe() }.getOrNull() ?: return false
        readEnd = pipe[0]
        writeStream = ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])
        partialText = ""
        finalText = null
        finished.set(false)
        val latch = CountDownLatch(1)
        doneLatch = latch
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.tag)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Balanced silence timeouts: keep mid-sentence pauses intact without excessive trailing delay.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 10000L)
            if (Build.VERSION.SDK_INT >= 33) {
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pipe[0])
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, 16000)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            }
        }
        main.post {
            runCatching {
                val sr = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
                recognizer = sr
                sr.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) = Unit
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                    override fun onPartialResults(partialResults: Bundle?) {
                        val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
                        if (!text.isNullOrEmpty()) partialText = text
                    }
                    override fun onResults(results: Bundle?) {
                        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
                        finalText = text?.takeIf { it.isNotEmpty() } ?: partialText.takeIf { it.isNotEmpty() }
                        complete(latch)
                    }
                    override fun onError(error: Int) {
                        android.util.Log.w("GlassesConnect", "AndroidPipeSpeech error=$error partial=${partialText.length}")
                        finalText = partialText.takeIf { it.isNotEmpty() }
                        complete(latch)
                        if (finalText == null) onReadyError()
                    }
                })
                sr.startListening(intent)
            }.onFailure {
                android.util.Log.w("GlassesConnect", "AndroidPipeSpeech start failed: ${it.message}")
                complete(latch)
                onReadyError()
            }
        }
        return true
    }

    fun feed(pcm: ByteArray) {
        val out = writeStream ?: return
        if (finished.get()) return
        runCatching { out.write(pcm) }.onFailure { closeWrite() }
    }

    /** Signals EOF on the PCM pipe and waits briefly for Google SpeechRecognizer to finalize. */
    fun finishAndGet(timeoutMs: Long = 3000): String? {
        closeWrite()
        val latch = doneLatch
        if (latch != null) runCatching { latch.await(timeoutMs, TimeUnit.MILLISECONDS) }
        val result = finalText?.takeIf { it.isNotBlank() } ?: partialText.takeIf { it.isNotBlank() }
        cancel()
        return result
    }

    fun cancel() {
        finished.set(true)
        closeWrite()
        doneLatch?.countDown()
        doneLatch = null
        val r = readEnd; readEnd = null
        main.post {
            runCatching { recognizer?.cancel() }
            runCatching { recognizer?.destroy() }
            recognizer = null
            runCatching { r?.close() }
        }
    }

    private fun closeWrite() {
        val out = writeStream
        writeStream = null
        runCatching { out?.flush() }
        runCatching { out?.close() }
    }

    private fun complete(latch: CountDownLatch) {
        finished.set(true)
        closeWrite()
        latch.countDown()
    }
}
