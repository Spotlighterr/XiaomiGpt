package io.github.spotlighterr.xiaomigpt

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

class GlassesSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = GlassesSession(this)
}

private class GlassesSession(private val appContext: Context) : VoiceInteractionSession(appContext) {
    override fun onCreate() {
        super.onCreate()
        setUiEnabled(false)
        setDisabledShowContext(SHOW_WITH_ASSIST or SHOW_WITH_SCREENSHOT)
    }
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        val journal = ActivationJournal(appContext)
        val appTest = args?.getBoolean(GlassesVoiceService.EXTRA_APP_TEST) == true
        journal.record(if (appTest) ActivationJournal.Kind.APP_TEST_SESSION else ActivationJournal.Kind.SYSTEM_SESSION, showFlags)
        // This iteration does not expose account/conversation UI over the keyguard.
        val locked = appContext.getSystemService(KeyguardManager::class.java).isKeyguardLocked
        val intent = Intent(appContext, AssistantActivity::class.java)
            .setAction(if (locked) Intent.ACTION_MAIN else Intent.ACTION_ASSIST)
        try { startAssistantActivity(intent) }
        catch (_: Exception) { journal.record(ActivationJournal.Kind.SESSION_OPEN_FAILED); finish() }
    }
    override fun onTaskFinished(intent: Intent?, taskId: Int) { super.onTaskFinished(intent, taskId); finish() }
}
