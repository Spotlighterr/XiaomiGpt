package io.github.spotlighterr.xiaomigpt

import okhttp3.Response
import org.json.JSONObject
import java.util.Locale

/** Deliberately excludes prompts, answers, account identity, URLs, credentials and raw bodies. */
data class InferenceDiagnostic(
    val httpStatus: Int,
    val mediaType: String,
    val shape: String,
    val errorCode: String? = null,
    val requestId: String? = null,
) {
    fun report() = buildString {
        appendLine("HTTP: $httpStatus")
        appendLine("Media type: $mediaType")
        appendLine("Body shape: $shape")
        appendLine("Error code: ${errorCode ?: "not provided / unrecognized"}")
        append("Request ID: ${requestId ?: "not provided"}")
    }
}

/** Accept genuine SSE even if its MIME is mislabeled; never interpret HTML/JSON as an answer. */
object InferenceResponse {
    fun read(response: Response, active: () -> Boolean, diagnostic: (InferenceDiagnostic) -> Unit): String {
        val type = response.body?.contentType()?.let { "${it.type}/${it.subtype}" }?.lowercase(Locale.ROOT) ?: "missing"
        val requestId = response.header("x-request-id")?.takeIf { it.matches(Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,159}")) }
        var info = InferenceDiagnostic(response.code, type, "not inspected", requestId = requestId)
        diagnostic(info)
        val body = response.body ?: error("OpenAI trả về nội dung rỗng (HTTP ${response.code}). Mở Chi tiết lỗi để kiểm tra.")
        check(active()) { "Đã dừng." }
        val source = body.source()
        // Peek at a bounded prefix without consuming the body or retaining it in diagnostics.
        // Correct SSE does not need to wait for a prefix buffer to fill.
        val isSse = type == "text/event-stream" || run {
            val peek = source.peek()
            peek.request(64)
            val prefix = peek.buffer.clone().readUtf8(minOf(peek.buffer.size, 64)).trimStart('\uFEFF', ' ', '\t', '\r', '\n')
            prefix.startsWith("event:") || prefix.startsWith("data:") || prefix.startsWith(":")
        }
        if (!response.isSuccessful || !isSse) {
            val peek = source.peek()
            val limit = 65536L
            peek.request(limit + 1)
            val tooLarge = peek.buffer.size > limit
            val raw = peek.buffer.clone().readUtf8(minOf(peek.buffer.size, limit))
            val parsed = if (!tooLarge) runCatching { JSONObject(raw) }.getOrNull() else null
            val error = parsed?.optJSONObject("error")
            val code = safeCode(error?.optString("code"))
            val shape = when {
                tooLarge -> "oversized non-SSE"
                raw.isBlank() -> "empty"
                error != null -> "JSON error"
                parsed?.has("detail") == true -> "JSON detail"
                parsed?.optString("object") == "response" -> "JSON response instead of SSE"
                parsed != null -> "JSON other"
                raw.trimStart().startsWith("<") -> "HTML"
                else -> "text / unrecognized"
            }
            info = info.copy(shape = shape, errorCode = code)
            diagnostic(info)
            throw IllegalStateException(failureMessage(response.code, code, shape))
        }
        info = info.copy(shape = if (type == "text/event-stream") "SSE" else "SSE with nonstandard MIME")
        diagnostic(info)
        val stream = ResponseStream { code ->
            info = info.copy(shape = "SSE failure", errorCode = safeCode(code))
            diagnostic(info)
        }
        body.charStream().buffered().use { reader ->
            var first = true
            while (true) {
                val raw = reader.readLine() ?: break
                check(active()) { "Đã dừng." }
                val line = if (first) raw.removePrefix("\uFEFF") else raw
                first = false
                stream.line(line)
                if (stream.completed) break
            }
        }
        check(active()) { "Đã dừng." }
        return stream.result()
    }

    private val recognizedCodes = setOf(
        "subscription_sharing_user_not_eligible", "subscription_sharing_usage_limit_exceeded",
        "subscription_sharing_usage_unavailable", "subscription_sharing_unsupported_capability",
        "subscription_sharing_route_not_supported", "subscription_sharing_invalid_user",
        "subscription_sharing_user_unavailable", "chatpass_v2_scope_not_authorized",
        "chatpass_v2_invalid_authorization_context", "model_not_found", "invalid_request_error",
        "invalid_api_key", "insufficient_quota", "rate_limit_exceeded", "server_error",
        "unsupported_model", "context_length_exceeded", "unsupported_country_region_territory",
    )
    internal fun safeCode(code: String?) = code?.takeIf { it in recognizedCodes }
    internal fun failureMessage(status: Int, code: String?, shape: String): String {
        val reason = when (code) {
            "subscription_sharing_user_not_eligible" -> "Tài khoản/workspace chưa đủ điều kiện dùng gói ChatGPT trong app."
            "subscription_sharing_usage_limit_exceeded", "insufficient_quota", "rate_limit_exceeded" -> "Đã chạm hạn mức. Mở Quản lý Usage trong ChatGPT."
            "subscription_sharing_unsupported_capability", "unsupported_model", "model_not_found" -> "Model hoặc tính năng này chưa được chấp nhận qua luồng tích hợp. Xem Chi tiết lỗi."
            "subscription_sharing_route_not_supported" -> "OpenAI chưa cho phép yêu cầu này trên tuyến tích hợp hiện tại."
            "chatpass_v2_scope_not_authorized", "chatpass_v2_invalid_authorization_context" -> "Quyền dùng gói ChatGPT chưa hợp lệ cho yêu cầu này."
            "subscription_sharing_usage_unavailable", "subscription_sharing_user_unavailable", "server_error" -> "Dịch vụ ChatGPT tạm thời chưa sẵn sàng. Thử lại sau."
            "subscription_sharing_invalid_user", "invalid_api_key" -> "Phiên tài khoản chưa được OpenAI chấp nhận. Xem Chi tiết lỗi trước khi đăng nhập lại."
            "context_length_exceeded" -> "Cuộc hội thoại quá dài. Bắt đầu cuộc trò chuyện mới."
            "unsupported_country_region_territory" -> "OpenAI báo khu vực truy cập chưa được hỗ trợ cho yêu cầu này."
            else -> when {
                status == 401 -> "OpenAI chưa chấp nhận phiên đăng nhập cho yêu cầu hội thoại."
                status == 403 -> "OpenAI từ chối yêu cầu do quyền truy cập hoặc chính sách."
                status == 429 -> "Đã chạm giới hạn yêu cầu. Mở Quản lý Usage trong ChatGPT."
                status >= 500 -> "Dịch vụ OpenAI tạm thời gặp lỗi. Thử lại sau."
                shape == "HTML" -> "Máy chủ trả về trang HTML thay vì hội thoại; chưa xác định nguồn trang."
                shape == "JSON detail" || shape == "JSON error" -> "Máy chủ trả về thông báo thay vì bắt đầu hội thoại."
                shape == "JSON response instead of SSE" -> "Máy chủ trả JSON thay vì luồng hội thoại đã yêu cầu."
                else -> "Phản hồi chưa đúng định dạng hội thoại."
            }
        }
        return "$reason (HTTP $status). Bấm Chi tiết lỗi để sao chép thông tin kiểm tra."
    }
}
