package com.theveloper.aura.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
    var showDeleteConfirmation by remember { mutableStateOf(false) }

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
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                },
                actions = {
                    uiState.task?.let { task ->
                        IconButton(onClick = { onNavigateToEdit(task.id) }) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = "Editar tarea"
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            val task = uiState.task
            if (task != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (task.status == TaskStatus.ACTIVE) {
                        FilledTonalButton(
                            onClick = viewModel::onCompleteTask,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.TaskAlt,
                                contentDescription = null
                            )
                            Text("Complete")
                        }
                    } else {
                        OutlinedButton(
                            onClick = viewModel::onReopenTask,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Reopen")
                        }
                    }

                    OutlinedButton(
                        onClick = { showDeleteConfirmation = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = null
                        )
                        Text("Delete")
                    }
                }
            }
        }
    ) { paddingValues ->
        TaskRenderer(
            task = uiState.task,
            isLoading = uiState.isLoading,
            mode = TaskRenderMode.INTERPRETED,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            onSignal = viewModel::onSignal
        )
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
