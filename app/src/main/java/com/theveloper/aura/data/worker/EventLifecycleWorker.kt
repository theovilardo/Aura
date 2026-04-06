package com.theveloper.aura.data.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.theveloper.aura.domain.model.EventStatus
import com.theveloper.aura.domain.repository.AuraEventRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Periodic worker that manages event lifecycle transitions:
 * - UPCOMING → ACTIVE when now >= startAt
 * - ACTIVE → COMPLETED when now > endAt
 *
 * When an event becomes ACTIVE, it dispatches [EventSubActionWorker]
 * for each enabled sub-action.
 */
@HiltWorker
class EventLifecycleWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val eventRepository: AuraEventRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        Log.d(TAG, "EventLifecycle running at $now")

        // Activate upcoming events
        val toActivate = eventRepository.getEventsToActivate(now)
        for (event in toActivate) {
            Log.d(TAG, "Activating event: ${event.id} (${event.title})")
            eventRepository.updateStatus(event.id, EventStatus.ACTIVE)
            // Sub-actions will be picked up by CronDispatcherWorker
            // or we can dispatch them immediately for interval-based ones
            dispatchSubActions(event.id)
        }

        // Complete expired events
        val toComplete = eventRepository.getEventsToComplete(now)
        for (event in toComplete) {
            Log.d(TAG, "Completing event: ${event.id} (${event.title})")
            eventRepository.updateStatus(event.id, EventStatus.COMPLETED)
        }

        Result.success()
    }

    private fun dispatchSubActions(eventId: String) {
        val workRequest = OneTimeWorkRequestBuilder<EventSubActionWorker>()
            .setInputData(
                Data.Builder()
                    .putString(EventSubActionWorker.KEY_EVENT_ID, eventId)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueue(workRequest)
    }

    companion object {
        private const val TAG = "EventLifecycleWorker"
        const val WORK_NAME = "event_lifecycle"
    }
}
