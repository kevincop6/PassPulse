package com.ulpro.passpulse

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager

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
                    if (status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING) return true
                    if (status == DownloadManager.STATUS_SUCCESSFUL && preferences.getString(PREF_DOWNLOADED_VERSION, null) == version) return true
                }
            }
        }

        val filename = result.assetName ?: "PassPulse-v$version.apk"
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("PassPulse $version")
            .setDescription("Descargando actualización")
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
        notificationManager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Actualizaciones", NotificationManager.IMPORTANCE_DEFAULT))
        val downloadIntent = Intent(context, UpdateNotificationReceiver::class.java).setAction(ACTION_DOWNLOAD_UPDATE)
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            context, AVAILABLE_NOTIFICATION_ID, downloadIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        notificationManager.notify(AVAILABLE_NOTIFICATION_ID, NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_lock)
            .setContentTitle("Nueva actualización de PassPulse")
            .setContentText("La versión ${result.latestVersion} está disponible")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(0, "Descargar", pendingIntent)
            .build())
    }

    fun downloadSavedUpdate(context: Context) {
        UpdateChecker.savedResult(context)?.let { result ->
            if (result.isUpdateAvailable()) startDownload(context, result)
        }
    }

    fun installIfDownloaded(context: Context): Boolean {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val id = preferences.getLong(PREF_DOWNLOAD_ID, -1L)
        if (id == -1L) return false
        val manager = context.getSystemService(DownloadManager::class.java)
        manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
            if (!cursor.moveToFirst() || cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) != DownloadManager.STATUS_SUCCESSFUL) return false
            val uri = manager.getUriForDownloadedFile(id) ?: return false
            return install(context, uri)
        }
    }

    fun notifyDownloadCompleted(context: Context, downloadId: Long) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        if (downloadId != preferences.getLong(PREF_DOWNLOAD_ID, -1L)) return
        val manager = context.getSystemService(DownloadManager::class.java)
        manager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
            if (!cursor.moveToFirst() || cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) != DownloadManager.STATUS_SUCCESSFUL) return
            val uri = manager.getUriForDownloadedFile(downloadId) ?: return
            val installIntent = Intent(context, ApkInstallActivity::class.java).setData(uri)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val pendingIntent = android.app.PendingIntent.getActivity(
                context, NOTIFICATION_ID, installIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Actualizaciones", NotificationManager.IMPORTANCE_DEFAULT))
            notificationManager.notify(NOTIFICATION_ID, NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_lock)
                .setContentTitle("Actualización de PassPulse lista")
                .setContentText("Toca para instalar la actualización")
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
}
