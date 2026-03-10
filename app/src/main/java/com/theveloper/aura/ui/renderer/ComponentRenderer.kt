package com.theveloper.aura.ui.renderer

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.theveloper.aura.domain.model.*
import com.theveloper.aura.ui.components.*

@Composable
fun ComponentRenderer(
    component: TaskComponent,
    modifier: Modifier = Modifier
) {
    val config = component.config
    
    when {
        component.type == ComponentType.CHECKLIST && config is ChecklistConfig -> {
            ChecklistComponent(
                config = config,
                initialItems = emptyList(), // Real items fetch omitted for MVP mock
                onItemToggle = { _, _ -> }
            )
        }
        component.type == ComponentType.PROGRESS_BAR && config is ProgressBarConfig -> {
            ProgressBarComponent(
                config = config,
                progress = 0.45f // mock data
            )
        }
        component.type == ComponentType.COUNTDOWN && config is CountdownConfig -> {
            CountdownComponent(
                config = config
            )
        }
        component.type == ComponentType.HABIT_RING && config is HabitRingConfig -> {
            HabitRingComponent(
                config = config,
                isCompletedToday = false,
                onToggle = { }
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
                currentValue = null,
                onSave = { }
            )
        }
        component.type == ComponentType.DATA_FEED && config is DataFeedConfig -> {
            DataFeedComponent(
                config = config
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
