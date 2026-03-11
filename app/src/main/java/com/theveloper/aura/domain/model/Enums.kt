package com.theveloper.aura.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class TaskType {
    GENERAL, TRAVEL, HABIT, HEALTH, PROJECT, FINANCE
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
    DATA_FEED
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
enum class SuggestionStatus {
    PENDING, APPROVED, REJECTED, EXPIRED
}
