package com.theveloper.aura.domain.usecase

import com.theveloper.aura.domain.model.Suggestion
import com.theveloper.aura.domain.model.SuggestionStatus
import com.theveloper.aura.domain.model.SuggestionType
import com.theveloper.aura.domain.repository.ReminderRepository
import com.theveloper.aura.domain.repository.SuggestionRepository
import com.theveloper.aura.domain.repository.TaskRepository
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApplySuggestionUseCase @Inject constructor(
    private val suggestionRepository: SuggestionRepository,
    private val taskRepository: TaskRepository,
    private val reminderRepository: ReminderRepository
) {
    suspend fun applySuggestion(suggestion: Suggestion) {
        val taskId = suggestion.taskId ?: return
        val task = taskRepository.getTask(taskId) ?: return
        
        when(suggestion.type) {
            SuggestionType.RESCHEDULE_REMINDER -> {
                val payload = runCatching { JSONObject(suggestion.payloadJson) }.getOrNull()
                val offset = payload?.optInt("daysOffset", -1) ?: -1
                if (offset >= 0) {
                    val targetDate = task.targetDate ?: System.currentTimeMillis()
                    val newTarget = targetDate + (offset * 24L * 60 * 60 * 1000L)
                    taskRepository.updateTask(task.copy(targetDate = newTarget))
                } else {
                    val hour = payload?.optInt("suggestedHour", -1) ?: -1
                    if (hour != -1) {
                         // Find reminders connected to task and somehow shift them...
                         val reminders = task.reminders
                         if (reminders.isNotEmpty()) {
                             // Just a simplification for MVP, rescheduling the first reminder
                             val first = reminders.first()
                             // shift scheduledAt to that hour
                             val date = java.util.Calendar.getInstance().apply {
                                 timeInMillis = first.scheduledAt
                                 set(java.util.Calendar.HOUR_OF_DAY, hour)
                                 set(java.util.Calendar.MINUTE, 0)
                             }
                             reminderRepository.update(first.copy(scheduledAt = date.timeInMillis))
                         }
                    }
                }
            }
            SuggestionType.RESURRECT_TASK -> {
                val newTargetDate = System.currentTimeMillis() + (1000 * 60 * 60 * 24) // Schedule it for tomorrow
                taskRepository.updateTask(task.copy(targetDate = newTargetDate))
                // F5-09 mentions 'create new Reminder' and modifies Task. 
            }
            SuggestionType.SIMPLIFY_TASK -> {
                // Reduces components: For example, remove checklist
                val simplifiedComponents = task.components.filter { it.type.name != "CHECKLIST" }
                // Just keeping the basics
                taskRepository.updateTask(task.copy(components = simplifiedComponents))
            }
        }
        
        suggestionRepository.updateStatus(suggestion.id, SuggestionStatus.APPROVED)
    }

    suspend fun rejectSuggestion(suggestion: Suggestion) {
        suggestionRepository.updateStatus(suggestion.id, SuggestionStatus.REJECTED)
    }
}
