package io.github.spotlighterr.xiaomigpt

import okhttp3.Call
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ChatGptClient(private val accounts: AccountStore) {
    private val http = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS).callTimeout(120, TimeUnit.SECONDS).build()
    @Volatile private var inference: Call? = null
    data class Choice(val slug: String, val label: String) { override fun toString() = label }

    private fun get(url: String): JSONObject = http.newCall(Request.Builder().url(url).build()).execute().use { response ->
        check(response.isSuccessful) { "Không tải được thông tin xác thực OpenAI (${response.code})." }
        JSONObject(response.body!!.string())
    }
    private fun postToken(fields: Map<String, String>): JSONObject {
        val form = FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build()
        return http.newCall(Request.Builder().url("${AssistantProtocol.ISSUER}/api/accounts/oauth/token").post(form).build()).execute().use { response ->
            // OAuth bodies contain credentials; never forward them into UI or diagnostics.
            check(response.isSuccessful) { "Đăng nhập cần thử lại (${response.code}). Hãy tiếp tục với ChatGPT lần nữa." }
            JSONObject(response.body!!.string())
        }
    }
    private fun verifyId(token: String, client: String, nonce: String?, subject: String? = null): JSONObject {
        val metadata = get("${AssistantProtocol.ISSUER}/.well-known/openid-configuration")
        require(metadata.getString("issuer") == AssistantProtocol.ISSUER)
        val jwks = metadata.getString("jwks_uri")
        require(jwks == "${AssistantProtocol.ISSUER}/.well-known/jwks.json")
        val keys = get(jwks).getJSONArray("keys")
        return AssistantProtocol.verifyToken(token, keys, client, nonce, System.currentTimeMillis() / 1000, subject)
    }
    fun exchange(callback: AssistantProtocol.Callback, verifier: String, redirect: String, nonce: String, previous: JSONObject?): JSONObject {
        val tokens = postToken(mapOf("grant_type" to "authorization_code", "client_id" to callback.clientId,
            "code" to callback.code, "code_verifier" to verifier, "redirect_uri" to redirect, "resource" to AssistantProtocol.RESOURCE))
        val claims = verifyId(tokens.getString("id_token"), callback.clientId, nonce, previous?.getString("subject"))
        return JSONObject().put("client_id", callback.clientId).put("subject", claims.getString("sub"))
            .put("email", claims.optString("email", "ChatGPT")).also { mergeTokens(it, tokens) }
    }
    private fun mergeTokens(account: JSONObject, tokens: JSONObject) {
        require(tokens.optString("token_type").equals("Bearer", ignoreCase = true)) { "Kiểu token không hợp lệ." }
        account.put("access_token", tokens.getString("access_token"))
            .put("expires_at", System.currentTimeMillis() / 1000 + tokens.getLong("expires_in"))
        listOf("refresh_token", "id_token", "scope").forEach { if (tokens.has(it)) account.put(it, tokens.getString(it)) }
    }
    @Synchronized private fun activeToken(): String {
        val account = accounts.active() ?: error("Hãy đăng nhập ChatGPT trước.")
        check(account.optString("access_token").isNotBlank()) { "Hãy đăng nhập lại tài khoản đã chọn." }
        if (account.optLong("expires_at") <= System.currentTimeMillis() / 1000 + 60) {
            check(account.optString("refresh_token").isNotBlank()) { "Phiên đã hết hạn. Hãy đăng nhập lại." }
            val tokens = postToken(mapOf("grant_type" to "refresh_token", "client_id" to account.getString("client_id"),
                "refresh_token" to account.getString("refresh_token"), "resource" to AssistantProtocol.RESOURCE))
            if (tokens.has("id_token")) verifyId(tokens.getString("id_token"), account.getString("client_id"), null, account.getString("subject"))
            mergeTokens(account, tokens)
            accounts.save(account)
        }
        check(AssistantProtocol.DIRECT_SCOPE in account.optString("scope").split(' ')) { "Đã đăng nhập, nhưng tài khoản chưa cấp quyền dùng gói ChatGPT cho XiaomiGpt." }
        return account.getString("access_token")
    }
    fun models(): List<Choice> {
        val request = Request.Builder().url("${AssistantProtocol.RESOURCE}/models").header("Authorization", "Bearer ${activeToken()}").build()
        return http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { apiError(response.code) }
            val models = JSONObject(response.body!!.string()).getJSONArray("models")
            (0 until models.length()).map { models.getJSONObject(it) }.filter { it.optString("visibility") == "list" }
                .map { Choice(it.getString("slug"), it.getString("display_name")) }
                .also { check(it.isNotEmpty()) { "Tài khoản chưa có model khả dụng cho XiaomiGpt." } }
        }
    }
    fun answer(model: String, history: List<Pair<String, String>>, question: String, active: () -> Boolean = { true }): String {
        val input = JSONArray()
        history.takeLast(12).forEach { (role, text) -> input.put(JSONObject().put("role", role).put("content", text)) }
        input.put(JSONObject().put("role", "user").put("content", question))
        val body = JSONObject().put("model", model).put("input", input).put("store", false).put("stream", true)
            .put("instructions", "You are the voice assistant in XiaomiGpt smart glasses. Respond in English, briefly and naturally for spoken playback. Use plain text. Do not claim to see a camera or photo: no image is attached.")
        val request = Request.Builder().url("${AssistantProtocol.RESOURCE}/responses").header("Authorization", "Bearer ${activeToken()}")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        val call = http.newCall(request)
        inference = call
        try {
            check(active()) { "Đã dừng." }
            return call.execute().use { response ->
                check(response.isSuccessful) { apiError(response.code) }
                check(response.header("Content-Type").orEmpty().startsWith("text/event-stream")) { "OpenAI không trả về luồng hội thoại hợp lệ." }
                val stream = ResponseStream()
                response.body!!.charStream().buffered().use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        check(active() && !call.isCanceled()) { "Đã dừng." }
                        stream.line(line)
                        if (stream.completed) break
                    }
                }
                stream.result()
            }
        } finally { if (inference === call) inference = null }
    }
    fun cancel() { inference?.cancel() }
    fun signOut(): Boolean {
        val account = accounts.active() ?: return true
        var revoked = account.optString("refresh_token").isBlank()
        try {
            if (!revoked) {
                val url = get("${AssistantProtocol.ISSUER}/.well-known/openid-configuration").getString("revocation_endpoint")
                require(url == "${AssistantProtocol.ISSUER}/api/accounts/oauth/revoke")
                val form = FormBody.Builder().add("token", account.getString("refresh_token")).add("token_type_hint", "refresh_token").add("client_id", account.getString("client_id")).build()
                revoked = http.newCall(Request.Builder().url(url).post(form).build()).execute().use { it.code == 200 }
            }
        } catch (_: Exception) { revoked = false }
        finally { accounts.clearTokens(account.getString("client_id")) }
        return revoked
    }
    private fun apiError(code: Int) = when (code) {
        401 -> "Phiên ChatGPT không còn hợp lệ. Hãy đăng nhập lại."
        403 -> "Tài khoản hoặc workspace chưa cho phép XiaomiGpt dùng gói ChatGPT. Kiểm tra quyền trong ChatGPT."
        429 -> "Đã chạm hạn mức ChatGPT. Chờ rồi thử lại hoặc kiểm tra Usage trong ChatGPT."
        else -> "Chưa nhận được câu trả lời từ OpenAI ($code). Thử lại sau."
    }
}

/** No partial answer is accepted unless the server terminates with response.completed. */
class ResponseStream {
    var completed = false; private set
    private val text = StringBuilder()
    private val data = StringBuilder()
    fun line(line: String) {
        if (line.isEmpty()) {
            if (data.isEmpty()) return
            val raw = data.toString().trimEnd('\n'); data.setLength(0)
            if (raw == "[DONE]") return
            val event = JSONObject(raw)
            when (event.optString("type")) {
                "response.output_text.delta" -> text.append(event.getString("delta"))
                "response.completed" -> completed = true
                "response.failed", "response.incomplete", "error" -> error("OpenAI chưa hoàn tất câu trả lời. Thử lại sau.")
            }
            check(text.length <= 32000) { "Câu trả lời quá dài cho chế độ giọng nói." }
        } else if (line.startsWith("data:")) {
            data.append(line.substring(5).removePrefix(" ")).append('\n')
            check(data.length <= 2_000_000) { "Sự kiện hội thoại quá lớn." }
        }
    }
    fun result(): String { check(completed) { "Kết nối kết thúc trước khi ChatGPT trả lời xong." }; return text.toString().trim().also { check(it.isNotBlank()) { "ChatGPT không trả về văn bản để đọc." } } }
}
