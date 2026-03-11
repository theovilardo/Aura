package com.theveloper.aura.engine.dsl

import com.theveloper.aura.domain.model.ComponentType
import com.theveloper.aura.domain.model.FetcherType
import com.theveloper.aura.domain.model.TaskType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class TaskDSLOutput(
    val title: String,
    val type: TaskType,
    val priority: Int = 0,
    val targetDateMs: Long? = null,
    val components: List<ComponentDSL> = emptyList(),
    val reminders: List<ReminderDSL> = emptyList(),
    val fetchers: List<FetcherDSL> = emptyList()
)

@Serializable
data class ComponentDSL(
    val type: ComponentType,
    val sortOrder: Int,
    val config: JsonObject
)

@Serializable
data class ReminderDSL(
    val scheduledAtMs: Long,
    val intervalDays: Float = 0f,
    val easeFactor: Float = 2.5f,
    val repetitions: Int = 0
)

@Serializable
data class FetcherDSL(
    val type: FetcherType,
    val params: JsonObject,
    val cronExpression: String
)
