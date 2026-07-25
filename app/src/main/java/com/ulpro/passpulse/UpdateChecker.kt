package com.ulpro.passpulse

import android.content.Context
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

data class UpdateResult(
    val latestVersion: String?,
    val releaseUrl: String?,
    val assetUrl: String? = null,
    val assetName: String? = null,
    val error: String? = null
) {
    fun isUpdateAvailable() = latestVersion != null && UpdateChecker.compareVersions(latestVersion, UpdateChecker.currentVersionName()) > 0
    fun toStatusText(context: Context) = when {
        error != null -> context.getString(R.string.update_check_failed)
        isUpdateAvailable() -> context.getString(R.string.update_available_status, latestVersion)
        else -> context.getString(R.string.latest_version)
    }
    fun toUserMessage(context: Context) = when {
        error != null -> context.getString(R.string.update_check_error_message)
        isUpdateAvailable() -> context.getString(R.string.update_available_message_short, latestVersion)
        else -> context.getString(R.string.app_updated_message, UpdateChecker.currentVersionName())
    }
}

object UpdateChecker {
    private const val RELEASES_URL = "https://api.github.com/repos/kevincop6/PassPulse/releases/latest"
    private const val PREF_LATEST_VERSION = "update_latest_version"
    private const val PREF_UPDATE_AVAILABLE = "update_available"
    private const val PREF_ASSET_URL = "update_asset_url"
    private const val PREF_ASSET_NAME = "update_asset_name"

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
            val assets = json.optJSONArray("assets") ?: JSONArray()
            val apk = (0 until assets.length())
                .map { assets.optJSONObject(it) }
                .firstOrNull { it?.optString("name")?.endsWith(".apk", ignoreCase = true) == true }
            val result = UpdateResult(
                latestVersion = normalizeVersion(json.optString("tag_name")),
                releaseUrl = json.optString("html_url"),
                assetUrl = apk?.optString("browser_download_url"),
                assetName = apk?.optString("name")
            )
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(PREF_LATEST_VERSION, result.latestVersion)
                .putBoolean(PREF_UPDATE_AVAILABLE, result.isUpdateAvailable())
                .putString(PREF_ASSET_URL, result.assetUrl)
                .putString(PREF_ASSET_NAME, result.assetName)
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
            latest == null -> context.getString(R.string.check_github_version)
            preferences.getBoolean(PREF_UPDATE_AVAILABLE, false) -> context.getString(R.string.update_available_status, latest)
            else -> context.getString(R.string.latest_version)
        }
    }

    fun savedResult(context: Context): UpdateResult? {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val version = preferences.getString(PREF_LATEST_VERSION, null) ?: return null
        return UpdateResult(
            latestVersion = version,
            releaseUrl = null,
            assetUrl = preferences.getString(PREF_ASSET_URL, null),
            assetName = preferences.getString(PREF_ASSET_NAME, null)
        )
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
