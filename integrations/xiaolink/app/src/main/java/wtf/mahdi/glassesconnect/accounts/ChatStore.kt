package wtf.mahdi.glassesconnect.accounts

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class ChatMessage(
    val role: String, // "user" or "assistant"
    val text: String,
    val time: Long = System.currentTimeMillis(),
    val provider: String = "",
    val hasImage: Boolean = false,
    val imagePath: String? = null,
)

data class ChatThread(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val messages: List<ChatMessage> = emptyList(),
)

/**
 * Persistent multi-thread chat history stored locally in app noBackupFilesDir (JSON).
 * Keeps conversations across app restarts so glasses voice turns and typed chats share one history.
 */
class ChatStore(private val file: File) {
    private val _threads = MutableStateFlow(load())
    val threads: StateFlow<List<ChatThread>> = _threads.asStateFlow()

    private val _activeId = MutableStateFlow(_threads.value.firstOrNull()?.id ?: createInitial().id)
    val activeId: StateFlow<String> = _activeId.asStateFlow()

    @Synchronized
    private fun createInitial(): ChatThread {
        val t = ChatThread()
        _threads.value = listOf(t)
        return t
    }

    @Synchronized
    fun activeThread(): ChatThread {
        val id = _activeId.value
        return _threads.value.firstOrNull { it.id == id } ?: _threads.value.firstOrNull() ?: createInitial().also { _activeId.value = it.id }
    }

    @Synchronized
    fun newThread(): ChatThread {
        val current = activeThread()
        if (current.messages.isEmpty()) return current
        val fresh = ChatThread()
        _threads.value = (listOf(fresh) + _threads.value).take(MAX_THREADS)
        _activeId.value = fresh.id
        save()
        return fresh
    }

    @Synchronized
    fun selectThread(id: String) {
        if (_threads.value.any { it.id == id }) _activeId.value = id
    }

    @Synchronized
    fun deleteThread(id: String) {
        _threads.value.firstOrNull { it.id == id }?.messages?.forEach { m -> m.imagePath?.let { runCatching { File(it).delete() } } }
        val remaining = _threads.value.filterNot { it.id == id }
        if (remaining.isEmpty()) {
            val fresh = ChatThread()
            _threads.value = listOf(fresh)
            _activeId.value = fresh.id
        } else {
            _threads.value = remaining
            if (_activeId.value == id) _activeId.value = remaining.first().id
        }
        save()
    }

    @Synchronized
    fun clearActive() {
        val current = activeThread()
        current.messages.forEach { m -> m.imagePath?.let { runCatching { File(it).delete() } } }
        val id = current.id
        _threads.value = _threads.value.map {
            if (it.id == id) it.copy(title = "", updatedAt = System.currentTimeMillis(), messages = emptyList()) else it
        }
        save()
    }

    /** Automatically starts a fresh thread if the current thread has been idle longer than [idleMs]. */
    @Synchronized
    fun rotateIfIdle(idleMs: Long = IDLE_NEW_CHAT_MS): Boolean {
        val current = activeThread()
        if (current.messages.isNotEmpty() && System.currentTimeMillis() - current.updatedAt > idleMs) {
            newThread()
            return true
        }
        return false
    }

    @Synchronized
    fun appendTurn(question: String, answer: String, provider: String, hasImage: Boolean = false, imagePath: String? = null) {
        val now = System.currentTimeMillis()
        val current = activeThread()
        val updatedMessages = (current.messages + listOf(
            ChatMessage("user", question, now, provider, hasImage, imagePath),
            ChatMessage("assistant", answer, now, provider, false, null),
        )).takeLast(MAX_MESSAGES_PER_THREAD)
        val title = current.title.ifBlank { question.trim().replace('\n', ' ').take(48) }
        val updated = current.copy(title = title, updatedAt = now, messages = updatedMessages)
        _threads.value = (listOf(updated) + _threads.value.filterNot { it.id == current.id }).take(MAX_THREADS)
        _activeId.value = updated.id
        save()
    }

    @Synchronized
    fun historyPairs(limit: Int = 12): List<Pair<String, String>> =
        activeThread().messages.takeLast(limit).map { it.role to it.text.take(1500) }

    private fun load(): List<ChatThread> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val msgsArr = o.optJSONArray("messages") ?: JSONArray()
                val msgs = (0 until msgsArr.length()).map { j ->
                    val m = msgsArr.getJSONObject(j)
                    ChatMessage(
                        role = m.optString("role", "user"),
                        text = m.optString("text", ""),
                        time = m.optLong("time", 0L),
                        provider = m.optString("provider", ""),
                        hasImage = m.optBoolean("hasImage", false),
                        imagePath = m.optString("imagePath", "").takeIf { it.isNotBlank() },
                    )
                }
                ChatThread(
                    id = o.optString("id", UUID.randomUUID().toString()),
                    title = o.optString("title", ""),
                    updatedAt = o.optLong("updatedAt", 0L),
                    messages = msgs,
                )
            }.take(MAX_THREADS)
        }.getOrElse { emptyList() }
    }

    private fun save() {
        runCatching {
            val arr = JSONArray()
            _threads.value.forEach { t ->
                val msgs = JSONArray()
                t.messages.forEach { m ->
                    msgs.put(JSONObject()
                        .put("role", m.role)
                        .put("text", m.text)
                        .put("time", m.time)
                        .put("provider", m.provider)
                        .put("hasImage", m.hasImage)
                        .put("imagePath", m.imagePath.orEmpty()))
                }
                arr.put(JSONObject()
                    .put("id", t.id)
                    .put("title", t.title)
                    .put("updatedAt", t.updatedAt)
                    .put("messages", msgs))
            }
            file.parentFile?.mkdirs()
            file.writeText(arr.toString())
        }
    }

    companion object {
        private const val MAX_THREADS = 30
        private const val MAX_MESSAGES_PER_THREAD = 60
        private const val IDLE_NEW_CHAT_MS = 15 * 60_000L
    }
}
