package com.theveloper.aura.engine.rule

import com.theveloper.aura.domain.model.rule.TriggerEvent

data class RuleEvent(
    val sourceComponentId: String,
    val taskId: String,
    val type: TriggerEvent,
    val data: Map<String, Any> = emptyMap()
)

object RuleEvents {

    fun checklistAllChecked(componentId: String, taskId: String) = RuleEvent(
        sourceComponentId = componentId,
        taskId = taskId,
        type = TriggerEvent.CHECKLIST_ALL_CHECKED
    )

    fun checklistAllUnchecked(componentId: String, taskId: String) = RuleEvent(
        sourceComponentId = componentId,
        taskId = taskId,
        type = TriggerEvent.CHECKLIST_ALL_UNCHECKED
    )

    fun checklistItemChecked(componentId: String, taskId: String, itemId: String) = RuleEvent(
        sourceComponentId = componentId,
        taskId = taskId,
        type = TriggerEvent.CHECKLIST_ITEM_CHECKED,
        data = mapOf("itemId" to itemId)
    )

    fun checklistProgressChanged(componentId: String, taskId: String, progress: Float) = RuleEvent(
        sourceComponentId = componentId,
        taskId = taskId,
        type = TriggerEvent.CHECKLIST_PROGRESS_CHANGED,
        data = mapOf("progress" to progress)
    )

    fun progressReached100(componentId: String, taskId: String) = RuleEvent(
        sourceComponentId = componentId,
        taskId = taskId,
        type = TriggerEvent.PROGRESS_REACHED_100
    )

    fun metricUpdated(componentId: String, taskId: String, value: Float) = RuleEvent(
        sourceComponentId = componentId,
        taskId = taskId,
        type = TriggerEvent.METRIC_UPDATED,
        data = mapOf("value" to value)
    )

    fun metricReachedGoal(componentId: String, taskId: String, value: Float) = RuleEvent(
        sourceComponentId = componentId,
        taskId = taskId,
        type = TriggerEvent.METRIC_REACHED_GOAL,
        data = mapOf("value" to value)
    )

    fun habitCompletedToday(componentId: String, taskId: String) = RuleEvent(
        sourceComponentId = componentId,
        taskId = taskId,
        type = TriggerEvent.HABIT_COMPLETED_TODAY
    )

    fun timerCompleted(componentId: String, taskId: String) = RuleEvent(
        sourceComponentId = componentId,
        taskId = taskId,
        type = TriggerEvent.TIMER_COMPLETED
    )
}
