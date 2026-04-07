package com.theveloper.aura.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import com.theveloper.aura.domain.model.ChecklistItem
import com.theveloper.aura.domain.model.ChecklistConfig
import com.theveloper.aura.domain.model.ComponentConfig
import com.theveloper.aura.domain.model.ComponentType
import com.theveloper.aura.domain.model.CountdownConfig
import com.theveloper.aura.domain.model.DataFeedConfig
import com.theveloper.aura.domain.model.HabitRingConfig
import com.theveloper.aura.domain.model.MetricTrackerConfig
import com.theveloper.aura.domain.model.NotesConfig
import com.theveloper.aura.domain.model.ProgressBarConfig
import com.theveloper.aura.domain.model.SignalType
import com.theveloper.aura.domain.model.Task
import com.theveloper.aura.domain.model.TaskComponent
import com.theveloper.aura.domain.model.TaskStatus
import com.theveloper.aura.ui.components.ComponentPill
import com.theveloper.aura.ui.components.FullscreenRenderedNotesContent
import com.theveloper.aura.ui.skill.uiSkillDisplayName
import com.theveloper.aura.ui.skill.uiSkillIcon
import com.theveloper.aura.ui.renderer.EditableTaskRenderer
import com.theveloper.aura.ui.renderer.TaskRenderMode
import com.theveloper.aura.ui.renderer.TaskRenderer
import kotlinx.coroutines.flow.collectLatest
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

internal const val TASK_NOTE_EDITOR_TEXT_KEY = "task_note_editor_text"
internal const val TASK_NOTE_EDITOR_IS_MARKDOWN_KEY = "task_note_editor_is_markdown"
internal const val TASK_NOTE_EDITOR_RESULT_COMPONENT_ID_KEY = "task_note_editor_result_component_id"
internal const val TASK_NOTE_EDITOR_RESULT_TEXT_KEY = "task_note_editor_result_text"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    taskId: String,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (String) -> Unit,
    onNavigateToNotesReader: (String, String) -> Unit,
    viewModel: TaskDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val taskListState = rememberLazyListState()
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
            title = { Text("Delete task") },
            text = { Text("\"${uiState.task?.title.orEmpty()}\" will be permanently deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        viewModel.onDeleteTask()
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
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
                onOpenNotes = { component ->
                    uiState.task?.let { task ->
                        onNavigateToNotesReader(task.id, component.id)
                    }
                },
                onSignal = viewModel::onSignal
            )

            uiState.task?.let { task ->
                TaskDetailFloatingBarHost(
                    task = task,
                    taskListState = taskListState,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    onCompleteTask = viewModel::onCompleteTask,
                    onReopenTask = viewModel::onReopenTask,
                    onDeleteTask = { showDeleteConfirmation = true }
                )
            }
        }
    }
}

@Composable
private fun TaskDetailFloatingBarHost(
    task: Task,
    taskListState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier,
    onCompleteTask: () -> Unit,
    onReopenTask: () -> Unit,
    onDeleteTask: () -> Unit
) {
    val isVisible by remember(taskListState) {
        derivedStateOf { !taskListState.isScrollInProgress }
    }

    AnimatedVisibility(
        visible = isVisible,
        modifier = modifier,
        enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        TaskDetailFloatingBar(
            task = task,
            onCompleteTask = onCompleteTask,
            onReopenTask = onReopenTask,
            onDeleteTask = onDeleteTask
        )
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
                contentDescription = "Back",
                onClick = onNavigateBack
            )

            if (showEditAction) {
                TaskDetailChromeIconButton(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = "Edit task",
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
    enabled: Boolean = true,
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
    val alpha = if (enabled) 1f else 0.46f

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = resolvedContainerColor.copy(alpha = resolvedContainerColor.alpha * alpha),
        tonalElevation = 2.dp,
        shadowElevation = shadowElevation,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(buttonSize)
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = resolvedContentColor.copy(alpha = alpha)
            )
        }
    }
}

