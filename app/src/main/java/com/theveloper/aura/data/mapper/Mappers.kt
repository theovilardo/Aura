package com.theveloper.aura.data.mapper

import com.theveloper.aura.data.db.ChecklistItemEntity
import com.theveloper.aura.data.db.ReminderEntity
import com.theveloper.aura.data.db.TaskComponentEntity
import com.theveloper.aura.data.db.TaskEntity
import com.theveloper.aura.data.db.TaskWithComponents
import com.theveloper.aura.domain.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val uiJson = Json {
    ignoreUnknownKeys = true
    classDiscriminator = "config_type"
}

fun TaskEntity.toDomain(components: List<TaskComponent> = emptyList()): Task {
    return Task(
        id = id,
        title = title,
        type = type,
        status = status,
        priority = priority,
        targetDate = targetDate,
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
        config = runCatching { uiJson.decodeFromString<ComponentConfig>(config) }.getOrDefault(UnknownConfig())
    )
}

fun TaskComponent.toEntity(): TaskComponentEntity {
    return TaskComponentEntity(
        id = id,
        taskId = taskId,
        type = type,
        sortOrder = sortOrder,
        config = uiJson.encodeToString(config as ComponentConfig)
    )
}

fun TaskWithComponents.toDomain(): Task {
    return task.toDomain(
        components = components.map { it.toDomain() }.sortedBy { it.sortOrder }
    )
}

fun ChecklistItemEntity.toDomain(): ChecklistItem {
    return ChecklistItem(
        id = id,
        componentId = componentId,
        text = text,
        isCompleted = isCompleted,
        sortOrder = sortOrder
    )
}

fun ChecklistItem.toEntity(): ChecklistItemEntity {
    return ChecklistItemEntity(
        id = id,
        componentId = componentId,
        text = text,
        isCompleted = isCompleted,
        sortOrder = sortOrder
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
