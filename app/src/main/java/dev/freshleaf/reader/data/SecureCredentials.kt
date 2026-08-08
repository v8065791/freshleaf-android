package dev.freshleaf.reader.data

import android.content.Context
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class AccountSettings(val endpoint: String, val username: String, val password: String)

class SecureCredentials(context: Context) {
    private val preferences = context.getSharedPreferences("account", Context.MODE_PRIVATE)
    private val alias = "freshleaf.account.key"

    fun save(endpoint: String, username: String, password: String) {
        val encrypted = encrypt(password)
        preferences.edit()
            .putString("endpoint", endpoint.trimEnd('/'))
            .putString("username", username)
            .putString("password", encrypted)
            .apply()
    }

    fun load(): AccountSettings? {
        val endpoint = preferences.getString("endpoint", null) ?: return null
        val username = preferences.getString("username", null) ?: return null
        val encrypted = preferences.getString("password", null) ?: return null
        return runCatching { AccountSettings(endpoint, username, decrypt(encrypted)) }.getOrNull()
    }

    fun clear() = preferences.edit().clear().apply()

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(alias, null) as? SecretKey
        if (existing != null) return existing
        return KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
            init(android.security.keystore.KeyGenParameterSpec.Builder(
                alias,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                    android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
            ).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
        }.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val payload = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val payload = Base64.decode(value, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, payload.copyOfRange(0, 12)))
        return cipher.doFinal(payload.copyOfRange(12, payload.size)).toString(Charsets.UTF_8)
    }
}

