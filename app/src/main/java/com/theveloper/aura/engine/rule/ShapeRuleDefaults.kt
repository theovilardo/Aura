package com.theveloper.aura.engine.rule

import com.theveloper.aura.domain.model.ComponentType
import com.theveloper.aura.domain.model.TaskShape
import com.theveloper.aura.domain.model.rule.ComponentRule
import com.theveloper.aura.domain.model.rule.RuleAction
import com.theveloper.aura.domain.model.rule.TriggerEvent

object ShapeRuleDefaults {

    /**
     * Returns default rules for a shape, given the real component IDs.
     * [componentIds] maps ComponentType → component ID for the created components.
     */
    fun getRulesForShape(
        taskId: String,
        shape: TaskShape,
        componentIds: Map<ComponentType, String>
    ): List<ComponentRule> = when (shape) {
        TaskShape.HABIT -> buildHabitRules(taskId, componentIds)
        TaskShape.PROJECT -> buildProjectRules(taskId, componentIds)
        TaskShape.TRAVEL -> buildTravelRules(taskId, componentIds)
        TaskShape.HEALTH -> buildHealthRules(taskId, componentIds)
        TaskShape.GOAL -> buildGoalRules(taskId, componentIds)
        TaskShape.FINANCE -> buildFinanceRules(taskId, componentIds)
        TaskShape.EVENT, TaskShape.NOTE -> emptyList()
    }

    private fun buildHabitRules(
        taskId: String,
        ids: Map<ComponentType, String>
    ): List<ComponentRule> {
        val habitRingId = ids[ComponentType.HABIT_RING] ?: return emptyList()

        return buildList {
            // If there's a checklist of habit steps → completing all marks the ring done
            val checklistId = ids[ComponentType.CHECKLIST]
            if (checklistId != null) {
                add(
                    ComponentRule(
                        taskId = taskId,
                        triggerComponentId = checklistId,
                        triggerEvent = TriggerEvent.CHECKLIST_ALL_CHECKED,
                        targetComponentId = habitRingId,
                        action = RuleAction.COMPLETE_HABIT_RING,
                        priority = 0
                    )
                )
            }

            // If there's a metric tracker → reaching goal completes the ring
            val metricId = ids[ComponentType.METRIC_TRACKER]
            if (metricId != null) {
                add(
                    ComponentRule(
                        taskId = taskId,
                        triggerComponentId = metricId,
                        triggerEvent = TriggerEvent.METRIC_REACHED_GOAL,
                        targetComponentId = habitRingId,
                        action = RuleAction.COMPLETE_HABIT_RING,
                        priority = 1
                    )
                )
            }
        }
    }

    private fun buildProjectRules(
        taskId: String,
        ids: Map<ComponentType, String>
    ): List<ComponentRule> {
        val checklistId = ids[ComponentType.CHECKLIST] ?: return emptyList()
        val progressBarId = ids[ComponentType.PROGRESS_BAR] ?: return emptyList()

        return listOf(
            // Completing all checklist items → progress bar 100%
            ComponentRule(
                taskId = taskId,
                triggerComponentId = checklistId,
                triggerEvent = TriggerEvent.CHECKLIST_ALL_CHECKED,
                targetComponentId = progressBarId,
                action = RuleAction.SET_PROGRESS_VALUE,
                actionParams = mapOf("value" to "100"),
                priority = 0
            )
        )
    }

    private fun buildTravelRules(
        taskId: String,
        ids: Map<ComponentType, String>
    ): List<ComponentRule> {
        val checklistId = ids[ComponentType.CHECKLIST] ?: return emptyList()

        return buildList {
            val progressBarId = ids[ComponentType.PROGRESS_BAR]
            if (progressBarId != null) {
                add(
                    ComponentRule(
                        taskId = taskId,
                        triggerComponentId = checklistId,
                        triggerEvent = TriggerEvent.CHECKLIST_ALL_CHECKED,
                        targetComponentId = progressBarId,
                        action = RuleAction.SET_PROGRESS_VALUE,
                        actionParams = mapOf("value" to "100"),
                        priority = 0
                    )
                )
            }
        }
    }

    private fun buildHealthRules(
        taskId: String,
        ids: Map<ComponentType, String>
    ): List<ComponentRule> {
        val metricId = ids[ComponentType.METRIC_TRACKER] ?: return emptyList()

        return buildList {
            val progressBarId = ids[ComponentType.PROGRESS_BAR]
            if (progressBarId != null) {
                add(
                    ComponentRule(
                        taskId = taskId,
                        triggerComponentId = metricId,
                        triggerEvent = TriggerEvent.METRIC_REACHED_GOAL,
                        targetComponentId = progressBarId,
                        action = RuleAction.SET_PROGRESS_VALUE,
                        actionParams = mapOf("value" to "100"),
                        priority = 0
                    )
                )
            }
            val habitRingId = ids[ComponentType.HABIT_RING]
            if (habitRingId != null) {
                add(
                    ComponentRule(
                        taskId = taskId,
                        triggerComponentId = metricId,
                        triggerEvent = TriggerEvent.METRIC_REACHED_GOAL,
                        targetComponentId = habitRingId,
                        action = RuleAction.COMPLETE_HABIT_RING,
                        priority = 1
                    )
                )
            }
        }
    }

    private fun buildGoalRules(
        taskId: String,
        ids: Map<ComponentType, String>
    ): List<ComponentRule> {
        val checklistId = ids[ComponentType.CHECKLIST] ?: return emptyList()
        val progressBarId = ids[ComponentType.PROGRESS_BAR] ?: return emptyList()

        return listOf(
            ComponentRule(
                taskId = taskId,
                triggerComponentId = checklistId,
                triggerEvent = TriggerEvent.CHECKLIST_ALL_CHECKED,
                targetComponentId = progressBarId,
                action = RuleAction.SET_PROGRESS_VALUE,
                actionParams = mapOf("value" to "100"),
                priority = 0
            )
        )
    }

    private fun buildFinanceRules(
        taskId: String,
        ids: Map<ComponentType, String>
    ): List<ComponentRule> {
        val metricId = ids[ComponentType.METRIC_TRACKER] ?: return emptyList()

        return buildList {
            val progressBarId = ids[ComponentType.PROGRESS_BAR]
            if (progressBarId != null) {
                add(
                    ComponentRule(
                        taskId = taskId,
                        triggerComponentId = metricId,
                        triggerEvent = TriggerEvent.METRIC_REACHED_GOAL,
                        targetComponentId = progressBarId,
                        action = RuleAction.SET_PROGRESS_VALUE,
                        actionParams = mapOf("value" to "100"),
                        priority = 0
                    )
                )
            }
        }
    }
}
