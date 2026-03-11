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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.theveloper.aura.domain.model.TaskStatus
import com.theveloper.aura.domain.model.TaskType
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    onNavigateToTaskDetail: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedFilterKey by rememberSaveable { mutableStateOf(TaskFilter.ALL.key) }
    var pendingDeleteTask by remember { mutableStateOf<Task?>(null) }
    val selectedFilter = remember(selectedFilterKey) { TaskFilter.fromKey(selectedFilterKey) }
    val sortedTasks = remember(uiState.tasks) { uiState.tasks.sortedWith(taskBoardComparator()) }
    val visibleTasks = remember(sortedTasks, selectedFilter) { selectedFilter.apply(sortedTasks) }
    val activeCount = remember(uiState.tasks) { uiState.tasks.count { it.status == TaskStatus.ACTIVE } }
    val completedCount = remember(uiState.tasks) { uiState.tasks.count { it.status == TaskStatus.COMPLETED } }
    val dueSoonCount = remember(uiState.tasks) {
        uiState.tasks.count { it.status == TaskStatus.ACTIVE && it.isDueSoon() }
    }
    val ritualCount = remember(uiState.tasks) {
        uiState.tasks.count {
            it.status == TaskStatus.ACTIVE && (it.type == TaskType.HABIT || it.type == TaskType.HEALTH)
        }
    }
    val systemCount = remember(uiState.tasks) {
        uiState.tasks.count {
            it.status == TaskStatus.ACTIVE &&
                (it.type == TaskType.PROJECT || it.type == TaskType.FINANCE || it.type == TaskType.TRAVEL)
        }
    }

    pendingDeleteTask?.let { task ->
        AlertDialog(
            onDismissRequest = { pendingDeleteTask = null },
            title = { Text("Eliminar tarea") },
            text = { Text("Se va a eliminar \"${task.title}\" de forma permanente.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTask(task.id)
                    pendingDeleteTask = null
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteTask = null }) { Text("Cancelar") }
            }
        )
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
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

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = innerPadding.calculateTopPadding() + 80.dp,
                    end = 16.dp,
                    bottom = innerPadding.calculateBottomPadding() + 216.dp
                )
            ) {
                item {
                    TasksOverviewRow(
                        activeCount = activeCount,
                        completedCount = completedCount,
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
                    uiState.errorMessage != null && uiState.tasks.isEmpty() -> item {
                        TasksStatePanel("Couldn't load tasks", uiState.errorMessage.orEmpty())
                    }
                    uiState.tasks.isEmpty() -> item {
                        TasksStatePanel(
                            "No tasks yet",
                            "Use the quick prompt or the add circle below to make the first one."
                        )
                    }
                    visibleTasks.isEmpty() -> item {
                        TasksStatePanel("No matches", "Try a different filter or create a fresh task.")
                    }
                    else -> {
                        if (uiState.errorMessage != null) {
                            item { TasksStatePanel("Sync needs attention", uiState.errorMessage.orEmpty()) }
                        }
                        items(visibleTasks, key = { it.id }) { task ->
                            CompactTaskBoardCard(
                                task = task,
                                onClick = { onNavigateToTaskDetail(task.id) },
                                onCompleteClick = { viewModel.completeTask(task.id) },
                                onReopenClick = { viewModel.reopenTask(task.id) },
                                onDeleteClick = { pendingDeleteTask = task }
                            )
                        }
                    }
                }
            }

            HomeTopBar(modifier = Modifier.align(Alignment.TopCenter))
        }
    }
}

// ── Top Bar ──────────────────────────────────────────────────────────────────

@Composable
private fun HomeTopBar(modifier: Modifier = Modifier) {
    val backgroundColor = MaterialTheme.colorScheme.background
    val topBarBrush = remember(backgroundColor) {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to backgroundColor,
                0.56f to backgroundColor,
                0.78f to backgroundColor.copy(alpha = 0.92f),
                0.9f to backgroundColor.copy(alpha = 0.54f),
                1f to Color.Transparent
            )
        )
    }
    val auraWordmarkStyle = rememberAuraWordmarkStyle()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(topBarBrush)
            .padding(bottom = 30.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Aura",
                style = auraWordmarkStyle,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun rememberAuraWordmarkStyle(): TextStyle {
    return remember {
        TextStyle(
            fontFamily = FontFamily(
                Font(
                    resId = R.font.google_sans_flex_variable_local,
                    weight = FontWeight(760),
                    style = FontStyle.Normal,
                    loadingStrategy = FontLoadingStrategy.Blocking,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(636),
                        FontVariation.width(152f),
                        FontVariation.opticalSizing(30.sp),
                        FontVariation.grade(40),
                        FontVariation.Setting("ROND", 50f)
                    )
                )
            ),
            fontWeight = FontWeight(760),
            fontSize = 30.sp,
            lineHeight = 30.sp
        )
    }
}

