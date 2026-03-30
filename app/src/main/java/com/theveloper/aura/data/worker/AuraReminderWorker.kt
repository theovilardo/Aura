package com.theveloper.aura.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.theveloper.aura.MainActivity
import com.theveloper.aura.domain.model.NotificationChannel as AuraChannel
import com.theveloper.aura.domain.model.ReminderStatus
import com.theveloper.aura.domain.model.ReminderType
import com.theveloper.aura.domain.repository.AuraReminderRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Worker for standalone [AuraReminder] notifications.
 * Handles one-time, repeating, and cyclical reminders.
 *
 * For repeating/cyclical: after firing, schedules the next occurrence
 * or marks COMPLETED if repeat count is exhausted.
 */
@HiltWorker
class AuraReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val reminderRepository: AuraReminderRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val reminderId = inputData.getString(KEY_REMINDER_ID) ?: return@withContext Result.failure()

        val reminder = reminderRepository.getById(reminderId) ?: return@withContext Result.failure()

        // Show notification
        showNotification(
            context = context,
            reminderId = reminder.id,
            title = reminder.title,
            body = reminder.body.ifBlank { reminder.title },
            linkedTaskId = reminder.linkedTaskId
        )

        // Handle recurrence
        when (reminder.reminderType) {
            ReminderType.ONE_TIME -> {
                reminderRepository.updateStatus(reminderId, ReminderStatus.TRIGGERED)
            }
            ReminderType.REPEATING -> {
                val firedCount = reminder.repeatCount - 1
                if (firedCount <= 0) {
                    reminderRepository.updateStatus(reminderId, ReminderStatus.COMPLETED)
                } else {
                    val nextSchedule = System.currentTimeMillis() + reminder.intervalMs
                    reminderRepository.update(
                        reminder.copy(
                            scheduledAt = nextSchedule,
                            repeatCount = firedCount,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
            ReminderType.CYCLICAL -> {
                // Cyclical: reschedule at next interval, never completes
                val nextSchedule = System.currentTimeMillis() + reminder.intervalMs
                reminderRepository.update(
                    reminder.copy(
                        scheduledAt = nextSchedule,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }

        Result.success()
    }

    private fun showNotification(
        context: Context,
        reminderId: String,
        title: String,
        body: String,
        linkedTaskId: String?
    ) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                AuraChannel.REMINDERS.name,
                "AURA Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Standalone reminder notifications" }
            notificationManager.createNotificationChannel(channel)
        }

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            // Deep-link to linked task if available
            linkedTaskId?.let { putExtra("navigateTo", "task_detail/$it") }
        }
        val pendingIntent = PendingIntent.getActivity(
            context, reminderId.hashCode(), contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, AuraChannel.REMINDERS.name)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(reminderId.hashCode(), notification)
    }

    companion object {
        const val KEY_REMINDER_ID = "aura_reminder_id"
    }
}
