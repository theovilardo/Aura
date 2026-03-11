package com.theveloper.aura.engine.habit

import com.theveloper.aura.data.db.HabitSignalEntity
import com.theveloper.aura.domain.model.SignalType
import com.theveloper.aura.domain.model.Task
import com.theveloper.aura.domain.model.TaskType
import com.theveloper.aura.domain.model.UserPattern
import com.theveloper.aura.domain.repository.HabitRepository
import com.theveloper.aura.domain.repository.TaskRepository
import com.theveloper.aura.domain.repository.UserPatternRepository
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HabitEngine @Inject constructor(
    private val habitRepository: HabitRepository,
    private val userPatternRepository: UserPatternRepository,
    private val taskRepository: TaskRepository
) {

    suspend fun logSignal(taskId: String, signalType: SignalType) {
        val now = java.util.Calendar.getInstance()
        val hourOfDay = now.get(java.util.Calendar.HOUR_OF_DAY)
        val dayOfWeek = now.get(java.util.Calendar.DAY_OF_WEEK)

        habitRepository.insertSignal(
            taskId = taskId,
            signalType = signalType,
            hourOfDay = hourOfDay,
            dayOfWeek = dayOfWeek
        )
    }

    suspend fun processBatch() {
        val tasks = taskRepository.getTasksFlow().firstOrNull() ?: return
        val taskMap = tasks.associateBy { it.id }
        
        // Need all signals. This would typically be batched or optimized in a real app,
        // but for MVP we process what we have.
        // We'll calculate patterns per taskType, hourOfDay, and dayOfWeek.
        
        val allSignals = mutableListOf<HabitSignalEntity>()
        for (task in tasks) {
            allSignals.addAll(habitRepository.getSignalsForTask(task.id))
        }
        
        // Group by (taskType, hourOfDay, dayOfWeek)
        val groupedSignals = allSignals.groupBy { signal ->
            val task = taskMap[signal.taskId]
            Triple(task?.type ?: TaskType.GENERAL, signal.hourOfDay, signal.dayOfWeek)
        }

        for ((key, signals) in groupedSignals) {
            val (taskType, hourOfDay, dayOfWeek) = key
            val pattern = calculatePattern(taskType, hourOfDay, dayOfWeek, signals)
            if (pattern != null) {
                userPatternRepository.upsertPattern(pattern)
            }
        }
    }

    private fun calculatePattern(
        taskType: TaskType,
        hourOfDay: Int,
        dayOfWeek: Int,
        signals: List<HabitSignalEntity>
    ): UserPattern? {
        val sampleSize = signals.size
        if (sampleSize == 0) return null

        val completedCount = signals.count { it.signalType == SignalType.TASK_COMPLETED }
        val dismissedCount = signals.count { it.signalType == SignalType.REMINDER_DISMISSED }
        val snoozedCount = signals.count { it.signalType == SignalType.REMINDER_SNOOZED }
        
        val completionRate = completedCount.toFloat() / sampleSize
        val dismissRate = dismissedCount.toFloat() / sampleSize
        
        // Confidence = min(1.0, sampleSize/20) as per spec F3-03
        val confidence = minOf(1.0f, sampleSize.toFloat() / 20f)
        
        // avgDelayMs will be handled when snooze signals carry more context. 
        // For MVP, we'll assign a placeholder based on snoozes. (e.g. 30 mins * snoozes / sampleSize)
        val avgDelayMs = (snoozedCount.toFloat() / sampleSize * 30 * 60 * 1000).toLong()

        return UserPattern(
            taskType = taskType,
            hourOfDay = hourOfDay,
            dayOfWeek = dayOfWeek,
            completionRate = completionRate,
            dismissRate = dismissRate,
            avgDelayMs = avgDelayMs,
            sampleSize = sampleSize,
            confidence = confidence
        )
    }
}
