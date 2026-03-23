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
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val taskId = inputData.getString("taskId") ?: return@withContext Result.failure()
        val reminderId = inputData.getString("reminderId") ?: return@withContext Result.failure()
        val title = inputData.getString("title") ?: "AURA"
        val message = inputData.getString("message") ?: "Tienes un recordatorio pendiente."

        showNotification(context, reminderId, taskId, title, message)
        Result.success()
    }

    private fun showNotification(context: Context, reminderId: String, taskId: String, title: String, message: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                AuraChannel.REMINDERS.name,
                "Recordatorios AURA",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for your task reminders"
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Actions
        val completeIntent = Intent(context, NotificationReceiver::class.java).apply {
            action = NotificationReceiver.ACTION_COMPLETE
            putExtra("reminderId", reminderId)
            putExtra("taskId", taskId)
        }
        val completePendingIntent = PendingIntent.getBroadcast(
            context, reminderId.hashCode(), completeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(context, NotificationReceiver::class.java).apply {
            action = NotificationReceiver.ACTION_SNOOZE
            putExtra("reminderId", reminderId)
            putExtra("taskId", taskId)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context, reminderId.hashCode() + 1, snoozeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(context, NotificationReceiver::class.java).apply {
            action = NotificationReceiver.ACTION_DISMISS
            putExtra("reminderId", reminderId)
            putExtra("taskId", taskId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context, reminderId.hashCode() + 2, dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, AuraChannel.REMINDERS.name)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // TODO: Mettre l'icône Aura
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_menu_agenda, "Completar", completePendingIntent)
            .addAction(android.R.drawable.ic_popup_sync, "Posponer 30min", snoozePendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Ignorar", dismissPendingIntent)

        notificationManager.notify(reminderId.hashCode(), builder.build())
    }
}
