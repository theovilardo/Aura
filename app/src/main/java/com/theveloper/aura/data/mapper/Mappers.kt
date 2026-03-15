package com.theveloper.aura.data.mapper

import com.theveloper.aura.core.json.auraJson
import com.theveloper.aura.data.db.ChecklistItemEntity
import com.theveloper.aura.data.db.MemorySlotEntity
import com.theveloper.aura.data.db.ReminderEntity
import com.theveloper.aura.data.db.TaskComponentEntity
import com.theveloper.aura.data.db.TaskEntity
import com.theveloper.aura.data.db.TaskWithDetails
import com.theveloper.aura.domain.model.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

fun TaskEntity.toDomain(components: List<TaskComponent> = emptyList()): Task {
    return Task(
        id = id,
        title = title,
        type = type,
        status = status,
        priority = priority,
        targetDate = targetDate,
        reminders = emptyList(),
        createdAt = createdAt,
        updatedAt = updatedAt,
        components = components
    )
}

fun Task.toEntity(): TaskEntity {
    return TaskEntity(
        id = id,
        title = title,
        type = type,
        status = status,
        priority = priority,
        targetDate = targetDate,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun TaskComponentEntity.toDomain(): TaskComponent {
    return TaskComponent(
        id = id,
        taskId = taskId,
        type = type,
        sortOrder = sortOrder,
        config = runCatching { auraJson.decodeFromString<ComponentConfig>(config) }.getOrDefault(UnknownConfig()),
        needsClarification = needsClarification
    )
}

fun TaskComponent.toEntity(): TaskComponentEntity {
    return TaskComponentEntity(
        id = id,
        taskId = taskId,
        type = type,
        sortOrder = sortOrder,
        config = auraJson.encodeToString(config),
        needsClarification = needsClarification
    )
}

fun TaskWithDetails.toDomain(): Task {
    return task.toDomain(
        components = components
            .map { component ->
                component.component.toDomain().copy(
                    checklistItems = component.checklistItems
                        .map { item -> item.toDomain() }
                        .sortedBy { item -> item.sortOrder }
                )
            }
            .sortedBy { it.sortOrder }
    ).copy(
        reminders = reminders.map { it.toDomain() }.sortedBy { it.scheduledAt }
    )
}

fun ChecklistItemEntity.toDomain(): ChecklistItem {
    return ChecklistItem(
        id = id,
        componentId = componentId,
        text = text,
        isCompleted = isCompleted,
        isSuggested = isSuggested,
        sortOrder = sortOrder
    )
}

fun ChecklistItem.toEntity(): ChecklistItemEntity {
    return ChecklistItemEntity(
        id = id,
        componentId = componentId,
        text = text,
        isCompleted = isCompleted,
        sortOrder = sortOrder,
        isSuggested = isSuggested
    )
}

fun ReminderEntity.toDomain(): Reminder {
    return Reminder(
        id = id,
        taskId = taskId,
        scheduledAt = scheduledAt,
        intervalDays = intervalDays,
        easeFactor = easeFactor,
        repetitions = repetitions
    )
}

fun Reminder.toEntity(): ReminderEntity {
    return ReminderEntity(
        id = id,
        taskId = taskId,
        scheduledAt = scheduledAt,
        intervalDays = intervalDays,
        easeFactor = easeFactor,
        repetitions = repetitions
    )
}

fun com.theveloper.aura.data.db.SuggestionEntity.toDomain(): Suggestion {
    return Suggestion(
        id = id,
        taskId = taskId,
        type = type,
        status = runCatching { SuggestionStatus.valueOf(status) }.getOrDefault(SuggestionStatus.PENDING),
        payloadJson = payloadJson,
        reasoning = reasoning,
        createdAt = createdAt,
        expiresAt = expiresAt
    )
}

fun Suggestion.toEntity(): com.theveloper.aura.data.db.SuggestionEntity {
    return com.theveloper.aura.data.db.SuggestionEntity(
        id = id,
        taskId = taskId,
        type = type,
        status = status.name,
        payloadJson = payloadJson,
        reasoning = reasoning,
        createdAt = createdAt,
        expiresAt = expiresAt
    )
}

fun MemorySlotEntity.toDomain(): MemorySlot {
    return MemorySlot(
        id = id,
        category = category,
        content = content,
        lastUpdatedAt = lastUpdatedAt,
        version = version,
        tokenCount = tokenCount,
        maxTokens = maxTokens
    )
}

fun MemorySlot.toEntity(): MemorySlotEntity {
    return MemorySlotEntity(
        id = id,
        category = category,
        content = content,
        lastUpdatedAt = lastUpdatedAt,
        version = version,
        tokenCount = tokenCount,
        maxTokens = maxTokens
    )
}

fun com.theveloper.aura.data.db.ComponentRuleEntity.toDomain(): com.theveloper.aura.domain.model.rule.ComponentRule {
    return com.theveloper.aura.domain.model.rule.ComponentRule(
        id = id,
        taskId = taskId,
        triggerComponentId = triggerComponentId,
        triggerEvent = runCatching {
            com.theveloper.aura.domain.model.rule.TriggerEvent.valueOf(triggerEvent)
        }.getOrDefault(com.theveloper.aura.domain.model.rule.TriggerEvent.TASK_COMPLETED),
        triggerCondition = triggerConditionJson?.let {
            runCatching {
                auraJson.decodeFromString<com.theveloper.aura.domain.model.rule.TriggerCondition>(it)
            }.getOrNull()
        },
        targetComponentId = targetComponentId,
        action = runCatching {
            com.theveloper.aura.domain.model.rule.RuleAction.valueOf(action)
        }.getOrDefault(com.theveloper.aura.domain.model.rule.RuleAction.MARK_TASK_COMPLETE),
        actionParams = actionParamsJson?.let {
            runCatching {
                auraJson.decodeFromString<Map<String, String>>(it)
            }.getOrDefault(emptyMap())
        } ?: emptyMap(),
        isEnabled = isEnabled,
        priority = priority,
        createdAt = createdAt,
        createdBy = runCatching {
            com.theveloper.aura.domain.model.rule.RuleOrigin.valueOf(createdBy)
        }.getOrDefault(com.theveloper.aura.domain.model.rule.RuleOrigin.SYSTEM)
    )
}

fun com.theveloper.aura.domain.model.rule.ComponentRule.toEntity(): com.theveloper.aura.data.db.ComponentRuleEntity {
    return com.theveloper.aura.data.db.ComponentRuleEntity(
        id = id,
        taskId = taskId,
        triggerComponentId = triggerComponentId,
        triggerEvent = triggerEvent.name,
        triggerConditionJson = triggerCondition?.let {
            auraJson.encodeToString(com.theveloper.aura.domain.model.rule.TriggerCondition.serializer(), it)
        },
        targetComponentId = targetComponentId,
        action = action.name,
        actionParamsJson = if (actionParams.isNotEmpty()) {
            auraJson.encodeToString(actionParams)
        } else null,
        isEnabled = isEnabled,
        priority = priority,
        createdAt = createdAt,
        createdBy = createdBy.name
    )
}
