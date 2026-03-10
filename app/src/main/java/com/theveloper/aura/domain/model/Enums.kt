package com.theveloper.aura.domain.model

enum class TaskType {
    GENERAL, TRAVEL, HABIT, HEALTH, PROJECT, FINANCE
}

enum class TaskStatus {
    ACTIVE, COMPLETED, ARCHIVED
}

enum class ComponentType {
    CHECKLIST,
    PROGRESS_BAR,
    COUNTDOWN,
    HABIT_RING,
    NOTES,
    METRIC_TRACKER,
    DATA_FEED
}

enum class SignalType {
    TASK_COMPLETED,
    REMINDER_SNOOZED,
    REMINDER_DISMISSED
}

enum class FetcherType {
    WEATHER,
    CURRENCY_EXCHANGE,
    FLIGHT_PRICES
}

enum class SuggestionType {
    RESCHEDULE_REMINDER,
    SIMPLIFY_TASK,
    RESURRECT_TASK
}

enum class NotificationChannel {
    REMINDERS,
    SUGGESTIONS
}
