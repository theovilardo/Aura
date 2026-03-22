package com.theveloper.aura.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.theveloper.aura.domain.model.TaskStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private const val CalendarBottomBarClearance = 112
private val CalendarBoardShape = RoundedCornerShape(30.dp)
private val CalendarCellShape = RoundedCornerShape(18.dp)

@Composable
fun TasksCalendarScreen(
    onNavigateToHabits: () -> Unit,
    onNavigateToDay: (LocalDate) -> Unit,
    onNavigateToTaskDetail: (String) -> Unit,
    viewModel: TasksCalendarViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var displayedMonthValue by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var selectedDateValue by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }

    val displayedMonth = remember(displayedMonthValue) { YearMonth.parse(displayedMonthValue) }
    val selectedDate = remember(selectedDateValue) { LocalDate.parse(selectedDateValue) }
    val monthEvents = remember(uiState.eventsByDate, displayedMonthValue) {
        uiState.eventsByDate
            .filterKeys { date -> displayedMonth.contains(date) }
            .values
            .flatten()
    }
    val activeDayCount = remember(uiState.eventsByDate, displayedMonthValue) {
        uiState.eventsByDate.keys.count { date ->
            displayedMonth.contains(date) && uiState.eventsByDate[date].orEmpty().isNotEmpty()
        }
    }
    val selectedDayEvents = remember(uiState.eventsByDate, selectedDateValue) {
        uiState.eventsByDate[LocalDate.parse(selectedDateValue)].orEmpty()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CalendarTopBar(
                onNavigateToHabits = onNavigateToHabits
            )
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .padding(bottom = CalendarBottomBarClearance.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CalendarMonthHeader(
                month = displayedMonth,
                eventCount = monthEvents.size,
                activeDayCount = activeDayCount,
                onPreviousMonth = {
                    val updatedMonth = displayedMonth.minusMonths(1)
                    displayedMonthValue = updatedMonth.toString()
                    selectedDateValue = preferredDateForMonth(updatedMonth, uiState.today).toString()
                },
                onNextMonth = {
                    val updatedMonth = displayedMonth.plusMonths(1)
                    displayedMonthValue = updatedMonth.toString()
                    selectedDateValue = preferredDateForMonth(updatedMonth, uiState.today).toString()
                },
                onJumpToToday = {
                    displayedMonthValue = YearMonth.from(uiState.today).toString()
                    selectedDateValue = uiState.today.toString()
                }
            )

            Surface(
                modifier = Modifier.weight(1f),
                shape = CalendarBoardShape,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CalendarWeekHeader()
                    Box(modifier = Modifier.weight(1f)) {
                        AnimatedContent(
                            targetState = displayedMonthValue,
                            transitionSpec = {
                                (fadeIn() + slideInVertically { it / 8 }) togetherWith
                                    (fadeOut() + slideOutVertically { -it / 8 }) using
                                    SizeTransform(clip = false)
                            },
                            label = "calendarMonthContent"
                        ) { monthKey ->
                            CalendarMonthBoard(
                                month = YearMonth.parse(monthKey),
                                today = uiState.today,
                                selectedDate = selectedDate,
                                eventsByDate = uiState.eventsByDate,
                                onSelectDate = { date ->
                                    selectedDateValue = date.toString()
                                    if (!displayedMonth.contains(date)) {
                                        displayedMonthValue = YearMonth.from(date).toString()
                                    }
                                },
                                onOpenDate = onNavigateToDay,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            SelectedDayDock(
                date = selectedDate,
                events = selectedDayEvents,
                onOpenDay = { onNavigateToDay(selectedDate) },
                onOpenTask = onNavigateToTaskDetail
            )
        }
    }
}

@Composable
fun CalendarDayScreen(
    initialDateValue: String,
    onNavigateBack: () -> Unit,
    onNavigateToTaskDetail: (String) -> Unit,
    viewModel: TasksCalendarViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val initialDate = remember(initialDateValue) { parseCalendarDate(initialDateValue) }
    var selectedDateValue by rememberSaveable(initialDateValue) { mutableStateOf(initialDate.toString()) }
    val selectedDate = remember(selectedDateValue) { LocalDate.parse(selectedDateValue) }
    val dayEvents = remember(uiState.eventsByDate, selectedDateValue) {
        uiState.eventsByDate[LocalDate.parse(selectedDateValue)].orEmpty()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CalendarDayTopBar(
                onNavigateBack = onNavigateBack
            )
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

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + 28.dp
            )
        ) {
            item(key = "calendar_day_hero") {
                CalendarDayHero(
                    date = selectedDate,
                    eventCount = dayEvents.size,
                    isToday = selectedDate == uiState.today,
                    onPreviousDay = { selectedDateValue = selectedDate.minusDays(1).toString() },
                    onNextDay = { selectedDateValue = selectedDate.plusDays(1).toString() },
                    onJumpToToday = { selectedDateValue = uiState.today.toString() }
                )
            }

            if (dayEvents.isEmpty()) {
                item(key = "calendar_day_empty") {
                    CalendarDayEmptyState(date = selectedDate)
                }
            } else {
                items(
                    items = dayEvents,
                    key = CalendarTaskEventUiState::instanceId,
                    contentType = { "calendar_event" }
                ) { event ->
                    CalendarAgendaItem(
                        event = event,
                        onClick = { onNavigateToTaskDetail(event.taskId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarTopBar(
    onNavigateToHabits: () -> Unit
) {
    AuraGradientTopBarContainer(
        style = AuraGradientTopBarStyle.Extended,
        bottomFadePadding = 12.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Calendar",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 30.sp,
                        lineHeight = 32.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Schedule by day",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            CalendarShortcutChip(
                label = "Habits",
                icon = Icons.Rounded.Repeat,
                onClick = onNavigateToHabits
            )
        }
    }
}

@Composable
private fun CalendarDayTopBar(
    onNavigateBack: () -> Unit
) {
    AuraGradientTopBarContainer(
        style = AuraGradientTopBarStyle.Linear,
        bottomFadePadding = 12.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CalendarChromeIconButton(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                onClick = onNavigateBack
            )
        }
    }
}

@Composable
private fun CalendarMonthHeader(
    month: YearMonth,
    eventCount: Int,
    activeDayCount: Int,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onJumpToToday: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Box(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = month.toString(),
                    transitionSpec = {
                        (fadeIn() + slideInVertically { it / 8 }) togetherWith
                            (fadeOut() + slideOutVertically { -it / 8 }) using
                            SizeTransform(clip = false)
                    },
                    label = "calendarMonthHeader"
                ) { monthValue ->
                    val currentMonth = YearMonth.parse(monthValue)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = currentMonth.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase(),
                            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.3.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${currentMonth.year}",
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 34.sp,
                                lineHeight = 34.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CalendarChromeIconButton(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Previous month",
                    onClick = onPreviousMonth,
                    buttonSize = 44.dp
                )
                CalendarShortcutChip(
                    label = "Today",
                    icon = Icons.Rounded.Today,
                    onClick = onJumpToToday
                )
                CalendarChromeIconButton(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = "Next month",
                    onClick = onNextMonth,
                    buttonSize = 44.dp
                )
            }
        }

        Text(
            text = when {
                eventCount == 0 -> "No scheduled items in this month"
                else -> "$eventCount scheduled items across $activeDayCount days"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CalendarWeekHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { label ->
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.6.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CalendarMonthBoard(
    month: YearMonth,
    today: LocalDate,
    selectedDate: LocalDate,
    eventsByDate: Map<LocalDate, List<CalendarTaskEventUiState>>,
    onSelectDate: (LocalDate) -> Unit,
    onOpenDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val cells = remember(month) { buildCalendarCells(month) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        cells.chunked(7).forEach { week ->
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                week.forEach { cell ->
                    CalendarDayCell(
                        cell = cell,
                        events = eventsByDate[cell.date].orEmpty(),
                        today = today,
                        isSelected = selectedDate == cell.date,
                        onSelect = {
                            if (selectedDate == cell.date) {
                                onOpenDate(cell.date)
                            } else {
                                onSelectDate(cell.date)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    cell: CalendarCellUiState,
    events: List<CalendarTaskEventUiState>,
    today: LocalDate,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isToday = cell.date == today
    val containerColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.inverseSurface
            isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f)
            events.isNotEmpty() -> MaterialTheme.colorScheme.surface
            else -> Color.Transparent
        },
        label = "calendarDayContainer"
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.inverseOnSurface
            isToday -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSurface
        },
        label = "calendarDayContent"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.14f)
            isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
            cell.isInMonth && events.isNotEmpty() -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
            else -> Color.Transparent
        },
        label = "calendarDayBorder"
    )

    Column(
        modifier = modifier
            .clip(CalendarCellShape)
            .background(containerColor)
            .border(1.dp, borderColor, CalendarCellShape)
            .clickable(onClick = onSelect)
            .padding(8.dp)
            .alpha(if (cell.isInMonth) 1f else 0.36f),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = cell.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = contentColor
            )
            if (events.isNotEmpty()) {
                Text(
                    text = events.size.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (isSelected) {
                        contentColor.copy(alpha = 0.72f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            events.take(3).forEach { event ->
                val eventColor = calendarEventAccent(event = event, selected = isSelected)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(eventColor)
                )
            }

            if (events.size > 3) {
                Text(
                    text = "+${events.size - 3}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = if (isSelected) contentColor.copy(alpha = 0.82f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SelectedDayDock(
    date: LocalDate,
    events: List<CalendarTaskEventUiState>,
    onOpenDay: () -> Unit,
    onOpenTask: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.68f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase(),
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.1.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatDockDate(date),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                CalendarShortcutChip(
                    label = "Open day",
                    icon = Icons.Rounded.CalendarMonth,
                    onClick = onOpenDay
                )
            }

            AnimatedContent(
                targetState = date.toString(),
                transitionSpec = {
                    (fadeIn() + slideInVertically { it / 10 }) togetherWith
                        (fadeOut() + slideOutVertically { -it / 10 }) using
                        SizeTransform(clip = false)
                },
                label = "selectedDayDockContent"
            ) { _ ->
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (events.isEmpty()) {
                        Text(
                            text = "No scheduled items for this day.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        events.take(3).forEach { event ->
                            CalendarDockEventRow(
                                event = event,
                                onClick = { onOpenTask(event.taskId) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDockEventRow(
    event: CalendarTaskEventUiState,
    onClick: () -> Unit
) {
    val tone = taskTone(event.type)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 10.dp, height = 34.dp)
                .clip(CircleShape)
                .background(tone.content.copy(alpha = 0.88f))
        )
        Text(
            text = formatEventTime(event),
            modifier = Modifier.width(56.dp),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = calendarEventMeta(event),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CalendarDayHero(
    date: LocalDate,
    eventCount: Int,
    isToday: Boolean,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onJumpToToday: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.68f))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
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
                        text = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH).uppercase(),
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatDayHeroDate(date),
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 34.sp,
                            lineHeight = 36.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (!isToday) {
                    CalendarShortcutChip(
                        label = "Today",
                        icon = Icons.Rounded.Today,
                        onClick = onJumpToToday
                    )
                }
            }

            Text(
                text = when {
                    eventCount == 0 -> "No scheduled items on this date"
                    eventCount == 1 -> "1 scheduled item"
                    else -> "$eventCount scheduled items"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CalendarShortcutChip(
                    label = "Previous",
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    onClick = onPreviousDay
                )
                CalendarShortcutChip(
                    label = "Next",
                    icon = Icons.AutoMirrored.Rounded.ArrowForward,
                    onClick = onNextDay
                )
            }
        }
    }
}

@Composable
private fun CalendarDayEmptyState(
    date: LocalDate
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.68f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(60.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Text(
                text = formatDockDate(date),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "There are no reminders or dated tasks registered for this day.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CalendarAgendaItem(
    event: CalendarTaskEventUiState,
    onClick: () -> Unit
) {
    val tone = taskTone(event.type)
    val statusLabel = when (event.status) {
        TaskStatus.ACTIVE -> event.type.label()
        TaskStatus.COMPLETED -> "Done"
        TaskStatus.ARCHIVED -> "Archived"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .clickable(onClick = onClick)
                .padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = tone.container,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = taskTypeIcon(event.type),
                        contentDescription = null,
                        tint = tone.content,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = calendarEventMeta(event),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = formatEventTime(event),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CalendarShortcutChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f))
    ) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun CalendarChromeIconButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    buttonSize: androidx.compose.ui.unit.Dp = 48.dp
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 2.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Box(
            modifier = Modifier
                .size(buttonSize)
                .clip(CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun calendarEventAccent(
    event: CalendarTaskEventUiState,
    selected: Boolean
): Color {
    if (selected) {
        return MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.88f)
    }

    return taskTone(event.type).content.copy(alpha = 0.88f)
}

private data class CalendarCellUiState(
    val date: LocalDate,
    val isInMonth: Boolean
)

private fun buildCalendarCells(month: YearMonth): List<CalendarCellUiState> {
    val firstOfMonth = month.atDay(1)
    val leadingDays = firstOfMonth.dayOfWeek.value - 1
    val gridStart = firstOfMonth.minusDays(leadingDays.toLong())

    return List(42) { index ->
        val date = gridStart.plusDays(index.toLong())
        CalendarCellUiState(
            date = date,
            isInMonth = month.contains(date)
        )
    }
}

private fun YearMonth.contains(date: LocalDate): Boolean {
    return year == date.year && month == date.month
}

private fun preferredDateForMonth(month: YearMonth, today: LocalDate): LocalDate {
    return if (month.contains(today)) today else month.atDay(1)
}

private fun parseCalendarDate(value: String): LocalDate {
    return runCatching { LocalDate.parse(value) }.getOrElse { LocalDate.now() }
}

private fun formatDockDate(date: LocalDate): String {
    val formatter = DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH)
    return formatter.format(date)
}

private fun formatDayHeroDate(date: LocalDate): String {
    val formatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)
    return formatter.format(date)
}

private fun formatEventTime(event: CalendarTaskEventUiState): String {
    if (event.isAllDay) return "All day"

    val formatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
    return formatter.format(event.timestamp.toLocalDateTime())
}

private fun calendarEventMeta(event: CalendarTaskEventUiState): String {
    val sourceLabel = when (event.source) {
        CalendarEventSource.TARGET_DATE -> if (event.type == com.theveloper.aura.domain.model.TaskType.EVENT) "Event" else "Date"
        CalendarEventSource.REMINDER -> "Reminder"
    }

    return buildString {
        append(sourceLabel)
        append(" · ")
        append(event.type.label())
        if (event.status == TaskStatus.COMPLETED) {
            append(" · done")
        }
    }
}

private fun Long.toLocalDateTime(): LocalDateTime {
    return Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
}
