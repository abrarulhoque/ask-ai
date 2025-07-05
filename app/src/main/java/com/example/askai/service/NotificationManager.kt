package com.example.askai.service

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

class WordReminderManager(private val context: Context) {

    fun schedulePeriodicNotifications(intervalMinutes: Int) {
        // Cancel existing work
        cancelPeriodicNotifications()

        // Create constraints for the work
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(false)
            .build()

        // Create periodic work request
        val workRequest = PeriodicWorkRequestBuilder<NotificationWorker>(
            intervalMinutes.toLong(),
            TimeUnit.MINUTES,
            // Add flex interval (last 5 minutes of the period)
            5,
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        // Enqueue the work
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                NotificationWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )
    }

    fun cancelPeriodicNotifications() {
        WorkManager.getInstance(context)
            .cancelUniqueWork(NotificationWorker.WORK_NAME)
    }

    fun isScheduled(): Boolean {
        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(NotificationWorker.WORK_NAME)
            .get()
        
        return workInfos.any { workInfo ->
            workInfo.state == WorkInfo.State.ENQUEUED || workInfo.state == WorkInfo.State.RUNNING
        }
    }
} 