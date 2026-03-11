package com.theveloper.aura.ui

import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.theveloper.aura.ui.screen.CreateTaskScreen
import com.theveloper.aura.ui.screen.HomeScreen
import com.theveloper.aura.ui.screen.SettingsScreen
import com.theveloper.aura.ui.screen.TaskCreationMode
import com.theveloper.aura.ui.screen.TaskDetailScreen
import com.theveloper.aura.ui.screen.TasksScreen
import com.theveloper.aura.ui.theme.AuraFloatingBarColors
import com.theveloper.aura.ui.theme.auraFloatingBarColors

private const val HOME_ROUTE = "home"
private const val TASKS_ROUTE = "tasks"
private const val TASK_DETAIL_ROUTE = "task_detail/{taskId}"
private const val SETTINGS_ROUTE = "settings"
private const val CREATE_TASK_BASE_ROUTE = "create_task"
private const val CREATE_TASK_ROUTE =
    "$CREATE_TASK_BASE_ROUTE?mode={mode}&input={input}&autoSubmit={autoSubmit}"

@Composable
fun AuraApp() {
    val navController = rememberNavController()
    var quickPrompt by rememberSaveable { mutableStateOf("") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
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
                }
            )
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = HOME_ROUTE,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(HOME_ROUTE) {
                HomeScreen(
                    onNavigateToTaskDetail = { taskId ->
                        navController.navigate("task_detail/$taskId")
                    },
                    onNavigateToTasks = {
                        navController.navigate(TASKS_ROUTE)
                    },
                    onNavigateToPromptCreate = { input ->
                        navController.navigate(
                            buildCreateTaskRoute(
                                mode = TaskCreationMode.PROMPT,
                                input = input
                            )
                        )
                    },
                    onNavigateToManualCreate = {
                        navController.navigate(buildCreateTaskRoute(mode = TaskCreationMode.MANUAL))
                    }
                )
            }
            composable(TASKS_ROUTE) {
                TasksScreen(
                    onNavigateToTaskDetail = { taskId ->
                        navController.navigate("task_detail/$taskId")
                    },
                    onNavigateToCreateTask = { mode ->
                        navController.navigate(buildCreateTaskRoute(mode = mode))
                    }
                )
            }
            composable(TASK_DETAIL_ROUTE) { backStackEntry ->
                val taskId = backStackEntry.arguments?.getString("taskId") ?: ""
                TaskDetailScreen(
                    taskId = taskId,
                    onNavigateBack = { navController.popBackStack() }
                )
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
                CreateTaskScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(SETTINGS_ROUTE) {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
fun AuraBottomBar(
    navController: NavHostController,
    quickPrompt: String,
    onQuickPromptChange: (String) -> Unit,
    onQuickPromptSubmit: () -> Unit
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val colors = auraFloatingBarColors()

    if (
        currentRoute == null ||
        currentRoute == TASK_DETAIL_ROUTE ||
        currentRoute.startsWith(CREATE_TASK_BASE_ROUTE)
    ) {
        return
    }

    val items = listOf(
        BottomBarItem(key = HOME_ROUTE, label = "Home", icon = Icons.Rounded.Home),
        BottomBarItem(key = TASKS_ROUTE, label = "Tasks", icon = Icons.Rounded.TaskAlt),
        BottomBarItem(key = SETTINGS_ROUTE, label = "Settings", icon = Icons.Rounded.Settings),
        BottomBarItem(key = "create", label = "Create", icon = Icons.Rounded.Add, accent = true)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        QuickPromptBar(
            prompt = quickPrompt,
            onPromptChange = onQuickPromptChange,
            onSubmit = onQuickPromptSubmit,
            colors = colors
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = CircleShape,
            color = colors.container,
            border = BorderStroke(width = 2.dp, color = colors.outline),
            shadowElevation = 14.dp
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
    val containerColor by animateColorAsState(
        targetValue = when {
            item.accent -> colors.accentCircle
            selected -> colors.selectedCircle
            else -> colors.mutedCircle
        },
        animationSpec = tween(durationMillis = 220),
        label = "bottomBarContainer"
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            item.accent -> colors.accentIcon
            selected -> colors.selectedIcon
            else -> colors.mutedIcon
        },
        animationSpec = tween(durationMillis = 220),
        label = "bottomBarIcon"
    )
    val scale by animateFloatAsState(
        targetValue = if (selected || item.accent) 1f else 0.93f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "bottomBarScale"
    )
    val verticalOffset by animateDpAsState(
        targetValue = if (selected) (-4).dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bottomBarOffset"
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
                .size(72.dp)
                .offset(y = verticalOffset)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clickable(onClick = onClick),
            shape = CircleShape,
            color = containerColor,
            shadowElevation = shadowElevation
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = contentColor,
                    modifier = Modifier.size(32.dp)
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
    val outlineColor by animateColorAsState(
        targetValue = if (isFocused) colors.activeOutline else colors.outline,
        animationSpec = tween(durationMillis = 180),
        label = "quickPromptOutline"
    )
    val shadowElevation by animateDpAsState(
        targetValue = if (isFocused || sendEnabled) 16.dp else 10.dp,
        animationSpec = tween(durationMillis = 180),
        label = "quickPromptShadow"
    )
    val sendScale by animateFloatAsState(
        targetValue = if (sendEnabled) 1f else 0.9f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "quickPromptSendScale"
    )

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
        shadowElevation = shadowElevation
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 76.dp)
                .padding(start = 16.dp, top = 12.dp, end = 10.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(42.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer(rotationZ = 45f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.diamond)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
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
                    onClick = onSubmit,
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
