package io.github.spotlighterr.xiaomigpt

import android.content.Intent
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/** Metadata compatibility field required by Android; unused by the framework on API 31+.
 * XiaomiGpt performs its own routed PCM recognition and is not a global STT provider. */
class LocalRecognitionPlaceholder : RecognitionService() {
    override fun onStartListening(intent: Intent?, listener: Callback) { listener.error(SpeechRecognizer.ERROR_CLIENT) }
    override fun onStopListening(listener: Callback) { listener.error(SpeechRecognizer.ERROR_CLIENT) }
    override fun onCancel(listener: Callback) = Unit
}
