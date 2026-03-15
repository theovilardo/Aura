package com.theveloper.aura.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.theveloper.aura.domain.model.ComponentType
import com.theveloper.aura.domain.model.TaskShape
import com.theveloper.aura.domain.model.TaskType
import com.theveloper.aura.domain.model.toTaskShape
import com.theveloper.aura.engine.classifier.TaskGenerationResult
import com.theveloper.aura.engine.classifier.TaskGenerationSource
import com.theveloper.aura.engine.dsl.ChecklistDslItems
import com.theveloper.aura.engine.dsl.ComponentDSL
import com.theveloper.aura.ui.components.ComponentPill
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

@Composable
fun TaskPreviewBottomSheet(
    preview: TaskGenerationResult,
    isSaving: Boolean,
    onTaskTypeChange: (TaskType) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val dsl = preview.dsl
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.88f)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 116.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Preview",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "Check the structure before the engine creates the task.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onCancel,
                        enabled = !isSaving
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close preview"
                        )
                    }
                }
            }

            item {
                PreviewSummaryCard(preview = preview)
            }

            item {
                PreviewShapeSelector(
                    selectedTaskType = dsl.type,
                    isSaving = isSaving,
                    onTaskTypeChange = onTaskTypeChange
                )
            }

            if (preview.warnings.isNotEmpty()) {
                item {
                    GenerationWarningsCard(preview = preview)
                }
            }

            if (dsl.components.isNotEmpty()) {
                item {
                    Text(
                        text = "Components",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                itemsIndexed(dsl.components, key = { index, component ->
                    "${component.type}-${component.sortOrder}-$index"
                }) { _, component ->
                    PreviewComponentRow(component = component)
                }
            }

            if (dsl.reminders.isNotEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(26.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Alarm,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "${dsl.reminders.size} reminder${if (dsl.reminders.size == 1) "" else "s"}",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = dsl.reminders.minByOrNull { it.scheduledAtMs }?.scheduledAtMs
                                        ?.let { "First at ${formatDateTime(it)}" }
                                        ?: "Schedule pending",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            text = {
                Text(
                    text = if (isSaving) "Creating..." else "Create task",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                )
            },
            icon = {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null
                    )
                }
            },
            onClick = onConfirm,
            expanded = true,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    }
}

@Composable
private fun PreviewSummaryCard(preview: TaskGenerationResult) {
    val dsl = preview.dsl
    val taskShape = dsl.type.toTaskShape()
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = dsl.title,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Forma detectada: ${taskShape.displayName}. ${taskShape.shortDescription}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ComponentPill(taskShape.displayName)
                ComponentPill(preview.sourceLabel())
                ComponentPill("P${dsl.priority}")
                dsl.targetDateMs?.let {
                    ComponentPill(formatDate(it))
                }
            }
        }
    }
}

