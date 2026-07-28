package com.ulpro.passpulse

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.io.IOException

/** Writes the already encrypted vault to a user-selected Drive/Files location. */
class VaultBackupWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = runCatching {
        val uri = applicationContext.getSharedPreferences("vault_backup", Context.MODE_PRIVATE).getString("uri", null) ?: return Result.success()
        val target = android.net.Uri.parse(uri)
        applicationContext.contentResolver.openOutputStream(target, "wt")?.use { it.write(SecurityRepository(applicationContext).exportEncrypted()) }
            ?: throw IOException("No se pudo abrir el destino")
        Result.success()
    }.getOrElse { Result.retry() }
}

object VaultBackupScheduler {
    fun schedule(context: Context) {
        val request = androidx.work.PeriodicWorkRequestBuilder<VaultBackupWorker>(1, java.util.concurrent.TimeUnit.DAYS).build()
        androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork("passpulse_vault_backup", androidx.work.ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
