package com.theveloper.aura.ui

import android.net.Uri
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
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.theveloper.aura.ui.screen.CreateTaskScreen
import com.theveloper.aura.ui.screen.CloudSettingsScreen
import com.theveloper.aura.ui.screen.DeveloperSettingsScreen
import com.theveloper.aura.ui.screen.HomeScreen
import com.theveloper.aura.ui.screen.IntelligenceApiSettingsScreen
import com.theveloper.aura.ui.screen.IntelligenceModelLibraryScreen
import com.theveloper.aura.ui.screen.IntelligenceSettingsScreen
import com.theveloper.aura.ui.screen.SettingsScreen
import com.theveloper.aura.ui.screen.TaskCreationMode
import com.theveloper.aura.ui.screen.TaskDetailScreen
import com.theveloper.aura.ui.screen.TaskEditScreen
import com.theveloper.aura.ui.screen.TaskMarkdownEditorScreen
import com.theveloper.aura.ui.screen.TaskMarkdownReaderScreen
import com.theveloper.aura.ui.screen.TasksScreen
import com.theveloper.aura.ui.theme.AuraFloatingBarColors
import com.theveloper.aura.ui.theme.auraFloatingBarColors
import kotlinx.coroutines.launch

private const val HOME_ROUTE = "home"
private const val TASKS_ROUTE = "tasks"
private const val TASK_DETAIL_ROUTE = "task_detail/{taskId}"
private const val TASK_EDIT_ROUTE = "task_detail_edit/{taskId}"
private const val TASK_NOTE_READER_ROUTE = "task_detail_note/{taskId}/{componentId}"
private const val TASK_NOTE_EDITOR_ROUTE = "task_detail_edit_note/{taskId}/{componentId}"
private const val SETTINGS_ROUTE = "settings"
private const val SETTINGS_INTELLIGENCE_ROUTE = "settings/intelligence"
private const val SETTINGS_INTELLIGENCE_APIS_ROUTE = "settings/intelligence/apis"
private const val SETTINGS_INTELLIGENCE_LIBRARY_ROUTE = "settings/intelligence/library"
private const val SETTINGS_CLOUD_ROUTE = "settings/cloud"
private const val SETTINGS_DEVELOPER_ROUTE = "settings/developer"
private const val CREATE_TASK_BASE_ROUTE = "create_task"
private const val CREATE_TASK_ROUTE =
    "$CREATE_TASK_BASE_ROUTE?mode={mode}&input={input}&autoSubmit={autoSubmit}"
private const val ROOT_TRANSITION_DURATION_MS = 430
private const val SECONDARY_TRANSITION_DURATION_MS = 380

