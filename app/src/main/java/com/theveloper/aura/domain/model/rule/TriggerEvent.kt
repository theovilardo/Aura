package com.theveloper.aura.domain.model.rule

enum class TriggerEvent {
    // Checklist
    CHECKLIST_ALL_CHECKED,
    CHECKLIST_ALL_UNCHECKED,
    CHECKLIST_ITEM_CHECKED,
    CHECKLIST_ITEM_UNCHECKED,
    CHECKLIST_PROGRESS_CHANGED,

    // Progress Bar
    PROGRESS_REACHED_100,
    PROGRESS_REACHED_VALUE,

    // Metric Tracker
    METRIC_UPDATED,
    METRIC_REACHED_GOAL,
    METRIC_EXCEEDED_GOAL,

    // Habit Ring
    HABIT_COMPLETED_TODAY,
    HABIT_STREAK_REACHED,

    // Countdown / Timer
    TIMER_COMPLETED,

    // Task
    TASK_COMPLETED
}
