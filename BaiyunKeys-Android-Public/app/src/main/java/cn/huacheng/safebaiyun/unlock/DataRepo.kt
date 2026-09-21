package cn.huacheng.safebaiyun.unlock

import android.content.Context
import android.content.SharedPreferences
import android.bluetooth.BluetoothAdapter
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import cn.huacheng.safebaiyun.util.ContextHolder
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 *
 *@description:
 *@author: guangzhou
 *@create: 2024-03-04
 */
object DataRepo {

    private const val KEY_ALIAS = "baiyun_door_config"
    private const val PAYLOAD = "encrypted_payload"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private val preferences: SharedPreferences by lazy {
        ContextHolder.get().getSharedPreferences("data", Context.MODE_PRIVATE)
    }

    fun readData(): Pair<String, String> {
        val encrypted = preferences.getString(PAYLOAD, null)
        if (encrypted != null) {
            return runCatching { decrypt(encrypted) }.getOrDefault("" to "")
        }

        // One-time migration from the original app's plaintext preferences.
        val legacyMac = preferences.getString("mac", "")?.trim().orEmpty()
        val legacyKey = preferences.getString("key", "")?.trim().orEmpty()
        if (validate(legacyMac, legacyKey) == null) {
            save(legacyMac, legacyKey)
            preferences.edit { remove("mac"); remove("key") }
            return normalizeMac(legacyMac) to legacyKey.uppercase()
        }
        return "" to ""
    }

    fun save(mac: String, key: String): Boolean {
        if (validate(mac, key) != null) return false
        val normalizedMac = normalizeMac(mac)
        val normalizedKey = key.trim().uppercase()
        preferences.edit {
            putString(PAYLOAD, encrypt("$normalizedMac\n$normalizedKey"))
            remove("mac")
            remove("key")
        }
        return true
    }

    fun isConfigured(): Boolean {
        val (mac, key) = readData()
        return validate(mac, key) == null
    }

    fun validate(mac: String, key: String): String? {
        val normalizedMac = normalizeMac(mac)
        if (!BluetoothAdapter.checkBluetoothAddress(normalizedMac)) return "MAC 地址格式不正确"
        if (!key.trim().matches(Regex("^[0-9a-fA-F]{16}$"))) return "Key 必须是 16 位十六进制字符"
        return null
    }

    private fun normalizeMac(mac: String): String = mac.trim().uppercase()

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val packed = cipher.iv + encrypted
        return Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    private fun decrypt(packedText: String): Pair<String, String> {
        val packed = Base64.decode(packedText, Base64.NO_WRAP)
        require(packed.size > 12)
        val iv = packed.copyOfRange(0, 12)
        val encrypted = packed.copyOfRange(12, packed.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(128, iv))
        val parts = cipher.doFinal(encrypted).toString(Charsets.UTF_8).split('\n', limit = 2)
        require(parts.size == 2)
        return parts[0] to parts[1]
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return keyGenerator.generateKey()
    }
}
