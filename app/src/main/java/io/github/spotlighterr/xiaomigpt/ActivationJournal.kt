package io.github.spotlighterr.xiaomigpt

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** Bounded, local event metadata only. An Android event does not identify a physical button. */
class ActivationJournal(context: Context) {
    private val preferences = context.getSharedPreferences("activation-events", Context.MODE_PRIVATE)
    enum class Kind { SYSTEM_SESSION, APP_TEST_SESSION, ASSIST_INTENT, VOICE_COMMAND_INTENT, FOREGROUND_KEY, SESSION_OPEN_FAILED,
        ROLE_REQUESTED, ROLE_RESULT, SETTINGS_ROUTE, SERVICE_TEST_RESULT, SERVICE_TEST_TIMEOUT }
    fun record(kind: Kind, number: Int = 0) = synchronized(lock) {
        val old = runCatching { JSONArray(preferences.getString("events", "[]")) }.getOrDefault(JSONArray())
        val events = JSONArray()
        for (index in maxOf(0, old.length() - 11) until old.length()) events.put(old.getJSONObject(index))
        events.put(JSONObject().put("time", Instant.now().toString()).put("kind", kind.name).put("number", number))
        preferences.edit().putString("events", events.toString()).apply()
    }
    fun report(): String = synchronized(lock) {
        val events = runCatching { JSONArray(preferences.getString("events", "[]")) }.getOrDefault(JSONArray())
        buildString {
            appendLine("XiaomiGpt activation ${BuildConfig.VERSION_NAME}")
            for (index in 0 until events.length()) {
                val item = events.getJSONObject(index)
                appendLine("${item.getString("time")} | ${item.getString("kind")} | flags/key=${item.getInt("number")}")
            }
            if (events.length() == 0) appendLine("No activation event recorded.")
            append("Event origin is not physical-device identification. No speech, transcript, email, MAC or token included.")
        }
    }
    fun clear() = synchronized(lock) { preferences.edit().remove("events").apply() }
    companion object { private val lock = Any() }
}

/** Collapse repeated Android deliveries from one press, independent of the conversation state. */
class ActivationGate {
    private var lastAt: Long? = null
    fun accept(nowMs: Long): Boolean {
        val previous = lastAt
        if (previous != null && nowMs >= previous && nowMs - previous < 750) return false
        lastAt = nowMs
        return true
    }
}
