package io.github.spotlighterr.xiaomigpt

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Account records are encrypted with a non-exportable Android Keystore key. */
class AccountStore(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "chatgpt-accounts.enc"))
    private val key: SecretKey by lazy {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("xiaomigpt-accounts-v1", null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder("xiaomigpt-accounts-v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }
    private val root: JSONObject by lazy {
        if (!file.baseFile.exists()) JSONObject().put("host", "urn:uuid:${UUID.randomUUID()}").put("accounts", JSONArray()).also { persist(it) }
        else {
            val envelope = JSONObject(file.readFully().toString(Charsets.UTF_8))
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, AssistantProtocol.decode(envelope.getString("iv"))))
            JSONObject(cipher.doFinal(AssistantProtocol.decode(envelope.getString("data"))).toString(Charsets.UTF_8))
        }
    }
    @Synchronized fun host(): String = root.getString("host")
    @Synchronized fun accounts(): List<JSONObject> = root.getJSONArray("accounts").let { a -> (0 until a.length()).map { JSONObject(a.getJSONObject(it).toString()) } }
    @Synchronized fun active(): JSONObject? = accounts().firstOrNull { it.getString("client_id") == root.optString("active") }
    @Synchronized fun select(client: String) { require(accounts().any { it.getString("client_id") == client }); root.put("active", client); persist(root) }
    @Synchronized fun planWelcomeSeen() = root.optBoolean("plan_welcome_seen")
    @Synchronized fun markPlanWelcomeSeen() { root.put("plan_welcome_seen", true); persist(root) }
    @Synchronized fun save(account: JSONObject) {
        val client = account.getString("client_id")
        val others = accounts().filter { it.getString("client_id") != client }
        root.put("accounts", JSONArray(others + account)).put("active", client)
        persist(root)
    }
    @Synchronized fun clearTokens(client: String) {
        val account = accounts().first { it.getString("client_id") == client }
        listOf("access_token", "refresh_token", "id_token", "expires_at", "scope").forEach { account.remove(it) }
        save(account)
    }
    private fun persist(value: JSONObject) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val data = cipher.doFinal(value.toString().toByteArray(Charsets.UTF_8))
        val envelope = JSONObject().put("iv", AssistantProtocol.encode(cipher.iv)).put("data", AssistantProtocol.encode(data)).toString()
        val output = file.startWrite()
        try { output.write(envelope.toByteArray(Charsets.UTF_8)); file.finishWrite(output) }
        catch (e: Exception) { file.failWrite(output); throw e }
    }
}
