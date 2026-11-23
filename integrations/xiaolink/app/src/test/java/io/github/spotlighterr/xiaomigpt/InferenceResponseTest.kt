package io.github.spotlighterr.xiaomigpt

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class InferenceResponseTest {
    private val sse = "data: {\"type\":\"response.output_text.delta\",\"delta\":\"Hello\"}\n\ndata: {\"type\":\"response.completed\"}\n\n"
    private fun response(type: String?, body: String, status: Int = 200): Response = Response.Builder()
        .request(Request.Builder().url("https://api.openai.com/v1/responses").build())
        .protocol(Protocol.HTTP_1_1).code(status).message("Test")
        .header("x-request-id", "req_test123")
        .apply { if (type != null) header("Content-Type", type) }
        .body(body.toResponseBody(type?.toMediaTypeOrNull())).build()

    @Test fun `SSE content type accepts mixed case and parameters`() {
        response("Text/Event-Stream; charset=utf-8", sse).use {
            assertEquals("Hello", InferenceResponse.read(it, { true }) {})
        }
    }
    @Test fun `genuine completed SSE works with missing or mislabeled MIME`() {
        for (type in listOf(null, "application/json", "text/plain")) {
            var diagnostic: InferenceDiagnostic? = null
            response(type, "\uFEFFevent: response.output_text.delta\n$sse").use {
                assertEquals("Hello", InferenceResponse.read(it, { true }) { item -> diagnostic = item })
            }
            assertEquals("SSE with nonstandard MIME", diagnostic!!.shape)
        }
    }
    @Test fun `HTTP 200 JSON error exposes code instead of MIME rejection`() {
        var diagnostic: InferenceDiagnostic? = null
        response("application/json", """{"error":{"code":"model_not_found","message":"private prompt"}}""").use {
            try { InferenceResponse.read(it, { true }) { item -> diagnostic = item }; fail() }
            catch (e: IllegalStateException) { assertTrue(e.message!!.contains("Model")); assertFalse(e.message!!.contains("private prompt")) }
        }
        assertEquals("model_not_found", diagnostic!!.errorCode)
        assertEquals(200, diagnostic!!.httpStatus)
        assertEquals("req_test123", diagnostic!!.requestId)
    }
    @Test fun `quota response keeps status and safe code but not body text`() {
        var diagnostic: InferenceDiagnostic? = null
        response("application/json", """{"error":{"code":"subscription_sharing_usage_limit_exceeded","message":"secret question"}}""", 429).use {
            try { InferenceResponse.read(it, { true }) { item -> diagnostic = item }; fail() }
            catch (e: IllegalStateException) { assertTrue(e.message!!.contains("Usage")) }
        }
        assertEquals(429, diagnostic!!.httpStatus)
        assertEquals("subscription_sharing_usage_limit_exceeded", diagnostic!!.errorCode)
        assertFalse(diagnostic!!.report().contains("secret question"))
    }
    @Test fun `admission detail and HTML are never treated as answers`() {
        for ((type, body, shape) in listOf(
            Triple("application/json", """{"detail":"private token and email@example.com"}""", "JSON detail"),
            Triple("text/html", "<html>private account text</html>", "HTML"),
        )) {
            var diagnostic: InferenceDiagnostic? = null
            response(type, body).use {
                try { InferenceResponse.read(it, { true }) { item -> diagnostic = item }; fail() }
                catch (_: IllegalStateException) { }
            }
            assertEquals(shape, diagnostic!!.shape)
            assertFalse(diagnostic!!.report().contains("private"))
            assertFalse(diagnostic!!.report().contains("email@example.com"))
        }
    }
    @Test fun `completed JSON object does not masquerade as completed SSE`() {
        var diagnostic: InferenceDiagnostic? = null
        response("application/json", """{"object":"response","status":"completed","output":[]}""").use {
            try { InferenceResponse.read(it, { true }) { item -> diagnostic = item }; fail() }
            catch (_: IllegalStateException) { }
        }
        assertEquals("JSON response instead of SSE", diagnostic!!.shape)
    }
    @Test fun `truncated mislabeled SSE never succeeds`() {
        response("text/plain", "data: {\"type\":\"response.output_text.delta\",\"delta\":\"Partial\"}\n\n").use {
            try { InferenceResponse.read(it, { true }) {}; fail() }
            catch (e: IllegalStateException) { assertTrue(e.message!!.contains("kết thúc")) }
        }
    }
    @Test fun `stream failure retains safe error code for diagnosis`() {
        var diagnostic: InferenceDiagnostic? = null
        val body = "data: {\"type\":\"response.failed\",\"response\":{\"error\":{\"code\":\"subscription_sharing_usage_unavailable\",\"message\":\"private text\"}}}\n\n"
        response("text/event-stream", body).use {
            try { InferenceResponse.read(it, { true }) { item -> diagnostic = item }; fail() }
            catch (_: IllegalStateException) { }
        }
        assertEquals("SSE failure", diagnostic!!.shape)
        assertEquals("subscription_sharing_usage_unavailable", diagnostic!!.errorCode)
        assertFalse(diagnostic!!.report().contains("private text"))
    }
    @Test fun `cancelled turn is rejected before body consumption`() {
        response("text/event-stream", sse).use {
            try { InferenceResponse.read(it, { false }) {}; fail() }
            catch (e: IllegalStateException) { assertEquals("Đã dừng.", e.message) }
        }
    }
}