// ── Task Board Components ─────────────────────────────────────────────────────

@Composable
private fun TasksOverviewRow(
    activeCount: Int,
    completedCount: Int,
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
        CompactMetricPill(label = "Active", value = activeCount.toString())
        CompactMetricPill(label = "Done", value = completedCount.toString())
        CompactMetricPill(label = "Due", value = dueSoonCount.toString())
        CompactMetricPill(label = "Rituals", value = ritualCount.toString())
        CompactMetricPill(label = "Systems", value = systemCount.toString())
    }
}

@Composable
private fun CompactMetricPill(label: String, value: String) {
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
private fun TaskFilterRow(selected: TaskFilter, onSelect: (TaskFilter) -> Unit) {
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
                color = if (active) MaterialTheme.colorScheme.inverseSurface
                        else MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (active) MaterialTheme.colorScheme.inverseSurface
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                ),
                modifier = Modifier.clickable { onSelect(filter) }
            ) {
                Text(
                    text = filter.label,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (active) MaterialTheme.colorScheme.inverseOnSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun CompactTaskBoardCard(
    task: Task,
    onClick: () -> Unit,
    onCompleteClick: () -> Unit,
    onReopenClick: () -> Unit,
    onDeleteClick: () -> Unit
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
                        text = task.boardSummaryLine(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TaskStatusChip(status = task.status)
                    Surface(shape = CircleShape, color = tone.container) {
                        Text(
                            text = task.type.label(),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = tone.content
                        )
                    }
                }
            }

            progress?.let { CompactTaskProgress(it) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                nextReminder?.let { timestamp ->
                    CompactMetaChip(icon = Icons.Rounded.Schedule, text = tasksRelativeReminder(timestamp))
                }
                CompactMetaChip(icon = Icons.Rounded.TaskAlt, text = "${task.components.size}")
                CompactMetaChip(icon = Icons.Rounded.AutoAwesome, text = task.updatedLabel())
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (task.status == TaskStatus.ACTIVE) {
                    FilledTonalButton(onClick = onCompleteClick, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.TaskAlt, null, modifier = Modifier.size(18.dp))
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(8.dp))
                        Text("Marcar completa")
                    }
                } else {
                    OutlinedButton(onClick = onReopenClick, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(18.dp))
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(8.dp))
                        Text("Reabrir")
                    }
                }
                OutlinedButton(onClick = onDeleteClick, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.DeleteOutline, null, modifier = Modifier.size(18.dp))
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(8.dp))
                    Text("Eliminar")
                }
            }
        }
    }
}

@Composable
private fun TaskStatusChip(status: TaskStatus) {
    val scheme = MaterialTheme.colorScheme
    val (container, content, label) = when (status) {
        TaskStatus.ACTIVE -> Triple(scheme.primaryContainer, scheme.onPrimaryContainer, "Activa")
        TaskStatus.COMPLETED -> Triple(scheme.tertiaryContainer, scheme.onTertiaryContainer, "Completada")
        TaskStatus.ARCHIVED -> Triple(scheme.surfaceContainerHighest, scheme.onSurfaceVariant, "Archivada")
    }
    Surface(shape = CircleShape, color = container) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = content
        )
    }
}

