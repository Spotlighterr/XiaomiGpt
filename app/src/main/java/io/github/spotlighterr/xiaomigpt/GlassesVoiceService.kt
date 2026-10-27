package io.github.spotlighterr.xiaomigpt

import android.content.Intent
import android.content.ComponentName
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession

/** Lightweight system-bound entry point. It never opens the microphone or account store. */
class GlassesVoiceService : VoiceInteractionService() {
    private var ready = false
    private var testPending = false
    private var destroyed = false
    override fun onReady() {
        super.onReady()
        if (destroyed) return
        ready = true
        setDisabledShowContext(VoiceInteractionSession.SHOW_WITH_ASSIST or VoiceInteractionSession.SHOW_WITH_SCREENSHOT)
        runPendingTest()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TEST) { testPending = true; runPendingTest() }
        stopSelf(startId)
        return START_NOT_STICKY
    }
    private fun runPendingTest() {
        if (!ready || !testPending) return
        testPending = false
        if (isActiveService(this, ComponentName(this, GlassesVoiceService::class.java))) {
            runCatching { showSession(Bundle().apply { putBoolean(EXTRA_APP_TEST, true) }, 0) }
        }
    }
    override fun onShutdown() { ready = false; testPending = false; super.onShutdown() }
    override fun onDestroy() { destroyed = true; ready = false; testPending = false; super.onDestroy() }
    companion object {
        const val ACTION_TEST = "io.github.spotlighterr.xiaomigpt.TEST_ASSISTANT"
        const val EXTRA_APP_TEST = "io.github.spotlighterr.xiaomigpt.APP_TEST"
    }
}
