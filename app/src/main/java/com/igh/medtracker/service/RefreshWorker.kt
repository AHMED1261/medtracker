package com.igh.medtracker.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Restarting the service is a no-op if it's already running, and brings it back if the
        // system (or an OEM battery manager) killed it.
        PersistentService.start(applicationContext)
        NotificationHelper.refreshAll(applicationContext)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "medtracker_refresh"

        // 15 minutes is WorkManager's minimum allowed periodic interval.
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