@Composable
internal fun CompactMetaChip(
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
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
            Text(text = text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CompactTaskProgress(progress: Float) {
    val safe = progress.coerceIn(0f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Momentum", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${(safe * 100f).roundToInt()}%", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
        }
        Box(modifier = Modifier.fillMaxWidth().height(7.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)) {
            Box(modifier = Modifier.fillMaxWidth(safe).height(7.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
        }
    }
}

@Composable
internal fun TasksStatePanel(title: String, body: String) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Shared Task Utilities ─────────────────────────────────────────────────────

internal data class TaskTone(val container: Color, val content: Color)

@Composable
internal fun taskTone(type: TaskType): TaskTone {
    val s = MaterialTheme.colorScheme
    return when (type) {
        TaskType.GENERAL -> TaskTone(s.secondaryContainer, s.onSecondaryContainer)
        TaskType.TRAVEL -> TaskTone(s.tertiaryContainer, s.onTertiaryContainer)
        TaskType.HABIT -> TaskTone(s.primaryContainer, s.onPrimaryContainer)
        TaskType.HEALTH -> TaskTone(s.errorContainer, s.onErrorContainer)
        TaskType.PROJECT -> TaskTone(s.surfaceContainerHighest, s.onSurface)
        TaskType.FINANCE -> TaskTone(s.inverseSurface, s.inverseOnSurface)
    }
}

internal fun TaskType.label(): String = when (this) {
    TaskType.GENERAL -> "General"
    TaskType.TRAVEL -> "Travel"
    TaskType.HABIT -> "Habit"
    TaskType.HEALTH -> "Health"
    TaskType.PROJECT -> "Project"
    TaskType.FINANCE -> "Finance"
}

internal enum class TaskFilter(val key: String, val label: String) {
    ALL("all", "All"),
    ACTIVE("active", "Active"),
    COMPLETED("completed", "Done"),
    DUE_SOON("due_soon", "Due soon"),
    RITUALS("rituals", "Rituals"),
    SYSTEMS("systems", "Systems");

    fun apply(tasks: List<Task>): List<Task> = when (this) {
        ALL -> tasks
        ACTIVE -> tasks.filter { it.status == TaskStatus.ACTIVE }
        COMPLETED -> tasks.filter { it.status == TaskStatus.COMPLETED }
        DUE_SOON -> tasks.filter { it.status == TaskStatus.ACTIVE && it.isDueSoon() }
        RITUALS -> tasks.filter { it.status == TaskStatus.ACTIVE && (it.type == TaskType.HABIT || it.type == TaskType.HEALTH) }
        SYSTEMS -> tasks.filter { it.status == TaskStatus.ACTIVE && (it.type == TaskType.PROJECT || it.type == TaskType.FINANCE || it.type == TaskType.TRAVEL) }
    }

    companion object {
        fun fromKey(key: String): TaskFilter = entries.firstOrNull { it.key == key } ?: ALL
    }
}

internal fun taskBoardComparator(): Comparator<Task> = compareBy(
    { !it.isDueSoon() },
    { it.nextTouchpoint() ?: Long.MAX_VALUE },
    { -it.updatedAt }
)

internal fun Task.isDueSoon(): Boolean {
    val now = System.currentTimeMillis()
    val threshold = now + 36L * 60L * 60L * 1000L
    return reminders.any { it.scheduledAt in now..threshold } ||
        (targetDate != null && targetDate in now..threshold)
}

internal fun Task.nextTouchpoint(): Long? = listOfNotNull(
    reminders.minByOrNull { it.scheduledAt }?.scheduledAt, targetDate
).minOrNull()

internal fun Task.progressRatio(): Float? {
    components.firstOrNull { it.type == ComponentType.PROGRESS_BAR }?.config
        ?.let { return (it as? ProgressBarConfig)?.manualProgress?.coerceIn(0f, 1f) }
    val checklist = components.firstOrNull { it.type == ComponentType.CHECKLIST }
    val total = checklist?.checklistItems?.size ?: 0
    if (total > 0) return (checklist?.checklistItems?.count { it.isCompleted } ?: 0).toFloat() / total
    return null
}

internal fun Task.updatedLabel(): String {
    val h = ((System.currentTimeMillis() - updatedAt) / 3_600_000L).coerceAtLeast(0L)
    return when { h < 1L -> "now"; h < 24L -> "${h}h"; else -> "${h / 24L}d" }
}

internal fun tasksRelativeReminder(timestamp: Long): String {
    val m = ((timestamp - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0L)
    return when { m < 60L -> "in ${m}m"; m < 1_440L -> "in ${m / 60L}h"; else -> "in ${m / 1_440L}d" }
}

private fun Task.boardSummaryLine(): String = when (type) {
    TaskType.TRAVEL -> {
        val cl = components.firstOrNull { it.type == ComponentType.CHECKLIST }
        "Countdown in play. Checklist at ${cl?.checklistItems?.count { it.isCompleted } ?: 0}/${cl?.checklistItems?.size ?: 0}."
    }
    TaskType.HABIT -> "Recurring rhythm with an active reminder cadence."
    TaskType.PROJECT -> {
        val p = components.firstOrNull { it.type == ComponentType.PROGRESS_BAR }?.config as? ProgressBarConfig
        "Manual progress at ${(((p?.manualProgress ?: 0f) * 100f).toInt())}%."
    }
    TaskType.HEALTH -> "Metrics and habits bundled into one health loop."
    TaskType.FINANCE -> "External data watchlist ready for quick checks."
    TaskType.GENERAL -> "Flexible task with notes, reminders and structure."
}
