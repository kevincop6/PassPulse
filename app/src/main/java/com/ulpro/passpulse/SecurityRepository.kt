package com.ulpro.passpulse

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.util.Date
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class StoredKey(val value: String, val createdAt: Long)

/** Local vault encrypted with an Android Keystore AES-256-GCM key. */
class SecurityRepository(private val context: Context) {
    private val file = File(context.filesDir, "passpulse_vault.bin")
    private val alias = "passpulse_device_master_key"

    fun ensureDeviceKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!store.containsAlias(alias)) {
            KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
                init(android.security.keystore.KeyGenParameterSpec.Builder(alias, android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(256)
                    .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true).build())
                generateKey()
            }
        }
        return (store.getEntry(alias, null) as KeyStore.SecretKeyEntry).secretKey
    }

    @Synchronized fun save(value: String) {
        val all = read().toMutableList()
        all.add(0, StoredKey(value, Date().time))
        write(all.take(30))
    }

    @Synchronized fun read(): List<StoredKey> = runCatching {
        val array = decrypt()
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(StoredKey(item.optString("v"), item.optLong("t")))
            }
        }
    }.getOrDefault(emptyList())

    @Synchronized fun clear() { file.delete() }

    @Synchronized fun removeExpired() {
        write(read().filter { Date().time - it.createdAt < 7L * 24 * 60 * 60 * 1000 })
    }

    private fun write(items: List<StoredKey>) {
        val array = JSONArray()
        items.forEach { array.put(JSONObject().put("v", it.value).put("t", it.createdAt)) }
        encrypt(array.toString())
    }

    private fun encrypt(plain: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, ensureDeviceKey())
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        file.writeText(Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP))
    }

    private fun decrypt(): JSONArray {
        val raw = Base64.decode(file.readText(), Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, ensureDeviceKey(), GCMParameterSpec(128, raw.copyOfRange(0, 12)))
        return JSONArray(String(cipher.doFinal(raw.copyOfRange(12, raw.size)), Charsets.UTF_8))
    }
}
