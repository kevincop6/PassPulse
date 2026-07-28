package com.ulpro.passpulse
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
class CleanupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    // Vault entries are intentionally persistent. This worker remains as a no-op for upgrades from older builds.
    override suspend fun doWork() = Result.success()
    companion object { fun schedule(context: Context) { WorkManager.getInstance(context).enqueueUniquePeriodicWork("passpulse_cleanup", ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<CleanupWorker>(1, TimeUnit.DAYS).build()) } }
}
