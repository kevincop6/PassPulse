package com.ulpro.passpulse

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import java.io.File

object ApkUpdateManager {
    private const val PREF_DOWNLOAD_ID = "update_download_id"
    private const val PREF_DOWNLOADED_VERSION = "update_downloaded_version"
    private const val CHANNEL_ID = "passpulse_updates"
    private const val NOTIFICATION_ID = 7307
    private const val AVAILABLE_NOTIFICATION_ID = 7308
    private const val ACTION_DOWNLOAD_UPDATE = "com.ulpro.passpulse.DOWNLOAD_UPDATE"
    private const val PREF_NOTIFIED_VERSION = "update_notified_version"

    fun startDownload(context: Context, result: UpdateResult): Boolean {
        val url = result.assetUrl ?: return false
        val version = result.latestVersion ?: return false
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val currentId = preferences.getLong(PREF_DOWNLOAD_ID, -1L)
        if (currentId != -1L) {
            val manager = context.getSystemService(DownloadManager::class.java)
            manager.query(DownloadManager.Query().setFilterById(currentId)).use { cursor ->
                if (cursor.moveToFirst()) {
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    if ((status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING) && preferences.getString(PREF_DOWNLOADED_VERSION, null) == version) return true
                    if (status == DownloadManager.STATUS_SUCCESSFUL && preferences.getString(PREF_DOWNLOADED_VERSION, null) == version) {
                        val uri = manager.getUriForDownloadedFile(currentId)
                        if (uri != null && isValidApk(context, currentId, version)) return true
                    }
                    clearDownloadState(context, currentId)
                }
            }
        }

        val filename = "PassPulse-update-v$version-${System.currentTimeMillis()}.apk"
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(context.getString(R.string.app_name) + " $version")
            .setDescription(context.getString(R.string.download_description))
            .setMimeType("application/vnd.android.package-archive")
            .setAllowedOverMetered(true)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, filename)
        val id = context.getSystemService(DownloadManager::class.java).enqueue(request)
        preferences.edit().putLong(PREF_DOWNLOAD_ID, id).putString(PREF_DOWNLOADED_VERSION, version).apply()
        return true
    }

    fun notifyUpdateAvailable(context: Context, result: UpdateResult) {
        if (result.assetUrl == null || !result.isUpdateAvailable()) return
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        if (preferences.getString(PREF_NOTIFIED_VERSION, null) == result.latestVersion) return
        preferences.edit().putString(PREF_NOTIFIED_VERSION, result.latestVersion).apply()
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(NotificationChannel(CHANNEL_ID, context.getString(R.string.updates_channel_name), NotificationManager.IMPORTANCE_DEFAULT))
        val downloadIntent = Intent(context, UpdateNotificationReceiver::class.java).setAction(ACTION_DOWNLOAD_UPDATE)
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            context, AVAILABLE_NOTIFICATION_ID, downloadIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        notificationManager.notify(AVAILABLE_NOTIFICATION_ID, NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_lock)
            .setContentTitle(context.getString(R.string.new_update_notification_title))
            .setContentText(context.getString(R.string.new_update_notification_text, result.latestVersion))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(0, context.getString(R.string.download_update_action), pendingIntent)
            .build())
    }

    fun downloadSavedUpdate(context: Context) {
        UpdateChecker.savedResult(context)?.let { result ->
            if (result.isUpdateAvailable()) startDownload(context, result)
        }
    }

    fun installIfDownloaded(context: Context, expectedVersion: String): Boolean {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val id = preferences.getLong(PREF_DOWNLOAD_ID, -1L)
        if (id == -1L) return false
        if (preferences.getString(PREF_DOWNLOADED_VERSION, null) != expectedVersion) return false
        val manager = context.getSystemService(DownloadManager::class.java)
        manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
            if (!cursor.moveToFirst() || cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) != DownloadManager.STATUS_SUCCESSFUL) return false
            val uri = manager.getUriForDownloadedFile(id) ?: return false
            if (!isValidApk(context, id, expectedVersion)) {
                clearDownloadState(context, id)
                return false
            }
            return install(context, uri)
        }
    }

    /** Removes the downloaded installer after Android has launched the new version. */
    fun cleanupAfterInstall(context: Context) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val id = preferences.getLong(PREF_DOWNLOAD_ID, -1L)
        val downloadedVersion = preferences.getString(PREF_DOWNLOADED_VERSION, null)
        if (id != -1L && downloadedVersion != null && UpdateChecker.compareVersions(downloadedVersion, UpdateChecker.currentVersionName()) <= 0) clearDownloadState(context, id)
    }

    fun notifyDownloadCompleted(context: Context, downloadId: Long) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        if (downloadId != preferences.getLong(PREF_DOWNLOAD_ID, -1L)) return
        val manager = context.getSystemService(DownloadManager::class.java)
        manager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
            if (!cursor.moveToFirst() || cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) != DownloadManager.STATUS_SUCCESSFUL) return
            val uri = manager.getUriForDownloadedFile(downloadId) ?: return
            val expectedVersion = preferences.getString(PREF_DOWNLOADED_VERSION, null) ?: return
            if (!isValidApk(context, downloadId, expectedVersion)) {
                clearDownloadState(context, downloadId)
                return
            }
            val installIntent = Intent(context, ApkInstallActivity::class.java).setData(uri)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val pendingIntent = android.app.PendingIntent.getActivity(
                context, NOTIFICATION_ID, installIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(NotificationChannel(CHANNEL_ID, context.getString(R.string.updates_channel_name), NotificationManager.IMPORTANCE_DEFAULT))
            notificationManager.notify(NOTIFICATION_ID, NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_lock)
                .setContentTitle(context.getString(R.string.update_ready_notification_title))
                .setContentText(context.getString(R.string.update_ready_notification_text))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build())
        }
    }

    fun install(context: Context, uri: Uri): Boolean {
        return runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            true
        }.getOrDefault(false)
    }

    private fun isValidApk(context: Context, id: Long, expectedVersion: String): Boolean = runCatching {
        val manager = context.getSystemService(DownloadManager::class.java)
        val temp = File(context.cacheDir, "passpulse-update-validation-$id.apk")
        manager.openDownloadedFile(id).use { descriptor ->
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input -> temp.outputStream().use { output -> input.copyTo(output) } }
        }
        val info = context.packageManager.getPackageArchiveInfo(temp.absolutePath, 0)
        val version = info?.let { "${it.versionName}.${it.longVersionCode}" }
        val valid = info?.packageName == context.packageName && version == expectedVersion && UpdateChecker.compareVersions(version, UpdateChecker.currentVersionName()) > 0
        temp.delete()
        valid
    }.getOrElse { false }

    private fun clearDownloadState(context: Context, id: Long) {
        runCatching { context.getSystemService(DownloadManager::class.java).remove(id) }
        PreferenceManager.getDefaultSharedPreferences(context).edit().remove(PREF_DOWNLOAD_ID).remove(PREF_DOWNLOADED_VERSION).apply()
    }
}
