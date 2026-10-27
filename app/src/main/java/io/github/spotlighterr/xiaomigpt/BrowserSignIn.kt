package io.github.spotlighterr.xiaomigpt

import android.net.Uri
import org.json.JSONObject
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.util.concurrent.Executors

/** RFC 8252 loopback callback, bound before the external browser opens. No WebView. */
class BrowserSignIn(private val store: AccountStore, private val client: ChatGptClient) {
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var listener: ServerSocket? = null
    @Volatile private var generation = 0
    fun start(previous: JSONObject?, open: (Uri) -> Unit, done: (Result<Unit>) -> Unit) {
        cancel()
        val ticket = generation
        worker.execute {
            try {
                val state = AssistantProtocol.randomSecret()
                val nonce = AssistantProtocol.randomSecret()
                val verifier = AssistantProtocol.randomSecret()
                val socket = ServerSocket(0, 5, InetAddress.getByName("127.0.0.1"))
                listener = socket
                socket.use {
                    check(ticket == generation) { "Đã hủy đăng nhập." }
                    socket.soTimeout = 1000
                    val redirect = "http://127.0.0.1:${socket.localPort}/auth/callback"
                    val values = linkedMapOf("client_id" to (previous?.getString("client_id") ?: "dynamic_agent_client"),
                        "ext_agent_host_id" to store.host(), "response_type" to "code", "redirect_uri" to redirect,
                        "scope" to "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct",
                        "resource" to AssistantProtocol.RESOURCE, "state" to state, "nonce" to nonce,
                        "code_challenge_method" to "S256", "code_challenge" to AssistantProtocol.challenge(verifier))
                    if (previous == null) values["agent_name_hint"] = "XiaomiGpt"
                    else if (previous.optString("id_token").isNotBlank()) values["id_token_hint"] = previous.getString("id_token")
                    open(Uri.parse("${AssistantProtocol.ISSUER}/api/accounts/authorize?${AssistantProtocol.form(values)}"))
                    val deadline = System.currentTimeMillis() + 5 * 60_000
                    var callback: AssistantProtocol.Callback? = null
                    while (callback == null && ticket == generation && System.currentTimeMillis() < deadline) {
                        try {
                            socket.accept().use { connection ->
                                connection.soTimeout = 2000
                                val input = connection.getInputStream()
                                val request = StringBuilder()
                                while (request.length < 8192) {
                                    val ch = input.read()
                                    if (ch == -1 || ch == 10) break
                                    request.append(ch.toChar())
                                }
                                val parts = request.toString().trimEnd('\r').split(' ')
                                callback = try {
                                    require(parts.size == 3 && parts[0] == "GET")
                                    AssistantProtocol.callback(parts[1], state, previous?.getString("client_id"))
                                } catch (declined: AssistantProtocol.ConsentDeclined) {
                                    connection.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nCache-Control: no-store\r\nConnection: close\r\n\r\nSign-in declined. Return to XiaomiGpt to try again.".toByteArray())
                                    throw declined
                                } catch (_: Exception) { null }
                                val text = if (callback != null) "You can close this tab and return to XiaomiGpt. Sign-in is being verified." else "This sign-in callback was not accepted. Return to XiaomiGpt or retry sign-in."
                                val body = text.toByteArray(Charsets.UTF_8)
                                connection.getOutputStream().write(("HTTP/1.1 ${if (callback != null) "200 OK" else "400 Bad Request"}\r\nContent-Type: text/plain; charset=utf-8\r\nCache-Control: no-store\r\nContent-Security-Policy: default-src 'none'\r\nConnection: close\r\nContent-Length: ${body.size}\r\n\r\n").toByteArray() + body)
                            }
                        } catch (_: SocketTimeoutException) { /* wait for browser, or an incomplete local request */ }
                    }
                    check(ticket == generation) { "Đã hủy đăng nhập." }
                    check(callback != null) { "Đã hết thời gian đăng nhập. Hãy thử lại." }
                    val verified = client.exchange(callback!!, verifier, redirect, nonce, previous)
                    synchronized(this@BrowserSignIn) {
                        if (ticket == generation) { store.save(verified); done(Result.success(Unit)) }
                    }
                }
            } catch (e: Exception) { if (ticket == generation) done(Result.failure(e)) }
            finally { if (ticket == generation) listener = null }
        }
    }
    @Synchronized fun cancel() { generation++; runCatching { listener?.close() }; listener = null }
    fun shutdown() { cancel(); worker.shutdown() }
}
