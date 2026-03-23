package com.theveloper.aura.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.theveloper.aura.domain.model.TaskStatus
import com.theveloper.aura.ui.LocalAuraBottomBarHeight
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign

private val CalendarViewModeStripShape = RoundedCornerShape(26.dp)
private val CalendarDayPillShape = RoundedCornerShape(16.dp)

private enum class CalendarViewMode(
    val label: String
) {
    MONTH("Month"),
    WEEK("Week"),
    YEAR("Year")
}

@Composable
fun TasksCalendarScreen(
    onNavigateToHabits: () -> Unit,
    onNavigateToDay: (LocalDate) -> Unit,
    onNavigateToTaskDetail: (String) -> Unit,
    viewModel: TasksCalendarViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val todayKey = uiState.today.toString()
    val floatingBarHeight = LocalAuraBottomBarHeight.current
    var anchorDateValue by rememberSaveable(todayKey) { mutableStateOf(todayKey) }
    var selectedDateValue by rememberSaveable(todayKey) { mutableStateOf(todayKey) }
    var viewModeKey by rememberSaveable { mutableStateOf(CalendarViewMode.MONTH.name) }
    var periodMotionDirection by rememberSaveable { mutableStateOf(0) }

    val anchorDate = remember(anchorDateValue) { parseCalendarDate(anchorDateValue) }
    val selectedDate = remember(selectedDateValue) { parseCalendarDate(selectedDateValue) }
    val viewMode = remember(viewModeKey) { CalendarViewMode.valueOf(viewModeKey) }
    val periodLabel = remember(viewModeKey, anchorDateValue) {
        formatCalendarPeriodLabel(viewMode, anchorDate)
    }
    val periodSummary = remember(uiState.eventsByDate, viewModeKey, anchorDateValue) {
        formatCalendarPeriodSummary(viewMode, anchorDate, uiState.eventsByDate)
    }
    val selectedDayEvents = remember(uiState.eventsByDate, selectedDateValue) {
        uiState.eventsByDate[parseCalendarDate(selectedDateValue)].orEmpty()
    }

    fun updatePeriod(direction: Int) {
        val shiftedAnchorDate = shiftDateByMode(anchorDate, viewMode, direction.toLong())
        periodMotionDirection = direction
        anchorDateValue = shiftedAnchorDate.toString()
        selectedDateValue = preferredSelectionForPeriod(
            anchorDate = shiftedAnchorDate,
            mode = viewMode,
            selectedDate = selectedDate,
            today = uiState.today,
            eventsByDate = uiState.eventsByDate
        ).toString()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CalendarTopBar(
                viewMode = viewMode,
                periodLabel = periodLabel,
                periodSummary = periodSummary,
                onViewModeChange = { mode ->
                    viewModeKey = mode.name
                    periodMotionDirection = 0
                    anchorDateValue = selectedDate.toString()
                },
                onPreviousPeriod = { updatePeriod(direction = -1) },
                onNextPeriod = { updatePeriod(direction = 1) },
                onNavigateToHabits = onNavigateToHabits
            )
        },
        bottomBar = {
            if (!uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = floatingBarHeight + 6.dp)
                ) {
                    SelectedDayRibbon(
                        date = selectedDate,
                        today = uiState.today,
                        events = selectedDayEvents,
                        onJumpToToday = {
                            periodMotionDirection = compareDateForDirection(selectedDate, uiState.today)
                            anchorDateValue = uiState.today.toString()
                            selectedDateValue = uiState.today.toString()
                        },
                        onOpenDay = { onNavigateToDay(selectedDate) }
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CalendarSwipeViewport(
                targetState = viewModeKey to anchorDateValue,
                motionDirection = periodMotionDirection,
                onSwipeToPrevious = { updatePeriod(direction = -1) },
                onSwipeToNext = { updatePeriod(direction = 1) },
                modifier = Modifier.weight(1f)
            ) { (modeKey, dateValue) ->
                val mode = CalendarViewMode.valueOf(modeKey)
                val anchoredDate = parseCalendarDate(dateValue)
                when (mode) {
                    CalendarViewMode.MONTH -> CalendarMonthView(
                        anchorDate = anchoredDate,
                        selectedDate = selectedDate,
                        today = uiState.today,
                        eventsByDate = uiState.eventsByDate,
                        onSelectDate = { date -> selectedDateValue = date.toString() },
                        onOpenDate = onNavigateToDay,
                        modifier = Modifier.fillMaxSize()
                    )
                    CalendarViewMode.WEEK -> CalendarWeekView(
                        anchorDate = anchoredDate,
                        selectedDate = selectedDate,
                        today = uiState.today,
                        eventsByDate = uiState.eventsByDate,
                        onSelectDate = { date -> selectedDateValue = date.toString() },
                        onOpenDate = onNavigateToDay,
                        onOpenTask = onNavigateToTaskDetail,
                        modifier = Modifier.fillMaxSize()
                    )
                    CalendarViewMode.YEAR -> CalendarYearView(
                        anchorDate = anchoredDate,
                        selectedDate = selectedDate,
                        today = uiState.today,
                        eventsByDate = uiState.eventsByDate,
                        onOpenMonth = { month ->
                            val preferredDate = preferredSelectionForPeriod(
                                anchorDate = month.atDay(1),
                                mode = CalendarViewMode.MONTH,
                                selectedDate = selectedDate,
                                today = uiState.today,
                                eventsByDate = uiState.eventsByDate
                            )
                            periodMotionDirection = 0
                            viewModeKey = CalendarViewMode.MONTH.name
                            anchorDateValue = preferredDate.toString()
                            selectedDateValue = preferredDate.toString()
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
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
        uiState.eventsByDate[parseCalendarDate(selectedDateValue)].orEmpty()
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
    viewMode: CalendarViewMode,
    periodLabel: String,
    periodSummary: String,
    onViewModeChange: (CalendarViewMode) -> Unit,
    onPreviousPeriod: () -> Unit,
    onNextPeriod: () -> Unit,
    onNavigateToHabits: () -> Unit
) {
    AuraGradientTopBarContainer(
        style = AuraGradientTopBarStyle.Extended,
        bottomFadePadding = 10.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Calendar",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 30.sp,
                            lineHeight = 32.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                CalendarActionChip(
                    label = "Habits",
                    icon = Icons.Rounded.Repeat,
                    onClick = onNavigateToHabits
                )
            }

            CalendarViewModeStrip(
                selected = viewMode,
                onSelect = onViewModeChange
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.68f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CalendarChevronButton(
                        imageVector = Icons.Rounded.ChevronLeft,
                        contentDescription = "Previous period",
                        onClick = onPreviousPeriod
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        AnimatedContent(
                            targetState = periodLabel,
                            transitionSpec = {
                                (fadeIn() + slideInVertically { it / 8 }) togetherWith
                                    (fadeOut() + slideOutVertically { -it / 8 }) using
                                    SizeTransform(clip = false)
                            },
                            label = "calendarPeriodTitle"
                        ) { label ->
                            Text(
                                text = label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    lineHeight = 22.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = periodSummary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    CalendarChevronButton(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = "Next period",
                        onClick = onNextPeriod
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarViewModeStrip(
    selected: CalendarViewMode,
    onSelect: (CalendarViewMode) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CalendarViewModeStripShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.68f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CalendarViewMode.entries.forEach { mode ->
                val selectedMode = mode == selected
                val containerColor by animateColorAsState(
                    targetValue = if (selectedMode) {
                        MaterialTheme.colorScheme.inverseSurface
                    } else {
                        Color.Transparent
                    },
                    label = "calendarModeContainer"
                )
                val contentColor by animateColorAsState(
                    targetValue = if (selectedMode) {
                        MaterialTheme.colorScheme.inverseOnSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    label = "calendarModeContent"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(22.dp))
                        .background(containerColor)
                        .clickable { onSelect(mode) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mode.label,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = contentColor
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarMonthView(
    anchorDate: LocalDate,
    selectedDate: LocalDate,
    today: LocalDate,
    eventsByDate: Map<LocalDate, List<CalendarTaskEventUiState>>,
    onSelectDate: (LocalDate) -> Unit,
    onOpenDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val month = remember(anchorDate) { YearMonth.from(anchorDate) }
    val cells = remember(month) { buildCalendarCells(month) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CalendarWeekHeader()
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            cells.chunked(7).forEach { week ->
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    week.forEach { cell ->
                        CalendarMonthDayCell(
                            cell = cell,
                            events = eventsByDate[cell.date].orEmpty(),
                            today = today,
                            selectedDate = selectedDate,
                            onSelectDate = onSelectDate,
                            onOpenDate = onOpenDate,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarMonthDayCell(
    cell: CalendarCellUiState,
    events: List<CalendarTaskEventUiState>,
    today: LocalDate,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    onOpenDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val isToday = cell.date == today
    val isSelected = cell.date == selectedDate
    val dayContainerColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.inverseSurface
            isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.94f)
            else -> Color.Transparent
        },
        label = "calendarMonthDayContainer"
    )
    val dayContentColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.inverseOnSurface
            !cell.isInMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.54f)
            isToday -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSurface
        },
        label = "calendarMonthDayContent"
    )
    val dayBorder = when {
        isSelected -> BorderStroke(1.dp, MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.12f))
        isToday -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
        else -> null
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable {
                if (isSelected) onOpenDate(cell.date) else onSelectDate(cell.date)
            }
            .padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.size(width = 48.dp, height = 40.dp),
                shape = CalendarDayPillShape,
                color = dayContainerColor,
                border = dayBorder
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = cell.date.dayOfMonth.toString(),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = dayContentColor
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp),
            contentAlignment = Alignment.Center
        ) {
            CalendarMonthEventMarkers(
                events = events,
                isSelected = isSelected,
                visible = cell.isInMonth
            )
        }
    }
}

@Composable
private fun CalendarMonthEventMarkers(
    events: List<CalendarTaskEventUiState>,
    isSelected: Boolean,
    visible: Boolean
) {
    if (!visible || events.isEmpty()) return

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        events.take(3).forEach { event ->
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(calendarEventAccent(event, isSelected))
            )
        }
        if (events.size > 3) {
            Text(
                text = "+${events.size - 3}",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.82f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun CalendarWeekView(
    anchorDate: LocalDate,
    selectedDate: LocalDate,
    today: LocalDate,
    eventsByDate: Map<LocalDate, List<CalendarTaskEventUiState>>,
    onSelectDate: (LocalDate) -> Unit,
    onOpenDate: (LocalDate) -> Unit,
    onOpenTask: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val dates = remember(anchorDate) { weekDates(anchorDate) }

    CalendarScrollableFadeFrame(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(
                items = dates,
                key = LocalDate::toString
            ) { date ->
                CalendarWeekRow(
                    date = date,
                    events = eventsByDate[date].orEmpty(),
                    isToday = date == today,
                    isSelected = date == selectedDate,
                    onSelectDate = onSelectDate,
                    onOpenDate = onOpenDate,
                    onOpenTask = onOpenTask
                )
            }
        }
    }
}

@Composable
private fun CalendarWeekRow(
    date: LocalDate,
    events: List<CalendarTaskEventUiState>,
    isToday: Boolean,
    isSelected: Boolean,
    onSelectDate: (LocalDate) -> Unit,
    onOpenDate: (LocalDate) -> Unit,
    onOpenTask: (String) -> Unit
) {
    val rowColor = when {
        isSelected -> MaterialTheme.colorScheme.surfaceContainerHigh
        isToday -> MaterialTheme.colorScheme.surfaceContainerLow
        events.isNotEmpty() -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.18f)
    }
    val rowBorder = when {
        isSelected -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.78f))
        events.isNotEmpty() -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.58f))
        else -> null
    }
    val badgeColor = when {
        isSelected -> MaterialTheme.colorScheme.inverseSurface
        isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.94f)
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val badgeContentColor = when {
        isSelected -> MaterialTheme.colorScheme.inverseOnSurface
        isToday -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val badgeBorder = when {
        isSelected -> BorderStroke(1.dp, MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.12f))
        isToday -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
        else -> null
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = rowColor,
        border = rowBorder
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (isSelected) onOpenDate(date) else onSelectDate(date)
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(width = 64.dp, height = 68.dp),
                shape = RoundedCornerShape(20.dp),
                color = badgeColor,
                border = badgeBorder
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
                        color = badgeContentColor.copy(alpha = 0.78f)
                    )
                    Text(
                        text = date.dayOfMonth.toString(),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = badgeContentColor
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = formatWeekRowDate(date),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = weekRowSummary(events),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (events.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CompactEventBadge(
                            event = events.first(),
                            onClick = { onOpenTask(events.first().taskId) },
                            modifier = Modifier.weight(1f)
                        )
                        if (events.size > 1) {
                            Text(
                                text = "+${events.size - 1}",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            CalendarChromeIconButton(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = "Open day",
                onClick = { onOpenDate(date) },
                buttonSize = 38.dp
            )
        }
    }
}

@Composable
private fun CalendarYearView(
    anchorDate: LocalDate,
    selectedDate: LocalDate,
    today: LocalDate,
    eventsByDate: Map<LocalDate, List<CalendarTaskEventUiState>>,
    onOpenMonth: (YearMonth) -> Unit,
    modifier: Modifier = Modifier
) {
    val year = anchorDate.year
    val months = remember(year) { (1..12).map { monthValue -> YearMonth.of(year, monthValue) } }

    CalendarScrollableFadeFrame(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(
                items = months.chunked(2),
                key = { row -> row.first().toString() }
            ) { rowMonths ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowMonths.forEach { month ->
                        YearMonthTile(
                            month = month,
                            selectedDate = selectedDate,
                            today = today,
                            eventsByDate = eventsByDate,
                            onClick = { onOpenMonth(month) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowMonths.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun YearMonthTile(
    month: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate,
    eventsByDate: Map<LocalDate, List<CalendarTaskEventUiState>>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cells = remember(month) { buildCalendarCells(month) }
    val selectedMonth = month.contains(selectedDate)
    val eventCount = remember(eventsByDate, month) {
        eventsByDate
            .filterKeys(month::contains)
            .values
            .sumOf(List<CalendarTaskEventUiState>::size)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = if (selectedMonth) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            1.dp,
            if (selectedMonth) {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.82f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.58f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = month.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = if (eventCount == 0) "Free" else "$eventCount",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                cells.chunked(7).forEach { week ->
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        week.forEach { cell ->
                            MiniMonthDayCell(
                                cell = cell,
                                hasEvents = eventsByDate[cell.date].orEmpty().isNotEmpty(),
                                isToday = cell.date == today,
                                isSelected = cell.date == selectedDate,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniMonthDayCell(
    cell: CalendarCellUiState,
    hasEvents: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    val containerColor = when {
        isSelected -> MaterialTheme.colorScheme.inverseSurface
        isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f)
        hasEvents -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> Color.Transparent
    }
    val contentColor = when {
        isSelected -> MaterialTheme.colorScheme.inverseOnSurface
        isToday -> MaterialTheme.colorScheme.onPrimaryContainer
        cell.isInMonth -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.36f)
    }

    Box(
        modifier = modifier.height(18.dp),
        contentAlignment = Alignment.Center
    ) {
        if (cell.isInMonth || isSelected || isToday || hasEvents) {
            Surface(
                modifier = Modifier.size(18.dp),
                shape = CircleShape,
                color = containerColor
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (cell.isInMonth) {
                        Text(
                            text = cell.date.dayOfMonth.toString(),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 9.sp),
                            color = contentColor
                        )
                    }
                }
            }
        }
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
private fun CalendarScrollableFadeFrame(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val background = MaterialTheme.colorScheme.background

    Box(modifier = modifier) {
        content()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            background,
                            background.copy(alpha = 0.82f),
                            Color.Transparent
                        )
                    )
                )
                .align(Alignment.TopCenter)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            background.copy(alpha = 0.82f),
                            background
                        )
                    )
                )
                .align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun SelectedDayRibbon(
    date: LocalDate,
    today: LocalDate,
    events: List<CalendarTaskEventUiState>,
    onJumpToToday: () -> Unit,
    onOpenDay: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.68f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .clickable(onClick = onJumpToToday)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.9.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatSelectedRibbonDate(date),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = selectedRibbonSummary(
                    events = events,
                    selectedDate = date,
                    today = today
                ),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            CalendarChromeIconButton(
                imageVector = Icons.Rounded.CalendarMonth,
                contentDescription = "Open day",
                onClick = onOpenDay,
                buttonSize = 40.dp
            )
        }
    }
}

@Composable
private fun CompactEventBadge(
    event: CalendarTaskEventUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tone = taskTone(event.type)

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = tone.container,
        border = BorderStroke(1.dp, tone.content.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatEventTime(event),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = tone.content
            )
            Text(
                text = event.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
                color = tone.content
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
                    CalendarActionChip(
                        label = "Today",
                        icon = Icons.Rounded.Today,
                        onClick = onJumpToToday,
                        compact = true
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
                CalendarActionChip(
                    label = "Previous",
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    onClick = onPreviousDay,
                    compact = true
                )
                CalendarActionChip(
                    label = "Next",
                    icon = Icons.AutoMirrored.Rounded.ArrowForward,
                    onClick = onNextDay,
                    compact = true
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
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
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
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
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
private fun CalendarActionChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    compact: Boolean = false
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
                .padding(
                    horizontal = if (compact) 11.dp else 12.dp,
                    vertical = if (compact) 8.dp else 10.dp
                ),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(if (compact) 15.dp else 16.dp)
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
private fun CalendarChevronButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f))
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun CalendarChromeIconButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    buttonSize: Dp = 48.dp
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

private fun preferredSelectionForPeriod(
    anchorDate: LocalDate,
    mode: CalendarViewMode,
    selectedDate: LocalDate,
    today: LocalDate,
    eventsByDate: Map<LocalDate, List<CalendarTaskEventUiState>>
): LocalDate {
    if (periodContainsDate(anchorDate, mode, selectedDate)) return selectedDate
    if (periodContainsDate(anchorDate, mode, today)) return today

    return firstEventDateInPeriod(anchorDate, mode, eventsByDate)
        ?: periodStartDate(anchorDate, mode)
}

private fun periodContainsDate(
    anchorDate: LocalDate,
    mode: CalendarViewMode,
    date: LocalDate
): Boolean {
    return when (mode) {
        CalendarViewMode.MONTH -> YearMonth.from(anchorDate).contains(date)
        CalendarViewMode.WEEK -> weekDates(anchorDate).contains(date)
        CalendarViewMode.YEAR -> anchorDate.year == date.year
    }
}

private fun firstEventDateInPeriod(
    anchorDate: LocalDate,
    mode: CalendarViewMode,
    eventsByDate: Map<LocalDate, List<CalendarTaskEventUiState>>
): LocalDate? {
    return eventsByDate.keys
        .filter { date -> periodContainsDate(anchorDate, mode, date) }
        .minOrNull()
}

private fun periodStartDate(
    anchorDate: LocalDate,
    mode: CalendarViewMode
): LocalDate {
    return when (mode) {
        CalendarViewMode.MONTH -> YearMonth.from(anchorDate).atDay(1)
        CalendarViewMode.WEEK -> weekDates(anchorDate).first()
        CalendarViewMode.YEAR -> LocalDate.of(anchorDate.year, 1, 1)
    }
}

private fun shiftDateByMode(
    date: LocalDate,
    mode: CalendarViewMode,
    amount: Long
): LocalDate {
    return when (mode) {
        CalendarViewMode.MONTH -> date.plusMonths(amount)
        CalendarViewMode.WEEK -> date.plusWeeks(amount)
        CalendarViewMode.YEAR -> date.plusYears(amount)
    }
}

private fun weekDates(anchorDate: LocalDate): List<LocalDate> {
    val start = anchorDate.minusDays((anchorDate.dayOfWeek.value - 1).toLong())
    return List(7) { offset -> start.plusDays(offset.toLong()) }
}

private fun parseCalendarDate(value: String): LocalDate {
    return runCatching { LocalDate.parse(value) }.getOrElse { LocalDate.now() }
}

private fun formatCalendarPeriodLabel(
    viewMode: CalendarViewMode,
    anchorDate: LocalDate
): String {
    return when (viewMode) {
        CalendarViewMode.MONTH -> DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH).format(anchorDate)
        CalendarViewMode.WEEK -> formatWeekRangeLabel(anchorDate)
        CalendarViewMode.YEAR -> anchorDate.year.toString()
    }
}

private fun formatCalendarPeriodSummary(
    viewMode: CalendarViewMode,
    anchorDate: LocalDate,
    eventsByDate: Map<LocalDate, List<CalendarTaskEventUiState>>
): String {
    return when (viewMode) {
        CalendarViewMode.MONTH -> {
            val month = YearMonth.from(anchorDate)
            val totalEvents = eventsByDate
                .filterKeys(month::contains)
                .values
                .sumOf(List<CalendarTaskEventUiState>::size)
            val activeDays = eventsByDate.keys.count(month::contains)
            formatPeriodSummary(totalEvents, activeDays, "d")
        }
        CalendarViewMode.WEEK -> {
            val dates = weekDates(anchorDate)
            val totalEvents = dates.sumOf { date -> eventsByDate[date].orEmpty().size }
            val activeDays = dates.count { date -> eventsByDate[date].orEmpty().isNotEmpty() }
            formatPeriodSummary(totalEvents, activeDays, "d")
        }
        CalendarViewMode.YEAR -> {
            val totalEvents = eventsByDate
                .filterKeys { date -> date.year == anchorDate.year }
                .values
                .sumOf(List<CalendarTaskEventUiState>::size)
            val activeMonths = eventsByDate.keys
                .filter { date -> date.year == anchorDate.year }
                .map(LocalDate::getMonthValue)
                .distinct()
                .size
            formatPeriodSummary(totalEvents, activeMonths, "m")
        }
    }
}

private fun formatPeriodSummary(
    totalEvents: Int,
    activeUnits: Int,
    unitLabel: String
): String {
    return when {
        totalEvents == 0 -> "0 items"
        totalEvents == 1 -> "1 item"
        else -> "$totalEvents items · $activeUnits $unitLabel"
    }
}

private fun formatWeekRangeLabel(anchorDate: LocalDate): String {
    val dates = weekDates(anchorDate)
    val start = dates.first()
    val end = dates.last()

    return when {
        start.year != end.year -> {
            "${start.dayOfMonth} ${start.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} - " +
                "${end.dayOfMonth} ${end.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${end.year}"
        }
        start.month != end.month -> {
            "${start.dayOfMonth} ${start.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} - " +
                "${end.dayOfMonth} ${end.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${end.year}"
        }
        else -> {
            "${start.dayOfMonth}-${end.dayOfMonth} ${end.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${end.year}"
        }
    }
}

private fun formatWeekRowDate(date: LocalDate): String {
    val formatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)
    return formatter.format(date)
}

private fun weekRowSummary(events: List<CalendarTaskEventUiState>): String {
    return when {
        events.isEmpty() -> "No scheduled items"
        events.size == 1 -> "${calendarEventMeta(events.first())} at ${formatEventTime(events.first())}"
        else -> "${events.size} scheduled items"
    }
}

private fun selectedRibbonSummary(events: List<CalendarTaskEventUiState>): String {
    return when {
        events.isEmpty() -> "Free"
        events.size == 1 -> formatEventTime(events.first())
        else -> "${events.size} items"
    }
}

private fun selectedRibbonSummary(
    events: List<CalendarTaskEventUiState>,
    selectedDate: LocalDate,
    today: LocalDate
): String {
    return if (selectedDate != today) {
        "Today"
    } else {
        selectedRibbonSummary(events)
    }
}

private fun compareDateForDirection(
    from: LocalDate,
    to: LocalDate
): Int {
    return when {
        to.isAfter(from) -> 1
        to.isBefore(from) -> -1
        else -> 0
    }
}

private fun formatSelectedRibbonDate(date: LocalDate): String {
    val formatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    return formatter.format(date)
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

@Composable
private fun <T> CalendarSwipeViewport(
    targetState: T,
    motionDirection: Int,
    onSwipeToPrevious: () -> Unit,
    onSwipeToNext: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit
) {
    val scope = rememberCoroutineScope()
    var viewportWidthPx by remember { mutableFloatStateOf(1f) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .onSizeChanged { viewportWidthPx = it.width.toFloat().coerceAtLeast(1f) }
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    dragOffsetPx = (dragOffsetPx + delta).coerceIn(
                        minimumValue = -viewportWidthPx,
                        maximumValue = viewportWidthPx
                    )
                },
                onDragStopped = { velocity ->
                    val threshold = viewportWidthPx * 0.18f
                    val targetDirection = when {
                        dragOffsetPx <= -threshold || velocity < -1100f -> 1
                        dragOffsetPx >= threshold || velocity > 1100f -> -1
                        else -> 0
                    }

                    scope.launch {
                        if (targetDirection == 0) {
                            animate(
                                initialValue = dragOffsetPx,
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = 0.88f,
                                    stiffness = 520f
                                )
                            ) { value, _ ->
                                dragOffsetPx = value
                            }
                        } else {
                            if (targetDirection > 0) {
                                onSwipeToNext()
                            } else {
                                onSwipeToPrevious()
                            }

                            animate(
                                initialValue = dragOffsetPx,
                                targetValue = 0f,
                                animationSpec = tween(
                                    durationMillis = 220,
                                    easing = FastOutSlowInEasing
                                )
                            ) { value, _ ->
                                dragOffsetPx = value
                            }
                        }
                    }
                }
            )
    ) {
        AnimatedContent(
            targetState = targetState,
            transitionSpec = {
                when {
                    motionDirection > 0 -> {
                        (fadeIn(animationSpec = tween(220)) + slideInHorizontally { fullWidth -> fullWidth / 4 }) togetherWith
                            (fadeOut(animationSpec = tween(180)) + slideOutHorizontally { fullWidth -> -fullWidth / 4 }) using
                            SizeTransform(clip = false)
                    }
                    motionDirection < 0 -> {
                        (fadeIn(animationSpec = tween(220)) + slideInHorizontally { fullWidth -> -fullWidth / 4 }) togetherWith
                            (fadeOut(animationSpec = tween(180)) + slideOutHorizontally { fullWidth -> fullWidth / 4 }) using
                            SizeTransform(clip = false)
                    }
                    else -> {
                        (fadeIn(animationSpec = tween(180)) + slideInVertically { it / 8 }) togetherWith
                            (fadeOut(animationSpec = tween(140)) + slideOutVertically { -it / 8 }) using
                            SizeTransform(clip = false)
                    }
                }
            },
            label = "calendarSwipeViewport"
        ) { state ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = dragOffsetPx
                        alpha = 1f - min(abs(dragOffsetPx) / (viewportWidthPx * 1.6f), 0.16f)
                    }
            ) {
                content(state)
            }
        }
    }
}
