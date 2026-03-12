package com.theveloper.aura.ui.renderer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theveloper.aura.domain.model.SignalType
import com.theveloper.aura.domain.model.Task
import com.theveloper.aura.domain.model.TaskStatus
import com.theveloper.aura.domain.model.TaskType
import com.theveloper.aura.ui.components.ComponentPill
import com.theveloper.aura.ui.components.EmptyStateView
import com.theveloper.aura.ui.components.ErrorStateView
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class TaskRenderMode(val key: String, val label: String) {
    INTERPRETED("interpreted", "Interpret"),
    EDIT("edit", "Edit");

    companion object {
        fun fromKey(key: String): TaskRenderMode = entries.firstOrNull { it.key == key } ?: INTERPRETED
    }
}

@Composable
fun TaskRenderer(
    task: Task?,
    isLoading: Boolean,
    mode: TaskRenderMode = TaskRenderMode.EDIT,
    modifier: Modifier = Modifier,
    listState: LazyListState? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onSignal: (SignalType) -> Unit = {}
) {
    when {
        isLoading -> {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
            }
        }
        task == null -> {
            ErrorStateView(
                title = "No encontramos la tarea",
                body = "Puede haber sido eliminada o todavía no terminó de cargarse.",
                modifier = modifier
            )
        }
        task.status == TaskStatus.ARCHIVED -> {
            EmptyStateView(
                title = "Tarea archivada",
                body = "Esta tarea ya no aparece como activa en la lista principal.",
                modifier = modifier
            )
        }
        task.components.isEmpty() -> {
            EmptyStateView(
                title = "Sin componentes",
                body = "La tarea existe, pero todavía no tiene widgets para renderizar.",
                modifier = modifier
            )
        }
        else -> {
            val resolvedListState = listState ?: rememberLazyListState()
            LazyColumn(
                state = resolvedListState,
                modifier = modifier.fillMaxWidth(),
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(if (mode == TaskRenderMode.INTERPRETED) 16.dp else 12.dp)
            ) {
                item {
                    when (mode) {
                        TaskRenderMode.INTERPRETED -> InterpretedTaskHeader(task = task)
                        TaskRenderMode.EDIT -> EditTaskHeader(task = task)
                    }
                }
                items(task.components.sortedBy { it.sortOrder }, key = { it.id }) { component ->
                    when (mode) {
                        TaskRenderMode.INTERPRETED -> InterpretedComponentRenderer(
                            component = component,
                            onSignal = onSignal
                        )
                        TaskRenderMode.EDIT -> ComponentRenderer(
                            component = component,
                            onSignal = onSignal
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EditableTaskRenderer(
    task: Task?,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    onTaskChange: (Task) -> Unit,
    onSignal: (SignalType) -> Unit = {}
) {
    when {
        isLoading -> {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
            }
        }
        task == null -> {
            ErrorStateView(
                title = "No encontramos la tarea",
                body = "Puede haber sido eliminada o todavía no terminó de cargarse.",
                modifier = modifier
            )
        }
        else -> {
            LazyColumn(
                modifier = modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    EditTaskHeader(task = task)
                }
                items(task.components.sortedBy { it.sortOrder }, key = { it.id }) { component ->
                    EditableComponentRenderer(
                        task = task,
                        component = component,
                        onTaskChange = onTaskChange,
                        onSignal = onSignal
                    )
                }
            }
        }
    }
}

@Composable
private fun EditTaskHeader(task: Task) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            AssistChip(
                onClick = {},
                label = { Text(task.type.name) },
                leadingIcon = {
                    Icon(
                        imageVector = typeIcon(task.type),
                        contentDescription = null
                    )
                }
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = task.title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                task.targetDate?.let { targetDate ->
                    AssistChip(
                        onClick = {},
                        label = { Text(formatDate(targetDate)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null
                            )
                        }
                    )
                }
                AssistChip(
                    onClick = {},
                    label = { Text("${task.components.size} blocks") }
                )
            }
        }
    }
}

@Composable
private fun InterpretedTaskHeader(task: Task) {
    val palette = interpretedTaskPalette(task.type)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = palette.container,
        border = BorderStroke(1.dp, palette.accent.copy(alpha = 0.24f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = palette.accent.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = typeIcon(task.type),
                            contentDescription = null,
                            tint = palette.accent
                        )
                        Text(
                            text = task.type.name.lowercase().replaceFirstChar { it.titlecase() },
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = palette.accent
                        )
                    }
                }

                ComponentPill(
                    text = if (task.status == TaskStatus.ACTIVE) "Live interface" else task.status.name.lowercase().replaceFirstChar { it.titlecase() }
                )
            }

            Text(
                text = task.title,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 34.sp,
                    lineHeight = 36.sp
                ),
                color = palette.content
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                task.targetDate?.let { targetDate ->
                    ComponentPill(formatDate(targetDate))
                }
                ComponentPill("${task.components.size} blocks")
            }
        }
    }
}

private data class InterpretedTaskPalette(
    val container: Color,
    val accent: Color,
    val content: Color
)

@Composable
private fun interpretedTaskPalette(type: TaskType): InterpretedTaskPalette {
    val scheme = MaterialTheme.colorScheme
    return when (type) {
        TaskType.HABIT -> InterpretedTaskPalette(
            container = scheme.primaryContainer.copy(alpha = 0.36f),
            accent = scheme.primary,
            content = scheme.onSurface
        )
        TaskType.HEALTH -> InterpretedTaskPalette(
            container = scheme.errorContainer.copy(alpha = 0.3f),
            accent = scheme.error,
            content = scheme.onSurface
        )
        TaskType.TRAVEL -> InterpretedTaskPalette(
            container = scheme.tertiaryContainer.copy(alpha = 0.34f),
            accent = scheme.tertiary,
            content = scheme.onSurface
        )
        TaskType.PROJECT -> InterpretedTaskPalette(
            container = scheme.surfaceContainerHigh,
            accent = scheme.primary,
            content = scheme.onSurface
        )
        TaskType.FINANCE -> InterpretedTaskPalette(
            container = scheme.secondaryContainer.copy(alpha = 0.3f),
            accent = scheme.secondary,
            content = scheme.onSurface
        )
        TaskType.GENERAL -> InterpretedTaskPalette(
            container = scheme.surfaceContainerLow,
            accent = scheme.primary,
            content = scheme.onSurface
        )
    }
}

private fun typeIcon(taskType: TaskType): ImageVector = when (taskType) {
    TaskType.TRAVEL -> Icons.Default.FlightTakeoff
    TaskType.HABIT -> Icons.Default.CheckCircle
    TaskType.HEALTH -> Icons.Default.Favorite
    TaskType.PROJECT -> Icons.Default.PieChart
    TaskType.FINANCE -> Icons.Default.Savings
    TaskType.GENERAL -> Icons.Default.CheckCircle
}

private fun formatDate(timestamp: Long): String {
    val formatter = DateTimeFormatter.ofPattern("d MMM")
    return formatter.format(
        Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
    )
}
