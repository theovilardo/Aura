package com.theveloper.aura.engine.dsl

import com.theveloper.aura.core.json.auraJson
import com.theveloper.aura.domain.model.ChecklistItem
import com.theveloper.aura.domain.model.ComponentConfig
import com.theveloper.aura.domain.model.ComponentType
import com.theveloper.aura.domain.model.Reminder
import com.theveloper.aura.domain.model.Task
import com.theveloper.aura.domain.model.TaskComponent
import kotlinx.serialization.json.decodeFromJsonElement
import java.util.UUID

object TaskDslMapper {

    fun toTask(
        dsl: TaskDSLOutput,
        taskId: String = UUID.randomUUID().toString(),
        now: Long = System.currentTimeMillis()
    ): Task {
        val components = dsl.components.map { componentDsl ->
            val componentId = UUID.randomUUID().toString()
            TaskComponent(
                id = componentId,
                taskId = taskId,
                type = componentDsl.type,
                sortOrder = componentDsl.sortOrder,
                config = auraJson.decodeFromJsonElement<ComponentConfig>(componentDsl.config),
                needsClarification = componentDsl.needsClarification,
                checklistItems = if (componentDsl.type == ComponentType.CHECKLIST) {
                    extractChecklistItems(componentDsl.config).mapIndexed { index, item ->
                        ChecklistItem(
                            id = UUID.randomUUID().toString(),
                            componentId = componentId,
                            text = item.label,
                            isSuggested = item.isSuggested,
                            sortOrder = index
                        )
                    }
                } else {
                    emptyList()
                }
            )
        }

        val reminders = dsl.reminders.map { reminder ->
            Reminder(
                id = UUID.randomUUID().toString(),
                taskId = taskId,
                scheduledAt = reminder.scheduledAtMs,
                intervalDays = reminder.intervalDays,
                easeFactor = reminder.easeFactor,
                repetitions = reminder.repetitions
            )
        }

        return Task(
            id = taskId,
            title = dsl.title.trim(),
            type = dsl.type,
            priority = dsl.priority.coerceIn(0, 3),
            targetDate = dsl.targetDateMs,
            components = components,
            reminders = reminders,
            createdAt = now,
            updatedAt = now
        )
    }

    private fun extractChecklistItems(config: kotlinx.serialization.json.JsonObject): List<ChecklistItemDSL> {
        return ChecklistDslItems.parse(config)
    }
}
