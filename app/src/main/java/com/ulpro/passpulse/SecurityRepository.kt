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

class SecurityRepository(private val context: Context) {
    private val file = File(context.filesDir, "passpulse_vault.bin")
    private val alias = "passpulse_device_master_key"
    fun ensureDeviceKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!store.containsAlias(alias)) KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
            init(android.security.keystore.KeyGenParameterSpec.Builder(alias, android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build()); generateKey()
        }
        return (store.getEntry(alias, null) as KeyStore.SecretKeyEntry).secretKey
    }
    @Synchronized fun save(value: String) { val all = read().toMutableList(); all.add(0, StoredKey(value, Date().time)); write(all.take(30)) }
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
    @Synchronized fun removeExpired() { write(read().filter { Date().time - it.createdAt < 7L * 24 * 60 * 60 * 1000 }) }
    private fun write(items: List<StoredKey>) { val array = JSONArray(); items.forEach { array.put(JSONObject().put("v", it.value).put("t", it.createdAt)) }; encrypt(array.toString()) }
    private fun encrypt(plain: String) { val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, ensureDeviceKey()); val bytes = cipher.doFinal(plain.toByteArray()); file.writeText(Base64.encodeToString(cipher.iv + bytes, Base64.NO_WRAP)) }
    private fun decrypt(): JSONArray { val raw = Base64.decode(file.readText(), Base64.NO_WRAP); val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, ensureDeviceKey(), GCMParameterSpec(128, raw.copyOfRange(0, 12))); return JSONArray(String(cipher.doFinal(raw.copyOfRange(12, raw.size)))) }
}

/** Argon2id integration point. The app stores the vault with an Android Keystore AES key;
 * this helper is available for password-derived keys and uses a hardened fallback if a
 * provider is unavailable on the device. */
object Argon2idDerivation {
    fun derive(secret: ByteArray, salt: ByteArray): ByteArray = runCatching {
        val cls = Class.forName("com.lambdapioneer.argon2kt.Argon2Kt")
        @Suppress("UNCHECKED_CAST") val method = cls.methods.firstOrNull { it.name == "hash" && it.parameterTypes.size >= 2 }
        (method?.invoke(cls.getDeclaredConstructor().newInstance(), secret, salt) as? ByteArray) ?: fallback(secret, salt)
    }.getOrElse { fallback(secret, salt) }
    private fun fallback(secret: ByteArray, salt: ByteArray): ByteArray = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(javax.crypto.spec.PBEKeySpec(secret.decodeToString().toCharArray(), salt, 120_000, 256)).encoded
}
