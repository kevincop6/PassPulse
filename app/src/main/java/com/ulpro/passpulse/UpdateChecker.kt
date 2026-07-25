package com.ulpro.passpulse

import android.content.Context
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateResult(val latestVersion: String?, val releaseUrl: String?, val error: String? = null) {
    fun isUpdateAvailable() = latestVersion != null && UpdateChecker.compareVersions(latestVersion, UpdateChecker.currentVersionName()) > 0
    fun toStatusText() = when {
        error != null -> "No se pudo comprobar la actualización"
        isUpdateAvailable() -> "Actualización disponible: $latestVersion"
        else -> "Estás usando la versión más reciente"
    }
    fun toUserMessage() = when {
        error != null -> "No se pudo comprobar la actualización. Inténtalo de nuevo más tarde."
        isUpdateAvailable() -> "Hay una actualización disponible: $latestVersion"
        else -> "PassPulse está actualizado (${UpdateChecker.currentVersionName()})"
    }
}

object UpdateChecker {
    private const val RELEASES_URL = "https://api.github.com/repos/kevincop6/PassPulse/releases/latest"
    private const val PREF_LATEST_VERSION = "update_latest_version"
    private const val PREF_UPDATE_AVAILABLE = "update_available"

    fun currentVersionName(): String = "${BuildConfig.VERSION_NAME}.${BuildConfig.VERSION_CODE}"

    suspend fun check(context: Context): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val connection = URL(RELEASES_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "PassPulse/${currentVersionName()}")
            val json = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
            connection.disconnect()
            val result = UpdateResult(normalizeVersion(json.optString("tag_name")), json.optString("html_url"))
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(PREF_LATEST_VERSION, result.latestVersion)
                .putBoolean(PREF_UPDATE_AVAILABLE, result.isUpdateAvailable())
                .apply()
            result
        } catch (error: Exception) {
            UpdateResult(null, null, error.message)
        }
    }

    fun savedStatus(context: Context): String {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val latest = preferences.getString(PREF_LATEST_VERSION, null)
        return when {
            latest == null -> "Comprobar versión en GitHub"
            preferences.getBoolean(PREF_UPDATE_AVAILABLE, false) -> "Actualización disponible: $latest"
            else -> "Estás usando la versión más reciente"
        }
    }

    private fun normalizeVersion(value: String) = value.trim().removePrefix("v").ifBlank { "0.0.0" }

    internal fun compareVersions(first: String, second: String): Int {
        val left = first.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val right = second.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        for (index in 0 until maxOf(left.size, right.size)) {
            val difference = left.getOrElse(index) { 0 } - right.getOrElse(index) { 0 }
            if (difference != 0) return difference
        }
        return 0
    }
}
