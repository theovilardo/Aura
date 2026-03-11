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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.theveloper.aura.domain.model.Task
import com.theveloper.aura.domain.model.TaskType
import com.theveloper.aura.ui.components.DayRescueBottomSheet
import com.theveloper.aura.ui.components.SuggestionCard
import java.time.LocalDate
import java.time.LocalTime

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToTaskDetail: (String) -> Unit,
    onNavigateToTasks: () -> Unit,
    onNavigateToPromptCreate: (String) -> Unit,
    onNavigateToManualCreate: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedCategory by rememberSaveable { mutableStateOf("Insights") }
    var showDayRescue by remember { mutableStateOf(false) }
    val habitCount = remember(uiState.tasks) { uiState.tasks.count { it.type == TaskType.HABIT } }
    val signalCount = remember(uiState.suggestions) { uiState.suggestions.size }

    if (showDayRescue) {
        DayRescueBottomSheet(
            tasks = uiState.tasks,
            suggestions = uiState.suggestions.filter { it.type.name == "RESCHEDULE_REMINDER" },
            onApply = { selected ->
                selected.forEach { viewModel.applySuggestion(it) }
                showDayRescue = false
            },
            onDismiss = { showDayRescue = false }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (LocalTime.now().hour >= 12 && uiState.tasks.isNotEmpty()) {
                FloatingActionButton(
                    onClick = {
                        viewModel.runDayRescue()
                        showDayRescue = true
                    },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Warning,
                        contentDescription = "Day rescue"
                    )
                }
            }
        }
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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    top = innerPadding.calculateTopPadding() + 108.dp,
                    end = 20.dp,
                    bottom = innerPadding.calculateBottomPadding() + 216.dp
                )
            ) {
                item {
                    HomeHero(
                        taskCount = uiState.tasks.size,
                        habitCount = habitCount,
                        signalCount = signalCount
                    )
                }

                item {
                    HabitTrackerOverview(uiState.tasks)
                }

                item {
                    QuickCategories(
                        selected = selectedCategory,
                        onSelect = { selectedCategory = it }
                    )
                }

                if (uiState.suggestions.isNotEmpty()) {
                    item {
                        SectionLabel(
                            text = "NOW",
                            actionLabel = null,
                            onClick = null
                        )
                    }

                    items(uiState.suggestions, key = { it.id }) { suggestion ->
                        SuggestionCard(
                            suggestion = suggestion,
                            onApprove = { viewModel.applySuggestion(it) },
                            onReject = { viewModel.rejectSuggestion(it) }
                        )
                    }
                }

                item {
                    SectionLabel(
                        text = if (uiState.tasks.isEmpty()) "READY FOR YOU" else "YOUR TASKS",
                        actionLabel = if (uiState.tasks.size > 3) "Open board" else null,
                        onClick = if (uiState.tasks.size > 3) onNavigateToTasks else null
                    )
                }

                when {
                    uiState.errorMessage != null && uiState.tasks.isEmpty() -> {
                        item {
                            HomeInfoCard(
                                title = "Something went wrong",
                                body = uiState.errorMessage.orEmpty()
                            )
                        }
                    }

                    uiState.tasks.isEmpty() -> {
                        item {
                            HomeInfoCard(
                                title = "No tasks yet",
                                body = "Use the floating prompt bar below to create one instantly, or tap the plus circle to build it manually."
                            )
                        }
                    }

                    else -> {
                        items(uiState.tasks, key = { it.id }) { task ->
                            HomeTaskCard(
                                task = task,
                                onClick = { onNavigateToTaskDetail(task.id) }
                            )
                        }
                    }
                }
            }
        

            HomeTopBar(
                taskCount = uiState.tasks.size,
                habitCount = habitCount,
                signalCount = signalCount,
                onOpenBoard = onNavigateToTasks,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
private fun HabitTrackerOverview(tasks: List<Task>) {
    val habitTasks = tasks.filter { it.type == TaskType.HABIT }
    if (habitTasks.isEmpty()) return
    val currentWeekdayIndex = LocalDate.now().dayOfWeek.ordinal

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel(text = "HABIT TRACKER", actionLabel = null, onClick = null)
        
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val days = listOf("M", "T", "W", "T", "F", "S", "S")
                days.forEachIndexed { index, day ->
                    val isActive = index == currentWeekdayIndex
                    val hasCompleted = index < currentWeekdayIndex

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (hasCompleted) MaterialTheme.colorScheme.primaryContainer else if (isActive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, if (isActive) MaterialTheme.colorScheme.primary else Color.Transparent)
                        ) {
                            Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                                if (hasCompleted) {
                                    Icon(
                                        imageVector = Icons.Rounded.TaskAlt,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                } else {
                                    Text(
                                        text = day,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (isActive) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeTopBar(
    taskCount: Int,
    habitCount: Int,
    signalCount: Int,
    onOpenBoard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val subtitle = when {
        signalCount > 0 && habitCount > 0 -> "$signalCount signals ready · $habitCount habits active"
        signalCount > 0 -> "$signalCount signals ready · $taskCount tasks live"
        habitCount > 0 -> "$habitCount habits active · $taskCount tasks live"
        else -> "$taskCount active tasks"
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
                        MaterialTheme.colorScheme.background.copy(alpha = 0.8f),
                        Color.Transparent
                    )
                )
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(10.dp)
                            .size(18.dp)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Aura",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                modifier = Modifier.clickable(onClick = onOpenBoard)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Board",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeHero(
    taskCount: Int,
    habitCount: Int,
    signalCount: Int
) {
    val greeting = when (LocalTime.now().hour) {
        in 5..11 -> "Good morning."
        in 12..18 -> "Good afternoon."
        else -> "Good evening."
    }

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
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 30.sp,
                        lineHeight = 34.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Habits, rescue signals and your next tasks should stay visible at a glance.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HomeHeroMetric(
                    label = "Tasks",
                    value = taskCount.toString()
                )
                HomeHeroMetric(
                    label = "Habits",
                    value = habitCount.toString()
                )
                HomeHeroMetric(
                    label = "Now",
                    value = signalCount.toString()
                )
            }
        }
    }
}

@Composable
private fun RowScope.HomeHeroMetric(
    label: String,
    value: String
) {
    Surface(
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
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
private fun QuickCategories(
    selected: String,
    onSelect: (String) -> Unit
) {
    val categories = listOf(
        QuickCategory("Insights", Icons.Rounded.AutoAwesome),
        QuickCategory("Focus", Icons.Rounded.FitnessCenter),
        QuickCategory("Daily", Icons.Rounded.CalendarMonth)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        categories.forEach { category ->
            val isSelected = category.label == selected
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    }
                ),
                modifier = Modifier.clickable { onSelect(category.label) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = null,
                        tint = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = category.label,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(
    text: String,
    actionLabel: String?,
    onClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (actionLabel != null && onClick != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onClick)
            )
        }
    }
}

@Composable
private fun HomeInfoCard(
    title: String,
    body: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
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

@Composable
private fun HomeTaskCard(
    task: Task,
    onClick: () -> Unit
) {
    val icon = task.icon()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.58f)),
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (task.type == TaskType.TRAVEL || task.type == TaskType.HEALTH) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier
                                .padding(10.dp)
                                .size(18.dp)
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = task.type.label(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            Text(
                text = task.summaryLine(),
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private data class QuickCategory(
    val label: String,
    val icon: ImageVector
)

private fun Task.icon(): ImageVector {
    return when (type) {
        TaskType.TRAVEL -> Icons.Rounded.CalendarMonth
        TaskType.HEALTH -> Icons.Rounded.FitnessCenter
        TaskType.PROJECT -> Icons.Rounded.TaskAlt
        else -> Icons.Rounded.AutoAwesome
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
        TaskType.TRAVEL -> "Countdown and checklist are ready for your next travel step."
        TaskType.HABIT -> "Keep the routine moving with a quick check-in."
        TaskType.PROJECT -> "Track progress and turn notes into next actions."
        TaskType.HEALTH -> "Review your health metrics and update the task."
        TaskType.FINANCE -> "Open the feed and inspect the latest signal."
        TaskType.GENERAL -> "Open the task and continue building it from your prompt."
    }
}
