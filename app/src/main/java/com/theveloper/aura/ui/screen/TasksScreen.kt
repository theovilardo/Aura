package com.theveloper.aura.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.theveloper.aura.domain.model.ComponentType
import com.theveloper.aura.domain.model.ProgressBarConfig
import com.theveloper.aura.domain.model.Task
import com.theveloper.aura.domain.model.TaskType
import kotlin.math.roundToInt

@Composable
fun TasksScreen(
    onNavigateToTaskDetail: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedFilterKey by rememberSaveable { mutableStateOf(TaskFilter.ALL.key) }
    val selectedFilter = remember(selectedFilterKey) { TaskFilter.fromKey(selectedFilterKey) }
    val sortedTasks = remember(uiState.tasks) { uiState.tasks.sortedWith(taskBoardComparator()) }
    val visibleTasks = remember(sortedTasks, selectedFilter) { selectedFilter.apply(sortedTasks) }
    val dueSoonCount = remember(uiState.tasks) { uiState.tasks.count { it.isDueSoon() } }
    val ritualCount = remember(uiState.tasks) {
        uiState.tasks.count { it.type == TaskType.HABIT || it.type == TaskType.HEALTH }
    }
    val systemCount = remember(uiState.tasks) {
        uiState.tasks.count { it.type == TaskType.PROJECT || it.type == TaskType.FINANCE || it.type == TaskType.TRAVEL }
    }

    if (uiState.isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Text(
                    text = "Building your task board...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 144.dp)
    ) {
        item {
            TasksHero(
                taskCount = uiState.tasks.size,
                dueSoonCount = dueSoonCount,
                ritualCount = ritualCount,
                systemCount = systemCount
            )
        }
        item {
            TaskFilterRow(
                selected = selectedFilter,
                onSelect = { selectedFilterKey = it.key }
            )
        }

        if (uiState.errorMessage != null && uiState.tasks.isNotEmpty()) {
            item {
                TasksStatePanel(
                    title = "Sync needs attention",
                    body = uiState.errorMessage.orEmpty()
                )
            }
        }

        when {
            uiState.errorMessage != null && uiState.tasks.isEmpty() -> {
                item {
                    TasksStatePanel(
                        title = "We couldn't load your tasks",
                        body = uiState.errorMessage.orEmpty()
                    )
                }
            }

            uiState.tasks.isEmpty() -> {
                item {
                    TasksStatePanel(
                        title = "No tasks yet",
                        body = "Use the floating prompt bar or the plus circle in the bottom bar to create your first task."
                    )
                }
            }

            visibleTasks.isEmpty() -> {
                item {
                    TasksStatePanel(
                        title = "Nothing in ${selectedFilter.label.lowercase()}",
                        body = "Try another filter or let the quick prompt create a fresh task for this board."
                    )
                }
            }

            else -> {
                items(visibleTasks, key = { it.id }) { task ->
                    TaskBoardCard(
                        task = task,
                        onClick = { onNavigateToTaskDetail(task.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TasksHero(
    taskCount: Int,
    dueSoonCount: Int,
    ritualCount: Int,
    systemCount: Int
) {
    Surface(
        shape = RoundedCornerShape(34.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        shadowElevation = 6.dp
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                text = "TASK BOARD",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Everything you're running.",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 31.sp,
                        lineHeight = 35.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "This is your scan view: active work, near-term pressure, and the systems that keep moving. Creation now lives in the floating prompt below.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TasksMetricCard(
                    label = "Active",
                    value = taskCount.toString()
                )
                TasksMetricCard(
                    label = "Due soon",
                    value = dueSoonCount.toString()
                )
                TasksMetricCard(
                    label = "Systems",
                    value = maxOf(ritualCount, systemCount).toString()
                )
            }
        }
    }
}

@Composable
private fun RowScope.TasksMetricCard(
    label: String,
    value: String
) {
    Surface(
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TaskFilterRow(
    selected: TaskFilter,
    onSelect: (TaskFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TaskFilter.entries.forEach { filter ->
            val active = filter == selected
            Surface(
                shape = CircleShape,
                color = if (active) MaterialTheme.colorScheme.inverseSurface else MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (active) MaterialTheme.colorScheme.inverseSurface else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                ),
                modifier = Modifier.clickable { onSelect(filter) }
            ) {
                Text(
                    text = filter.label,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (active) {
                        MaterialTheme.colorScheme.inverseOnSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
private fun TaskBoardCard(
    task: Task,
    onClick: () -> Unit
) {
    val tone = taskTone(task.type)
    val nextReminder = task.reminders.minByOrNull { it.scheduledAt }?.scheduledAt
    val progress = task.progressRatio()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        shadowElevation = 5.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = tone.container
                ) {
                    Text(
                        text = task.type.label(),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = tone.content
                    )
                }

                nextReminder?.let { timestamp ->
                    MetaPill(
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                        },
                        text = tasksRelativeReminder(timestamp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 24.sp,
                        lineHeight = 30.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = task.summaryLine(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            progress?.let {
                TaskProgressTrack(progress = it)
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MetaPill(
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.TaskAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                    },
                    text = "${task.components.size} modules"
                )
                MetaPill(
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                    },
                    text = task.updatedLabel()
                )
            }
        }
    }
}

@Composable
private fun MetaPill(
    icon: @Composable () -> Unit,
    text: String
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TaskProgressTrack(progress: Float) {
    val safeProgress = progress.coerceIn(0f, 1f)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Momentum",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${(safeProgress * 100f).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = CircleShape
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(safeProgress)
                    .height(8.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
private fun TasksStatePanel(
    title: String,
    body: String
) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private enum class TaskFilter(val key: String, val label: String) {
    ALL("all", "All"),
    DUE_SOON("due_soon", "Due soon"),
    RITUALS("rituals", "Rituals"),
    SYSTEMS("systems", "Systems");

    fun apply(tasks: List<Task>): List<Task> {
        return when (this) {
            ALL -> tasks
            DUE_SOON -> tasks.filter { it.isDueSoon() }
            RITUALS -> tasks.filter { it.type == TaskType.HABIT || it.type == TaskType.HEALTH }
            SYSTEMS -> tasks.filter {
                it.type == TaskType.PROJECT || it.type == TaskType.FINANCE || it.type == TaskType.TRAVEL
            }
        }
    }

    companion object {
        fun fromKey(key: String): TaskFilter {
            return entries.firstOrNull { it.key == key } ?: ALL
        }
    }
}

private data class TaskTone(
    val container: androidx.compose.ui.graphics.Color,
    val content: androidx.compose.ui.graphics.Color
)

@Composable
private fun taskTone(type: TaskType): TaskTone {
    val scheme = MaterialTheme.colorScheme
    return when (type) {
        TaskType.GENERAL -> TaskTone(
            container = scheme.secondaryContainer,
            content = scheme.onSecondaryContainer
        )
        TaskType.TRAVEL -> TaskTone(
            container = scheme.tertiaryContainer,
            content = scheme.onTertiaryContainer
        )
        TaskType.HABIT -> TaskTone(
            container = scheme.primaryContainer,
            content = scheme.onPrimaryContainer
        )
        TaskType.HEALTH -> TaskTone(
            container = scheme.errorContainer,
            content = scheme.onErrorContainer
        )
        TaskType.PROJECT -> TaskTone(
            container = scheme.surfaceContainerHighest,
            content = scheme.onSurface
        )
        TaskType.FINANCE -> TaskTone(
            container = scheme.inverseSurface,
            content = scheme.inverseOnSurface
        )
    }
}

private fun taskBoardComparator(): Comparator<Task> {
    return compareBy<Task>(
        { !it.isDueSoon() },
        { it.nextTouchpoint() ?: Long.MAX_VALUE },
        { -it.updatedAt }
    )
}

private fun Task.isDueSoon(): Boolean {
    val now = System.currentTimeMillis()
    val soonThreshold = now + 36L * 60L * 60L * 1000L
    return reminders.any { it.scheduledAt in now..soonThreshold } ||
        (targetDate != null && targetDate in now..soonThreshold)
}

private fun Task.nextTouchpoint(): Long? {
    return listOfNotNull(
        reminders.minByOrNull { it.scheduledAt }?.scheduledAt,
        targetDate
    ).minOrNull()
}

private fun Task.progressRatio(): Float? {
    components.firstOrNull { it.type == ComponentType.PROGRESS_BAR }
        ?.config
        ?.let { config ->
            val progress = config as? ProgressBarConfig
            return progress?.manualProgress?.coerceIn(0f, 1f)
        }

    val checklist = components.firstOrNull { it.type == ComponentType.CHECKLIST }
    val checklistTotal = checklist?.checklistItems?.size ?: 0
    if (checklistTotal > 0) {
        val complete = checklist?.checklistItems?.count { it.isCompleted } ?: 0
        return complete.toFloat() / checklistTotal.toFloat()
    }

    return null
}

private fun Task.updatedLabel(): String {
    val diffHours = ((System.currentTimeMillis() - updatedAt) / 3_600_000L).coerceAtLeast(0L)
    return when {
        diffHours < 1L -> "updated now"
        diffHours < 24L -> "updated ${diffHours}h ago"
        else -> "updated ${diffHours / 24L}d ago"
    }
}

private fun TaskType.label(): String {
    return when (this) {
        TaskType.GENERAL -> "General"
        TaskType.TRAVEL -> "Travel"
        TaskType.HABIT -> "Habit"
        TaskType.HEALTH -> "Health"
        TaskType.PROJECT -> "Project"
        TaskType.FINANCE -> "Finance"
    }
}

private fun Task.summaryLine(): String {
    return when (type) {
        TaskType.TRAVEL -> {
            val checklist = components.firstOrNull { it.type == ComponentType.CHECKLIST }
            val done = checklist?.checklistItems?.count { it.isCompleted } ?: 0
            val total = checklist?.checklistItems?.size ?: 0
            "Countdown in play. Checklist at $done/$total."
        }
        TaskType.HABIT -> "Recurring rhythm with an active reminder cadence."
        TaskType.PROJECT -> {
            val progress = components.firstOrNull { it.type == ComponentType.PROGRESS_BAR }?.config as? ProgressBarConfig
            "Manual progress parked at ${(((progress?.manualProgress ?: 0f) * 100f).toInt())}%."
        }
        TaskType.HEALTH -> "Metrics and habits bundled into one health loop."
        TaskType.FINANCE -> "External data watchlist ready for quick checks."
        TaskType.GENERAL -> "Flexible task with notes, reminders and structure."
    }
}

private fun tasksRelativeReminder(timestamp: Long): String {
    val diffMinutes = ((timestamp - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0L)
    return when {
        diffMinutes < 60L -> "in ${diffMinutes}m"
        diffMinutes < 1_440L -> "in ${diffMinutes / 60L}h"
        else -> "in ${diffMinutes / 1_440L}d"
    }
}
