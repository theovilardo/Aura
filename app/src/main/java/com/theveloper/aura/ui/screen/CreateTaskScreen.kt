package com.theveloper.aura.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.theveloper.aura.domain.model.TaskType
import com.theveloper.aura.engine.dsl.TaskComponentCatalog
import com.theveloper.aura.engine.dsl.TaskComponentTemplate
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTaskScreen(
    onNavigateBack: () -> Unit,
    viewModel: CreateTaskViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is CreateTaskEffect.TaskCreated -> onNavigateBack()
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CreateTaskTopBar(
                mode = uiState.mode,
                onNavigateBack = onNavigateBack,
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            BuildTaskFab(
                mode = uiState.mode,
                isBusy = uiState.isClassifying || uiState.isSaving,
                onClick = viewModel::submit
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 6.dp, bottom = 128.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ModePill(
                        label = "Prompt",
                        selected = uiState.mode == TaskCreationMode.PROMPT,
                        onClick = { viewModel.selectMode(TaskCreationMode.PROMPT) }
                    )
                    ModePill(
                        label = "Manual",
                        selected = uiState.mode == TaskCreationMode.MANUAL,
                        onClick = { viewModel.selectMode(TaskCreationMode.MANUAL) }
                    )
                }
            }

            item {
                if (uiState.mode == TaskCreationMode.PROMPT) {
                    PromptBuilder(
                        input = uiState.input,
                        isWorking = uiState.isClassifying,
                        onValueChange = viewModel::updateInput
                    )
                } else {
                    ManualBuilder(
                        uiState = uiState,
                        onTitleChange = viewModel::updateManualTitle,
                        onTaskTypeSelected = viewModel::selectManualTaskType,
                        onTemplateToggle = viewModel::toggleTemplate
                    )
                }
            }

            uiState.errorMessage?.let { errorMessage ->
                item {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        uiState.preview?.let { preview ->
            ModalBottomSheet(onDismissRequest = viewModel::dismissPreview) {
                TaskPreviewBottomSheet(
                    preview = preview,
                    isSaving = uiState.isSaving,
                    onConfirm = viewModel::confirmPreview,
                    onCancel = viewModel::dismissPreview
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateTaskTopBar(
    mode: TaskCreationMode,
    onNavigateBack: () -> Unit,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior
) {
    val title = if (mode == TaskCreationMode.MANUAL) {
        "Create task"
    } else {
        "Prompt task"
    }

    MediumTopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver"
                )
            }
        },
        colors = TopAppBarDefaults.mediumTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background
        ),
        scrollBehavior = scrollBehavior,
        windowInsets = TopAppBarDefaults.windowInsets
    )
}

@Composable
private fun BuildTaskFab(
    mode: TaskCreationMode,
    isBusy: Boolean,
    onClick: () -> Unit
) {
    ExtendedFloatingActionButton(
        onClick = {
            if (!isBusy) {
                onClick()
            }
        },
        containerColor = if (isBusy) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        } else {
            MaterialTheme.colorScheme.primary
        },
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = CircleShape,
        icon = {
            if (isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null
                )
            }
        },
        text = {
            Text(
                if (mode == TaskCreationMode.PROMPT) {
                    "Generate preview"
                } else {
                    "Build preview"
                }
            )
        }
    )
}

@Composable
private fun ModePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PromptBuilder(
    input: String,
    isWorking: Boolean,
    onValueChange: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                label = { Text("Describí tu tarea...") },
                placeholder = { Text("Quiero viajar a Madrid en agosto y seguir precios de vuelos") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                supportingText = {
                    Text(
                        if (isWorking) "Clasificando..." else "Prompt example: recordatorio para tomar agua cada 2 horas"
                    )
                },
                maxLines = 10
            )
        }
    }
}

@Composable
private fun ManualBuilder(
    uiState: CreateTaskUiState,
    onTitleChange: (String) -> Unit,
    onTaskTypeSelected: (TaskType) -> Unit,
    onTemplateToggle: (String) -> Unit
) {
    val recommendedTemplates = TaskComponentCatalog.recommended(uiState.manual.taskType)
    val selectedTemplates = recommendedTemplates.filter { it.id in uiState.manual.selectedTemplateIds }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        ManualSection(
            title = "Task title"
        ) {
            OutlinedTextField(
                value = uiState.manual.title,
                onValueChange = onTitleChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Task title") },
                placeholder = { Text("Plan workout for tomorrow") }
            )
        }

        ManualSection(
            title = "Task type"
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TaskType.entries.forEach { taskType ->
                    ModePill(
                        label = taskType.name.lowercase().replaceFirstChar { it.titlecase() },
                        selected = taskType == uiState.manual.taskType,
                        onClick = { onTaskTypeSelected(taskType) }
                    )
                }
            }
        }

        ManualSection(
            title = "Modules",
            body = if (selectedTemplates.isEmpty()) null else "${selectedTemplates.size} selected"
        ) {
            if (selectedTemplates.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    selectedTemplates.forEach { template ->
                        SelectedTemplatePill(template = template)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                recommendedTemplates.chunked(2).forEach { rowTemplates ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowTemplates.forEach { template ->
                            ManualTemplateCard(
                                modifier = Modifier.weight(1f),
                                template = template,
                                selected = template.id in uiState.manual.selectedTemplateIds,
                                onToggle = { onTemplateToggle(template.id) }
                            )
                        }
                        if (rowTemplates.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualSection(
    title: String,
    body: String? = null,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        body?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        content()
    }
}

@Composable
private fun SelectedTemplatePill(template: TaskComponentTemplate) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Text(
            text = "${template.title} · ${template.variantLabel}",
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ManualTemplateCard(
    template: TaskComponentTemplate,
    selected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onToggle),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            }
        ),
        border = if (selected) {
            androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                text = template.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Surface(
                shape = CircleShape,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                } else {
                    MaterialTheme.colorScheme.surface
                }
            ) {
                Text(
                    text = template.variantLabel,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = template.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
