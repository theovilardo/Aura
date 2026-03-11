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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.theveloper.aura.R
import com.theveloper.aura.domain.model.ComponentType
import com.theveloper.aura.domain.model.ProgressBarConfig
import com.theveloper.aura.domain.model.Task
import com.theveloper.aura.domain.model.TaskType
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
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
        uiState.tasks.count {
            it.type == TaskType.PROJECT || it.type == TaskType.FINANCE || it.type == TaskType.TRAVEL
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { TasksTopBar() }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = innerPadding.calculateTopPadding() + 10.dp,
                end = 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 216.dp
            )
        ) {
            item {
                TasksOverviewRow(
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

            when {
                uiState.errorMessage != null && uiState.tasks.isEmpty() -> {
                    item {
                        TasksStatePanel(
                            title = "Couldn’t load tasks",
                            body = uiState.errorMessage.orEmpty()
                        )
                    }
                }

                uiState.tasks.isEmpty() -> {
                    item {
                        TasksStatePanel(
                            title = "No tasks yet",
                            body = "Use the quick prompt or the add circle below to make the first one."
                        )
                    }
                }

                visibleTasks.isEmpty() -> {
                    item {
                        TasksStatePanel(
                            title = "No matches",
                            body = "Try a different filter or create a fresh task from the prompt bar."
                        )
                    }
                }

                else -> {
                    if (uiState.errorMessage != null) {
                        item {
                            TasksStatePanel(
                                title = "Sync needs attention",
                                body = uiState.errorMessage.orEmpty()
                            )
                        }
                    }

                    items(visibleTasks, key = { it.id }) { task ->
                        CompactTaskBoardCard(
                            task = task,
                            onClick = { onNavigateToTaskDetail(task.id) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TasksTopBar() {
    val titleStyle = rememberTasksTitleStyle()

    CenterAlignedTopAppBar(
        title = {
            Text(
                text = "Tasks",
                style = titleStyle,
                color = MaterialTheme.colorScheme.onBackground
            )
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background
        ),
        windowInsets = TopAppBarDefaults.windowInsets
    )
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun rememberTasksTitleStyle(): TextStyle {
    return remember {
        TextStyle(
            fontFamily = FontFamily(
                Font(
                    resId = R.font.google_sans_flex_variable_local,
                    weight = FontWeight(720),
                    style = FontStyle.Normal,
                    loadingStrategy = FontLoadingStrategy.Blocking,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(688),
                        FontVariation.width(134f),
                        FontVariation.opticalSizing(32.sp),
                        FontVariation.grade(26),
                        FontVariation.Setting("ROND", 22f)
                    )
                )
            ),
            fontWeight = FontWeight(720),
            fontSize = 32.sp,
            lineHeight = 32.sp
        )
    }
}

@Composable
private fun TasksOverviewRow(
    taskCount: Int,
    dueSoonCount: Int,
    ritualCount: Int,
    systemCount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CompactMetricPill(label = "Active", value = taskCount.toString())
        CompactMetricPill(label = "Due", value = dueSoonCount.toString())
        CompactMetricPill(label = "Rituals", value = ritualCount.toString())
        CompactMetricPill(label = "Systems", value = systemCount.toString())
    }
}

@Composable
private fun CompactMetricPill(
    label: String,
    value: String
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.58f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
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
                color = if (active) {
                    MaterialTheme.colorScheme.inverseSurface
                } else {
                    MaterialTheme.colorScheme.surface
                },
                border = BorderStroke(
                    width = 1.dp,
                    color = if (active) {
                        MaterialTheme.colorScheme.inverseSurface
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                    }
                ),
                modifier = Modifier.clickable { onSelect(filter) }
            ) {
                Text(
                    text = filter.label,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
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
private fun CompactTaskBoardCard(
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
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        shadowElevation = 3.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 21.sp,
                            lineHeight = 25.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = task.summaryLine(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = tone.container
                ) {
                    Text(
                        text = task.type.label(),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = tone.content
                    )
                }
            }

            progress?.let {
                CompactTaskProgress(progress = it)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                nextReminder?.let { timestamp ->
                    CompactMetaChip(
                        icon = Icons.Rounded.Schedule,
                        text = tasksRelativeReminder(timestamp)
                    )
                }
                CompactMetaChip(
                    icon = Icons.Rounded.TaskAlt,
                    text = "${task.components.size}"
                )
                CompactMetaChip(
                    icon = Icons.Rounded.AutoAwesome,
                    text = task.updatedLabel()
                )
            }
        }
    }
}

@Composable
private fun CompactMetaChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CompactTaskProgress(progress: Float) {
    val safeProgress = progress.coerceIn(0f, 1f)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                .height(7.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = CircleShape
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(safeProgress)
                    .height(7.dp)
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
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
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
        diffHours < 1L -> "now"
        diffHours < 24L -> "${diffHours}h"
        else -> "${diffHours / 24L}d"
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
