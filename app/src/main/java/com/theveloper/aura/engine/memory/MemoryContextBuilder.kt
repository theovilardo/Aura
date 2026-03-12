package com.theveloper.aura.engine.memory

import com.theveloper.aura.domain.model.MemoryCategory
import com.theveloper.aura.domain.model.MemorySlot
import com.theveloper.aura.domain.model.TaskType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryContextBuilder @Inject constructor() {

    fun buildContextForClassifier(
        detectedType: TaskType?,
        memorySlots: List<MemorySlot>
    ): String {
        val relevantCategories = when (detectedType) {
            TaskType.HABIT, TaskType.HEALTH -> listOf(
                MemoryCategory.ROUTINE,
                MemoryCategory.REMINDER_BEHAVIOR,
                MemoryCategory.PERSONAL_CONTEXT
            )

            TaskType.PROJECT, TaskType.FINANCE -> listOf(
                MemoryCategory.WORK_CONTEXT,
                MemoryCategory.ROUTINE,
                MemoryCategory.TASK_PREFERENCES
            )

            TaskType.TRAVEL -> listOf(
                MemoryCategory.PERSONAL_CONTEXT,
                MemoryCategory.ROUTINE,
                MemoryCategory.VOCABULARY
            )

            TaskType.GENERAL, null -> MemoryCategory.entries
        }

        return memorySlots
            .filter { slot -> slot.category in relevantCategories && slot.content.isNotBlank() }
            .joinToString("\n") { slot -> "[${slot.category.name}]: ${slot.content}" }
    }
}
