package wtf.mahdi.glassesconnect.accounts

import io.github.spotlighterr.xiaomigpt.SpeechLanguage
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Minimal OpenAI-compatible DeepSeek Chat Completions client (text-only, low latency). */
class DeepSeekClient {
    @Volatile private var activeConnection: HttpURLConnection? = null

    fun cancel() {
        runCatching { activeConnection?.disconnect() }
        activeConnection = null
    }

    fun ask(
        apiKey: String,
        model: String,
        history: List<Pair<String, String>>,
        question: String,
        language: SpeechLanguage,
        active: () -> Boolean,
    ): String {
        val key = apiKey.trim()
        require(key.isNotBlank()) { "Nhập DeepSeek API Key trước." }
        val systemPrompt = if (language == SpeechLanguage.VIETNAMESE) {
            "Bạn là trợ lý giọng nói trên kính thông minh. Trả lời bằng tiếng Việt ngắn gọn, tự nhiên, trực tiếp, không dùng markdown phức tạp để dễ nghe khi đọc thành tiếng."
        } else {
            "You are a smart glasses voice assistant. Answer briefly in natural plain English suitable for spoken playback."
        }
        val messages = JSONArray().put(JSONObject().put("role", "system").put("content", systemPrompt))
        history.forEach { (role, text) ->
            messages.put(JSONObject().put("role", role).put("content", text))
        }
        messages.put(JSONObject().put("role", "user").put("content", question))
        val body = JSONObject()
            .put("model", model.ifBlank { "deepseek-chat" })
            .put("messages", messages)
            .put("stream", false)
            .toString()

        val conn = (URL("https://api.deepseek.com/v1/chat/completions").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 45_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $key")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        activeConnection = conn
        try {
            check(active()) { "Đã dừng." }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            check(active()) { "Đã dừng." }
            if (code !in 200..299) {
                val msg = runCatching { JSONObject(text).optJSONObject("error")?.optString("message") }.getOrNull()
                error(msg?.takeIf { it.isNotBlank() } ?: "DeepSeek trả lỗi HTTP $code.")
            }
            val content = JSONObject(text)
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()
                .trim()
            check(content.isNotBlank()) { "DeepSeek chưa trả nội dung." }
            return content
        } finally {
            if (activeConnection === conn) activeConnection = null
            conn.disconnect()
        }
    }

    companion object {
        val MODELS = listOf("deepseek-chat", "deepseek-reasoner")
    }
}
