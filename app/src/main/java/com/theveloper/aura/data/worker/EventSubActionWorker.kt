package com.theveloper.aura.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.theveloper.aura.domain.model.EventStatus
import com.theveloper.aura.domain.model.EventSubActionType
import com.theveloper.aura.domain.repository.AuraEventRepository
import com.theveloper.aura.data.db.EventSubActionDao
import com.theveloper.aura.data.mapper.toDomain
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Executes sub-actions for an active event.
 * Handles: NOTIFICATION, METRIC_PROMPT, AUTOMATION, CHECKLIST_REMIND.
 */
@HiltWorker
class EventSubActionWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val eventRepository: AuraEventRepository,
    private val subActionDao: EventSubActionDao
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val eventId = inputData.getString(KEY_EVENT_ID) ?: return@withContext Result.failure()

        val event = eventRepository.getById(eventId)
        if (event == null || event.status != EventStatus.ACTIVE) {
            Log.d(TAG, "Event $eventId not active, skipping sub-actions")
            return@withContext Result.success()
        }

        val enabledActions = subActionDao.getEnabledForEvent(eventId)
        for (actionEntity in enabledActions) {
            val action = actionEntity.toDomain()
            try {
                when (action.type) {
                    EventSubActionType.NOTIFICATION -> {
                        showNotification(
                            title = event.title,
                            body = action.title.ifBlank { action.prompt }
                        )
                    }
                    EventSubActionType.METRIC_PROMPT -> {
                        // Show a notification that deep-links to the event detail
                        // with a metric input prompt
                        showNotification(
                            title = event.title,
                            body = action.prompt.ifBlank { action.title }
                        )
                    }
                    EventSubActionType.CHECKLIST_REMIND -> {
                        showNotification(
                            title = event.title,
                            body = action.title.ifBlank { "Check your event checklist" }
                        )
                    }
                    EventSubActionType.AUTOMATION -> {
                        // Delegate to AutomationWorker if there's a linked automation
                        Log.d(TAG, "Automation sub-action for event $eventId — would trigger automation")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Sub-action ${action.id} failed for event $eventId", e)
            }
        }

        Result.success()
    }

    private fun showNotification(title: String, body: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channelId = "EVENTS"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "AURA Events", NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Event sub-action notifications" }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify((title + body).hashCode(), notification)
    }

    companion object {
        private const val TAG = "EventSubActionWorker"
        const val KEY_EVENT_ID = "event_id"
    }
}
