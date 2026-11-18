package com.example.tratohecho.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface TokenProvider {
    fun accessToken(): String?
    fun refreshToken(): String?
    fun store(access: String, refresh: String)
    fun clear()
}

/** JWT y refresh token cifrados con AES-GCM en Android Keystore (API >= 26). */
class TokenVault(context: Context) : TokenProvider {
    private val preferences = context.getSharedPreferences("auth_secure", Context.MODE_PRIVATE)
    private val alias = "tratohecho_auth_aes_v1"
    private val key: SecretKey
        get() {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (ks.getKey(alias, null) as? SecretKey)?.let { return it }
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
            return generator.generateKey()
        }

    override fun accessToken() = decrypt(preferences.getString("access", null))
    override fun refreshToken() = decrypt(preferences.getString("refresh", null))
    @Synchronized override fun store(access: String, refresh: String) {
        preferences.edit().putString("access", encrypt(access)).putString("refresh", encrypt(refresh)).commit()
    }
    @Synchronized override fun clear() { preferences.edit().clear().commit() }

    private fun encrypt(text: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(text.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }
    private fun decrypt(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val bytes = Base64.decode(raw, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
        }.getOrNull()
    }
}
