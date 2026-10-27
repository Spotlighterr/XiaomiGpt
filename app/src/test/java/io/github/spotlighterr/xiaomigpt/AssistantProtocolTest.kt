package io.github.spotlighterr.xiaomigpt

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPublicKey

class AssistantProtocolTest {
    private fun rejected(block: () -> Unit) { try { block(); fail("Expected rejection") } catch (_: IllegalArgumentException) { } }

    @Test fun `PKCE matches RFC 7636 example`() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", AssistantProtocol.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
    }
    @Test fun `callback binds state path and issued registration`() {
        val callback = AssistantProtocol.callback("/auth/callback?state=secret&code=abc&client_id=oaiapp_test", "secret", null)
        assertEquals("oaiapp_test", callback.clientId)
        assertEquals("oaiapp_test", AssistantProtocol.callback("/auth/callback?state=secret&code=abc", "secret", "oaiapp_test").clientId)
        listOf("/auth/callback?state=wrong&code=abc&client_id=oaiapp_test", "/callback?state=secret&code=abc&client_id=oaiapp_test",
            "/auth/callback?state=secret&state=secret&code=abc&client_id=oaiapp_test", "/auth/callback?state=secret&code=abc&client_id=dynamic_agent_client",
            "http://evil.test/auth/callback?state=secret&code=abc&client_id=oaiapp_test").forEach { target -> rejected { AssistantProtocol.callback(target, "secret", null) } }
        rejected { AssistantProtocol.callback("/auth/callback?state=secret&code=abc&client_id=oaiapp_other", "secret", "oaiapp_test") }
    }
    @Test fun `consent denial is handled only after validating state`() {
        try { AssistantProtocol.callback("/auth/callback?state=secret&error=access_denied", "secret", null); fail() }
        catch (_: AssistantProtocol.ConsentDeclined) { }
        try { AssistantProtocol.callback("/auth/callback?state=wrong&error=access_denied", "secret", null); fail() }
        catch (_: AssistantProtocol.ConsentDeclined) { fail("Untrusted denial must not end the pending attempt") }
        catch (_: IllegalArgumentException) { }
    }
    private fun claims() = JSONObject().put("iss", AssistantProtocol.ISSUER).put("aud", "oaiapp_test")
        .put("sub", "user1").put("nonce", "nonce1").put("exp", 2000).put("iat", 900)
    @Test fun `claims reject wrong issuer audience expiry nonce and returning identity`() {
        AssistantProtocol.validateClaims(claims(), "oaiapp_test", "nonce1", 1000)
        listOf("iss" to "https://evil.test", "aud" to "other", "exp" to 999, "nonce" to "nonce2", "iat" to 2000).forEach { (key, value) ->
            rejected { AssistantProtocol.validateClaims(claims().put(key, value), "oaiapp_test", "nonce1", 1000) }
        }
        rejected { AssistantProtocol.validateClaims(claims(), "oaiapp_test", "nonce1", 1000, "user2") }
        rejected { AssistantProtocol.validateClaims(claims().put("aud", JSONArray(listOf("oaiapp_test", "other"))).put("azp", "other"), "oaiapp_test", "nonce1", 1000) }
    }
    @Test fun `ID token accepts trusted RSA signature and rejects altered payload`() {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val public = pair.public as RSAPublicKey
        val jwk = JSONObject().put("kid", "test").put("kty", "RSA").put("use", "sig")
            .put("n", AssistantProtocol.encode(public.modulus.toByteArray())).put("e", AssistantProtocol.encode(public.publicExponent.toByteArray()))
        val header = AssistantProtocol.encode("""{"kid":"test","alg":"RS256"}""".toByteArray())
        val payload = AssistantProtocol.encode(claims().toString().toByteArray())
        val unsigned = "$header.$payload"
        val signature = Signature.getInstance("SHA256withRSA").apply { initSign(pair.private); update(unsigned.toByteArray()) }.sign()
        val token = "$unsigned.${AssistantProtocol.encode(signature)}"
        assertEquals("user1", AssistantProtocol.verifyToken(token, JSONArray().put(jwk), "oaiapp_test", "nonce1", 1000).getString("sub"))
        val altered = "$header.${AssistantProtocol.encode(claims().put("sub", "attacker").toString().toByteArray())}.${AssistantProtocol.encode(signature)}"
        rejected { AssistantProtocol.verifyToken(altered, JSONArray().put(jwk), "oaiapp_test", "nonce1", 1000) }
    }
}
