package wtf.mahdi.glassesconnect.accounts

import io.github.spotlighterr.xiaomigpt.VisualInput
import java.net.InetAddress
import java.net.URI
import java.util.Base64
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

object BridgePolicy {
    fun baseUrl(raw: String): String {
        val u = URI(raw.trim())
        require(u.scheme in setOf("http", "https") && !u.host.isNullOrBlank() && u.rawUserInfo == null &&
            u.rawQuery == null && u.rawFragment == null && u.path in listOf("", "/")) { "Nhập địa chỉ máy chủ dạng https://host:port hoặc HTTP trong mạng riêng." }
        require(u.port == -1 || u.port in 1..65535)
        return u.toASCIIString().trimEnd('/')
    }
    fun privateAddress(a: InetAddress): Boolean {
        val b = a.address
        return a.isLoopbackAddress || a.isSiteLocalAddress ||
            (b.size == 4 && (b[0].toInt() and 255) == 100 && (b[1].toInt() and 255) in 64..127)
    }
}

class BridgeClient {
    private val calls = java.util.concurrent.ConcurrentHashMap.newKeySet<Call>()
    private fun client(base: String): OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false).callTimeout(125, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS).dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> = Dns.SYSTEM.lookup(hostname).also { addresses ->
                if (base.startsWith("http://")) require(addresses.isNotEmpty() && addresses.all(BridgePolicy::privateAddress)) {
                    "HTTP chỉ được dùng với địa chỉ mạng riêng."
                }
            }
        }).build()
    fun status(url: String, token: String): JSONObject = request(url, token, "/v1/status", null)
    fun ask(url: String, token: String, question: String, jpeg: ByteArray?, model: String,
            history: List<Pair<String, String>>): String {
        val body = JSONObject().put("provider", "gemini").put("question", question)
            .put("history", JSONArray(history.takeLast(12).map { (role, text) -> JSONObject().put("role", role).put("text", text) }))
        if (jpeg != null) {
            VisualInput.content(question, jpeg)
            body.put("image_base64", Base64.getEncoder().encodeToString(jpeg))
        }
        if (model.isNotBlank()) body.put("model", model)
        return request(url, token, "/v1/ask", body).getString("answer").also { check(it.isNotBlank()) { "Máy chủ trả lời rỗng." } }
    }
    private fun request(url: String, token: String, path: String, body: JSONObject?): JSONObject {
        val base = BridgePolicy.baseUrl(url)
        require(token.length >= 32 && '\n' !in token && '\r' !in token) { "Mã kết nối máy chủ phải có ít nhất 32 ký tự." }
        val req = Request.Builder().url(base + path).header("Authorization", "Bearer $token")
        if (body != null) req.post(body.toString().toRequestBody("application/json".toMediaType()))
        val task = client(base).newCall(req.build()); calls.add(task)
        try {
            return task.execute().use { response ->
                check(response.isSuccessful) { when (response.code) {
                    401, 403 -> "Máy chủ từ chối mã kết nối."
                    409, 429, 503 -> "Máy chủ đang bận hoặc tài khoản chưa sẵn sàng."
                    else -> "Không nhận được trả lời từ máy chủ (HTTP ${response.code})."
                } }
                val source = response.body?.source() ?: error("Máy chủ trả về nội dung rỗng.")
                source.request(65537); require(source.buffer.size <= 65536) { "Phản hồi máy chủ quá lớn." }
                JSONObject(source.readUtf8())
            }
        } finally { calls.remove(task) }
    }
    fun cancel() { calls.toList().forEach { it.cancel() } }
}
