package com.theveloper.aura.data.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.theveloper.aura.domain.repository.AuraReminderRepository
import com.theveloper.aura.domain.repository.AutomationRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Periodic worker (runs every 15 minutes via WorkManager) that evaluates
 * cron expressions for automations and cyclical reminders, enqueuing
 * the appropriate execution workers when items are due.
 *
 * This bridges WorkManager's 15-min minimum interval with cron expressions.
 */
@HiltWorker
class CronDispatcherWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val automationRepository: AutomationRepository,
    private val reminderRepository: AuraReminderRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        Log.d(TAG, "CronDispatcher running at $now")

        // 1. Check automations
        dispatchAutomations(now)

        // 2. Check due reminders
        dispatchReminders(now)

        Result.success()
    }

    private suspend fun dispatchAutomations(now: Long) {
        try {
            val activeAutomations = automationRepository.getActive()
            for (automation in activeAutomations) {
                if (shouldFireCron(automation.cronExpression, now, automation.lastExecutionAt)) {
                    Log.d(TAG, "Dispatching automation: ${automation.id} (${automation.title})")
                    val workRequest = OneTimeWorkRequestBuilder<AutomationWorker>()
                        .setInputData(
                            Data.Builder()
                                .putString(AutomationWorker.KEY_AUTOMATION_ID, automation.id)
                                .build()
                        )
                        .build()
                    WorkManager.getInstance(context).enqueue(workRequest)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dispatch automations", e)
        }
    }

    private suspend fun dispatchReminders(now: Long) {
        try {
            val dueReminders = reminderRepository.getDueReminders(now)
            for (reminder in dueReminders) {
                Log.d(TAG, "Dispatching reminder: ${reminder.id} (${reminder.title})")
                val workRequest = OneTimeWorkRequestBuilder<AuraReminderWorker>()
                    .setInputData(
                        Data.Builder()
                            .putString(AuraReminderWorker.KEY_REMINDER_ID, reminder.id)
                            .build()
                    )
                    .build()
                WorkManager.getInstance(context).enqueue(workRequest)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dispatch reminders", e)
        }
    }

    /**
     * Simple cron evaluation: checks if the cron expression should fire
     * given the current time and last execution time.
     *
     * Supports standard 5-field cron: minute hour day-of-month month day-of-week
     * Uses a simplified match: if the current time matches the cron fields
     * and it hasn't fired in the last 14 minutes (to avoid double-firing
     * within the 15-min WorkManager window).
     */
    private fun shouldFireCron(cronExpression: String, now: Long, lastExecution: Long?): Boolean {
        if (cronExpression.isBlank()) return false

        // Debounce: don't fire if last execution was less than 14 minutes ago
        if (lastExecution != null && (now - lastExecution) < 14 * 60 * 1000L) return false

        val parts = cronExpression.trim().split("\\s+".toRegex())
        if (parts.size != 5) return false

        val cal = java.util.Calendar.getInstance().apply { timeInMillis = now }
        val currentMinute = cal.get(java.util.Calendar.MINUTE)
        val currentHour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val currentDom = cal.get(java.util.Calendar.DAY_OF_MONTH)
        val currentMonth = cal.get(java.util.Calendar.MONTH) + 1 // Calendar months are 0-based
        val currentDow = cal.get(java.util.Calendar.DAY_OF_WEEK) - 1 // Calendar DOW is 1-based (Sun=1)

        return matchesCronField(parts[0], currentMinute) &&
                matchesCronField(parts[1], currentHour) &&
                matchesCronField(parts[2], currentDom) &&
                matchesCronField(parts[3], currentMonth) &&
                matchesCronField(parts[4], currentDow)
    }

    /**
     * Match a single cron field against a current value.
     * Supports: "*", exact number, "star/step" (e.g. "star/2" for every 2).
     */
    private fun matchesCronField(field: String, value: Int): Boolean {
        if (field == "*") return true
        // Exact match
        field.toIntOrNull()?.let { return it == value }
        // Step: */N
        if (field.startsWith("*/")) {
            val step = field.removePrefix("*/").toIntOrNull() ?: return false
            return step > 0 && value % step == 0
        }
        // Comma-separated: 1,3,5
        if (field.contains(",")) {
            return field.split(",").any { it.trim().toIntOrNull() == value }
        }
        return false
    }

    companion object {
        private const val TAG = "CronDispatcherWorker"
        const val WORK_NAME = "cron_dispatcher"
    }
}
