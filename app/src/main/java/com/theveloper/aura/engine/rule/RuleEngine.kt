package com.theveloper.aura.engine.rule

import android.util.Log
import com.theveloper.aura.domain.model.ChecklistConfig
import com.theveloper.aura.domain.model.ComponentType
import com.theveloper.aura.domain.model.HabitRingConfig
import com.theveloper.aura.domain.model.MetricTrackerConfig
import com.theveloper.aura.domain.model.ProgressBarConfig
import com.theveloper.aura.domain.model.Task
import com.theveloper.aura.domain.model.TaskComponent
import com.theveloper.aura.domain.model.TaskStatus
import com.theveloper.aura.domain.model.rule.ComponentRule
import com.theveloper.aura.domain.model.rule.ConditionOperator
import com.theveloper.aura.domain.model.rule.RuleAction
import com.theveloper.aura.domain.model.rule.TriggerCondition
import com.theveloper.aura.domain.model.rule.TriggerEvent
import com.theveloper.aura.domain.repository.ComponentRuleRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuleEngine @Inject constructor(
    private val ruleRepository: ComponentRuleRepository
) {

    /**
     * Async version: loads rules from DB and processes the event.
     */
    suspend fun processEvent(task: Task, event: RuleEvent): Task {
        val rules = ruleRepository.getRulesForComponent(event.sourceComponentId)
        return processEventWithRules(task, event, rules, depth = 0)
    }

    /**
     * Synchronous version: uses pre-loaded [allRules] for the task.
     * Call this from the UI layer where rules are cached in the ViewModel.
     */
    fun processEventSync(task: Task, event: RuleEvent, allRules: List<ComponentRule>): Task {
        return processEventWithRules(task, event, allRules, depth = 0)
    }

    /**
     * Detect what changed between [oldTask] and [newTask] for a specific [componentId],
     * then process all resulting events through the rules. Returns the final task.
     */
    fun applyRulesForChange(
        oldTask: Task,
        newTask: Task,
        componentId: String,
        allRules: List<ComponentRule>
    ): Task {
        val events = detectEventsFromChange(oldTask, newTask, componentId)
        if (events.isEmpty()) return newTask

        var result = newTask
        for (event in events) {
            result = processEventWithRules(result, event, allRules, depth = 0)
        }
        return result
    }

    // ── Internal processing ──────────────────────────────────────────

    private fun processEventWithRules(
        task: Task,
        event: RuleEvent,
        allRules: List<ComponentRule>,
        depth: Int
    ): Task {
        if (depth >= MAX_CHAIN_DEPTH) {
            Log.w(TAG, "Max chain depth reached, stopping rule chain")
            return task
        }

        val matchingRules = allRules
            .filter { it.triggerComponentId == event.sourceComponentId }
            .filter { it.triggerEvent == event.type }
            .filter { evaluateCondition(it.triggerCondition, event.data) }
            .sortedBy { it.priority }

        var currentTask = task
        for (rule in matchingRules) {
            currentTask = executeAction(currentTask, rule, allRules, depth)
        }

        return currentTask
    }

    private fun executeAction(
        task: Task,
        rule: ComponentRule,
        allRules: List<ComponentRule>,
        depth: Int
    ): Task {
        val targetId = rule.targetComponentId
        val params = rule.actionParams

        val modifiedTask = when (rule.action) {
            RuleAction.RESET_CHECKLIST -> task.resetChecklist(targetId)
            RuleAction.COMPLETE_CHECKLIST -> task.completeChecklist(targetId)
            RuleAction.SET_PROGRESS_VALUE -> {
                val value = params["value"]?.toFloatOrNull() ?: return task
                task.setProgressValue(targetId, value)
            }
            RuleAction.RESET_PROGRESS -> task.setProgressValue(targetId, 0f)
            RuleAction.UPDATE_METRIC -> {
                val value = params["value"]?.toFloatOrNull() ?: return task
                task.appendMetricValue(targetId, value)
            }
            RuleAction.COMPLETE_HABIT_RING -> task.setHabitRingCompleted(targetId, true)
            RuleAction.RESET_HABIT_RING -> task.setHabitRingCompleted(targetId, false)
            RuleAction.SHOW_COMPONENT -> task
            RuleAction.HIDE_COMPONENT -> task
            RuleAction.MARK_TASK_COMPLETE -> task.copy(status = TaskStatus.COMPLETED)
        }

        // Chain: if an action changed something, fire follow-up events
        if (modifiedTask != task) {
            val chainEvents = detectEventsFromChange(task, modifiedTask, targetId)
            var chainedTask = modifiedTask
            for (chainEvent in chainEvents) {
                chainedTask = processEventWithRules(chainedTask, chainEvent, allRules, depth + 1)
            }
            return chainedTask
        }

        return modifiedTask
    }

    // ── Event detection ──────────────────────────────────────────────

    /**
     * Compare old and new task to detect what events a component change produced.
     */
    fun detectEventsFromChange(
        oldTask: Task,
        newTask: Task,
        componentId: String
    ): List<RuleEvent> {
        val events = mutableListOf<RuleEvent>()
        val oldComponent = oldTask.components.find { it.id == componentId } ?: return events
        val newComponent = newTask.components.find { it.id == componentId } ?: return events
        val taskId = newTask.id

        val oldConfig = oldComponent.config
        val newConfig = newComponent.config

        // Checklist events
        if (oldConfig is ChecklistConfig && newConfig is ChecklistConfig) {
            val oldAllChecked = oldComponent.checklistItems.isNotEmpty() &&
                oldComponent.checklistItems.all { it.isCompleted }
            val newAllChecked = newComponent.checklistItems.isNotEmpty() &&
                newComponent.checklistItems.all { it.isCompleted }
            val newAllUnchecked = newComponent.checklistItems.isNotEmpty() &&
                newComponent.checklistItems.none { it.isCompleted }
            val oldAllUnchecked = oldComponent.checklistItems.isNotEmpty() &&
                oldComponent.checklistItems.none { it.isCompleted }

            if (!oldAllChecked && newAllChecked) {
                events.add(RuleEvents.checklistAllChecked(componentId, taskId))
            }
            if (!oldAllUnchecked && newAllUnchecked) {
                events.add(RuleEvents.checklistAllUnchecked(componentId, taskId))
            }

            // Individual item events
            for (newItem in newComponent.checklistItems) {
                val oldItem = oldComponent.checklistItems.find { it.id == newItem.id }
                if (oldItem != null && oldItem.isCompleted != newItem.isCompleted) {
                    if (newItem.isCompleted) {
                        events.add(RuleEvents.checklistItemChecked(componentId, taskId, newItem.id))
                    }
                }
            }

            // Progress changed
            val oldProgress = if (oldComponent.checklistItems.isEmpty()) 0f
            else oldComponent.checklistItems.count { it.isCompleted }.toFloat() / oldComponent.checklistItems.size
            val newProgress = if (newComponent.checklistItems.isEmpty()) 0f
            else newComponent.checklistItems.count { it.isCompleted }.toFloat() / newComponent.checklistItems.size
            if (oldProgress != newProgress) {
                events.add(RuleEvents.checklistProgressChanged(componentId, taskId, newProgress * 100))
            }
        }

        // Habit Ring events
        if (oldConfig is HabitRingConfig && newConfig is HabitRingConfig) {
            if (!oldConfig.completedToday && newConfig.completedToday) {
                events.add(RuleEvents.habitCompletedToday(componentId, taskId))
            }
        }

        // Progress Bar events
        if (oldConfig is ProgressBarConfig && newConfig is ProgressBarConfig) {
            val oldProgress = oldConfig.manualProgress ?: 0f
            val newProgress = newConfig.manualProgress ?: 0f
            if (oldProgress < 100f && newProgress >= 100f) {
                events.add(RuleEvents.progressReached100(componentId, taskId))
            }
        }

        // Metric Tracker events
        if (oldConfig is MetricTrackerConfig && newConfig is MetricTrackerConfig) {
            if (newConfig.history.size > oldConfig.history.size) {
                val latestValue = newConfig.history.last()
                events.add(RuleEvents.metricUpdated(componentId, taskId, latestValue))

                val goal = newConfig.goal
                if (goal != null && latestValue >= goal) {
                    events.add(RuleEvents.metricReachedGoal(componentId, taskId, latestValue))
                }
            }
        }

        return events
    }

    // ── Condition evaluation ─────────────────────────────────────────

    private fun evaluateCondition(
        condition: TriggerCondition?,
        data: Map<String, Any>
    ): Boolean {
        if (condition == null) return true

        val rawValue = data[condition.field] ?: return false

        return try {
            val eventValue = rawValue.toString().toDouble()
            val conditionValue = condition.value.toDouble()

            when (condition.operator) {
                ConditionOperator.EQUALS -> eventValue == conditionValue
                ConditionOperator.NOT_EQUALS -> eventValue != conditionValue
                ConditionOperator.GREATER_THAN -> eventValue > conditionValue
                ConditionOperator.GREATER_THAN_OR_EQUAL -> eventValue >= conditionValue
                ConditionOperator.LESS_THAN -> eventValue < conditionValue
                ConditionOperator.LESS_THAN_OR_EQUAL -> eventValue <= conditionValue
                ConditionOperator.MULTIPLE_OF -> conditionValue != 0.0 && eventValue % conditionValue == 0.0
            }
        } catch (_: NumberFormatException) {
            when (condition.operator) {
                ConditionOperator.EQUALS -> rawValue.toString() == condition.value
                ConditionOperator.NOT_EQUALS -> rawValue.toString() != condition.value
                else -> false
            }
        }
    }

    companion object {
        private const val TAG = "RuleEngine"
        private const val MAX_CHAIN_DEPTH = 5
    }
}

