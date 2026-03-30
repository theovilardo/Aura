package com.theveloper.aura.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.EaseOutCubic
import android.view.HapticFeedbackConstants
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.theveloper.aura.BuildConfig
import com.theveloper.aura.ui.screen.CreateAutomationScreen
import com.theveloper.aura.ui.screen.CreateEventScreen
import com.theveloper.aura.ui.screen.CreateReminderScreen
import com.theveloper.aura.ui.screen.CreateTaskScreen
import com.theveloper.aura.ui.screen.SystemPanelScreen
import com.theveloper.aura.ui.screen.CalendarDayScreen
import com.theveloper.aura.ui.screen.CloudSettingsScreen
import com.theveloper.aura.ui.screen.DeveloperSettingsScreen
import com.theveloper.aura.ui.screen.HomeScreen
import com.theveloper.aura.ui.screen.IntelligenceApiSettingsScreen
import com.theveloper.aura.ui.screen.IntelligenceModelLibraryScreen
import com.theveloper.aura.ui.screen.IntelligenceSettingsScreen
import com.theveloper.aura.ui.screen.EcosystemSettingsScreen
import com.theveloper.aura.ui.screen.SettingsScreen
import com.theveloper.aura.ui.screen.TaskCreationMode
import com.theveloper.aura.ui.screen.TaskDetailScreen
import com.theveloper.aura.ui.screen.TaskEditScreen
import com.theveloper.aura.ui.screen.TaskMarkdownEditorScreen
import com.theveloper.aura.ui.screen.TaskMarkdownReaderScreen
import com.theveloper.aura.ui.screen.TasksCalendarScreen
import com.theveloper.aura.ui.screen.TasksScreen
import com.theveloper.aura.ui.theme.AuraFloatingBarColors
import com.theveloper.aura.ui.theme.auraFloatingBarColors
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import java.time.LocalDate

private const val HOME_ROUTE = "home"
private const val TASKS_ROUTE = "tasks"
private const val HABITS_ROUTE = "habits"
private const val CALENDAR_DAY_BASE_ROUTE = "calendar_day"
private const val CALENDAR_DAY_ROUTE = "$CALENDAR_DAY_BASE_ROUTE/{date}"
private const val TASK_DETAIL_BASE_ROUTE = "task_detail"
private const val TASK_DETAIL_ROUTE = "$TASK_DETAIL_BASE_ROUTE/{taskId}"
private const val TASK_EDIT_BASE_ROUTE = "task_detail_edit"
private const val TASK_EDIT_ROUTE = "$TASK_EDIT_BASE_ROUTE/{taskId}"
private const val TASK_NOTE_READER_BASE_ROUTE = "task_detail_note"
private const val TASK_NOTE_READER_ROUTE = "$TASK_NOTE_READER_BASE_ROUTE/{taskId}/{componentId}"
private const val TASK_NOTE_EDITOR_BASE_ROUTE = "task_detail_edit_note"
private const val TASK_NOTE_EDITOR_ROUTE = "$TASK_NOTE_EDITOR_BASE_ROUTE/{taskId}/{componentId}"
private const val SETTINGS_ROUTE = "settings"
private const val SETTINGS_INTELLIGENCE_ROUTE = "settings/intelligence"
private const val SETTINGS_INTELLIGENCE_APIS_ROUTE = "settings/intelligence/apis"
private const val SETTINGS_INTELLIGENCE_LIBRARY_ROUTE = "settings/intelligence/library"
private const val SETTINGS_CLOUD_ROUTE = "settings/cloud"
private const val SETTINGS_DEVELOPER_ROUTE = "settings/developer"
private const val SETTINGS_ECOSYSTEM_ROUTE = "settings/ecosystem"
private const val CREATE_TASK_BASE_ROUTE = "create_task"
private const val CREATE_TASK_ROUTE =
    "$CREATE_TASK_BASE_ROUTE?mode={mode}&input={input}&autoSubmit={autoSubmit}"

// Multi-Creation-Type routes
private const val CREATE_REMINDER_BASE_ROUTE = "create_reminder"
private const val CREATE_REMINDER_ROUTE = "$CREATE_REMINDER_BASE_ROUTE?mode={mode}"
private const val CREATE_AUTOMATION_BASE_ROUTE = "create_automation"
private const val CREATE_AUTOMATION_ROUTE = "$CREATE_AUTOMATION_BASE_ROUTE?mode={mode}"
private const val CREATE_EVENT_BASE_ROUTE = "create_event"
private const val CREATE_EVENT_ROUTE = "$CREATE_EVENT_BASE_ROUTE?mode={mode}"
private const val SYSTEM_PANEL_ROUTE = "system_panel"
private const val REMINDER_DETAIL_BASE_ROUTE = "reminder_detail"
private const val REMINDER_DETAIL_ROUTE = "$REMINDER_DETAIL_BASE_ROUTE/{reminderId}"
private const val AUTOMATION_DETAIL_BASE_ROUTE = "automation_detail"
private const val AUTOMATION_DETAIL_ROUTE = "$AUTOMATION_DETAIL_BASE_ROUTE/{automationId}"
private const val EVENT_DETAIL_BASE_ROUTE = "event_detail"
private const val EVENT_DETAIL_ROUTE = "$EVENT_DETAIL_BASE_ROUTE/{eventId}"
private const val ROOT_TRANSITION_DURATION_MS = 430
private const val STACK_TRANSITION_DURATION_MS = 400
private const val MODAL_TRANSITION_DURATION_MS = 420
private const val BOTTOM_BAR_TRANSITION_DURATION_MS = 280
private const val CREATE_ITEM_KEY = "create"

