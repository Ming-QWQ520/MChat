package com.ming.mchat.core.data.keystore

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android Keystore 封装：API Key 使用 AES/GCM 加密，Room 中只保存密文引用（别名）。
 */
@Singleton
class CryptoManager @Inject constructor() {

    fun encrypt(alias: String, plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(alias))
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
    }

    fun decrypt(alias: String, encoded: String): String? = try {
        val data = Base64.decode(encoded, Base64.NO_WRAP)
        require(data.size > IV_SIZE) { "invalid ciphertext" }
        val iv = data.copyOfRange(0, IV_SIZE)
        val encrypted = data.copyOfRange(IV_SIZE, data.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(alias), GCMParameterSpec(TAG_BITS, iv))
        String(cipher.doFinal(encrypted), Charsets.UTF_8)
    } catch (t: Throwable) {
        null
    }

    fun hasKey(alias: String): Boolean = try {
        keyStore().containsAlias(alias)
    } catch (t: Throwable) {
        false
    }

    private fun getOrCreateKey(alias: String): SecretKey {
        val ks = keyStore()
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE)
                .build()
        )
        return generator.generateKey()
    }

    private fun keyStore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val TAG_BITS = 128
        const val KEY_SIZE = 256
    }
}
