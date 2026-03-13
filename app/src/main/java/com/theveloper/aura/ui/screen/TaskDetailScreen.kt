package com.theveloper.aura.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.theveloper.aura.domain.model.ChecklistItem
import com.theveloper.aura.domain.model.ComponentType
import com.theveloper.aura.domain.model.HabitRingConfig
import com.theveloper.aura.domain.model.MetricTrackerConfig
import com.theveloper.aura.domain.model.NotesConfig
import com.theveloper.aura.domain.model.SignalType
import com.theveloper.aura.domain.model.Task
import com.theveloper.aura.domain.model.TaskComponent
import com.theveloper.aura.domain.model.TaskStatus
import com.theveloper.aura.ui.renderer.EditableTaskRenderer
import com.theveloper.aura.ui.renderer.TaskRenderMode
import com.theveloper.aura.ui.renderer.TaskRenderer
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    taskId: String,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (String) -> Unit,
    viewModel: TaskDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val taskListState = rememberLazyListState()
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val isFloatingBarVisible = !taskListState.isScrollInProgress

    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collectLatest { event ->
            if (event is TaskDetailEvent.TaskDeleted) {
                onNavigateBack()
            }
        }
    }

    if (showDeleteConfirmation && uiState.task != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Eliminar tarea") },
            text = { Text("Se va a eliminar \"${uiState.task?.title.orEmpty()}\" de forma permanente.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        viewModel.onDeleteTask()
                    }
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TaskDetailTopBar(
                onNavigateBack = onNavigateBack,
                onNavigateToEdit = {
                    uiState.task?.let { task -> onNavigateToEdit(task.id) }
                },
                showEditAction = uiState.task != null
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            TaskRenderer(
                task = uiState.task,
                isLoading = uiState.isLoading,
                mode = TaskRenderMode.INTERPRETED,
                listState = taskListState,
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding() + 4.dp,
                    bottom = paddingValues.calculateBottomPadding() + if (uiState.task != null) 112.dp else 24.dp
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                onSignal = viewModel::onSignal
            )

            uiState.task?.let { task ->
                AnimatedVisibility(
                    visible = isFloatingBarVisible,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    TaskDetailFloatingBar(
                        task = task,
                        onCompleteTask = viewModel::onCompleteTask,
                        onReopenTask = viewModel::onReopenTask,
                        onDeleteTask = { showDeleteConfirmation = true }
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskDetailTopBar(
    onNavigateBack: () -> Unit,
    onNavigateToEdit: () -> Unit,
    showEditAction: Boolean
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
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TaskDetailChromeIconButton(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Volver",
                onClick = onNavigateBack
            )

            if (showEditAction) {
                TaskDetailChromeIconButton(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = "Editar tarea",
                    onClick = onNavigateToEdit,
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.94f),
                    contentColor = MaterialTheme.colorScheme.primary
                )
            } else {
                Spacer(modifier = Modifier.size(48.dp))
            }
        }
    }
}

@Composable
private fun TaskDetailFloatingBar(
    task: Task,
    onCompleteTask: () -> Unit,
    onReopenTask: () -> Unit,
    onDeleteTask: () -> Unit
) {
    val isActive = task.status == TaskStatus.ACTIVE
    val primaryIcon = if (isActive) Icons.Rounded.TaskAlt else Icons.Rounded.Refresh
    val primaryContainer = if (isActive) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val primaryContent = if (isActive) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    Surface(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 16.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TaskDetailChromeIconButton(
                imageVector = primaryIcon,
                contentDescription = if (isActive) "Complete" else "Reopen",
                onClick = if (isActive) onCompleteTask else onReopenTask,
                containerColor = primaryContainer,
                contentColor = primaryContent,
                buttonSize = 54.dp,
                shadowElevation = 8.dp
            )

            TaskDetailChromeIconButton(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = "Delete",
                onClick = onDeleteTask,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.error,
                buttonSize = 54.dp,
                shadowElevation = 8.dp
            )
        }
    }
}

@Composable
private fun TaskDetailChromeIconButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    buttonSize: Dp = 48.dp,
    shadowElevation: Dp = 8.dp
) {
    val resolvedContainerColor = if (containerColor == Color.Unspecified) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    } else {
        containerColor
    }
    val resolvedContentColor = if (contentColor == Color.Unspecified) {
        MaterialTheme.colorScheme.onSurface
    } else {
        contentColor
    }

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = resolvedContainerColor,
        tonalElevation = 2.dp,
        shadowElevation = shadowElevation,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(buttonSize)
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = resolvedContentColor
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditScreen(
    taskId: String,
    onNavigateBack: () -> Unit,
    viewModel: TaskDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var draftTask by remember(taskId) { mutableStateOf<Task?>(null) }

    LaunchedEffect(uiState.task?.id) {
        if (draftTask == null && uiState.task != null) {
            draftTask = uiState.task
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collectLatest { event ->
            if (event is TaskDetailEvent.TaskSaved) {
                onNavigateBack()
            }
        }
    }

    val originalTask = uiState.task
    val currentDraft = draftTask ?: originalTask
    val hasChanges = currentDraft != null && originalTask != null && currentDraft != originalTask

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Cancelar edición"
                        )
                    }
                }
            )
        },
        containerColor = Color.Transparent,
        bottomBar = {
            if (currentDraft != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }

                    FilledTonalButton(
                        onClick = { viewModel.saveTaskEdits(currentDraft) },
                        modifier = Modifier.weight(1f),
                        enabled = hasChanges
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Save,
                            contentDescription = null
                        )
                        Text("Save")
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TaskEditTextureBackground()

            EditableTaskRenderer(
                task = currentDraft,
                isLoading = uiState.isLoading,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                onTaskChange = { updated -> draftTask = updated },
                onSignal = viewModel::onSignal
            )
        }
    }
}

