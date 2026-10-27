package io.github.spotlighterr.xiaomigpt

import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.RSAPublicKeySpec
import java.math.BigInteger
import java.util.Base64

/** Pure protocol checks shared by the browser callback and ID-token verifier. */
object AssistantProtocol {
    const val ISSUER = "https://auth.openai.com"
    const val RESOURCE = "https://api.openai.com/v1"
    const val DIRECT_SCOPE = "chatgpt.tokens.use.direct"
    fun randomSecret() = encode(ByteArray(32).also { SecureRandom().nextBytes(it) })
    fun encode(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    fun decode(value: String): ByteArray = Base64.getUrlDecoder().decode(value)
    fun challenge(verifier: String) = encode(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
    fun form(values: Map<String, String>) = values.entries.joinToString("&") {
        URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
    }

    data class Callback(val code: String, val clientId: String)
    class ConsentDeclined : IllegalArgumentException("Bạn chưa cấp quyền đăng nhập. Thử lại khi sẵn sàng.")
    fun callback(target: String, expectedState: String, registeredClient: String?): Callback {
        val uri = URI(target)
        require(!uri.isAbsolute && uri.rawAuthority == null && uri.rawPath == "/auth/callback" && uri.rawFragment == null) { "Callback không hợp lệ." }
        val parameters = linkedMapOf<String, String>()
        uri.rawQuery.orEmpty().split('&').filter { it.isNotEmpty() }.forEach {
            val pair = it.split('=', limit = 2)
            val key = URLDecoder.decode(pair[0], "UTF-8")
            require(!parameters.containsKey(key)) { "Callback có tham số trùng lặp." }
            parameters[key] = URLDecoder.decode(pair.getOrElse(1) { "" }, "UTF-8")
        }
        require(MessageDigest.isEqual(parameters["state"].orEmpty().toByteArray(), expectedState.toByteArray())) { "Không xác minh được phiên đăng nhập." }
        if (parameters.containsKey("error")) throw ConsentDeclined()
        val client = parameters["client_id"] ?: registeredClient
        require(!client.isNullOrBlank() && client != "dynamic_agent_client") { "Chưa nhận được mã đăng ký XiaomiGpt." }
        require(registeredClient == null || registeredClient == client) { "Mã đăng ký không khớp tài khoản." }
        val code = parameters["code"].orEmpty()
        require(code.isNotBlank()) { "Chưa nhận được mã đăng nhập." }
        return Callback(code, client)
    }

    fun verifyToken(token: String, keys: org.json.JSONArray, client: String, nonce: String?, now: Long, subject: String? = null): JSONObject {
        val parts = token.split('.')
        require(parts.size == 3) { "ID token không hợp lệ." }
        val header = JSONObject(decode(parts[0]).toString(Charsets.UTF_8))
        require(header.getString("alg") == "RS256" && !header.has("crit")) { "Kiểu chữ ký ID token không được hỗ trợ." }
        val jwk = (0 until keys.length()).map { keys.getJSONObject(it) }.single {
            it.optString("kid") == header.getString("kid") && it.optString("kty") == "RSA" &&
                it.optString("use", "sig") == "sig" && it.optString("alg", "RS256") == "RS256"
        }
        val modulus = BigInteger(1, decode(jwk.getString("n")))
        require(modulus.bitLength() >= 2048) { "Khóa ký ID token không đủ mạnh." }
        val key = KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(modulus, BigInteger(1, decode(jwk.getString("e")))))
        val verifier = Signature.getInstance("SHA256withRSA").apply { initVerify(key); update("${parts[0]}.${parts[1]}".toByteArray(Charsets.US_ASCII)) }
        require(verifier.verify(decode(parts[2]))) { "Không xác minh được chữ ký tài khoản ChatGPT." }
        return JSONObject(decode(parts[1]).toString(Charsets.UTF_8)).also { validateClaims(it, client, nonce, now, subject) }
    }

    fun validateClaims(claims: JSONObject, clientId: String, nonce: String?, now: Long, subject: String? = null) {
        require(claims.getString("iss") == ISSUER) { "ID token sai nguồn cấp." }
        val aud = claims.get("aud")
        val audiences = if (aud is String) listOf(aud) else (aud as org.json.JSONArray).let { a -> (0 until a.length()).map { a.getString(it) } }
        require(clientId in audiences) { "ID token không dành cho XiaomiGpt." }
        if (audiences.size > 1 || claims.has("azp")) require(claims.getString("azp") == clientId) { "ID token sai client." }
        require(claims.getLong("exp") > now && claims.optLong("iat", now) <= now + 60 && claims.optLong("nbf", 0) <= now + 60) { "ID token đã hết hạn hoặc chưa có hiệu lực." }
        if (nonce != null) require(claims.getString("nonce") == nonce) { "ID token không khớp lần đăng nhập." }
        require(claims.getString("sub").isNotBlank()) { "Thiếu danh tính tài khoản." }
        if (subject != null) require(claims.getString("sub") == subject) { "Danh tính tài khoản đã thay đổi." }
    }
}