@Composable
fun TaskMarkdownReaderScreen(
    taskId: String,
    componentId: String,
    onNavigateBack: () -> Unit,
    viewModel: TaskDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val notesConfig = remember(taskId, uiState.task, componentId) {
        uiState.task?.components
            ?.firstOrNull { it.id == componentId }
            ?.config as? NotesConfig
    }

    Scaffold(
        topBar = {
            TaskMarkdownReaderTopBar(
                isMarkdown = notesConfig?.isMarkdown ?: true,
                onNavigateBack = onNavigateBack
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            TaskEditTextureBackground()

            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .padding(
                        top = paddingValues.calculateTopPadding() + 6.dp,
                        bottom = paddingValues.calculateBottomPadding() + 24.dp
                    ),
                shape = RoundedCornerShape(34.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
                shadowElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    if (notesConfig == null) {
                        Text(
                            text = "We couldn't find this note anymore.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        FullscreenRenderedNotesContent(
                            config = notesConfig,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskMarkdownReaderTopBar(
    isMarkdown: Boolean,
    onNavigateBack: () -> Unit
) {
    AuraGradientTopBarContainer(
        style = AuraGradientTopBarStyle.Linear,
        bottomFadePadding = 10.dp
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
                contentDescription = "Back",
                onClick = onNavigateBack
            )

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.96f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.26f)),
                tonalElevation = 2.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Notes,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isMarkdown) "Markdown reader" else "Note reader",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.size(48.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditScreen(
    taskId: String,
    onNavigateBack: () -> Unit,
    onNavigateToNotesEditor: (String, String) -> Unit,
    editorStateHandle: SavedStateHandle,
    viewModel: TaskDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val taskListState = rememberLazyListState()
    var draftTask by remember(taskId) { mutableStateOf<Task?>(null) }
    var isReorderSheetVisible by remember { mutableStateOf(false) }
    val noteEditorResultComponentId by editorStateHandle
        .getStateFlow<String?>(TASK_NOTE_EDITOR_RESULT_COMPONENT_ID_KEY, null)
        .collectAsState()
    val noteEditorResultText by editorStateHandle
        .getStateFlow<String?>(TASK_NOTE_EDITOR_RESULT_TEXT_KEY, null)
        .collectAsState()

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

    LaunchedEffect(noteEditorResultComponentId, noteEditorResultText, uiState.task?.id) {
        if (noteEditorResultComponentId != null && noteEditorResultText != null) {
            val resolvedDraft = draftTask ?: uiState.task
            if (resolvedDraft != null) {
                draftTask = resolvedDraft.updateNotesComponent(
                    componentId = noteEditorResultComponentId.orEmpty(),
                    text = noteEditorResultText.orEmpty()
                )
            }
            editorStateHandle.remove<String>(TASK_NOTE_EDITOR_RESULT_COMPONENT_ID_KEY)
            editorStateHandle.remove<String>(TASK_NOTE_EDITOR_RESULT_TEXT_KEY)
        }
    }

    Scaffold(
        topBar = {
            TaskEditTopBar(
                onNavigateBack = onNavigateBack,
                onOpenReorder = { isReorderSheetVisible = true },
                showReorderAction = (currentDraft?.components?.size ?: 0) > 1
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            TaskEditTextureBackground()

            EditableTaskRenderer(
                task = currentDraft,
                isLoading = uiState.isLoading,
                listState = taskListState,
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding() + 4.dp,
                    bottom = paddingValues.calculateBottomPadding() + if (currentDraft != null) 116.dp else 24.dp
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                onTaskChange = { updated ->
                    val current = currentDraft
                    if (current != null) {
                        val changedId = current.components.zip(updated.components)
                            .firstOrNull { (old, new) -> old != new }?.second?.id
                        draftTask = if (changedId != null) {
                            viewModel.applyRules(current, updated, changedId)
                        } else {
                            updated
                        }
                    } else {
                        draftTask = updated
                    }
                },
                onEditNotes = { component ->
                    val config = component.config as? NotesConfig ?: return@EditableTaskRenderer
                    editorStateHandle[TASK_NOTE_EDITOR_TEXT_KEY] = config.text
                    editorStateHandle[TASK_NOTE_EDITOR_IS_MARKDOWN_KEY] = config.isMarkdown
                    editorStateHandle.remove<String>(TASK_NOTE_EDITOR_RESULT_COMPONENT_ID_KEY)
                    editorStateHandle.remove<String>(TASK_NOTE_EDITOR_RESULT_TEXT_KEY)
                    onNavigateToNotesEditor(taskId, component.id)
                },
                onSignal = viewModel::onSignal
            )

            if (currentDraft != null) {
                TaskEditFloatingBar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    hasChanges = hasChanges,
                    onCancel = onNavigateBack,
                    onSave = { viewModel.saveTaskEdits(currentDraft) }
                )
            }
        }
    }

    if (isReorderSheetVisible && currentDraft != null) {
        TaskComponentReorderSheet(
            task = currentDraft,
            onTaskChange = { updated -> draftTask = updated },
            onDismissRequest = { isReorderSheetVisible = false }
        )
    }
}

@Composable
private fun TaskEditTopBar(
    onNavigateBack: () -> Unit,
    onOpenReorder: () -> Unit,
    showReorderAction: Boolean
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
                contentDescription = "Back",
                onClick = onNavigateBack
            )

            if (showReorderAction) {
                TaskDetailChromeIconButton(
                    imageVector = Icons.Rounded.SwapVert,
                    contentDescription = "Reorder components",
                    onClick = onOpenReorder,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
                    contentColor = MaterialTheme.colorScheme.primary
                )
            } else {
                Spacer(modifier = Modifier.size(48.dp))
            }
        }
    }
}

@Composable
private fun TaskEditFloatingBar(
    modifier: Modifier = Modifier,
    hasChanges: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    Surface(
        modifier = modifier
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
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TaskEditFloatingAction(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Close,
                label = "Cancel",
                onClick = onCancel,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            )

            TaskEditFloatingAction(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Save,
                label = "Save",
                onClick = onSave,
                enabled = hasChanges,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
private fun TaskEditFloatingAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color,
    contentColor: Color
) {
    val alpha = if (enabled) 1f else 0.46f

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = containerColor.copy(alpha = containerColor.alpha * alpha),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
        shadowElevation = 2.dp,
        onClick = onClick,
        enabled = enabled
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor.copy(alpha = alpha)
            )
            Spacer(modifier = Modifier.size(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor.copy(alpha = alpha),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TaskEditTextureBackground() {
    val lineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f)
    val accentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskComponentReorderSheet(
    task: Task,
    onTaskChange: (Task) -> Unit,
    onDismissRequest: () -> Unit
) {
    val orderedComponents = task.components.sortedBy(TaskComponent::sortOrder)
    val latestTask by rememberUpdatedState(task)
    val listState = rememberLazyListState()
    val hapticFeedback = LocalHapticFeedback.current
    val reorderableLazyListState = rememberReorderableLazyListState(
        lazyListState = listState,
        scrollThresholdPadding = WindowInsets.systemBars.asPaddingValues()
    ) { from, to ->
        onTaskChange(latestTask.reorderComponents(from.index, to.index))
        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Ordenar componentes",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Arrastrá los bloques para redefinir cómo se presenta la tarea.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                itemsIndexed(
                    items = orderedComponents,
                    key = { _, component -> component.id },
                    contentType = { _, component -> component.type }
                ) { index, component ->
                    ReorderableItem(
                        state = reorderableLazyListState,
                        key = component.id
                    ) { isDragging ->
                        val shadowElevation by animateDpAsState(
                            targetValue = if (isDragging) 10.dp else 0.dp,
                            label = "reorder-shadow"
                        )
                        val borderColor = if (isDragging) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)
                        }

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            color = if (isDragging) {
                                MaterialTheme.colorScheme.surface
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                            border = BorderStroke(1.dp, borderColor),
                            shadowElevation = shadowElevation
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                                ) {
                                    Icon(
                                        imageVector = component.icon(),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = component.displayName(),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = component.reorderSubtitle(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                ComponentPill("${index + 1}")

                                TaskDetailChromeIconButton(
                                    imageVector = Icons.Rounded.DragHandle,
                                    contentDescription = "Mover ${component.displayName()}",
                                    onClick = {},
                                    modifier = Modifier.draggableHandle(
                                        onDragStarted = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                        },
                                        onDragStopped = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                        }
                                    ),
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    shadowElevation = 0.dp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskMarkdownEditorScreen(
    taskId: String,
    componentId: String,
    editorStateHandle: SavedStateHandle?,
    onNavigateBack: () -> Unit,
    viewModel: TaskDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val fallbackConfig = remember(uiState.task, componentId) {
        uiState.task?.components
            ?.firstOrNull { it.id == componentId }
            ?.config as? NotesConfig
    }
    val initialText = remember(editorStateHandle, fallbackConfig) {
        editorStateHandle?.get<String>(TASK_NOTE_EDITOR_TEXT_KEY)
            ?: fallbackConfig?.text
            ?: ""
    }
    val isMarkdown = remember(editorStateHandle, fallbackConfig) {
        editorStateHandle?.get<Boolean>(TASK_NOTE_EDITOR_IS_MARKDOWN_KEY)
            ?: fallbackConfig?.isMarkdown
            ?: true
    }
    var editorMode by remember(componentId, isMarkdown) {
        mutableStateOf(MarkdownEditorMode.Markdown)
    }
    var editorValue by remember(componentId, initialText) {
        mutableStateOf(
            TextFieldValue(
                text = initialText,
                selection = TextRange(initialText.length)
            )
        )
    }

    fun pushResult(text: String) {
        editorStateHandle?.set(TASK_NOTE_EDITOR_TEXT_KEY, text)
        editorStateHandle?.set(TASK_NOTE_EDITOR_RESULT_COMPONENT_ID_KEY, componentId)
        editorStateHandle?.set(TASK_NOTE_EDITOR_RESULT_TEXT_KEY, text)
    }

    fun finishEditor() {
        pushResult(editorValue.text)
        onNavigateBack()
    }

    BackHandler(onBack = ::finishEditor)

    Scaffold(
        topBar = {
            MarkdownEditorTopBar(
                isMarkdown = isMarkdown,
                editorMode = editorMode,
                onNavigateBack = ::finishEditor,
                onModeSelected = { editorMode = it }
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            TaskEditTextureBackground()

            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .let { baseModifier ->
                        if (isMarkdown && editorMode == MarkdownEditorMode.Markdown) {
                            baseModifier.imePadding()
                        } else {
                            baseModifier
                        }
                    }
                    .padding(
                        top = paddingValues.calculateTopPadding() + 6.dp,
                        bottom = if (isMarkdown && editorMode == MarkdownEditorMode.Markdown) 112.dp else 24.dp
                    ),
                shape = RoundedCornerShape(34.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
                shadowElevation = 4.dp
            ) {
                if (editorMode == MarkdownEditorMode.Preview && isMarkdown) {
                    MarkdownRenderedPreview(
                        text = editorValue.text,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    MarkdownEditorInput(
                        value = editorValue,
                        onValueChange = { updatedValue ->
                            editorValue = updatedValue
                            pushResult(updatedValue.text)
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        isMarkdown = isMarkdown,
                        placeholder = if (isMarkdown) {
                            "Write markdown here. Use the floating toolbar to format fast."
                        } else {
                            "Write the note here."
                        },
                        minLines = 14,
                        cursorBottomPadding = if (isMarkdown) 52.dp else 28.dp
                    )
                }
            }

            if (isMarkdown && editorMode == MarkdownEditorMode.Markdown) {
                MarkdownEditorFloatingToolbar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    onApplyShortcut = { shortcut ->
                        val updatedValue = applyMarkdownShortcut(editorValue, shortcut)
                        editorValue = updatedValue
                        pushResult(updatedValue.text)
                    }
                )
            }
        }
    }
}

@Composable
private fun MarkdownRenderedPreview(
    text: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (text.isBlank()) {
            Text(
                text = "Nothing to preview yet. Switch back to markdown to keep writing.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            FullscreenRenderedNotesContent(
                config = NotesConfig(
                    text = text,
                    isMarkdown = true
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun MarkdownEditorInput(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    isMarkdown: Boolean,
    placeholder: String,
    minLines: Int,
    cursorBottomPadding: Dp
) {
    BoxWithConstraints(modifier = modifier) {
        val editorViewportHeight = maxHeight
        val bringIntoViewRequester = remember { BringIntoViewRequester() }
        val density = LocalDensity.current
        val scrollState = rememberScrollState()
        var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

        LaunchedEffect(value.text, value.selection) {
            val layoutResult = textLayoutResult ?: return@LaunchedEffect
            val cursorOffset = maxOf(value.selection.start, value.selection.end).coerceIn(0, value.text.length)
            val cursorRect = layoutResult.getCursorRect(cursorOffset)
            val extraTop = with(density) { 28.dp.toPx() }
            val extraBottom = with(density) { cursorBottomPadding.toPx() }

            bringIntoViewRequester.bringIntoView(
                Rect(
                    left = 0f,
                    top = (cursorRect.top - extraTop).coerceAtLeast(0f),
                    right = cursorRect.right.coerceAtLeast(1f),
                    bottom = cursorRect.bottom + extraBottom
                )
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            contentAlignment = Alignment.TopStart
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = editorViewportHeight)
                    .bringIntoViewRequester(bringIntoViewRequester)
                    .padding(bottom = cursorBottomPadding),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = if (isMarkdown) FontFamily.Monospace else FontFamily.Default
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                minLines = minLines,
                onTextLayout = { layoutResult ->
                    textLayoutResult = layoutResult
                },
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.TopStart
                    ) {
                        if (value.text.isBlank()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }
    }
}

@Composable
private fun MarkdownEditorTopBar(
    isMarkdown: Boolean,
    editorMode: MarkdownEditorMode,
    onNavigateBack: () -> Unit,
    onModeSelected: (MarkdownEditorMode) -> Unit
) {
    AuraGradientTopBarContainer(
        style = AuraGradientTopBarStyle.Linear,
        bottomFadePadding = 10.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TaskDetailChromeIconButton(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Volver al editor de tarea",
                onClick = onNavigateBack
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isMarkdown) {
                    MarkdownEditorModeToggle(
                        editorMode = editorMode,
                        onModeSelected = onModeSelected
                    )
                }
            }

            TaskDetailChromeIconButton(
                imageVector = Icons.Rounded.Check,
                contentDescription = "Cerrar editor",
                onClick = onNavigateBack,
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.96f),
                contentColor = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun MarkdownEditorModeToggle(
    editorMode: MarkdownEditorMode,
    onModeSelected: (MarkdownEditorMode) -> Unit
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.26f)),
        tonalElevation = 2.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MarkdownEditorModeToggleButton(
                icon = Icons.Rounded.Code,
                contentDescription = "Ver markdown",
                selected = editorMode == MarkdownEditorMode.Markdown,
                onClick = { onModeSelected(MarkdownEditorMode.Markdown) }
            )
            MarkdownEditorModeToggleButton(
                icon = Icons.Rounded.TextFields,
                contentDescription = "Ver texto interpretado",
                selected = editorMode == MarkdownEditorMode.Preview,
                onClick = { onModeSelected(MarkdownEditorMode.Preview) }
            )
        }
    }
}

@Composable
private fun MarkdownEditorModeToggleButton(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
        color = if (selected) {
            MaterialTheme.colorScheme.surface
        } else {
            Color.Transparent
        },
        border = BorderStroke(
            1.dp,
            if (selected) {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f)
            } else {
                Color.Transparent
            }
        ),
        onClick = onClick
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (selected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun MarkdownEditorFloatingToolbar(
    modifier: Modifier = Modifier,
    onApplyShortcut: (MarkdownShortcut) -> Unit
) {
    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 16.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MarkdownShortcut.entries.forEach { shortcut ->
                MarkdownToolbarActionButton(
                    shortcut = shortcut,
                    onClick = { onApplyShortcut(shortcut) }
                )
            }
        }
    }
}

private enum class MarkdownEditorMode {
    Markdown,
    Preview
}

@Composable
private fun MarkdownToolbarActionButton(
    shortcut: MarkdownShortcut,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.size(52.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)),
        onClick = onClick
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            when (shortcut) {
                MarkdownShortcut.Heading1,
                MarkdownShortcut.Heading2,
                MarkdownShortcut.Bold,
                MarkdownShortcut.Italic -> {
                    Text(
                        text = shortcut.toolbarLabel(),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (shortcut == MarkdownShortcut.Bold) FontWeight.Bold else FontWeight.SemiBold,
                            fontStyle = if (shortcut == MarkdownShortcut.Italic) FontStyle.Italic else FontStyle.Normal
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    Icon(
                        imageVector = shortcut.toolbarIcon(),
                        contentDescription = shortcut.contentDescription(),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private enum class MarkdownShortcut {
    Heading1,
    Heading2,
    Italic,
    Bullet,
    Quote,
    Bold,
    Code,
    Link
}

private fun MarkdownShortcut.toolbarLabel(): String = when (this) {
    MarkdownShortcut.Heading1 -> "H1"
    MarkdownShortcut.Heading2 -> "H2"
    MarkdownShortcut.Italic -> "I"
    MarkdownShortcut.Bold -> "B"
    else -> ""
}

private fun MarkdownShortcut.toolbarIcon(): ImageVector = when (this) {
    MarkdownShortcut.Bullet -> Icons.AutoMirrored.Rounded.FormatListBulleted
    MarkdownShortcut.Quote -> Icons.Rounded.FormatQuote
    MarkdownShortcut.Code -> Icons.Rounded.Code
    MarkdownShortcut.Link -> Icons.Rounded.Link
    else -> Icons.Rounded.Code
}

private fun MarkdownShortcut.contentDescription(): String = when (this) {
    MarkdownShortcut.Heading1 -> "Heading 1"
    MarkdownShortcut.Heading2 -> "Heading 2"
    MarkdownShortcut.Italic -> "Italic"
    MarkdownShortcut.Bullet -> "List"
    MarkdownShortcut.Quote -> "Quote"
    MarkdownShortcut.Bold -> "Bold"
    MarkdownShortcut.Code -> "Code"
    MarkdownShortcut.Link -> "Link"
}

private fun applyMarkdownShortcut(
    value: TextFieldValue,
    shortcut: MarkdownShortcut
): TextFieldValue {
    return when (shortcut) {
        MarkdownShortcut.Heading1 -> prefixSelectionLines(value, "# ")
        MarkdownShortcut.Heading2 -> prefixSelectionLines(value, "## ")
        MarkdownShortcut.Italic -> wrapSelection(value, prefix = "*", suffix = "*", placeholder = "italic")
        MarkdownShortcut.Bullet -> prefixSelectionLines(value, "- ")
        MarkdownShortcut.Quote -> prefixSelectionLines(value, "> ")
        MarkdownShortcut.Bold -> wrapSelection(value, prefix = "**", suffix = "**", placeholder = "bold")
        MarkdownShortcut.Code -> wrapSelection(value, prefix = "```\n", suffix = "\n```", placeholder = "code")
        MarkdownShortcut.Link -> wrapSelection(value, prefix = "[", suffix = "](url)", placeholder = "link text")
    }
}

private fun wrapSelection(
    value: TextFieldValue,
    prefix: String,
    suffix: String,
    placeholder: String
): TextFieldValue {
    val selectionStart = minOf(value.selection.start, value.selection.end)
    val selectionEnd = maxOf(value.selection.start, value.selection.end)
    val selectedText = value.text.substring(selectionStart, selectionEnd).ifBlank { placeholder }
    val replacement = prefix + selectedText + suffix
    val updatedText = buildString {
        append(value.text.substring(0, selectionStart))
        append(replacement)
        append(value.text.substring(selectionEnd))
    }

    return value.copy(
        text = updatedText,
        selection = TextRange(
            start = selectionStart + prefix.length,
            end = selectionStart + prefix.length + selectedText.length
        )
    )
}

private fun prefixSelectionLines(
    value: TextFieldValue,
    prefix: String
): TextFieldValue {
    val selectionStart = minOf(value.selection.start, value.selection.end)
    val selectionEnd = maxOf(value.selection.start, value.selection.end)

    if (selectionStart == selectionEnd) {
        val lineStart = value.text.lastIndexOf('\n', startIndex = (selectionStart - 1).coerceAtLeast(0))
            .let { if (it == -1) 0 else it + 1 }
        val updatedText = buildString {
            append(value.text.substring(0, lineStart))
            append(prefix)
            append(value.text.substring(lineStart))
        }

        return value.copy(
            text = updatedText,
            selection = TextRange(selectionStart + prefix.length)
        )
    }

    val selectedText = value.text.substring(selectionStart, selectionEnd)
    val prefixedText = selectedText
        .split('\n')
        .joinToString(separator = "\n") { line ->
            if (line.isBlank()) line else prefix + line
        }
    val updatedText = buildString {
        append(value.text.substring(0, selectionStart))
        append(prefixedText)
        append(value.text.substring(selectionEnd))
    }

    return value.copy(
        text = updatedText,
        selection = TextRange(selectionStart, selectionStart + prefixedText.length)
    )
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

internal fun Task.reorderComponents(fromIndex: Int, toIndex: Int): Task {
    val orderedComponents = components.sortedBy(TaskComponent::sortOrder).toMutableList()
    if (fromIndex !in orderedComponents.indices || toIndex !in orderedComponents.indices || fromIndex == toIndex) {
        return this
    }

    val movedComponent = orderedComponents.removeAt(fromIndex)
    orderedComponents.add(toIndex, movedComponent)

    return copy(
        components = orderedComponents.mapIndexed { index, component ->
            component.copy(sortOrder = index)
        }
    )
}

private fun TaskComponent.displayName(): String = type.displayName()

private fun TaskComponent.reorderSubtitle(): String {
    val details = config.reorderDetails()
    return if (details.isBlank()) {
        "UI-Skill ${sortOrder + 1}"
    } else {
        details
    }
}

private fun ComponentConfig.reorderDetails(): String = when (this) {
    is ChecklistConfig -> if (label.isNotBlank()) label else "Checklist block"
    is ProgressBarConfig -> if (label.isNotBlank()) label else "Progress tracker"
    is CountdownConfig -> if (label.isNotBlank()) label else "Countdown"
    is HabitRingConfig -> if (label.isNotBlank()) label else "Habit loop"
    is NotesConfig -> if (isMarkdown) "Markdown notes" else "Plain text notes"
    is MetricTrackerConfig -> when {
        label.isNotBlank() -> label
        unit.isNotBlank() -> "Metric in $unit"
        else -> "Metric tracker"
    }
    is DataFeedConfig -> if (displayLabel.isNotBlank()) displayLabel else "Live data block"
    else -> ""
}

private fun TaskComponent.icon(): ImageVector = type.uiSkillIcon()

private fun ComponentType.displayName(): String = uiSkillDisplayName()