@Composable
fun AuraApp() {
    val navController = rememberNavController()
    var quickPrompt by rememberSaveable { mutableStateOf("") }

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
                TasksScreen()
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
            composable(SETTINGS_ROUTE) {
                SettingsScreen(
                    onOpenIntelligenceSettings = { navController.navigate(SETTINGS_INTELLIGENCE_ROUTE) },
                    onOpenCloudSettings = { navController.navigate(SETTINGS_CLOUD_ROUTE) },
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
            quickPrompt = quickPrompt,
            onQuickPromptChange = { quickPrompt = it },
            onQuickPromptSubmit = {
                val input = quickPrompt.trim()
                if (input.isNotBlank()) {
                    navController.navigate(
                        buildCreateTaskRoute(
                            mode = TaskCreationMode.PROMPT,
                            input = input,
                            autoSubmit = true
                        )
                    )
                    quickPrompt = ""
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
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
    quickPrompt: String,
    onQuickPromptChange: (String) -> Unit,
    onQuickPromptSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val colors = auraFloatingBarColors()
    val rootRoutes = remember { setOf(HOME_ROUTE, TASKS_ROUTE, SETTINGS_ROUTE) }
    val isVisible = remember(currentRoute, rootRoutes) {
        normalizedRoute(currentRoute) in rootRoutes
    }

    val items = listOf(
        BottomBarItem(key = HOME_ROUTE, label = "Home", icon = Icons.Rounded.Home),
        BottomBarItem(key = TASKS_ROUTE, label = "Habits", icon = Icons.Rounded.CalendarMonth),
        BottomBarItem(key = SETTINGS_ROUTE, label = "Settings", icon = Icons.Rounded.Settings),
        BottomBarItem(key = "create", label = "Create", icon = Icons.Rounded.Add, accent = true)
    )

    AnimatedVisibility(
        visible = isVisible,
        modifier = modifier.fillMaxWidth(),
        enter = slideInVertically(
            animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
            initialOffsetY = { it / 2 }
        ) + fadeIn(animationSpec = tween(durationMillis = 220, delayMillis = 40)),
        exit = slideOutVertically(
            animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
            targetOffsetY = { it / 2 }
        ) + fadeOut(animationSpec = tween(durationMillis = 180))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 0.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
//        QuickPromptBar(
//            prompt = quickPrompt,
//            onPromptChange = onQuickPromptChange,
//            onSubmit = onQuickPromptSubmit,
//            colors = colors
//        )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = CircleShape,
                color = colors.container,
                border = BorderStroke(width = 2.dp, color = colors.outline),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items.forEach { item ->
                        AuraBottomBarItem(
                            item = item,
                            selected = item.key == currentRoute,
                            onClick = {
                                when (item.key) {
                                    "create" -> navController.navigate(
                                        buildCreateTaskRoute(mode = TaskCreationMode.MANUAL)
                                    )
                                    else -> navController.navigateToRootRoute(item.key)
                                }
                            },
                            colors = colors
                        )
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

@Composable
private fun RowScope.AuraBottomBarItem(
    item: BottomBarItem,
    selected: Boolean,
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

    Box(
        modifier = Modifier.weight(1f),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
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
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = contentColor,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
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

private fun AnimatedContentTransitionScope<NavBackStackEntry>.auraEnterTransition(): EnterTransition {
    val initialRoute = normalizedRoute(initialState.destination.route)
    val targetRoute = normalizedRoute(targetState.destination.route)
    val rootDirection = rootSlideDirection(initialRoute, targetRoute)

    return when {
        rootDirection != null -> slideInHorizontally(
            animationSpec = tween(durationMillis = ROOT_TRANSITION_DURATION_MS, easing = FastOutSlowInEasing),
            initialOffsetX = { fullWidth -> if (rootDirection > 0) fullWidth else -fullWidth }
        ) + fadeIn(animationSpec = tween(durationMillis = 220, delayMillis = 70))

        isSecondaryRoute(targetRoute) -> slideInHorizontally(
            animationSpec = tween(durationMillis = SECONDARY_TRANSITION_DURATION_MS, easing = FastOutSlowInEasing),
            initialOffsetX = { fullWidth -> (fullWidth * 0.92f).toInt() }
        ) + fadeIn(animationSpec = tween(durationMillis = 240, delayMillis = 50))

        else -> fadeIn(animationSpec = tween(durationMillis = 180))
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.auraExitTransition(): ExitTransition {
    val initialRoute = normalizedRoute(initialState.destination.route)
    val targetRoute = normalizedRoute(targetState.destination.route)
    val rootDirection = rootSlideDirection(initialRoute, targetRoute)

    return when {
        rootDirection != null -> slideOutHorizontally(
            animationSpec = tween(durationMillis = ROOT_TRANSITION_DURATION_MS, easing = FastOutSlowInEasing),
            targetOffsetX = { fullWidth -> if (rootDirection > 0) -fullWidth else fullWidth }
        ) + fadeOut(animationSpec = tween(durationMillis = 220))

        isSecondaryRoute(targetRoute) -> slideOutHorizontally(
            animationSpec = tween(durationMillis = SECONDARY_TRANSITION_DURATION_MS, easing = FastOutSlowInEasing),
            targetOffsetX = { fullWidth -> -(fullWidth / 4) }
        ) + fadeOut(animationSpec = tween(durationMillis = 200))

        else -> fadeOut(animationSpec = tween(durationMillis = 180))
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.auraPopEnterTransition(): EnterTransition {
    val initialRoute = normalizedRoute(initialState.destination.route)
    val targetRoute = normalizedRoute(targetState.destination.route)
    val rootDirection = rootSlideDirection(targetRoute, initialRoute)

    return when {
        rootDirection != null -> slideInHorizontally(
            animationSpec = tween(durationMillis = ROOT_TRANSITION_DURATION_MS, easing = FastOutSlowInEasing),
            initialOffsetX = { fullWidth -> if (rootDirection > 0) -fullWidth else fullWidth }
        ) + fadeIn(animationSpec = tween(durationMillis = 220, delayMillis = 70))

        isSecondaryRoute(initialRoute) -> slideInHorizontally(
            animationSpec = tween(durationMillis = SECONDARY_TRANSITION_DURATION_MS, easing = FastOutSlowInEasing),
            initialOffsetX = { fullWidth -> -(fullWidth / 3) }
        ) + fadeIn(animationSpec = tween(durationMillis = 220, delayMillis = 40))

        else -> fadeIn(animationSpec = tween(durationMillis = 180))
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.auraPopExitTransition(): ExitTransition {
    val initialRoute = normalizedRoute(initialState.destination.route)
    val targetRoute = normalizedRoute(targetState.destination.route)
    val rootDirection = rootSlideDirection(targetRoute, initialRoute)

    return when {
        rootDirection != null -> slideOutHorizontally(
            animationSpec = tween(durationMillis = ROOT_TRANSITION_DURATION_MS, easing = FastOutSlowInEasing),
            targetOffsetX = { fullWidth -> if (rootDirection > 0) fullWidth else -fullWidth }
        ) + fadeOut(animationSpec = tween(durationMillis = 220))

        isSecondaryRoute(initialRoute) -> slideOutHorizontally(
            animationSpec = tween(durationMillis = SECONDARY_TRANSITION_DURATION_MS, easing = FastOutSlowInEasing),
            targetOffsetX = { fullWidth -> fullWidth }
        ) + fadeOut(animationSpec = tween(durationMillis = 200))

        else -> fadeOut(animationSpec = tween(durationMillis = 180))
    }
}

private fun normalizedRoute(route: String?): String {
    return route
        ?.substringBefore("?")
        ?.replace(Regex("/\\{[^}]+\\}"), "")
        .orEmpty()
}

private fun isSecondaryRoute(route: String): Boolean = route.isNotBlank() && rootRouteIndex(route) == null

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