@Composable
private fun PreviewShapeSelector(
    selectedTaskType: TaskType,
    isSaving: Boolean,
    onTaskTypeChange: (TaskType) -> Unit
) {
    val selectedShape = selectedTaskType.toTaskShape()
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Task shape",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = selectedShape.shortDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TaskShape.userFacingOrder.forEach { shape ->
                    val active = shape.taskType == selectedTaskType
                    Surface(
                        shape = CircleShape,
                        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.44f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                        ),
                        modifier = Modifier
                            .clickable(enabled = !isSaving) { onTaskTypeChange(shape.taskType) }
                    ) {
                        Text(
                            text = shape.displayName,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GenerationWarningsCard(preview: TaskGenerationResult) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.28f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Text(
                    text = "Generation notes",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
            }
            preview.warnings.forEach { warning ->
                Text(
                    text = warning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PreviewComponentRow(component: ComponentDSL) {
    val metadataLine = componentMetadataLine(component)
    val badgeLabel = componentBadgeLabel(component)
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface
            ) {
                Icon(
                    imageVector = previewComponentIcon(component.type),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(10.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = component.type.displayName(),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = metadataLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ComponentPill(badgeLabel)
        }
    }
}

private fun componentMetadataLine(component: ComponentDSL): String {
    return when (component.type) {
        ComponentType.CHECKLIST -> {
            val items = ChecklistDslItems.parse(component.config)
            val suggestedCount = items.count { it.isSuggested }
            when {
                component.needsClarification -> "Items pending"
                items.isEmpty() -> "Empty list"
                suggestedCount > 0 -> "${items.size} items · $suggestedCount suggested"
                else -> "${items.size} items"
            }
        }

        ComponentType.COUNTDOWN -> {
            val targetDate = component.config["targetDate"]?.jsonPrimitive?.longOrNull ?: 0L
            if (targetDate > 0L) {
                "Target ${formatDate(targetDate)}"
            } else {
                "Date pending"
            }
        }

        else -> "Slot ${component.sortOrder + 1}"
    }
}

private fun componentBadgeLabel(component: ComponentDSL): String {
    if (component.needsClarification) {
        return "Needs info"
    }

    if (component.type == ComponentType.CHECKLIST) {
        val suggestedCount = ChecklistDslItems.parse(component.config).count { it.isSuggested }
        if (suggestedCount > 0) {
            return "Suggested"
        }
    }

    return when {
        component.populatedFromInput -> "Filled"
        else -> component.type.shortLabel()
    }
}

private fun previewComponentIcon(type: ComponentType): ImageVector = when (type) {
    ComponentType.CHECKLIST -> Icons.Rounded.DoneAll
    ComponentType.PROGRESS_BAR -> Icons.Rounded.DonutLarge
    ComponentType.COUNTDOWN -> Icons.Rounded.Timer
    ComponentType.HABIT_RING -> Icons.Rounded.Autorenew
    ComponentType.NOTES -> Icons.AutoMirrored.Rounded.Notes
    ComponentType.METRIC_TRACKER -> Icons.AutoMirrored.Rounded.ShowChart
    ComponentType.DATA_FEED -> Icons.Rounded.Sync
}

private fun TaskGenerationResult.sourceLabel(): String = when (source) {
    TaskGenerationSource.MANUAL -> "Manual"
    TaskGenerationSource.RULES -> "Basic guide"
    TaskGenerationSource.LOCAL_AI -> "Local AI"
    TaskGenerationSource.GROQ_API -> "Connected AI"
}

private fun TaskGenerationResult.summaryLine(): String {
    val taskDsl = dsl
    val taskShape = taskDsl.type.toTaskShape()
    val sourceSummary = when (source) {
        TaskGenerationSource.MANUAL -> "Built manually"
        TaskGenerationSource.RULES -> "Built from local rules"
        TaskGenerationSource.LOCAL_AI -> "Built with the local AI composer"
        TaskGenerationSource.GROQ_API -> "Refined with connected AI"
    }
    return "$sourceSummary. ${taskShape.displayName} with ${taskDsl.components.size} component${if (taskDsl.components.size == 1) "" else "s"}."
}

private fun ComponentType.displayName(): String = when (this) {
    ComponentType.CHECKLIST -> "Checklist"
    ComponentType.PROGRESS_BAR -> "Progress"
    ComponentType.COUNTDOWN -> "Countdown"
    ComponentType.HABIT_RING -> "Habit ring"
    ComponentType.NOTES -> "Notes"
    ComponentType.METRIC_TRACKER -> "Metric tracker"
    ComponentType.DATA_FEED -> "Data feed"
}

private fun ComponentType.shortLabel(): String = when (this) {
    ComponentType.CHECKLIST -> "List"
    ComponentType.PROGRESS_BAR -> "Track"
    ComponentType.COUNTDOWN -> "Time"
    ComponentType.HABIT_RING -> "Habit"
    ComponentType.NOTES -> "Text"
    ComponentType.METRIC_TRACKER -> "Metric"
    ComponentType.DATA_FEED -> "Live"
}

private fun formatDate(timestamp: Long): String {
    val formatter = DateTimeFormatter.ofPattern("d MMM")
    return formatter.format(
        Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
    )
}

private fun formatDateTime(timestamp: Long): String {
    val formatter = DateTimeFormatter.ofPattern("d MMM, HH:mm")
    return formatter.format(
        Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
    )
}
