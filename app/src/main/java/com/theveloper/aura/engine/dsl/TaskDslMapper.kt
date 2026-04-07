package com.theveloper.aura.engine.dsl

import com.theveloper.aura.core.json.auraJson
import com.theveloper.aura.domain.model.ChecklistItem
import com.theveloper.aura.domain.model.ComponentConfig
import com.theveloper.aura.domain.model.ComponentType
import com.theveloper.aura.domain.model.Reminder
import com.theveloper.aura.domain.model.Task
import com.theveloper.aura.domain.model.TaskComponent
import com.theveloper.aura.domain.model.TaskFunctionSkill
import com.theveloper.aura.engine.skill.SkillRegistry
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.JsonObject
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
                skillId = componentDsl.skillId,
                skillRuntime = componentDsl.skillRuntime ?: componentDsl.skillId?.let { SkillRegistry.resolveUi(it)?.runtime },
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

        val functionSkills = dsl.functionSkills.map { functionSkill ->
            TaskFunctionSkill(
                skillId = functionSkill.skillId,
                runtime = functionSkill.runtime ?: SkillRegistry.resolveFunction(functionSkill.skillId)?.runtime
                    ?: com.theveloper.aura.domain.model.FunctionSkillRuntime.PROMPT_AUGMENTATION,
                enabled = functionSkill.enabled,
                configJson = auraJson.encodeToString(JsonObject.serializer(), functionSkill.config)
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
            functionSkills = functionSkills,
            reminders = reminders,
            createdAt = now,
            updatedAt = now
        )
    }

    private fun extractChecklistItems(config: kotlinx.serialization.json.JsonObject): List<ChecklistItemDSL> {
        return ChecklistDslItems.parse(config)
    }
}
