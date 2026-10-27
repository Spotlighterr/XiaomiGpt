package io.github.spotlighterr.xiaomigpt

import org.junit.Assert.*
import org.junit.Test

class ResponseStreamTest {
    private fun send(stream: ResponseStream, json: String) { stream.line("data: $json"); stream.line("") }
    @Test fun `completed stream combines deltas and ignores comments`() {
        val stream = ResponseStream()
        stream.line(": keepalive")
        send(stream, """{"type":"response.output_text.delta","delta":"Hello "}""")
        send(stream, """{"type":"response.output_text.delta","delta":"world"}""")
        send(stream, """{"type":"response.completed"}""")
        assertEquals("Hello world", stream.result())
    }
    @Test(expected = IllegalStateException::class) fun `dropped connection never accepts partial reply`() {
        val stream = ResponseStream()
        send(stream, """{"type":"response.output_text.delta","delta":"Incomplete"}""")
        stream.result()
    }
    @Test(expected = IllegalStateException::class) fun `server failure rejects partial reply`() {
        val stream = ResponseStream()
        send(stream, """{"type":"response.output_text.delta","delta":"Incomplete"}""")
        send(stream, """{"type":"response.failed"}""")
    }
    @Test(expected = IllegalStateException::class) fun `done sentinel alone is not successful inference`() {
        val stream = ResponseStream(); send(stream, "[DONE]"); stream.result()
    }
}
