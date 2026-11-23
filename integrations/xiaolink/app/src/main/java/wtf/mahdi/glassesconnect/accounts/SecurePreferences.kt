package wtf.mahdi.glassesconnect.accounts

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypt every string/secret at rest; app backup is disabled separately. No plaintext migration. */
class SecurePreferences(context: Context, private val backing: SharedPreferences =
    context.getSharedPreferences("integrated-glasses", Context.MODE_PRIVATE)) : SharedPreferences by backing {
    private val key: SecretKey by lazy {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance("AES", "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }
    private fun encrypt(value: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, key)
        return Base64.getEncoder().encodeToString(c.iv + c.doFinal(value.toByteArray(Charsets.UTF_8)))
    }
    private fun decrypt(value: String): String {
        val bytes = Base64.getDecoder().decode(value); require(bytes.size >= 28)
        val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return c.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
    }
    override fun getString(key: String?, defValue: String?): String? = backing.getString(key, null)?.let(::decrypt) ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        backing.getStringSet(key, null)?.map(::decrypt)?.toMutableSet() ?: defValues
    override fun edit(): SharedPreferences.Editor {
        val e = backing.edit()
        return object : SharedPreferences.Editor by e {
            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                e.putString(key, value?.let(::encrypt)); return this
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
                e.putStringSet(key, values?.map(::encrypt)?.toMutableSet()); return this
            }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor { e.putBoolean(key, value); return this }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor { e.putInt(key, value); return this }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor { e.putLong(key, value); return this }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor { e.putFloat(key, value); return this }
            override fun remove(key: String?): SharedPreferences.Editor { e.remove(key); return this }
            override fun clear(): SharedPreferences.Editor { e.clear(); return this }
        }
    }
    companion object { private const val ALIAS = "xiaomigpt-link-preferences-v1" }
}
