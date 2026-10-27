package io.github.spotlighterr.xiaomigpt

import android.content.Intent
import android.content.ComponentName
import android.os.Bundle
import android.os.ResultReceiver
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession

/** Lightweight system-bound entry point. It never opens the microphone or account store. */
class GlassesVoiceService : VoiceInteractionService() {
    private var ready = false
    private var testPending = false
    private var destroyed = false
    private var reply: ResultReceiver? = null
    override fun onReady() {
        super.onReady()
        if (destroyed) return
        ready = true
        setDisabledShowContext(VoiceInteractionSession.SHOW_WITH_ASSIST or VoiceInteractionSession.SHOW_WITH_SCREENSHOT)
        runPendingTest()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TEST) {
            @Suppress("DEPRECATION")
            val receiver = intent.getParcelableExtra<ResultReceiver>(AssistantProbe.EXTRA_REPLY)
            reply = receiver
            testPending = true
            if (!ready) reply?.send(AssistantProbe.WAITING, null)
            runPendingTest()
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }
    private fun runPendingTest() {
        if (!ready || !testPending) return
        testPending = false
        try {
            if (isActiveService(this, ComponentName(this, GlassesVoiceService::class.java))) {
                showSession(Bundle().apply { putBoolean(EXTRA_APP_TEST, true) }, 0)
                reply?.send(AssistantProbe.REQUESTED, null)
            } else reply?.send(AssistantProbe.NOT_ACTIVE, null)
        } catch (_: Exception) { reply?.send(AssistantProbe.FAILED, null) }
        reply = null
    }
    override fun onShutdown() { ready = false; testPending = false; reply?.send(AssistantProbe.SHUT_DOWN, null); reply = null; super.onShutdown() }
    override fun onDestroy() { destroyed = true; ready = false; testPending = false; reply?.send(AssistantProbe.SHUT_DOWN, null); reply = null; super.onDestroy() }
    companion object {
        const val ACTION_TEST = "io.github.spotlighterr.xiaomigpt.TEST_ASSISTANT"
        const val EXTRA_APP_TEST = "io.github.spotlighterr.xiaomigpt.APP_TEST"
    }
}
