package com.theveloper.aura.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class TaskType {
    GENERAL, TRAVEL, HABIT, HEALTH, PROJECT, FINANCE, EVENT, GOAL
}

@Serializable
enum class TaskStatus {
    ACTIVE, COMPLETED, ARCHIVED
}

@Serializable
enum class ComponentType {
    CHECKLIST,
    PROGRESS_BAR,
    COUNTDOWN,
    HABIT_RING,
    NOTES,
    METRIC_TRACKER,
    DATA_FEED,
    HOSTED_UI
}

@Serializable
enum class SkillKind {
    UI,
    FUNCTION
}

@Serializable
enum class SkillSource {
    BUILTIN,
    BUNDLED,
    IMPORTED
}

@Serializable
enum class UiSkillRuntime {
    NATIVE,
    COMPOSE_HOSTED,
    HTML_JS
}

@Serializable
enum class FunctionSkillRuntime {
    PROMPT_AUGMENTATION,
    LOCAL_EXECUTOR,
    REMOTE_EXECUTOR
}

@Serializable
enum class SignalType {
    TASK_COMPLETED,
    REMINDER_SNOOZED,
    REMINDER_DISMISSED
}

@Serializable
enum class FetcherType {
    WEATHER,
    CURRENCY_EXCHANGE,
    FLIGHT_PRICES
}

@Serializable
enum class SuggestionType {
    RESCHEDULE_REMINDER,
    SIMPLIFY_TASK,
    RESURRECT_TASK
}

@Serializable
enum class NotificationChannel {
    REMINDERS,
    SUGGESTIONS
}

@Serializable
enum class DataFeedStatus {
    LOADING,
    DATA,
    ERROR,
    STALE
}

@Serializable
enum class MemoryCategory {
    ROUTINE,
    WORK_CONTEXT,
    PERSONAL_CONTEXT,
    TASK_PREFERENCES,
    REMINDER_BEHAVIOR,
    VOCABULARY
}

@Serializable
enum class SuggestionStatus {
    PENDING, APPROVED, REJECTED, EXPIRED
}

// ── Multi-Creation-Type System ──────────────────────────────────────────────

/**
 * Top-level discriminator for the 5 creation options in the bottom bar.
 * [TASK] maps to the existing TaskClassifier pipeline; the others have dedicated classifiers.
 */
@Serializable
enum class CreationType {
    SYSTEM, REMINDER, AUTOMATION, EVENT, TASK
}

/** Schedule pattern for standalone reminders (not SM-2 task reminders). */
@Serializable
enum class ReminderType {
    /** Fires once at the scheduled time. */
    ONE_TIME,
    /** Fires a fixed number of times at the given interval. */
    REPEATING,
    /** Fires indefinitely on a cron-like cycle. */
    CYCLICAL
}

@Serializable
enum class ReminderStatus {
    PENDING, TRIGGERED, COMPLETED, CANCELLED
}

@Serializable
enum class AutomationStatus {
    ACTIVE, PAUSED, FAILED, COMPLETED
}

@Serializable
enum class EventStatus {
    UPCOMING, ACTIVE, COMPLETED
}

/** The kind of action that runs during an active [AuraEvent]. */
@Serializable
enum class EventSubActionType {
    /** Plain notification at interval. */
    NOTIFICATION,
    /** Popup with an input field (e.g. "How much did you spend?"). */
    METRIC_PROMPT,
    /** Triggers a full automation execution. */
    AUTOMATION,
    /** Remind user about a checklist inside the event. */
    CHECKLIST_REMIND
}

/** How an automation delivers its result. */
@Serializable
enum class AutomationOutputType {
    NOTIFICATION, TASK_UPDATE, SUMMARY, CUSTOM
}

/** Steps inside an [AutomationExecutionPlan]. */
@Serializable
enum class AutomationStepType {
    /** Gather data from local DB (tasks, events, metrics, etc.). */
    GATHER_CONTEXT,
    /** Send gathered context to LLM for processing. */
    LLM_PROCESS,
    /** Deliver the result (notification, task mutation, etc.). */
    OUTPUT
}