@Composable
private fun TaskEditTextureBackground() {
    val lineColor = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f)
    val accentColor = androidx.compose.material3.MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)

    Canvas(modifier = Modifier.fillMaxSize()) {
        val gap = 34.dp.toPx()
        var x = -size.height
        while (x < size.width) {
            drawLine(
                color = lineColor,
                start = Offset(x, 0f),
                end = Offset(x + size.height, size.height),
                strokeWidth = 1.dp.toPx()
            )
            x += gap
        }

        var y = gap / 2f
        while (y < size.height) {
            var dotX = gap / 2f
            while (dotX < size.width) {
                drawCircle(
                    color = accentColor,
                    radius = 1.2.dp.toPx(),
                    center = Offset(dotX, y)
                )
                dotX += gap
            }
            y += gap
        }
    }
}

private fun Task.updateComponent(
    componentId: String,
    transform: (TaskComponent) -> TaskComponent
): Task {
    return copy(
        components = components.map { component ->
            if (component.id == componentId) transform(component) else component
        }
    )
}

internal fun Task.updateNotesComponent(componentId: String, text: String): Task {
    return updateComponent(componentId) { component ->
        val config = component.config as? NotesConfig ?: return@updateComponent component
        component.copy(config = config.copy(text = text))
    }
}

internal fun Task.appendMetricTrackerValue(componentId: String, value: Float): Task {
    return updateComponent(componentId) { component ->
        val config = component.config as? MetricTrackerConfig ?: return@updateComponent component
        component.copy(config = config.copy(history = (config.history + value).takeLast(30)))
    }
}

internal fun Task.updateChecklistItem(componentId: String, updatedItem: ChecklistItem): Task {
    return updateComponent(componentId) { component ->
        if (component.type != ComponentType.CHECKLIST) return@updateComponent component
        component.copy(
            checklistItems = component.checklistItems.map { item ->
                if (item.id == updatedItem.id) updatedItem.copy(isSuggested = false) else item
            }
        )
    }
}

internal fun Task.updateHabitRingComponent(componentId: String, completedToday: Boolean): Task {
    return updateComponent(componentId) { component ->
        val config = component.config as? HabitRingConfig ?: return@updateComponent component
        component.copy(config = config.copy(completedToday = completedToday))
    }
}
