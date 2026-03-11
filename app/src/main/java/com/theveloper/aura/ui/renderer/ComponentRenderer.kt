package com.theveloper.aura.ui.renderer

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
import com.theveloper.aura.domain.model.TaskComponent
import com.theveloper.aura.ui.components.ChecklistComponent
import com.theveloper.aura.ui.components.CountdownComponent
import com.theveloper.aura.ui.components.DataFeedComponent
import com.theveloper.aura.ui.components.HabitRingComponent
import com.theveloper.aura.ui.components.MetricTrackerComponent
import com.theveloper.aura.ui.components.NotesComponent
import com.theveloper.aura.ui.components.ProgressBarComponent

@Composable
fun ComponentRenderer(
    component: TaskComponent,
    onSignal: (SignalType) -> Unit,
    modifier: Modifier = Modifier
) {
    val config = component.config

    when {
        component.type == ComponentType.CHECKLIST && config is ChecklistConfig -> {
            ChecklistComponent(
                config = config,
                initialItems = component.checklistItems,
                onItemToggle = { _, _ -> }
            )
        }
        component.type == ComponentType.PROGRESS_BAR && config is ProgressBarConfig -> {
            ProgressBarComponent(config = config)
        }
        component.type == ComponentType.COUNTDOWN && config is CountdownConfig -> {
            CountdownComponent(config = config)
        }
        component.type == ComponentType.HABIT_RING && config is HabitRingConfig -> {
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
        component.type == ComponentType.NOTES && config is NotesConfig -> {
            NotesComponent(
                config = config,
                onSave = { }
            )
        }
        component.type == ComponentType.METRIC_TRACKER && config is MetricTrackerConfig -> {
            MetricTrackerComponent(
                config = config,
                currentValue = config.history.lastOrNull(),
                onSave = { }
            )
        }
        component.type == ComponentType.DATA_FEED && config is DataFeedConfig -> {
            DataFeedComponent(
                config = config,
                onRefresh = { }
            )
        }
        else -> {
            Text(
                text = "Unsupported component: ${component.type}",
                color = MaterialTheme.colorScheme.error,
                modifier = modifier.padding(vertical = 8.dp)
            )
        }
    }
}
