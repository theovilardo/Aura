package com.theveloper.aura.ui.renderer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.theveloper.aura.domain.model.SignalType
import com.theveloper.aura.domain.model.Task
import com.theveloper.aura.domain.model.TaskStatus
import com.theveloper.aura.domain.model.TaskType
import com.theveloper.aura.ui.components.EmptyStateView
import com.theveloper.aura.ui.components.ErrorStateView
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TaskRenderer(
    task: Task?,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
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
            LazyColumn(
                modifier = modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    TaskHeader(task = task)
                }
                items(task.components.sortedBy { it.sortOrder }, key = { it.id }) { component ->
                    ComponentRenderer(
                        component = component,
                        onSignal = onSignal
                    )
                }
            }
        }
    }
}

@Composable
fun TaskHeader(task: Task) {
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
                AssistChip(
                    onClick = {},
                    label = { Text("P${task.priority}") }
                )
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
            }
        }

        IconButton(onClick = { }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
