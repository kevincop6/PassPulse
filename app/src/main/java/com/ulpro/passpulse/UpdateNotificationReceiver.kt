package com.ulpro.passpulse

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class UpdateNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.ulpro.passpulse.DOWNLOAD_UPDATE") {
            ApkUpdateManager.downloadSavedUpdate(context)
        }
    }
}
