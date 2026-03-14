package com.theveloper.aura.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.theveloper.aura.R
import com.theveloper.aura.domain.model.TaskType
import java.time.LocalDate
import java.time.format.TextStyle as JavaDateTextStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun TasksScreen(
    viewModel: HabitTrackerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { HabitTrackerTopBar() }
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

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding() + 216.dp
                )
            ) {
                item {
                    DateProgressHeader(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        today = uiState.today,
                        doneToday = uiState.doneToday,
                        totalActive = uiState.totalActive
                    )
                }

                if (uiState.habitItems.isNotEmpty()) {
                    item {
                        HabitStreakSection(habits = uiState.habitItems)
                    }
                    items(uiState.habitItems, key = { it.taskId }) { habit ->
                        HabitCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            habit = habit,
                            today = uiState.today
                        )
                    }
                } else {
                    item {
                        HabitEmptyState()
                    }
                }
            }

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
            )
        }
    }
}

// ─── Top bar ─────────────────────────────────────────────────────────────────

@Composable
private fun HabitTrackerTopBar(modifier: Modifier = Modifier) {
    val titleStyle = rememberHabitTrackerTitleStyle()
    AuraGradientTopBarContainer(
        modifier = modifier.fillMaxWidth(),
        bottomFadePadding = 20.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Habits",
                style = titleStyle,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun rememberHabitTrackerTitleStyle(): TextStyle {
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

// ─── Date + progress header ──────────────────────────────────────────────────

@Composable
private fun DateProgressHeader(
    modifier: Modifier = Modifier,
    today: LocalDate,
    doneToday: Int,
    totalActive: Int
) {
    val dayName = remember(today) {
        today.dayOfWeek.getDisplayName(JavaDateTextStyle.FULL, Locale.ENGLISH)
    }
    val monthDay = remember(today) {
        val month = today.month.getDisplayName(JavaDateTextStyle.FULL, Locale.ENGLISH)
        "$month ${today.dayOfMonth}"
    }
    val year = remember(today) { today.year.toString() }
    val progressRatio = if (totalActive > 0) doneToday.toFloat() / totalActive.toFloat() else 0f

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = dayName.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = monthDay,
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 36.sp,
                        lineHeight = 36.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = year,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Light,
                        fontSize = 20.sp,
                        lineHeight = 20.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$doneToday / $totalActive done today",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${(progressRatio * 100f).roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressRatio.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
            }
        }
    }
}

// ─── Active streaks row ──────────────────────────────────────────────────────

@Composable
private fun HabitStreakSection(habits: List<HabitItemUiState>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            modifier = Modifier.padding(start = 16.dp),
            text = "ACTIVE STREAKS",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                horizontal = 16.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(habits, key = { it.taskId }) { habit ->
                ActiveStreakBadge(habit = habit)
            }
        }
    }
}

@Composable
private fun ActiveStreakBadge(habit: HabitItemUiState) {
    val accentColor = habitAccentColor(habit.type)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = 1.dp,
            color = if (habit.completedToday) {
                accentColor.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .width(118.dp)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = accentColor.copy(alpha = 0.14f),
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = habitTypeIcon(habit.type),
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                if (habit.completedToday) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Done today",
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                text = habit.title,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.LocalFireDepartment,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = "${habit.streakDays}d",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = accentColor
                )
            }
        }
    }
}

// ─── Per-habit card ──────────────────────────────────────────────────────────

@Composable
private fun HabitCard(
    modifier: Modifier,
    habit: HabitItemUiState,
    today: LocalDate
) {
    val accentColor = habitAccentColor(habit.type)
    val weekData = remember(habit.completionGrid30d) { habit.completionGrid30d.takeLast(7) }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Title + streak badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = habit.title,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 10.dp),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                        lineHeight = 22.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = accentColor.copy(alpha = 0.13f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LocalFireDepartment,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "${habit.streakDays}",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = accentColor
                        )
                    }
                }
            }

            // 30-day completion grid
            HabitCompletionGrid(
                grid30d = habit.completionGrid30d,
                today = today,
                accentColor = accentColor
            )

            // Stats + sparkline
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Column(
                    modifier = Modifier.width(84.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HabitStatChip(label = "7 DAYS", value = habit.completionRate7d)
                    HabitStatChip(label = "30 DAYS", value = habit.completionRate30d)
                }
                WeekSparkline(
                    weekData = weekData,
                    accentColor = accentColor,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                )
            }
        }
    }
}

// ─── 30-day completion grid ───────────────────────────────────────────────────

@Composable
private fun HabitCompletionGrid(
    grid30d: List<Boolean>,
    today: LocalDate,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val startDay = remember(today) { today.minusDays(29) }
    // DayOfWeek.value: 1=Mon … 7=Sun → leading blanks = value-1 (Mon=0, Sun=6)
    val leadingBlanks = remember(startDay) { startDay.dayOfWeek.value - 1 }

    val allCells: List<Boolean?> = remember(grid30d, leadingBlanks) {
        buildList {
            repeat(leadingBlanks) { add(null) }
            addAll(grid30d)
            val trailing = (7 - (leadingBlanks + 30) % 7) % 7
            repeat(trailing) { add(null) }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        // Day-of-week header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                )
            }
        }
        // Grid rows (5-6 rows of 7)
        allCells.chunked(7).forEach { rowCells ->
            val paddedRow: List<Boolean?> = if (rowCells.size < 7) {
                rowCells + List(7 - rowCells.size) { null }
            } else {
                rowCells
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                paddedRow.forEach { cell ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                when (cell) {
                                    true -> accentColor.copy(alpha = 0.82f)
                                    false -> MaterialTheme.colorScheme.surfaceContainerHighest
                                    null -> Color.Transparent
                                }
                            )
                    )
                }
            }
        }
    }
}

// ─── Completion stats chip ────────────────────────────────────────────────────

@Composable
private fun HabitStatChip(label: String, value: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            text = "${(value * 100f).roundToInt()}%",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
        )
    }
}

// ─── Weekly sparkline bar chart ───────────────────────────────────────────────

@Composable
private fun WeekSparkline(
    weekData: List<Boolean>,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        weekData.forEach { done ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(if (done) 1f else 0.16f)
                    .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                    .background(
                        if (done) accentColor.copy(alpha = 0.78f)
                        else MaterialTheme.colorScheme.surfaceContainerHighest
                    )
            )
        }
    }
}

// ─── Empty state ──────────────────────────────────────────────────────────────

@Composable
private fun HabitEmptyState() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(56.dp)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Repeat,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Text(
                text = "No active habits",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Create a habit or health task from the prompt bar to start tracking your rituals.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

@Composable
private fun habitAccentColor(type: TaskType): Color {
    val scheme = MaterialTheme.colorScheme
    return when (type) {
        TaskType.HABIT -> scheme.primary
        TaskType.HEALTH -> scheme.error
        else -> scheme.primary
    }
}

private fun habitTypeIcon(type: TaskType): ImageVector {
    return when (type) {
        TaskType.HABIT -> Icons.Rounded.Repeat
        TaskType.HEALTH -> Icons.Rounded.Favorite
        else -> Icons.Rounded.Repeat
    }
}
