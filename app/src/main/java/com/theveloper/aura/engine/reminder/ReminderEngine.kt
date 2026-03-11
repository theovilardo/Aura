package com.theveloper.aura.engine.reminder

import com.theveloper.aura.domain.model.Reminder
import com.theveloper.aura.domain.model.SignalType
import com.theveloper.aura.domain.model.TaskType
import com.theveloper.aura.domain.repository.ReminderRepository
import com.theveloper.aura.domain.repository.UserPatternRepository
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderEngine @Inject constructor(
    private val reminderRepository: ReminderRepository,
    private val userPatternRepository: UserPatternRepository
) {
    suspend fun calculateOptimalTime(taskType: TaskType): Pair<Int, Int> {
        val pattern = userPatternRepository.getBestPatternForType(taskType)
        
        if (pattern != null && pattern.confidence > 0.5f) {
            return Pair(pattern.hourOfDay, 0)
        }
        
        // F3-06: Si no hay data, usa defaultTimeForType()
        return defaultTimeForType(taskType)
    }
    
    // Default times depending on type
    private fun defaultTimeForType(taskType: TaskType): Pair<Int, Int> {
        return when (taskType) {
            TaskType.HABIT, TaskType.HEALTH -> Pair(8, 0) // Morning
            TaskType.PROJECT, TaskType.FINANCE -> Pair(10, 0) // Work/Focus hours
            TaskType.GENERAL, TaskType.TRAVEL -> Pair(18, 0) // Evening
        }
    }

    // SM-2 Implementation as per F3-07
    fun calculateNextInterval(oldIntervalDays: Float, easeFactor: Float, repetitions: Int, responseQuality: Int): Triple<Float, Float, Int> {
        // responseQuality: COMPLETED->5, SNOOZED->3, DISMISSED->1
        var newRepetitions = repetitions
        var newInterval: Float
        var newEaseFactor = easeFactor

        if (responseQuality >= 3) {
            if (newRepetitions == 0) {
                newInterval = 1f
            } else if (newRepetitions == 1) {
                newInterval = 6f
            } else {
                newInterval = oldIntervalDays * easeFactor
            }
            newRepetitions++
        } else {
            // "3 DISMISSED seguidos resetean" - We reset if quality is 1. (Simplification per SM-2 basic version)
            newRepetitions = 0
            newInterval = 1f
        }

        newEaseFactor += (0.1f - (5 - responseQuality) * (0.08f + (5 - responseQuality) * 0.02f))
        
        if (newEaseFactor < 1.3f) {
            newEaseFactor = 1.3f
        }

        return Triple(newInterval, newEaseFactor, newRepetitions)
    }

    suspend fun onReminderResponse(reminderId: String, signalType: SignalType) {
        // Implementación de F3-07
        // get reminder
        val activeReminders = reminderRepository.getActiveRemindersScheduled(System.currentTimeMillis() + 1000L).firstOrNull()
        val reminder = activeReminders?.find { it.id == reminderId } ?: return

        val quality = when (signalType) {
            SignalType.TASK_COMPLETED -> 5
            SignalType.REMINDER_SNOOZED -> 3
            SignalType.REMINDER_DISMISSED -> 1
        }

        val (newInterval, newEase, newReps) = calculateNextInterval(
            reminder.intervalDays,
            reminder.easeFactor,
            reminder.repetitions,
            quality
        )

        val nextScheduledAt = System.currentTimeMillis() + (newInterval * 24 * 60 * 60 * 1000).toLong()

        val updatedReminder = reminder.copy(
            intervalDays = newInterval,
            easeFactor = newEase,
            repetitions = newReps,
            scheduledAt = nextScheduledAt
        )

        reminderRepository.update(updatedReminder)
    }
}
