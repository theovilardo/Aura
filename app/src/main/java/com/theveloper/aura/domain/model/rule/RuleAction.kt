package com.theveloper.aura.domain.model.rule

enum class RuleAction {
    // Checklist
    RESET_CHECKLIST,
    COMPLETE_CHECKLIST,

    // Progress Bar
    SET_PROGRESS_VALUE,
    RESET_PROGRESS,

    // Metric Tracker
    UPDATE_METRIC,

    // Habit Ring
    COMPLETE_HABIT_RING,
    RESET_HABIT_RING,

    // Visibility
    SHOW_COMPONENT,
    HIDE_COMPONENT,

    // Task
    MARK_TASK_COMPLETE
}
