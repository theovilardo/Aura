package com.theveloper.aura.ui.renderer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.theveloper.aura.domain.model.ChecklistConfig
import com.theveloper.aura.domain.model.ComponentType
import com.theveloper.aura.domain.model.CountdownConfig
import com.theveloper.aura.domain.model.DataFeedConfig
import com.theveloper.aura.domain.model.HabitRingConfig
import com.theveloper.aura.domain.model.MetricTrackerConfig
import com.theveloper.aura.domain.model.NotesConfig
import com.theveloper.aura.domain.model.ProgressBarConfig
import com.theveloper.aura.domain.model.SignalType
import com.theveloper.aura.domain.model.Task
import com.theveloper.aura.domain.model.TaskComponent
import com.theveloper.aura.engine.skill.UiSkillRegistry
import com.theveloper.aura.ui.components.ChecklistComponent
import com.theveloper.aura.ui.components.CountdownComponent
import com.theveloper.aura.ui.components.DataFeedComponent
import com.theveloper.aura.ui.components.HabitRingComponent
import com.theveloper.aura.ui.components.InterpretedNotesComponent
import com.theveloper.aura.ui.components.MetricTrackerComponent
import com.theveloper.aura.ui.components.NotesComponent
import com.theveloper.aura.ui.components.ProgressBarComponent
import com.theveloper.aura.ui.screen.appendMetricTrackerValue
import com.theveloper.aura.ui.screen.updateChecklistItem
import com.theveloper.aura.ui.screen.updateHabitRingComponent
import com.theveloper.aura.ui.screen.updateNotesComponent

object UiSkillRendererRegistry {

    @Composable
    fun Render(
        component: TaskComponent,
        onSignal: (SignalType) -> Unit,
        modifier: Modifier = Modifier
    ) {
        val config = component.config
        val resolvedType = resolveType(component)

        when {
            resolvedType == ComponentType.CHECKLIST && config is ChecklistConfig -> {
                ChecklistComponent(
                    config = config,
                    initialItems = component.checklistItems,
                    onItemToggle = { _, _ -> }
                )
            }
            resolvedType == ComponentType.PROGRESS_BAR && config is ProgressBarConfig -> {
                ProgressBarComponent(config = config)
            }
            resolvedType == ComponentType.COUNTDOWN && config is CountdownConfig -> {
                CountdownComponent(config = config)
            }
            resolvedType == ComponentType.HABIT_RING && config is HabitRingConfig -> {
                HabitRingComponent(
                    config = config,
                    isCompletedToday = config.completedToday,
                    onToggle = { completed ->
                        if (completed) {
                            onSignal(SignalType.TASK_COMPLETED)
                        }
                    }
                )
            }
            resolvedType == ComponentType.NOTES && config is NotesConfig -> {
                NotesComponent(
                    config = config,
                    onSave = { }
                )
            }
            resolvedType == ComponentType.METRIC_TRACKER && config is MetricTrackerConfig -> {
                MetricTrackerComponent(
                    config = config,
                    currentValue = config.history.lastOrNull(),
                    onSave = { }
                )
            }
            resolvedType == ComponentType.DATA_FEED && config is DataFeedConfig -> {
                DataFeedComponent(config = config)
            }
            else -> UnsupportedUiSkillFallback(
                type = component.type,
                modifier = modifier
            )
        }
    }