val LocalAuraBottomBarHeight = staticCompositionLocalOf<Dp> { 0.dp }

@Composable
fun AuraApp() {
    val navController = rememberNavController()
    var quickPrompt by rememberSaveable { mutableStateOf("") }
    var bottomBarHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val rootRoutes = remember { setOf(HOME_ROUTE, TASKS_ROUTE, SETTINGS_ROUTE) }
    val visibleEntries by navController.visibleEntries.collectAsState()
    val topVisibleRoute = remember(visibleEntries) {
        normalizedRoute(visibleEntries.lastOrNull()?.destination?.route)
    }
    val bottomBarVisible = remember(topVisibleRoute, rootRoutes) {
        topVisibleRoute in rootRoutes
    }
    val shouldComposeBottomBar = remember(visibleEntries, rootRoutes) {
        visibleEntries.any { entry ->
            normalizedRoute(entry.destination.route) in rootRoutes
        }
    }
    val selectedBottomBarRoute = remember(visibleEntries, rootRoutes) {
        visibleEntries
            .asReversed()
            .firstOrNull { entry ->
                normalizedRoute(entry.destination.route) in rootRoutes
            }
            ?.destination
            ?.route
            .let(::normalizedRoute)
    }
    val bottomBarHeight = with(density) { bottomBarHeightPx.toDp() }
    val animatedBottomBarHeight by animateDpAsState(
        targetValue = if (bottomBarVisible) bottomBarHeight else 0.dp,
        animationSpec = if (BuildConfig.DEBUG) {
            snap()
        } else {
            tween(
                durationMillis = BOTTOM_BAR_TRANSITION_DURATION_MS,
                easing = FastOutSlowInEasing
            )
        },
        label = "auraBottomBarHeight"
    )

    CompositionLocalProvider(LocalAuraBottomBarHeight provides animatedBottomBarHeight) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            NavHost(
                navController = navController,
                startDestination = HOME_ROUTE,
                modifier = Modifier.fillMaxSize(),
                enterTransition = { auraEnterTransition() },
                exitTransition = { auraExitTransition() },
                popEnterTransition = { auraPopEnterTransition() },
                popExitTransition = { auraPopExitTransition() }
            ) {
            composable(HOME_ROUTE) {
                HomeScreen(
                    onNavigateToTaskDetail = { taskId ->
                        navController.navigate("task_detail/$taskId")
                    }
                )
            }
            composable(TASKS_ROUTE) {
                TasksCalendarScreen(
                    onNavigateToHabits = { navController.navigate(HABITS_ROUTE) },
                    onNavigateToDay = { date ->
                        navController.navigate(buildCalendarDayRoute(date))
                    },
                    onNavigateToTaskDetail = { taskId ->
                        navController.navigate("task_detail/$taskId")
                    }
                )
            }
            composable(HABITS_ROUTE) {
                SecondaryScreenFrame {
                    TasksScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
            composable(CALENDAR_DAY_ROUTE) { backStackEntry ->
                val date = backStackEntry.arguments?.getString("date") ?: ""
                SecondaryScreenFrame {
                    CalendarDayScreen(
                        initialDateValue = date,
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToTaskDetail = { taskId ->
                            navController.navigate("task_detail/$taskId")
                        }
                    )
                }
            }
            composable(TASK_DETAIL_ROUTE) { backStackEntry ->
                val taskId = backStackEntry.arguments?.getString("taskId") ?: ""
                SecondaryScreenFrame {
                    TaskDetailScreen(
                        taskId = taskId,
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToEdit = { editableTaskId ->
                            navController.navigate("task_detail_edit/$editableTaskId")
                        },
                        onNavigateToNotesReader = { readableTaskId, componentId ->
                            navController.navigate(buildTaskNoteReaderRoute(readableTaskId, componentId))
                        }
                    )
                }
            }
            composable(TASK_NOTE_READER_ROUTE) { backStackEntry ->
                val taskId = backStackEntry.arguments?.getString("taskId") ?: ""
                val componentId = backStackEntry.arguments?.getString("componentId") ?: ""
                SecondaryScreenFrame {
                    TaskMarkdownReaderScreen(
                        taskId = taskId,
                        componentId = componentId,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
            composable(TASK_EDIT_ROUTE) { backStackEntry ->
                val taskId = backStackEntry.arguments?.getString("taskId") ?: ""
                SecondaryScreenFrame {
                    TaskEditScreen(
                        taskId = taskId,
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToNotesEditor = { editableTaskId, componentId ->
                            navController.navigate(buildTaskNoteEditorRoute(editableTaskId, componentId))
                        },
                        editorStateHandle = backStackEntry.savedStateHandle
                    )
                }
            }
            composable(TASK_NOTE_EDITOR_ROUTE) { backStackEntry ->
                val taskId = backStackEntry.arguments?.getString("taskId") ?: ""
                val componentId = backStackEntry.arguments?.getString("componentId") ?: ""
                SecondaryScreenFrame {
                    TaskMarkdownEditorScreen(
                        taskId = taskId,
                        componentId = componentId,
                        editorStateHandle = navController.previousBackStackEntry?.savedStateHandle,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
            composable(
                route = CREATE_TASK_ROUTE,
                arguments = listOf(
                    navArgument("mode") {
                        type = NavType.StringType
                        defaultValue = TaskCreationMode.PROMPT.navValue
                    },
                    navArgument("input") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument("autoSubmit") {
                        type = NavType.BoolType
                        defaultValue = false
                    }
                )
            ) {
                SecondaryScreenFrame {
                    CreateTaskScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
            // ── Multi-Creation-Type Screens ─────────────────────────────────
            composable(
                route = CREATE_REMINDER_ROUTE,
                arguments = listOf(
                    navArgument("mode") {
                        type = NavType.StringType
                        defaultValue = "prompt"
                    }
                )
            ) {
                SecondaryScreenFrame {
                    CreateReminderScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
            composable(
                route = CREATE_AUTOMATION_ROUTE,
                arguments = listOf(
                    navArgument("mode") {
                        type = NavType.StringType
                        defaultValue = "prompt"
                    }
                )
            ) {
                SecondaryScreenFrame {
                    CreateAutomationScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
            composable(
                route = CREATE_EVENT_ROUTE,
                arguments = listOf(
                    navArgument("mode") {
                        type = NavType.StringType
                        defaultValue = "prompt"
                    }
                )
            ) {
                SecondaryScreenFrame {
                    CreateEventScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
            composable(SYSTEM_PANEL_ROUTE) {
                SecondaryScreenFrame {
                    SystemPanelScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
            composable(SETTINGS_ROUTE) {
                SettingsScreen(
                    onOpenIntelligenceSettings = { navController.navigate(SETTINGS_INTELLIGENCE_ROUTE) },
                    onOpenCloudSettings = { navController.navigate(SETTINGS_CLOUD_ROUTE) },
                    onOpenEcosystemSettings = { navController.navigate(SETTINGS_ECOSYSTEM_ROUTE) },
                    onOpenDeveloperSettings = { navController.navigate(SETTINGS_DEVELOPER_ROUTE) }
                )
            }
            composable(SETTINGS_INTELLIGENCE_ROUTE) {
                SecondaryScreenFrame {
                    IntelligenceSettingsScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onOpenApiSettings = { navController.navigate(SETTINGS_INTELLIGENCE_APIS_ROUTE) },
                        onOpenModelLibrary = { navController.navigate(SETTINGS_INTELLIGENCE_LIBRARY_ROUTE) }
                    )
                }
            }
            composable(SETTINGS_INTELLIGENCE_APIS_ROUTE) {
                SecondaryScreenFrame {
                    IntelligenceApiSettingsScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
            composable(SETTINGS_INTELLIGENCE_LIBRARY_ROUTE) {
                SecondaryScreenFrame {
                    IntelligenceModelLibraryScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
            composable(SETTINGS_CLOUD_ROUTE) {
                SecondaryScreenFrame {
                    CloudSettingsScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
            composable(SETTINGS_ECOSYSTEM_ROUTE) {
                SecondaryScreenFrame {
                    EcosystemSettingsScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
            composable(SETTINGS_DEVELOPER_ROUTE) {
                SecondaryScreenFrame {
                    DeveloperSettingsScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
            }

            AuraBottomBar(
                navController = navController,
                selectedRoute = selectedBottomBarRoute,
                visible = bottomBarVisible,
                keepComposed = shouldComposeBottomBar,
                onMeasuredHeightChanged = { bottomBarHeightPx = it },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun SecondaryScreenFrame(
    content: @Composable () -> Unit
) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val scale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.985f,
        animationSpec = tween(durationMillis = 320, easing = EaseOutCubic),
        label = "secondaryScreenScale"
    )
    val cornerRadius by animateDpAsState(
        targetValue = if (entered) 0.dp else 26.dp,
        animationSpec = tween(durationMillis = 360, easing = EaseOutCubic),
        label = "secondaryScreenCorner"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(cornerRadius))
    ) {
        content()
    }
}

@Composable
fun AuraBottomBar(
    navController: NavHostController,
    selectedRoute: String,
    visible: Boolean,
    keepComposed: Boolean,
    onMeasuredHeightChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = auraFloatingBarColors()
    var isCreateSheetExpanded by remember { mutableStateOf(false) }
    var collapsedBarHeightPx by remember { mutableIntStateOf(0) }
    val optionRowHeight = 76.dp
    val optionDividerHeight = 1.dp
    val optionSectionVerticalPadding = 10.dp
    val footerHeight = 96.dp
    val optionCount = 5

    LaunchedEffect(visible) {
        if (!visible) {
            isCreateSheetExpanded = false
        }
    }
    LaunchedEffect(selectedRoute) {
        isCreateSheetExpanded = false
    }
    BackHandler(enabled = isCreateSheetExpanded) {
        isCreateSheetExpanded = false
    }

    val items = listOf(
        BottomBarItem(key = HOME_ROUTE, label = "Home", icon = Icons.Rounded.Home),
        BottomBarItem(key = TASKS_ROUTE, label = "Calendar", icon = Icons.Rounded.CalendarMonth),
        BottomBarItem(key = SETTINGS_ROUTE, label = "Settings", icon = Icons.Rounded.Settings),
        BottomBarItem(key = CREATE_ITEM_KEY, label = "Create", icon = Icons.Rounded.Add, accent = true)
    )
    val createOptions = remember {
        listOf(
            CreateSheetOption(key = "system", label = "System", icon = Icons.Rounded.Tune),
            CreateSheetOption(key = "reminder", label = "Reminder", icon = Icons.Rounded.Alarm),
            CreateSheetOption(key = "automation", label = "Automation", icon = Icons.Rounded.Bolt),
            CreateSheetOption(key = "event", label = "Event", icon = Icons.Rounded.Event),
            CreateSheetOption(key = "task", label = "Task", icon = Icons.Rounded.TaskAlt)
        )
    }
    val expandedOptionsHeight = remember {
        (optionSectionVerticalPadding * 2) +
            (optionRowHeight * optionCount) +
            (optionDividerHeight * (optionCount - 1))
    }
    val containerBorderColor by animateColorAsState(
        targetValue = if (isCreateSheetExpanded) colors.mutedIcon else colors.outline,
        animationSpec = spring(
            dampingRatio = 0.92f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "bottomBarBorder"
    )
    val containerShadowElevation by animateDpAsState(
        targetValue = if (isCreateSheetExpanded) 14.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = 0.9f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "bottomBarSurfaceShadow"
    )
    val surfaceCornerRadius by animateDpAsState(
        targetValue = if (isCreateSheetExpanded) 48.dp else 60.dp,
        animationSpec = spring(
            dampingRatio = 0.92f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "bottomBarCornerRadius"
    )
    val optionsContainerHeight by animateDpAsState(
        targetValue = if (isCreateSheetExpanded) expandedOptionsHeight else 0.dp,
        animationSpec = spring(
            dampingRatio = 0.9f,
            stiffness = Spring.StiffnessLow
        ),
        label = "bottomBarOptionsHeight"
    )
    val footerDividerHeight by animateDpAsState(
        targetValue = if (isCreateSheetExpanded) 1.dp else 0.dp,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "bottomBarDividerHeight"
    )

    val shape = AbsoluteSmoothCornerShape(
        cornerRadiusBL = surfaceCornerRadius,
        smoothnessAsPercentBL = 70,
        cornerRadiusBR = surfaceCornerRadius,
        smoothnessAsPercentBR = 70,
        cornerRadiusTL = surfaceCornerRadius,
        smoothnessAsPercentTL = 70,
        cornerRadiusTR = surfaceCornerRadius,
        smoothnessAsPercentTR = 70
    )
        //RoundedCornerShape(surfaceCornerRadius)

    if (keepComposed) {
        Box(
            modifier = modifier.fillMaxSize()
        ) {
            AnimatedVisibility(
                visible = isCreateSheetExpanded,
                modifier = Modifier
                    .fillMaxSize(),
                enter = fadeIn(animationSpec = tween(durationMillis = 220)),
                exit = fadeOut(animationSpec = tween(durationMillis = 180))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            isCreateSheetExpanded = false
                    }
                )
            }

            AnimatedVisibility(
                visible = visible,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = if (BuildConfig.DEBUG) {
                    EnterTransition.None
                } else {
                    slideInVertically(
                        animationSpec = tween(
                            durationMillis = BOTTOM_BAR_TRANSITION_DURATION_MS,
                            easing = FastOutSlowInEasing
                        ),
                        initialOffsetY = { fullHeight -> fullHeight / 2 }
                    ) + fadeIn(
                        animationSpec = tween(
                            durationMillis = BOTTOM_BAR_TRANSITION_DURATION_MS,
                            delayMillis = 30
                        )
                    )
                },
                exit = if (BuildConfig.DEBUG) {
                    ExitTransition.None
                } else {
                    slideOutVertically(
                        animationSpec = tween(
                            durationMillis = BOTTOM_BAR_TRANSITION_DURATION_MS,
                            easing = FastOutSlowInEasing
                        ),
                        targetOffsetY = { fullHeight -> fullHeight / 2 }
                    ) + fadeOut(
                        animationSpec = tween(durationMillis = BOTTOM_BAR_TRANSITION_DURATION_MS - 40)
                    )
                }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp)
                        .onSizeChanged { size ->
                            if (!isCreateSheetExpanded || collapsedBarHeightPx == 0) {
                                collapsedBarHeightPx = size.height
                                onMeasuredHeightChanged(size.height)
                            }
                        }
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter),
                        shape = shape,
                        color = colors.container,
                        border = BorderStroke(width = 2.dp, color = containerBorderColor),
                        shadowElevation = containerShadowElevation
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(optionsContainerHeight)
                                    .clipToBounds()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            start = 14.dp,
                                            top = optionSectionVerticalPadding,
                                            end = 14.dp,
                                            bottom = optionSectionVerticalPadding
                                        )
                                ) {
                                    createOptions.forEachIndexed { index, option ->
                                        AnimatedVisibility(
                                            visible = isCreateSheetExpanded,
                                            enter = if (BuildConfig.DEBUG) {
                                                EnterTransition.None
                                            } else {
                                                slideInVertically(
                                                    animationSpec = tween(
                                                        durationMillis = 320,
                                                        delayMillis = 56 + (index * 34),
                                                        easing = FastOutSlowInEasing
                                                    ),
                                                    initialOffsetY = { it / 4 }
                                                ) + fadeIn(
                                                    animationSpec = tween(
                                                        durationMillis = 220,
                                                        delayMillis = 36 + (index * 34)
                                                    )
                                                )
                                            },
                                            exit = if (BuildConfig.DEBUG) {
                                                ExitTransition.None
                                            } else {
                                                slideOutVertically(
                                                    animationSpec = tween(
                                                        durationMillis = 150,
                                                        delayMillis = (createOptions.lastIndex - index) * 12,
                                                        easing = FastOutSlowInEasing
                                                    ),
                                                    targetOffsetY = { it / 6 }
                                                ) + fadeOut(
                                                    animationSpec = tween(
                                                        durationMillis = 120,
                                                        delayMillis = (createOptions.lastIndex - index) * 10
                                                    )
                                                )
                                            }
                                        ) {
                                            Column(modifier = Modifier.fillMaxWidth()) {
                                                CreateSheetOptionRow(
                                                    option = option,
                                                    colors = colors,
                                                    rowHeight = optionRowHeight,
                                                    onClick = {
                                                        isCreateSheetExpanded = false
                                                        when (option.key) {
                                                            "system" -> navController.navigate(SYSTEM_PANEL_ROUTE)
                                                            "reminder" -> navController.navigate("$CREATE_REMINDER_BASE_ROUTE?mode=prompt")
                                                            "automation" -> navController.navigate("$CREATE_AUTOMATION_BASE_ROUTE?mode=prompt")
                                                            "event" -> navController.navigate("$CREATE_EVENT_BASE_ROUTE?mode=prompt")
                                                            else -> navController.navigate(
                                                                buildCreateTaskRoute(mode = TaskCreationMode.MANUAL)
                                                            )
                                                        }
                                                    }
                                                )
                                                if (index < createOptions.lastIndex) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(optionDividerHeight)
                                                            .background(colors.outline.copy(alpha = 0.46f))
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (footerDividerHeight > 0.dp) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .height(footerDividerHeight)
                                        .background(colors.outline.copy(alpha = 0.58f))
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(footerHeight)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    items.filter { it.key != CREATE_ITEM_KEY }.forEach { item ->
                                        Box(
                                            modifier = Modifier.weight(1f),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            androidx.compose.animation.AnimatedVisibility(
                                                visible = !isCreateSheetExpanded,
                                                enter = if (BuildConfig.DEBUG) {
                                                    EnterTransition.None
                                                } else {
                                                    slideInVertically(
                                                        animationSpec = tween(
                                                            durationMillis = 240,
                                                            easing = FastOutSlowInEasing
                                                        ),
                                                        initialOffsetY = { it / 3 }
                                                    ) + fadeIn(animationSpec = tween(durationMillis = 180))
                                                },
                                                exit = if (BuildConfig.DEBUG) {
                                                    ExitTransition.None
                                                } else {
                                                    slideOutVertically(
                                                        animationSpec = tween(
                                                            durationMillis = 180,
                                                            easing = FastOutSlowInEasing
                                                        ),
                                                        targetOffsetY = { it / 3 }
                                                    ) + fadeOut(animationSpec = tween(durationMillis = 120))
                                                }
                                            ) {
                                                AuraBottomBarItem(
                                                    item = item,
                                                    selected = item.key == selectedRoute,
                                                    rotationDegrees = 0f,
                                                    buttonSize = 62.dp,
                                                    iconSize = 30.dp,
                                                    onClick = { navController.navigateToRootRoute(item.key) },
                                                    colors = colors
                                                )
                                            }
                                        }
                                    }

                                    Box(
                                        modifier = Modifier.weight(1f),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AuraBottomBarItem(
                                            item = items.first { it.key == CREATE_ITEM_KEY },
                                            selected = isCreateSheetExpanded,
                                            rotationDegrees = if (isCreateSheetExpanded) 45f else 0f,
                                            buttonSize = if (isCreateSheetExpanded) 70.dp else 62.dp,
                                            iconSize = if (isCreateSheetExpanded) 34.dp else 30.dp,
                                            onClick = { isCreateSheetExpanded = !isCreateSheetExpanded },
                                            colors = colors
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
}

private data class BottomBarItem(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val accent: Boolean = false
)

private data class CreateSheetOption(
    val key: String,
    val label: String,
    val icon: ImageVector
)

@Composable
private fun AuraBottomBarItem(
    item: BottomBarItem,
    selected: Boolean,
    rotationDegrees: Float,
    buttonSize: Dp,
    iconSize: Dp,
    onClick: () -> Unit,
    colors: AuraFloatingBarColors
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    val pulseScale = remember { Animatable(1f) }
    val containerColor by animateColorAsState(
        targetValue = when {
            item.accent -> colors.accentCircle
            selected -> colors.selectedCircle
            else -> colors.mutedCircle
        },
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "bottomBarContainer"
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            item.accent -> colors.accentIcon
            selected -> colors.selectedIcon
            else -> colors.mutedIcon
        },
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "bottomBarIcon"
    )
    val shadowElevation by animateDpAsState(
        targetValue = when {
            selected -> 10.dp
            item.accent -> 8.dp
            else -> 0.dp
        },
        animationSpec = tween(durationMillis = 200),
        label = "bottomBarShadow"
    )

    val animatedButtonSize by animateDpAsState(
        targetValue = buttonSize,
        animationSpec = spring(
            dampingRatio = 0.78f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "bottomBarButtonSize"
    )
    val animatedIconSize by animateDpAsState(
        targetValue = iconSize,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "bottomBarIconSize"
    )
    val animatedRotation by animateFloatAsState(
        targetValue = rotationDegrees,
        animationSpec = spring(
            dampingRatio = 0.82f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "bottomBarRotation"
    )

    Surface(
        modifier = Modifier
            .size(animatedButtonSize)
            .graphicsLayer {
                scaleX = pulseScale.value
                scaleY = pulseScale.value
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                scope.launch {
                    pulseScale.stop()
                    pulseScale.animateTo(
                        targetValue = 0.92f,
                        animationSpec = tween(
                            durationMillis = 72,
                            easing = FastOutSlowInEasing
                        )
                    )
                    pulseScale.animateTo(
                        targetValue = 1.035f,
                        animationSpec = spring(
                            dampingRatio = 0.58f,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                    pulseScale.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = 0.82f,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                }
                onClick()
            },
        shape = CircleShape,
        color = containerColor,
        shadowElevation = shadowElevation
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = contentColor,
                modifier = Modifier
                    .size(animatedIconSize)
                    .graphicsLayer {
                        rotationZ = animatedRotation
                    }
            )
        }
    }
}

@Composable
private fun CreateSheetOptionRow(
    option: CreateSheetOption,
    onClick: () -> Unit,
    colors: AuraFloatingBarColors,
    rowHeight: Dp
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    val pulseScale = remember { Animatable(1f) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .clip(RoundedCornerShape(28.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                scope.launch {
                    pulseScale.stop()
                    pulseScale.snapTo(0.94f)
                    pulseScale.animateTo(
                        targetValue = 1.03f,
                        animationSpec = spring(
                            dampingRatio = 0.7f,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                    pulseScale.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = 0.82f,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                }
                onClick()
            }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier
                .size(52.dp)
                .graphicsLayer {
                    scaleX = pulseScale.value
                    scaleY = pulseScale.value
                },
            shape = CircleShape,
            color = colors.mutedCircle
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = option.icon,
                    contentDescription = option.label,
                    tint = colors.mutedIcon,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = option.label.uppercase(),
            style = MaterialTheme.typography.titleMedium.copy(
                color = colors.promptText,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.1.sp
            )
        )
    }
}

@Composable
private fun QuickPromptBar(
    prompt: String,
    onPromptChange: (String) -> Unit,
    onSubmit: () -> Unit,
    colors: AuraFloatingBarColors
) {
    val sendEnabled = prompt.trim().isNotEmpty()
    var isFocused by remember { mutableStateOf(false) }
    val view = LocalView.current
    val outlineColor by animateColorAsState(
        targetValue = if (isFocused) colors.activeOutline else colors.outline,
        animationSpec = tween(durationMillis = 180),
        label = "quickPromptOutline"
    )
    val sendScale by animateFloatAsState(
        targetValue = if (sendEnabled) 1f else 0.9f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "quickPromptSendScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .padding(horizontal = 0.dp, vertical = 0.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ),
            shape = CircleShape,
            color = colors.container,
            border = BorderStroke(width = 2.dp, color = outlineColor),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 68.dp)
                    .padding(start = 14.dp, top = 9.dp, end = 10.dp, bottom = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(38.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = "AI prompt",
                        tint = colors.assistantIcon,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                BasicTextField(
                    value = prompt,
                    onValueChange = onPromptChange,
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { isFocused = it.isFocused },
                    textStyle = MaterialTheme.typography.titleLarge.copy(
                        color = colors.promptText,
                        fontWeight = FontWeight.SemiBold
                    ),
                    cursorBrush = SolidColor(colors.promptText),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (sendEnabled) {
                                onSubmit()
                            }
                        }
                    ),
                    minLines = 1,
                    maxLines = 4,
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (prompt.isBlank()) {
                                Text(
                                    text = "Create task...",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        color = colors.placeholder,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .graphicsLayer {
                            scaleX = sendScale
                            scaleY = sendScale
                        },
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = {
                            if (sendEnabled) {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                            onSubmit()
                        },
                        enabled = sendEnabled,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Create task",
                            tint = if (sendEnabled) colors.promptText else colors.placeholder.copy(alpha = 0.42f),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun NavHostController.navigateToRootRoute(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun buildCreateTaskRoute(
    mode: TaskCreationMode,
    input: String = "",
    autoSubmit: Boolean = false
): String {
    return "$CREATE_TASK_BASE_ROUTE?mode=${mode.navValue}&input=${Uri.encode(input)}&autoSubmit=$autoSubmit"
}

private fun buildTaskNoteEditorRoute(
    taskId: String,
    componentId: String
): String {
    return "task_detail_edit_note/${Uri.encode(taskId)}/${Uri.encode(componentId)}"
}

private fun buildTaskNoteReaderRoute(
    taskId: String,
    componentId: String
): String {
    return "task_detail_note/${Uri.encode(taskId)}/${Uri.encode(componentId)}"
}

private fun buildCalendarDayRoute(date: LocalDate): String {
    return "$CALENDAR_DAY_BASE_ROUTE/${Uri.encode(date.toString())}"
}

private enum class AuraRoutePresentation {
    ROOT,
    STACK,
    MODAL
}

private data class AuraRouteMotion(
    val presentation: AuraRoutePresentation,
    val depth: Int,
    val rootIndex: Int? = null
)

private fun routeMotion(route: String): AuraRouteMotion {
    val rootIndex = rootRouteIndex(route)
    if (rootIndex != null) {
        return AuraRouteMotion(
            presentation = AuraRoutePresentation.ROOT,
            depth = 0,
            rootIndex = rootIndex
        )
    }

    return when (route) {
        CREATE_TASK_BASE_ROUTE -> AuraRouteMotion(AuraRoutePresentation.MODAL, depth = 1)
        HABITS_ROUTE,
        CALENDAR_DAY_BASE_ROUTE,
        TASK_DETAIL_BASE_ROUTE,
        SETTINGS_INTELLIGENCE_ROUTE,
        SETTINGS_CLOUD_ROUTE,
        SETTINGS_ECOSYSTEM_ROUTE,
        SETTINGS_DEVELOPER_ROUTE -> AuraRouteMotion(AuraRoutePresentation.STACK, depth = 1)

        TASK_NOTE_READER_BASE_ROUTE,
        TASK_EDIT_BASE_ROUTE,
        SETTINGS_INTELLIGENCE_APIS_ROUTE,
        SETTINGS_INTELLIGENCE_LIBRARY_ROUTE -> AuraRouteMotion(AuraRoutePresentation.STACK, depth = 2)

        TASK_NOTE_EDITOR_BASE_ROUTE -> AuraRouteMotion(AuraRoutePresentation.STACK, depth = 3)
        else -> AuraRouteMotion(AuraRoutePresentation.STACK, depth = 1)
    }
}

private fun stackPushOffsetFraction(depth: Int): Float {
    return when {
        depth >= 3 -> 0.66f
        depth == 2 -> 0.78f
        else -> 0.9f
    }
}

private fun stackParallaxFraction(depth: Int): Float {
    return when {
        depth >= 3 -> 0.08f
        depth == 2 -> 0.12f
        else -> 0.18f
    }
}

private fun stackPopExitFraction(depth: Int): Float {
    return when {
        depth >= 3 -> 0.72f
        depth == 2 -> 0.84f
        else -> 0.94f
    }
}

private fun incomingStackScale(depth: Int): Float {
    return when {
        depth >= 3 -> 0.95f
        depth == 2 -> 0.965f
        else -> 0.98f
    }
}

private fun outgoingStackScale(depth: Int): Float {
    return when {
        depth >= 3 -> 0.91f
        depth == 2 -> 0.935f
        else -> 0.96f
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.auraEnterTransition(): EnterTransition {
    val initialRoute = normalizedRoute(initialState.destination.route)
    val targetRoute = normalizedRoute(targetState.destination.route)
    val targetMotion = routeMotion(targetRoute)
    val rootDirection = rootSlideDirection(initialRoute, targetRoute)

    return when {
        rootDirection != null -> slideInHorizontally(
            animationSpec = tween(durationMillis = ROOT_TRANSITION_DURATION_MS, easing = FastOutSlowInEasing),
            initialOffsetX = { fullWidth -> if (rootDirection > 0) fullWidth else -fullWidth }
        ) + fadeIn(animationSpec = tween(durationMillis = 240, delayMillis = 70)) +
            scaleIn(
                animationSpec = tween(durationMillis = ROOT_TRANSITION_DURATION_MS, easing = FastOutSlowInEasing),
                initialScale = 0.96f
            )

        targetMotion.presentation == AuraRoutePresentation.MODAL -> slideInVertically(
            animationSpec = tween(durationMillis = MODAL_TRANSITION_DURATION_MS, easing = FastOutSlowInEasing),
            initialOffsetY = { fullHeight -> (fullHeight * 0.16f).toInt() }
        ) + fadeIn(animationSpec = tween(durationMillis = 220, delayMillis = 40)) +
            scaleIn(
                animationSpec = tween(durationMillis = MODAL_TRANSITION_DURATION_MS, easing = FastOutSlowInEasing),
                initialScale = 0.97f
            )

        targetMotion.presentation == AuraRoutePresentation.STACK -> slideInHorizontally(
            animationSpec = tween(durationMillis = STACK_TRANSITION_DURATION_MS, easing = FastOutSlowInEasing),
            initialOffsetX = { fullWidth ->
                (fullWidth * stackPushOffsetFraction(targetMotion.depth)).toInt()
            }
        ) + fadeIn(animationSpec = tween(durationMillis = 240, delayMillis = 45)) +
            scaleIn(
                animationSpec = tween(durationMillis = STACK_TRANSITION_DURATION_MS, easing = FastOutSlowInEasing),
                initialScale = incomingStackScale(targetMotion.depth)
            )

        else -> fadeIn(animationSpec = tween(durationMillis = 180))
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.auraExitTransition(): ExitTransition {
    val initialRoute = normalizedRoute(initialState.destination.route)
    val targetRoute = normalizedRoute(targetState.destination.route)
    val targetMotion = routeMotion(targetRoute)
    val initialMotion = routeMotion(initialRoute)
    val rootDirection = rootSlideDirection(initialRoute, targetRoute)
    val stackDepth = maxOf(initialMotion.depth, targetMotion.depth)

    return when {
        rootDirection != null -> slideOutHorizontally(
            animationSpec = tween(durationMillis = ROOT_TRANSITION_DURATION_MS, easing = FastOutSlowInEasing),
            targetOffsetX = { fullWidth -> if (rootDirection > 0) -fullWidth else fullWidth }
        ) + fadeOut(animationSpec = tween(durationMillis = 220)) +
            scaleOut(
                animationSpec = tween(durationMillis = ROOT_TRANSITION_DURATION_MS, easing = FastOutSlowInEasing),
                targetScale = 0.94f
            )

        targetMotion.presentation == AuraRoutePresentation.MODAL -> slideOutVertically(
            animationSpec = tween(durationMillis = MODAL_TRANSITION_DURATION_MS, easing = EaseOutCubic),
            targetOffsetY = { fullHeight -> -(fullHeight * 0.06f).toInt() }
        ) + fadeOut(animationSpec = tween(durationMillis = 180)) +
            scaleOut(
                animationSpec = tween(durationMillis = MODAL_TRANSITION_DURATION_MS, easing = EaseOutCubic),
                targetScale = 0.985f
            )

        targetMotion.presentation == AuraRoutePresentation.STACK -> slideOutHorizontally(
            animationSpec = tween(durationMillis = STACK_TRANSITION_DURATION_MS, easing = EaseOutCubic),
            targetOffsetX = { fullWidth ->
                -(fullWidth * stackParallaxFraction(stackDepth)).toInt()
            }
        ) + fadeOut(animationSpec = tween(durationMillis = 190)) +
            scaleOut(
                animationSpec = tween(durationMillis = STACK_TRANSITION_DURATION_MS, easing = EaseOutCubic),
                targetScale = outgoingStackScale(stackDepth)
            )

        else -> fadeOut(animationSpec = tween(durationMillis = 180))
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.auraPopEnterTransition(): EnterTransition {
    val initialRoute = normalizedRoute(initialState.destination.route)
    val targetRoute = normalizedRoute(targetState.destination.route)
    val initialMotion = routeMotion(initialRoute)
    val targetMotion = routeMotion(targetRoute)
    val rootDirection = rootSlideDirection(targetRoute, initialRoute)

    return when {
        rootDirection != null -> slideInHorizontally(
            animationSpec = tween(durationMillis = ROOT_TRANSITION_DURATION_MS, easing = FastOutSlowInEasing),
            initialOffsetX = { fullWidth -> if (rootDirection > 0) -fullWidth else fullWidth }
        ) + fadeIn(animationSpec = tween(durationMillis = 240, delayMillis = 70)) +
            scaleIn(
                animationSpec = tween(durationMillis = ROOT_TRANSITION_DURATION_MS, easing = FastOutSlowInEasing),
                initialScale = 0.96f
            )

        initialMotion.presentation == AuraRoutePresentation.MODAL -> slideInVertically(
            animationSpec = tween(durationMillis = MODAL_TRANSITION_DURATION_MS, easing = FastOutSlowInEasing),
            initialOffsetY = { fullHeight -> -(fullHeight * 0.06f).toInt() }
        ) + fadeIn(animationSpec = tween(durationMillis = 200, delayMillis = 30)) +
            scaleIn(
                animationSpec = tween(durationMillis = MODAL_TRANSITION_DURATION_MS, easing = FastOutSlowInEasing),
                initialScale = 0.985f
            )

        initialMotion.presentation == AuraRoutePresentation.STACK -> slideInHorizontally(
            animationSpec = tween(durationMillis = STACK_TRANSITION_DURATION_MS, easing = FastOutSlowInEasing),
            initialOffsetX = { fullWidth ->
                -(fullWidth * stackParallaxFraction(targetMotion.depth)).toInt()
            }
        ) + fadeIn(animationSpec = tween(durationMillis = 220, delayMillis = 35)) +
            scaleIn(
                animationSpec = tween(durationMillis = STACK_TRANSITION_DURATION_MS, easing = FastOutSlowInEasing),
                initialScale = outgoingStackScale(targetMotion.depth)
            )

        else -> fadeIn(animationSpec = tween(durationMillis = 180))
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.auraPopExitTransition(): ExitTransition {
    val initialRoute = normalizedRoute(initialState.destination.route)
    val targetRoute = normalizedRoute(targetState.destination.route)
    val initialMotion = routeMotion(initialRoute)
    val rootDirection = rootSlideDirection(targetRoute, initialRoute)

    return when {
        rootDirection != null -> slideOutHorizontally(
            animationSpec = tween(durationMillis = ROOT_TRANSITION_DURATION_MS, easing = FastOutSlowInEasing),
            targetOffsetX = { fullWidth -> if (rootDirection > 0) fullWidth else -fullWidth }
        ) + fadeOut(animationSpec = tween(durationMillis = 220)) +
            scaleOut(
                animationSpec = tween(durationMillis = ROOT_TRANSITION_DURATION_MS, easing = FastOutSlowInEasing),
                targetScale = 0.94f
            )

        initialMotion.presentation == AuraRoutePresentation.MODAL -> slideOutVertically(
            animationSpec = tween(durationMillis = MODAL_TRANSITION_DURATION_MS, easing = EaseOutCubic),
            targetOffsetY = { fullHeight -> (fullHeight * 0.14f).toInt() }
        ) + fadeOut(animationSpec = tween(durationMillis = 180)) +
            scaleOut(
                animationSpec = tween(durationMillis = MODAL_TRANSITION_DURATION_MS, easing = EaseOutCubic),
                targetScale = 0.97f
            )

        initialMotion.presentation == AuraRoutePresentation.STACK -> slideOutHorizontally(
            animationSpec = tween(durationMillis = STACK_TRANSITION_DURATION_MS, easing = EaseOutCubic),
            targetOffsetX = { fullWidth ->
                (fullWidth * stackPopExitFraction(initialMotion.depth)).toInt()
            }
        ) + fadeOut(animationSpec = tween(durationMillis = 190)) +
            scaleOut(
                animationSpec = tween(durationMillis = STACK_TRANSITION_DURATION_MS, easing = EaseOutCubic),
                targetScale = incomingStackScale(initialMotion.depth)
            )

        else -> fadeOut(animationSpec = tween(durationMillis = 180))
    }
}

private fun normalizedRoute(route: String?): String {
    return route
        ?.substringBefore("?")
        ?.replace(Regex("/\\{[^}]+\\}"), "")
        .orEmpty()
}

private fun rootSlideDirection(initialRoute: String, targetRoute: String): Int? {
    val initialIndex = rootRouteIndex(initialRoute) ?: return null
    val targetIndex = rootRouteIndex(targetRoute) ?: return null
    return (targetIndex - initialIndex).takeIf { it != 0 }
}

private fun rootRouteIndex(route: String): Int? {
    return when (route) {
        HOME_ROUTE -> 0
        TASKS_ROUTE -> 1
        SETTINGS_ROUTE -> 2
        else -> null
    }
}
