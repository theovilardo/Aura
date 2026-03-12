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
