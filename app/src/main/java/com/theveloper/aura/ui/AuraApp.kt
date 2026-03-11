package com.theveloper.aura.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.rounded.Diamond
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

private const val HOME_ROUTE = "home"
private const val TASKS_ROUTE = "tasks"
private const val TASK_DETAIL_ROUTE = "task_detail/{taskId}"
private const val SETTINGS_ROUTE = "settings"
private const val CREATE_TASK_BASE_ROUTE = "create_task"
private const val CREATE_TASK_ROUTE =
    "$CREATE_TASK_BASE_ROUTE?mode={mode}&input={input}&autoSubmit={autoSubmit}"

//Son placeholders, hay que reemplazarlos por colores reales de la paleta
private val FloatingChrome = Color(0xFF2D2B2D)
private val FloatingOutline = Color(0xFF232224)
private val FloatingMutedCircle = Color(0xB08E8E8F)
private val FloatingSelectedCircle = Color(0xFFF0EFF0)
private val FloatingAccentCircle = Color(0xFFE67938)
private val FloatingPromptText = Color(0xFFF3F3F3)
private val FloatingPlaceholder = Color(0xFFC9C7C8)
private val FloatingMutedIcon = Color(0xFFE6F0F7)
private val FloatingSelectedIcon = Color(0xFF232224)
private val FloatingAccentIcon = Color(0xFF6F2E0F)
private val FloatingDiamond = Color(0xFF686769)

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
            onSubmit = onQuickPromptSubmit
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = CircleShape,
            color = FloatingChrome,
            border = BorderStroke(width = 2.dp, color = FloatingOutline),
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
                        }
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
    onClick: () -> Unit
) {
    val containerColor = when {
        item.accent -> FloatingAccentCircle
        selected -> FloatingSelectedCircle
        else -> FloatingMutedCircle
    }
    val contentColor = when {
        item.accent -> FloatingAccentIcon
        selected -> FloatingSelectedIcon
        else -> FloatingMutedIcon
    }

    Box(
        modifier = Modifier.weight(1f),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(CircleShape)
                .background(containerColor)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = contentColor,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

@Composable
private fun QuickPromptBar(
    prompt: String,
    onPromptChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    val sendEnabled = prompt.trim().isNotEmpty()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CircleShape,
        color = FloatingChrome,
        border = BorderStroke(width = 2.dp, color = FloatingOutline),
        shadowElevation = 12.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 38.dp, max = 60.dp)
                .padding(start = 18.dp, top = 14.dp, end = 12.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Diamond,
                contentDescription = "Diamond",
                tint = FloatingDiamond,
            )
            Spacer(modifier = Modifier.width(18.dp))
            BasicTextField(
                value = prompt,
                onValueChange = onPromptChange,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.titleLarge.copy(
                    color = FloatingPromptText,
                    fontWeight = FontWeight.SemiBold
                ),
                cursorBrush = SolidColor(FloatingPromptText),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (sendEnabled) {
                            onSubmit()
                        }
                    }
                ),
                maxLines = 4,
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            //.padding(vertical = 10.dp)
                    ) {
                        if (prompt.isBlank()) {
                            Text(
                                text = "Create task...",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = FloatingPlaceholder,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                        innerTextField()
                    }
                }
            )
            IconButton(
                onClick = onSubmit,
                enabled = sendEnabled,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Send,
                    contentDescription = "Create task",
                    tint = if (sendEnabled) FloatingPromptText else FloatingPlaceholder.copy(alpha = 0.42f),
                    modifier = Modifier.size(34.dp)
                )
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