// ── Task transformation helpers ──────────────────────────────────────

private fun Task.updateComponent(
    componentId: String,
    transform: (TaskComponent) -> TaskComponent
): Task = copy(
    components = components.map { if (it.id == componentId) transform(it) else it }
)

private fun Task.resetChecklist(componentId: String): Task =
    updateComponent(componentId) { component ->
        component.copy(
            checklistItems = component.checklistItems.map { it.copy(isCompleted = false) }
        )
    }

private fun Task.completeChecklist(componentId: String): Task =
    updateComponent(componentId) { component ->
        component.copy(
            checklistItems = component.checklistItems.map { it.copy(isCompleted = true) }
        )
    }

private fun Task.setProgressValue(componentId: String, value: Float): Task =
    updateComponent(componentId) { component ->
        val config = component.config as? ProgressBarConfig ?: return@updateComponent component
        component.copy(config = config.copy(manualProgress = value))
    }

private fun Task.appendMetricValue(componentId: String, value: Float): Task =
    updateComponent(componentId) { component ->
        val config = component.config as? MetricTrackerConfig ?: return@updateComponent component
        component.copy(config = config.copy(history = (config.history + value).takeLast(30)))
    }

private fun Task.setHabitRingCompleted(componentId: String, completed: Boolean): Task =
    updateComponent(componentId) { component ->
        val config = component.config as? HabitRingConfig ?: return@updateComponent component
        component.copy(config = config.copy(completedToday = completed))
    }