    @Composable
    fun RenderInterpreted(
        component: TaskComponent,
        onSignal: (SignalType) -> Unit,
        onOpenNotes: (TaskComponent) -> Unit = {},
        modifier: Modifier = Modifier
    ) {
        val config = component.config
        val resolvedType = resolveType(component)

        when {
            resolvedType == ComponentType.CHECKLIST && config is ChecklistConfig -> {
                ChecklistComponent(
                    config = config,
                    initialItems = component.checklistItems,
                    onItemToggle = { _, _ -> }
                )
            }
            resolvedType == ComponentType.PROGRESS_BAR && config is ProgressBarConfig -> {
                ProgressBarComponent(config = config)
            }
            resolvedType == ComponentType.COUNTDOWN && config is CountdownConfig -> {
                CountdownComponent(config = config)
            }
            resolvedType == ComponentType.HABIT_RING && config is HabitRingConfig -> {
                HabitRingComponent(
                    config = config,
                    isCompletedToday = config.completedToday,
                    onToggle = { completed ->
                        if (completed) {
                            onSignal(SignalType.TASK_COMPLETED)
                        }
                    }
                )
            }
            resolvedType == ComponentType.NOTES && config is NotesConfig -> {
                InterpretedNotesComponent(
                    config = config,
                    modifier = modifier.clickable(onClick = { onOpenNotes(component) })
                )
            }
            resolvedType == ComponentType.METRIC_TRACKER && config is MetricTrackerConfig -> {
                MetricTrackerComponent(
                    config = config,
                    currentValue = config.history.lastOrNull(),
                    onSave = { }
                )
            }
            resolvedType == ComponentType.DATA_FEED && config is DataFeedConfig -> {
                DataFeedComponent(config = config)
            }
            else -> UnsupportedUiSkillFallback(
                type = component.type,
                modifier = modifier
            )
        }
    }

    @Composable
    fun RenderEditable(
        task: Task,
        component: TaskComponent,
        onTaskChange: (Task) -> Unit,
        onSignal: (SignalType) -> Unit,
        onEditNotes: (TaskComponent) -> Unit = {},
        modifier: Modifier = Modifier
    ) {
        val config = component.config
        val resolvedType = resolveType(component)

        when {
            resolvedType == ComponentType.CHECKLIST && config is ChecklistConfig -> {
                ChecklistComponent(
                    config = config,
                    initialItems = component.checklistItems,
                    onItemToggle = { item, checked ->
                        onTaskChange(
                            task.updateChecklistItem(
                                componentId = component.id,
                                updatedItem = item.copy(isCompleted = checked)
                            )
                        )
                    }
                )
            }
            resolvedType == ComponentType.PROGRESS_BAR && config is ProgressBarConfig -> {
                ProgressBarComponent(config = config)
            }
            resolvedType == ComponentType.COUNTDOWN && config is CountdownConfig -> {
                CountdownComponent(config = config)
            }
            resolvedType == ComponentType.HABIT_RING && config is HabitRingConfig -> {
                HabitRingComponent(
                    config = config,
                    isCompletedToday = config.completedToday,
                    onToggle = { completed ->
                        onTaskChange(task.updateHabitRingComponent(component.id, completed))
                        if (completed) {
                            onSignal(SignalType.TASK_COMPLETED)
                        }
                    }
                )
            }
            resolvedType == ComponentType.NOTES && config is NotesConfig -> {
                NotesComponent(
                    config = config,
                    onSave = { text ->
                        onTaskChange(task.updateNotesComponent(component.id, text))
                    },
                    onOpenEditor = { onEditNotes(component) },
                    saveDelayMillis = 0L
                )
            }
            resolvedType == ComponentType.METRIC_TRACKER && config is MetricTrackerConfig -> {
                MetricTrackerComponent(
                    config = config,
                    currentValue = config.history.lastOrNull(),
                    onSave = { value ->
                        onTaskChange(task.appendMetricTrackerValue(component.id, value))
                    }
                )
            }
            resolvedType == ComponentType.DATA_FEED && config is DataFeedConfig -> {
                DataFeedComponent(config = config)
            }
            else -> UnsupportedUiSkillFallback(
                type = component.type,
                modifier = modifier
            )
        }
    }

    private fun resolveType(component: TaskComponent): ComponentType? {
        return UiSkillRegistry.resolve(component.type)?.componentType ?: component.type
    }
}

@Composable
private fun UnsupportedUiSkillFallback(
    type: ComponentType,
    modifier: Modifier = Modifier
) {
    Text(
        text = "Unsupported UI skill: $type",
        color = MaterialTheme.colorScheme.error,
        modifier = modifier.padding(vertical = 8.dp)
    )
}
