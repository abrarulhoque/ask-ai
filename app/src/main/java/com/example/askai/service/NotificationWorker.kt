package com.example.askai.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.askai.MainActivity
import com.example.askai.R
import com.example.askai.data.DefinitionStore
import com.example.askai.data.SettingsStore
import kotlinx.coroutines.flow.first
import kotlin.random.Random

class NotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val CHANNEL_ID = "word_reminder_channel"
        const val NOTIFICATION_ID = 1001
        const val WORK_NAME = "word_reminder_work"
    }

    override suspend fun doWork(): Result {
        try {
            val settingsStore = SettingsStore(applicationContext)
            val definitionStore = DefinitionStore(applicationContext)
            
            // Check if notifications are enabled
            val settings = settingsStore.settingsFlow.first()
            if (!settings.notificationEnabled) {
                return Result.success()
            }

            // Check notification permission (Android 13+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ActivityCompat.checkSelfPermission(
                        applicationContext,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return Result.success()
                }
            }

            // Get all definitions
            val definitions = definitionStore.allDefinitionsFlow.first()
            if (definitions.isEmpty()) {
                return Result.success()
            }

            // Pick a random definition
            val randomDefinition = definitions[Random.nextInt(definitions.size)]

            // Create notification
            createNotificationChannel()
            showNotification(randomDefinition.query, randomDefinition.explanation)

            return Result.success()
        } catch (e: Exception) {
            return Result.failure()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Word Reminders"
            val descriptionText = "Periodic reminders of saved words and definitions"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }

            val notificationManager: NotificationManager =
                applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(word: String, definition: String) {
        // Create intent to open the app
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Truncate definition for notification
        val shortDefinition = if (definition.length > 100) {
            definition.take(97) + "..."
        } else {
            definition
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification) // You'll need to add this icon
            .setContentTitle("Word Reminder: $word")
            .setContentText(shortDefinition)
            .setStyle(NotificationCompat.BigTextStyle().bigText(definition))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(applicationContext)
                .notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Handle the case where notification permission is not granted
        }
    }
} 