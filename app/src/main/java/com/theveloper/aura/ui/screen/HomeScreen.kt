package com.theveloper.aura.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.FlightTakeoff
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.NoteAlt
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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

private val HomeTopBarFallbackHeight = 152.dp
private val HomeTopBarContentOverlap = 30.dp
private val HomeTopBarMinContentPadding = 110.dp

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
    var topBarHeightPx by remember { mutableIntStateOf(0) }
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
            val topContentPadding = homeTopBarContentPadding(topBarHeightPx)
            val topListPadding =
                (innerPadding.calculateTopPadding() + topContentPadding - 14.dp).coerceAtLeast(0.dp)

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = topListPadding,
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

            HomeTopBar(
                selectedFilter = selectedFilter,
                onSelectFilter = { selectedFilterKey = it.key },
                modifier = Modifier.align(Alignment.TopCenter),
                onHeightChanged = { topBarHeightPx = it }
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .height(78.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
            ) {

            }
        }
    }
}

// ── Top Bar ──────────────────────────────────────────────────────────────────

@Composable
private fun HomeTopBar(
    selectedFilter: TaskFilter,
    onSelectFilter: (TaskFilter) -> Unit,
    modifier: Modifier = Modifier,
    onHeightChanged: (Int) -> Unit = {}
) {
    val auraWordmarkStyle = rememberAuraWordmarkStyle()

    AuraGradientTopBarContainer(
        modifier = modifier
            .fillMaxWidth(),
        style = AuraGradientTopBarStyle.Extended,
        bottomFadePadding = 12.dp,
        onHeightChanged = onHeightChanged
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 12.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Aura",
                    style = auraWordmarkStyle,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            TaskFilterRow(
                selected = selectedFilter,
                onSelect = onSelectFilter,
            )
        }
    }
}

