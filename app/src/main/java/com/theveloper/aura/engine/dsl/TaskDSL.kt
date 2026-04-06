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
    val config: JsonObject,
    val populatedFromInput: Boolean = false,
    val needsClarification: Boolean = false
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

// ── DSL outputs for new creation types ──────────────────────────────────────

@Serializable
data class ReminderDSLOutput(
    val title: String,
    val body: String = "",
    val reminderType: com.theveloper.aura.domain.model.ReminderType =
        com.theveloper.aura.domain.model.ReminderType.ONE_TIME,
    val scheduledAtMs: Long = 0L,
    val repeatCount: Int = 0,
    val intervalMs: Long = 0L,
    val cronExpression: String = "",
    val linkedTaskId: String? = null,
    val checklistItems: List<String> = emptyList(),
    val links: List<String> = emptyList()
)

@Serializable
data class AutomationDSLOutput(
    val title: String,
    val prompt: String,
    val cronExpression: String = "",
    val executionPlan: com.theveloper.aura.domain.model.AutomationExecutionPlan =
        com.theveloper.aura.domain.model.AutomationExecutionPlan(),
    val outputType: com.theveloper.aura.domain.model.AutomationOutputType =
        com.theveloper.aura.domain.model.AutomationOutputType.NOTIFICATION
)

@Serializable
data class EventDSLOutput(
    val title: String,
    val description: String = "",
    val startAtMs: Long = 0L,
    val endAtMs: Long = 0L,
    val subActions: List<EventSubActionDSL> = emptyList(),
    val components: List<ComponentDSL> = emptyList()
)

@Serializable
data class EventSubActionDSL(
    val type: com.theveloper.aura.domain.model.EventSubActionType,
    val title: String = "",
    val cronExpression: String = "",
    val intervalMs: Long = 0L,
    val prompt: String = "",
    val config: JsonObject = JsonObject(emptyMap()),
    val enabled: Boolean = true
)

/**
 * Unified result type returned by [CreationTypeClassifier].
 * Each variant wraps the DSL output for the corresponding creation type.
 */
@Serializable
sealed interface CreationDSLResult {
    @Serializable
    data class TaskResult(val dsl: TaskDSLOutput) : CreationDSLResult

    @Serializable
    data class ReminderResult(val dsl: ReminderDSLOutput) : CreationDSLResult

    @Serializable
    data class AutomationResult(val dsl: AutomationDSLOutput) : CreationDSLResult

    @Serializable
    data class EventResult(val dsl: EventDSLOutput) : CreationDSLResult
}
