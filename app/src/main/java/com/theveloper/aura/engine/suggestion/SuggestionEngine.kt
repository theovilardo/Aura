package com.theveloper.aura.engine.suggestion

import com.theveloper.aura.domain.model.*
import com.theveloper.aura.domain.repository.HabitRepository
import com.theveloper.aura.domain.repository.SuggestionRepository
import com.theveloper.aura.domain.repository.TaskRepository
import com.theveloper.aura.domain.repository.UserPatternRepository
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SuggestionEngine @Inject constructor(
    private val suggestionRepository: SuggestionRepository,
    private val userPatternRepository: UserPatternRepository,
    private val taskRepository: TaskRepository,
    private val habitRepository: HabitRepository
) {
    suspend fun evaluatePatterns(tasks: List<Task>? = null) {
        val activeTasks = tasks?.filter { it.status == TaskStatus.ACTIVE } ?: taskRepository.getTasksFlow().first().filter { it.status == TaskStatus.ACTIVE }

        for (task in activeTasks) {
            val patterns = userPatternRepository.getPatternsForType(task.type)
            val signals = habitRepository.getSignalsForTask(task.id)
            
            // F5-03 & F5-04 Task Resurrection
            val lastSignalTime = signals.maxOfOrNull { it.recordedAt } ?: 0L
            val daysSinceLastSignal = (System.currentTimeMillis() - lastSignalTime) / (1000 * 60 * 60 * 24)
            
            // Evaluate completion rate for resurrection based on past signals if needed 
            // the requirement says: "historical completionRate > 0.4"
            // we will calculate historical completionRate roughly with signals
            val totalSignals = signals.size
            val completedSignals = signals.count { it.signalType == SignalType.TASK_COMPLETED }
            val historicalCompletionRate = if (totalSignals > 0) completedSignals.toFloat() / totalSignals else 0f
            
            if (totalSignals > 5 && daysSinceLastSignal >= 10 && historicalCompletionRate > 0.4f) {
                // Generar Suggestion RESURRECT_TASK
                if (!hasRecentSuggestion(task.id, SuggestionType.RESURRECT_TASK)) {
                    val payload = JSONObject().apply {
                        put("simplify", true)
                        put("suggestedDuration", 15)
                        put("suggestedReminderTime", "10:00") // Mock
                    }.toString()
                    
                    val reasoning = "Notamos que solías completar esta tarea regularmente hace más de $daysSinceLastSignal días. ¿Quieres retomarla quizás simplificándola un poco?"
                    createSuggestion(task.id, SuggestionType.RESURRECT_TASK, payload, reasoning)
                }
            } else {
                // F5-02
                // SIMPLIFY_TASK
                if (totalSignals > 5 && historicalCompletionRate < 0.3f) {
                    if (!hasRecentSuggestion(task.id, SuggestionType.SIMPLIFY_TASK)) {
                        val payload = JSONObject().apply {
                            put("action", "reduce_components")
                        }.toString()
                        val reasoning = "Detectamos que rara vez completas esta tarea. Podríamos simplificar los pasos necesarios para ayudarte a lograrla con menos esfuerzo."
                        createSuggestion(task.id, SuggestionType.SIMPLIFY_TASK, payload, reasoning)
                    }
                }
            }
        }
        
        // Evaluate general patterns (for RESCHEDULE_REMINDER)
        // Since we evaluate per task type patterns
        for (type in TaskType.values()) {
            val patterns = userPatternRepository.getPatternsForType(type)
            for (pattern in patterns) {
                if (pattern.confidence > 0.7f && pattern.dismissRate > 0.6f) {
                    val candidateTasks = activeTasks.filter { it.type == type }
                    // Create suggest for the worst performing task
                    val worstTask = candidateTasks.firstOrNull() // Simplify for MVP
                    if (worstTask != null && !hasRecentSuggestion(worstTask.id, SuggestionType.RESCHEDULE_REMINDER)) {
                        val payload = JSONObject().apply {
                            put("suggestedHour", (pattern.hourOfDay + 2) % 24)
                        }.toString()
                        val reasoning = "Observamos que frecuentemente ignoras este recordatorio a esta hora (${pattern.hourOfDay}:00). Sugerimos reprogramarlo para un momento del día en que sueles tener mejor respuesta."
                        createSuggestion(worstTask.id, SuggestionType.RESCHEDULE_REMINDER, payload, reasoning)
                    }
                }
            }
        }
    }

    private suspend fun hasRecentSuggestion(taskId: String, type: SuggestionType): Boolean {
        // Here we ideally check DB, but in a simple MVP we'll query pending
        val allPending = suggestionRepository.getPendingSuggestions().first()
        return allPending.any { it.taskId == taskId && it.type == type }
    }

    private suspend fun createSuggestion(taskId: String, type: SuggestionType, payload: String, reasoning: String) {
        val suggestion = Suggestion(
            id = UUID.randomUUID().toString(),
            taskId = taskId,
            type = type,
            status = SuggestionStatus.PENDING,
            payloadJson = payload,
            reasoning = reasoning,
            createdAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + (72L * 60 * 60 * 1000) // 72 horas expiracion (F5-12)
        )
        suggestionRepository.save(suggestion)
    }
}
