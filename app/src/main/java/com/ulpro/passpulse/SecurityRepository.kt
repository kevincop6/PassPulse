package com.ulpro.passpulse

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** An offline-first vault. The file contains only AES-256-GCM ciphertext. */
data class VaultEntry(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val username: String = "",
    val password: String,
    val notes: String = "",
    val uris: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val kind: String = "login"
)

/** Local vault encrypted with an Android Keystore AES-256-GCM key. Nothing expires automatically. */
class SecurityRepository(private val context: Context) {
    private val file = File(context.filesDir, "passpulse_vault.bin")
    private val alias = "passpulse_device_master_key"

    fun ensureDeviceKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!store.containsAlias(alias)) {
            KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
                init(android.security.keystore.KeyGenParameterSpec.Builder(alias, android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(256).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true).build())
                generateKey()
            }
        }
        return (store.getEntry(alias, null) as KeyStore.SecretKeyEntry).secretKey
    }

    @Synchronized fun read(): List<VaultEntry> = runCatching {
        val plain = decrypt()
        val root = runCatching { JSONObject(plain) }.getOrNull()
        val array = root?.optJSONArray("items") ?: runCatching { JSONArray(plain) }.getOrElse { JSONArray() }
        buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val legacy = item.has("v")
                add(VaultEntry(item.optString("id", UUID.randomUUID().toString()), item.optString("name", if (legacy) "Contraseña generada" else "Contraseña"), item.optString("username"), item.optString("password", item.optString("v")), item.optString("notes"), item.optJSONArray("uris").toStringList(), item.optLong("createdAt", item.optLong("t", System.currentTimeMillis())), item.optLong("updatedAt", item.optLong("t", System.currentTimeMillis())), item.optString("kind", if (legacy) "generated" else "login")))
            }
        }
    }.getOrElse { emptyList() }

    @Synchronized fun upsert(entry: VaultEntry) {
        val items = read().filterNot { it.id == entry.id }.toMutableList()
        items.add(0, entry.copy(updatedAt = System.currentTimeMillis()))
        write(items)
    }

    @Synchronized fun saveGenerated(password: String) = upsert(VaultEntry(name = "Contraseña generada", password = password, kind = "generated"))

    @Synchronized fun remove(id: String) = write(read().filterNot { it.id == id })

    /** Kept for an explicit user-initiated wipe; there is no automatic cleanup anymore. */
    @Synchronized fun clear() { file.delete() }

    @Synchronized fun exportEncrypted(): ByteArray = file.takeIf { it.exists() }?.readBytes() ?: ByteArray(0)

    @Synchronized fun importEncrypted(bytes: ByteArray) {
        // Validate authentication tag before replacing the local vault.
        decrypt(bytes.toString(Charsets.UTF_8))
        file.writeBytes(bytes)
    }

    private fun write(items: List<VaultEntry>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().put("id", item.id).put("name", item.name).put("username", item.username).put("password", item.password).put("notes", item.notes).put("uris", JSONArray(item.uris)).put("createdAt", item.createdAt).put("updatedAt", item.updatedAt).put("kind", item.kind))
        }
        encrypt(JSONObject().put("format", "passpulse-vault-1").put("items", array).toString())
    }

    private fun encrypt(plain: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, ensureDeviceKey()) }
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        file.writeText(Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP))
    }

    private fun decrypt(): String = decrypt(file.readText())

    private fun decrypt(encoded: String): String {
        val raw = Base64.decode(encoded, Base64.NO_WRAP)
        require(raw.size > 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, ensureDeviceKey(), GCMParameterSpec(128, raw.copyOfRange(0, 12)))
        return String(cipher.doFinal(raw.copyOfRange(12, raw.size)), Charsets.UTF_8)
    }
}

private fun JSONArray?.toStringList(): List<String> = this?.let { array -> buildList { for (i in 0 until array.length()) add(array.optString(i)) } } ?: emptyList()
