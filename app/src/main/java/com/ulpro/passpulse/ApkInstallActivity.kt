package com.ulpro.passpulse

import android.app.Activity
import android.net.Uri
import android.os.Bundle

class ApkInstallActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.data?.let { ApkUpdateManager.install(this, it) }
        finish()
    }
}