@Composable
private fun homeTopBarContentPadding(topBarHeightPx: Int): Dp {
    val measuredHeight = if (topBarHeightPx == 0) HomeTopBarFallbackHeight else with(androidx.compose.ui.platform.LocalDensity.current) {
        topBarHeightPx.toDp()
    }
    return (measuredHeight - HomeTopBarContentOverlap).coerceAtLeast(HomeTopBarMinContentPadding)
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
    val scheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(204.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1.1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OverviewMetricCard(
                label = "Active",
                value = activeCount.toString(),
                supporting = "in motion",
                containerColor = scheme.primaryContainer.copy(alpha = 0.38f),
                accentColor = scheme.primary,
                modifier = Modifier
                    .weight(1.16f)
                    .fillMaxWidth()
            )

            Row(
                modifier = Modifier
                    .weight(0.84f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OverviewMetricCard(
                    label = "Done",
                    value = completedCount.toString(),
                    supporting = "closed",
                    containerColor = scheme.secondaryContainer.copy(alpha = 0.34f),
                    accentColor = scheme.secondary,
                    compact = true,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
                OverviewMetricCard(
                    label = "Due soon",
                    value = dueSoonCount.toString(),
                    supporting = "next up",
                    containerColor = scheme.tertiaryContainer.copy(alpha = 0.34f),
                    accentColor = scheme.tertiary,
                    compact = true,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(0.9f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OverviewMetricCard(
                label = "Rituals",
                value = ritualCount.toString(),
                supporting = "habit loops",
                containerColor = scheme.errorContainer.copy(alpha = 0.28f),
                accentColor = scheme.error,
                compact = true,
                modifier = Modifier
                    .weight(0.78f)
                    .fillMaxWidth()
            )
            OverviewMetricCard(
                label = "Systems",
                value = systemCount.toString(),
                supporting = "projects + travel",
                containerColor = scheme.surfaceContainerHigh,
                accentColor = scheme.onSurface,
                modifier = Modifier
                    .weight(1.22f)
                    .fillMaxWidth()
            )
        }
    }
}

@Composable
private fun OverviewMetricCard(
    label: String,
    value: String,
    supporting: String,
    containerColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val labelText = if (compact) label else label.uppercase()
    val horizontalPadding = if (compact) 14.dp else 16.dp
    val verticalPadding = if (compact) 12.dp else 15.dp
    val labelStyle = if (compact) {
        MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.2.sp
        )
    } else {
        MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp
        )
    }
    val valueStyle = if (compact) {
        MaterialTheme.typography.headlineMedium.copy(
            fontSize = 26.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.Black
        )
    } else {
        MaterialTheme.typography.displaySmall.copy(
            fontSize = 36.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.Black
        )
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(if (compact) 14.dp else 18.dp),
        color = containerColor,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.14f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding, vertical = verticalPadding)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = labelText,
                    style = labelStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.76f),
                    maxLines = if (compact) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )
                Surface(
                    shape = CircleShape,
                    color = accentColor.copy(alpha = 0.14f)
                ) {
                    Box(modifier = Modifier.size(if (compact) 8.dp else 12.dp))
                }
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))

            Text(
                text = value,
                style = valueStyle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )

            if (!compact) {
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TaskFilterRow(
    selected: TaskFilter,
    onSelect: (TaskFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(
            horizontal = 20.dp
        )
    ) {
        TaskFilter.entries.forEach { filter ->
            val active = filter == selected
            item {
                Surface(
                    shape = CircleShape,
                    color = if (active) MaterialTheme.colorScheme.inverseSurface
                    else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (active) MaterialTheme.colorScheme.inverseSurface
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                    ),
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onSelect(filter) }
                ) {
                    Text(
                        text = filter.label,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = if (active) MaterialTheme.colorScheme.inverseOnSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
    val insight = remember(task) { task.boardInsight() }
    val primaryActionLabel = remember(task.status, task.type) { task.boardPrimaryActionLabel() }
    val primaryActionIcon = remember(task.status, task.type) {
        when {
            task.status != TaskStatus.ACTIVE -> Icons.Rounded.Refresh
            task.type == TaskType.HABIT || task.type == TaskType.HEALTH -> Icons.Rounded.Repeat
            else -> Icons.Rounded.TaskAlt
        }
    }
    val isUrgent = task.status == TaskStatus.ACTIVE && task.isDueSoon()
    val borderColor = when {
        isUrgent -> MaterialTheme.colorScheme.error.copy(alpha = 0.34f)
        task.status == TaskStatus.COMPLETED -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.24f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    modifier = Modifier.size(38.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = tone.container
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = taskTypeIcon(task.type),
                            contentDescription = null,
                            tint = tone.content,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = task.title,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 19.sp,
                                lineHeight = 22.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        TaskStatusChip(status = task.status, isUrgent = isUrgent)
                    }

                    Text(
                        text = task.boardSummaryLine(),
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TaskBoardInsightPill(
                    modifier = Modifier.weight(1f),
                    insight = insight,
                    tone = tone
                )

                TaskPrimaryActionButton(
                    label = primaryActionLabel,
                    icon = primaryActionIcon,
                    onClick = if (task.status == TaskStatus.ACTIVE) onCompleteClick else onReopenClick,
                    tone = tone,
                    emphasized = task.status == TaskStatus.ACTIVE
                )

                TaskDeleteActionButton(onClick = onDeleteClick)
            }
        }
    }
}

private data class TaskBoardInsight(
    val icon: ImageVector,
    val text: String
)

@Composable
private fun TaskBoardInsightPill(
    modifier: Modifier = Modifier,
    insight: TaskBoardInsight,
    tone: TaskTone
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = tone.container.copy(alpha = 0.34f),
        border = BorderStroke(1.dp, tone.content.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = insight.icon,
                contentDescription = null,
                tint = tone.content,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = insight.text,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TaskStatusChip(
    status: TaskStatus,
    isUrgent: Boolean
) {
    val scheme = MaterialTheme.colorScheme
    val chip = when {
        isUrgent -> Triple(scheme.errorContainer, scheme.onErrorContainer, "Soon")
        status == TaskStatus.COMPLETED -> Triple(scheme.tertiaryContainer, scheme.onTertiaryContainer, "Done")
        status == TaskStatus.ARCHIVED -> Triple(scheme.surfaceContainerHighest, scheme.onSurfaceVariant, "Archived")
        else -> null
    } ?: return

    Surface(shape = CircleShape, color = chip.first) {
        Text(
            text = chip.third,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = chip.second
        )
    }
}

@Composable
private fun TaskPrimaryActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    tone: TaskTone,
    emphasized: Boolean
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    val containerColor = if (emphasized) tone.container else scheme.surfaceContainerLow
    val contentColor = if (emphasized) tone.content else scheme.onSurface

    Surface(
        shape = shape,
        color = containerColor,
        border = BorderStroke(
            1.dp,
            if (emphasized) contentColor.copy(alpha = 0.12f)
            else scheme.outlineVariant.copy(alpha = 0.45f)
        )
    ) {
        Row(
            modifier = Modifier
                .clip(shape)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = contentColor
            )
        }
    }
}

@Composable
private fun TaskDeleteActionButton(
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        modifier = Modifier.size(38.dp),
        shape = shape,
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.52f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.14f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
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

internal fun taskTypeIcon(type: TaskType): ImageVector = when (type) {
    TaskType.GENERAL -> Icons.Rounded.AutoAwesome
    TaskType.TRAVEL -> Icons.Rounded.FlightTakeoff
    TaskType.HABIT -> Icons.Rounded.Repeat
    TaskType.HEALTH -> Icons.Rounded.LocalHospital
    TaskType.PROJECT -> Icons.Rounded.Bolt
    TaskType.FINANCE -> Icons.Rounded.Payments
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

internal fun Task.boardPrimaryActionLabel(): String = when {
    status != TaskStatus.ACTIVE -> "Reopen"
    type == TaskType.HABIT || type == TaskType.HEALTH -> "Log"
    else -> "Done"
}

internal fun Task.primaryComponentIcon(): ImageVector {
    val primaryType = components.firstOrNull()?.type
    return when (primaryType) {
        ComponentType.CHECKLIST -> Icons.Rounded.Checklist
        ComponentType.PROGRESS_BAR -> Icons.Rounded.TaskAlt
        ComponentType.COUNTDOWN -> Icons.Rounded.Event
        ComponentType.HABIT_RING -> Icons.Rounded.Repeat
        ComponentType.NOTES -> Icons.Rounded.NoteAlt
        ComponentType.METRIC_TRACKER -> when (type) {
            TaskType.FINANCE -> Icons.Rounded.Savings
            else -> Icons.Rounded.CalendarMonth
        }
        ComponentType.DATA_FEED -> when (type) {
            TaskType.FINANCE -> Icons.Rounded.Payments
            TaskType.TRAVEL -> Icons.Rounded.FlightTakeoff
            else -> Icons.Rounded.AutoAwesome
        }
        null -> taskTypeIcon(type)
    }
}

internal fun tasksRelativeReminder(timestamp: Long): String {
    val m = ((timestamp - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0L)
    return when { m < 60L -> "in ${m}m"; m < 1_440L -> "in ${m / 60L}h"; else -> "in ${m / 1_440L}d" }
}

private fun Task.boardInsight(): TaskBoardInsight {
    val checklist = components.firstOrNull { it.type == ComponentType.CHECKLIST }
    val checklistDone = checklist?.checklistItems?.count { it.isCompleted } ?: 0
    val checklistTotal = checklist?.checklistItems?.size ?: 0
    val progress = progressRatio()
    val nextTouchpoint = nextTouchpoint()

    if (status == TaskStatus.COMPLETED) {
        return TaskBoardInsight(
            icon = Icons.Rounded.TaskAlt,
            text = "Finished ${updatedLabel()} ago"
        )
    }

    return when {
        isDueSoon() && nextTouchpoint != null -> TaskBoardInsight(
            icon = Icons.Rounded.Schedule,
            text = "Due ${tasksRelativeReminder(nextTouchpoint)}"
        )
        checklistTotal > 0 -> TaskBoardInsight(
            icon = Icons.Rounded.Checklist,
            text = "$checklistDone/$checklistTotal steps"
        )
        progress != null -> TaskBoardInsight(
            icon = Icons.Rounded.TaskAlt,
            text = "${(progress * 100f).roundToInt()}% progress"
        )
        nextTouchpoint != null -> TaskBoardInsight(
            icon = Icons.Rounded.Schedule,
            text = "Next ${tasksRelativeReminder(nextTouchpoint)}"
        )
        components.any { it.type == ComponentType.HABIT_RING } || type == TaskType.HABIT || type == TaskType.HEALTH -> TaskBoardInsight(
            icon = Icons.Rounded.Repeat,
            text = "Recurring rhythm"
        )
        components.any { it.type == ComponentType.DATA_FEED } -> TaskBoardInsight(
            icon = if (type == TaskType.FINANCE) Icons.Rounded.Payments else Icons.Rounded.AutoAwesome,
            text = when (type) {
                TaskType.FINANCE -> "Live finance feed"
                TaskType.TRAVEL -> "Live travel context"
                else -> "Live data attached"
            }
        )
        components.any { it.type == ComponentType.NOTES } -> TaskBoardInsight(
            icon = Icons.Rounded.NoteAlt,
            text = "Notes attached"
        )
        else -> TaskBoardInsight(
            icon = primaryComponentIcon(),
            text = "${components.size} module${if (components.size == 1) "" else "s"}"
        )
    }
}

private fun Task.boardSummaryLine(): String = when {
    status == TaskStatus.COMPLETED -> "Completed task kept around in case it needs another pass."
    components.any { it.type == ComponentType.CHECKLIST } -> "Checklist-driven flow with concrete next steps."
    progressRatio() != null -> "Measured progress makes this task easy to scan."
    components.any { it.type == ComponentType.DATA_FEED } -> "Live context is attached for quicker decisions."
    components.any { it.type == ComponentType.NOTES } -> "Notes and context stay attached to the work."
    type == TaskType.HABIT || type == TaskType.HEALTH -> "Built for repeatable check-ins instead of one-off work."
    type == TaskType.TRAVEL -> "Keeps timing, prep and trip details together."
    type == TaskType.FINANCE -> "Tracks money movement with signals worth checking."
    type == TaskType.PROJECT -> "Structured work with a clearer execution path."
    else -> "Flexible task with reminders and lightweight context."
}
