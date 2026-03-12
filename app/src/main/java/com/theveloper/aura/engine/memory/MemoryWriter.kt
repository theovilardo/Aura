package com.theveloper.aura.engine.memory

import com.theveloper.aura.domain.model.ComponentType
import com.theveloper.aura.domain.model.MemoryCategory
import com.theveloper.aura.domain.model.MemorySlot
import com.theveloper.aura.domain.model.Task
import com.theveloper.aura.domain.model.TaskType
import com.theveloper.aura.domain.model.UserPattern
import com.theveloper.aura.domain.repository.MemoryRepository
import com.theveloper.aura.domain.repository.TaskRepository
import com.theveloper.aura.domain.repository.UserPatternRepository
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryWriter @Inject constructor(
    private val taskRepository: TaskRepository,
    private val userPatternRepository: UserPatternRepository,
    private val memoryRepository: MemoryRepository,
    private val memoryCompactor: MemoryCompactor
) {

    suspend fun refresh() {
        val tasks = taskRepository.getTasksFlow().firstOrNull().orEmpty()
        val patterns = TaskType.entries.flatMap { type -> userPatternRepository.getPatternsForType(type) }
        val existingByCategory = memoryRepository.getSlots().associateBy { it.category }
        val now = System.currentTimeMillis()

        val candidates = buildCandidates(tasks, patterns, now)
            .mapNotNull { candidate ->
                val previous = existingByCategory[candidate.category]
                if (candidate.content.isBlank() || previous?.content == candidate.content) {
                    null
                } else {
                    candidate
                }
            }
            .take(2)
            .map { candidate ->
                if (candidate.tokenCount > candidate.maxTokens) {
                    memoryCompactor.compact(candidate)
                } else {
                    candidate
                }
            }

        memoryRepository.upsertAll(candidates)
    }

    private fun buildCandidates(
        tasks: List<Task>,
        patterns: List<UserPattern>,
        now: Long
    ): List<MemorySlot> {
        val orderedCategories = listOf(
            MemoryCategory.REMINDER_BEHAVIOR,
            MemoryCategory.TASK_PREFERENCES,
            MemoryCategory.VOCABULARY,
            MemoryCategory.ROUTINE,
            MemoryCategory.WORK_CONTEXT,
            MemoryCategory.PERSONAL_CONTEXT
        )

        return orderedCategories.mapNotNull { category ->
            buildContent(category, tasks, patterns)
                ?.takeIf { it.isNotBlank() }
                ?.let { content ->
                    MemorySlot(
                        id = category.name.lowercase(),
                        category = category,
                        content = content,
                        lastUpdatedAt = now,
                        tokenCount = content.countApproxTokens()
                    )
                }
        }
    }

    private fun buildContent(
        category: MemoryCategory,
        tasks: List<Task>,
        patterns: List<UserPattern>
    ): String? {
        return when (category) {
            MemoryCategory.ROUTINE -> buildRoutineContent(patterns)
            MemoryCategory.WORK_CONTEXT -> buildContextContent(tasks.filter { it.type == TaskType.PROJECT || it.type == TaskType.FINANCE }, "Trabajo")
            MemoryCategory.PERSONAL_CONTEXT -> buildContextContent(tasks.filter { it.type == TaskType.TRAVEL || it.type == TaskType.HEALTH || it.type == TaskType.HABIT }, "Personal")
            MemoryCategory.TASK_PREFERENCES -> buildTaskPreferencesContent(tasks)
            MemoryCategory.REMINDER_BEHAVIOR -> buildReminderBehaviorContent(patterns)
            MemoryCategory.VOCABULARY -> buildVocabularyContent(tasks)
        }
    }

    private fun buildRoutineContent(patterns: List<UserPattern>): String? {
        val bestWindows = patterns
            .filter { it.confidence >= 0.25f && it.completionRate >= 0.55f }
            .sortedByDescending { it.completionRate * it.confidence }
            .take(2)
        if (bestWindows.isEmpty()) return null

        return bestWindows.joinToString(" ") { pattern ->
            "Suele responder mejor a ${pattern.taskType.name.lowercase()} los ${dayName(pattern.dayOfWeek)} cerca de las ${pattern.hourOfDay}h."
        }
    }

    private fun buildReminderBehaviorContent(patterns: List<UserPattern>): String? {
        val bestCompletion = patterns
            .filter { it.confidence >= 0.25f }
            .maxByOrNull { it.completionRate * it.confidence }
        val worstDismiss = patterns
            .filter { it.confidence >= 0.25f }
            .maxByOrNull { it.dismissRate * it.confidence }

        val lines = buildList {
            bestCompletion?.let { pattern ->
                add("Responde mejor a recordatorios de ${pattern.taskType.name.lowercase()} los ${dayName(pattern.dayOfWeek)} cerca de las ${pattern.hourOfDay}h.")
            }
            worstDismiss
                ?.takeIf { it.dismissRate >= 0.4f }
                ?.let { pattern ->
                    add("Suele ignorar recordatorios de ${pattern.taskType.name.lowercase()} los ${dayName(pattern.dayOfWeek)} cerca de las ${pattern.hourOfDay}h.")
                }
        }

        return lines.takeIf { it.isNotEmpty() }?.joinToString(" ")
    }

    private fun buildTaskPreferencesContent(tasks: List<Task>): String? {
        val componentCounts = tasks
            .flatMap { task -> task.components.map { component -> component.type } }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(3)
        if (componentCounts.isEmpty()) return null

        val favoriteComponents = componentCounts.joinToString(", ") { entry ->
            entry.key.name.lowercase()
        }

        val frequentShoppingItems = tasks
            .asSequence()
            .filter { SHOPPING_CONTEXT_REGEX.containsMatchIn(it.title) }
            .flatMap { task -> task.components.asSequence() }
            .filter { component -> component.type == ComponentType.CHECKLIST }
            .flatMap { component -> component.checklistItems.asSequence() }
            .map { item -> item.text.trim().lowercase() }
            .filter { item -> item.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(5)
            .joinToString(", ") { it.key }

        return buildString {
            append("Prefiere estructurar tareas con $favoriteComponents.")
            if (frequentShoppingItems.isNotBlank()) {
                append(" Compras frecuentes: $frequentShoppingItems.")
            }
        }
    }

    private fun buildVocabularyContent(tasks: List<Task>): String? {
        val recurringTerms = tasks
            .flatMap { task ->
                task.title.split(WORD_SPLIT_REGEX).map(String::trim)
            }
            .map { token -> token.lowercase() }
            .filter { token -> token.length >= 4 && token !in STOP_WORDS }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(6)
            .map { it.key }
        if (recurringTerms.isEmpty()) return null

        return "Vocabulario recurrente: ${recurringTerms.joinToString(", ")}."
    }

    private fun buildContextContent(tasks: List<Task>, label: String): String? {
        val titles = tasks
            .sortedByDescending { it.updatedAt }
            .take(3)
            .map { it.title.trim() }
            .filter { it.isNotBlank() }
        if (titles.isEmpty()) return null

        return "$label reciente: ${titles.joinToString("; ")}."
    }

    private fun dayName(dayOfWeek: Int): String {
        return when (dayOfWeek) {
            java.util.Calendar.MONDAY -> "lunes"
            java.util.Calendar.TUESDAY -> "martes"
            java.util.Calendar.WEDNESDAY -> "miercoles"
            java.util.Calendar.THURSDAY -> "jueves"
            java.util.Calendar.FRIDAY -> "viernes"
            java.util.Calendar.SATURDAY -> "sabados"
            java.util.Calendar.SUNDAY -> "domingos"
            else -> "dias"
        }
    }

    private companion object {
        val SHOPPING_CONTEXT_REGEX = Regex("(?i)\\b(compra|compras|super|mercado|ingredientes)\\b")
        val WORD_SPLIT_REGEX = Regex("[^\\p{L}\\p{Nd}]+")
        val STOP_WORDS = setOf(
            "para",
            "este",
            "esta",
            "tarea",
            "organizar",
            "hacer",
            "crear",
            "lista"
        )
    }
}
