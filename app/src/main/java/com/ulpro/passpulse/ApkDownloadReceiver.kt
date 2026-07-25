package com.ulpro.passpulse

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ApkDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.intent.action.DOWNLOAD_COMPLETE") {
            ApkUpdateManager.notifyDownloadCompleted(context, intent.getLongExtra("extra_download_id", -1L))
        }
    }
}
