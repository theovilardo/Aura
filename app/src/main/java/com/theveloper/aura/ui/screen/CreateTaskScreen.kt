package com.theveloper.aura.ui.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AddTask
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.rounded.FlightTakeoff
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LocalDrink
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.NoteAlt
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.theveloper.aura.domain.model.TaskShape
import com.theveloper.aura.domain.model.TaskType
import com.theveloper.aura.domain.model.toTaskShape
import com.theveloper.aura.engine.dsl.TaskComponentCatalog
import com.theveloper.aura.engine.dsl.TaskComponentTemplate
import com.theveloper.aura.ui.components.ClarificationCard
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTaskScreen(
    onNavigateBack: () -> Unit,
    viewModel: CreateTaskViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedShape = remember(uiState.manual.taskTypeOverride) { uiState.manual.taskTypeOverride?.toTaskShape() }
    val selectedTemplates = remember(uiState.manual.selectedTemplateIds) {
        uiState.manual.selectedTemplateIds.mapNotNull(TaskComponentCatalog::find)
    }
    val recommendedTemplates = remember(uiState.manual.resolvedTaskType) {
        TaskComponentCatalog.recommended(uiState.manual.resolvedTaskType)
    }
    val canSubmit = uiState.input.isNotBlank() || uiState.manual.title.isNotBlank()

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is CreateTaskEffect.TaskCreated -> onNavigateBack()
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CreateTaskTopBar(onNavigateBack = onNavigateBack)
        },
        bottomBar = {
            CreateTaskPromptBar(
                prompt = uiState.input,
                canSubmit = canSubmit,
                isBusy = uiState.isClassifying || uiState.isSaving,
                onPromptChange = viewModel::updateInput,
                onSubmit = viewModel::submit
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding()
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                BuilderSection(
                    title = "Title",
                    titlePadding = 16.dp
                ) {
                    CreateTaskTitleField(
                        value = uiState.manual.title,
                        onValueChange = viewModel::updateManualTitle,
                    )
                }
            }

            item {
                BuilderSection(
                    title = "Shape",
                    titlePadding = 16.dp,
                    body = selectedShape?.shortDescription
                        ?: "Aura can detect the shape automatically, or you can pin one before creating."
                ) {
                    CreateTaskTypeRow(
                        selected = uiState.manual.taskTypeOverride,
                        onSelect = viewModel::selectManualTaskType
                    )
                }
            }

            item {
                BuilderSection(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    title = "Modules",
                    body = if (selectedTemplates.isEmpty()) {
                        "Pick the modules you want Aura to keep when it builds this shape."
                    } else {
                        "${selectedTemplates.size} selected"
                    }
                ) {
                    if (selectedTemplates.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shape = CircleShape)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            selectedTemplates.forEach { template ->
                                SelectedTemplatePill(template = template)
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.padding(top = if (selectedTemplates.isEmpty()) 0.dp else 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        recommendedTemplates.forEach { template ->
                            ManualTemplateCard(
                                modifier = Modifier.fillMaxWidth(),
                                template = template,
                                selected = template.id in uiState.manual.selectedTemplateIds,
                                onToggle = { viewModel.toggleTemplate(template.id) }
                            )
                        }
                    }
                }
            }

            uiState.errorMessage?.let { errorMessage ->
                item {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.error.copy(alpha = 0.28f)
                        )
                    ) {
                        Text(
                            text = errorMessage,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        uiState.clarification?.let { clarification ->
            ModalBottomSheet(onDismissRequest = viewModel::skipClarification) {
                ClarificationCard(
                    title = clarification.baseResult.dsl.title,
                    question = clarification.currentRequest.question,
                    answer = clarification.answer,
                    skipLabel = clarification.currentRequest.skipLabel,
                    isBusy = uiState.isClassifying,
                    onAnswerChange = viewModel::updateClarificationAnswer,
                    onSubmit = viewModel::submitClarification,
                    onSkip = viewModel::skipClarification
                )
            }
        }

        uiState.preview?.let { preview ->
            ModalBottomSheet(onDismissRequest = viewModel::dismissPreview) {
                TaskPreviewBottomSheet(
                    preview = preview,
                    isSaving = uiState.isSaving,
                    onTaskTypeChange = viewModel::selectPreviewTaskType,
                    onConfirm = viewModel::confirmPreview,
                    onCancel = viewModel::dismissPreview
                )
            }
        }
    }
}

@Composable
private fun CreateTaskTopBar(
    onNavigateBack: () -> Unit
) {
    AuraGradientTopBarContainer(
        style = AuraGradientTopBarStyle.Linear,
        bottomFadePadding = 12.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            CreateTaskChromeIconButton(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                onClick = onNavigateBack,
                modifier = Modifier.align(Alignment.CenterStart)
            )

            Text(
                text = "Create",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 30.sp,
                    lineHeight = 30.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
private fun CreateTaskPromptBar(
    prompt: String,
    canSubmit: Boolean,
    isBusy: Boolean,
    onPromptChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    val backgroundColor = MaterialTheme.colorScheme.background
    val bottomBarBrush = remember(backgroundColor) {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color.Transparent,
                0.32f to backgroundColor.copy(alpha = 0.76f),
                1.0f to backgroundColor
            )
        )
    }
    var promptVisualLineCount by remember { mutableIntStateOf(1) }
    val promptCornerRadius by animateDpAsState(
        targetValue = when {
            promptVisualLineCount <= 1 -> 34.dp
            promptVisualLineCount == 2 -> 28.dp
            promptVisualLineCount == 3 -> 24.dp
            else -> 20.dp
        },
        label = "createPromptCorner"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bottomBarBrush)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .animateContentSize(),
                shape = RoundedCornerShape(promptCornerRadius),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            ) {
                BasicTextField(
                    value = prompt,
                    onValueChange = onPromptChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 64.dp)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (canSubmit && !isBusy) {
                                onSubmit()
                            }
                        }
                    ),
                    minLines = 1,
                    maxLines = 6,
                    onTextLayout = { layoutResult ->
                        promptVisualLineCount = layoutResult.lineCount.coerceIn(1, 6)
                    },
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (prompt.isBlank()) {
                                Text(
                                    text = "Describe task or add context...",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }

            Surface(
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                color = if (canSubmit) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                border = BorderStroke(
                    1.dp,
                    if (canSubmit) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)
                    }
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(enabled = canSubmit && !isBusy, onClick = onSubmit),
                    contentAlignment = Alignment.Center
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.AddTask,
                            contentDescription = "Build task",
                            tint = if (canSubmit) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateTaskTitleField(
    value: String,
    onValueChange: (String) -> Unit
) {
    TextField(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        placeholder = {
            Text(
                text = "Launch summer habit reset",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            disabledContainerColor = MaterialTheme.colorScheme.surface,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            cursorColor = MaterialTheme.colorScheme.primary
        )
    )
}

@Composable
private fun CreateTaskTypeRow(
    selected: TaskType?,
    onSelect: (TaskType?) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        item {
            val active = selected == null
            Surface(
                shape = CircleShape,
                color = if (active) MaterialTheme.colorScheme.inverseSurface
                else MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (active) MaterialTheme.colorScheme.inverseSurface
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                ),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onSelect(null) }
            ) {
                Text(
                    text = "Auto",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = if (active) MaterialTheme.colorScheme.inverseOnSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        TaskShape.userFacingOrder.forEach { shape ->
            val active = shape.taskType == selected
            item {
                Surface(
                    shape = CircleShape,
                    color = if (active) MaterialTheme.colorScheme.inverseSurface
                    else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (active) MaterialTheme.colorScheme.inverseSurface
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                    ),
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onSelect(shape.taskType) }
                ) {
                    Text(
                        text = shape.displayName,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = if (active) MaterialTheme.colorScheme.inverseOnSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun BuilderSection(
    modifier: Modifier = Modifier,
    title: String,
    titlePadding: Dp = 0.dp,
    body: String? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            modifier = Modifier.padding(start = titlePadding),
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        body?.let {
            Text(
                modifier = Modifier.padding(horizontal = titlePadding),
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        content()
    }
}

@Composable
private fun CreateTaskChromeIconButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified
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
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
        )
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = resolvedContentColor
            )
        }
    }
}

@Composable
private fun SelectedTemplatePill(template: TaskComponentTemplate) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = createTaskTemplateIcon(template),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "${template.title} · ${template.variantLabel}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
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
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            1.dp,
            if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.58f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val templateIcon = createTaskTemplateIcon(template)

            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = templateIcon,
                        contentDescription = null,
                        tint = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = template.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = template.variantLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                Text(
                    text = template.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }

            Surface(
                shape = CircleShape,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                }
            ) {
                Text(
                    text = if (selected) "Added" else "Add",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

private fun createTaskTemplateIcon(template: TaskComponentTemplate): ImageVector {
    return when (template.id) {
        "travel_countdown" -> Icons.Rounded.FlightTakeoff
        "deadline_countdown" -> Icons.Rounded.Event
        "event_countdown" -> Icons.Rounded.Event
        "payment_countdown" -> Icons.Rounded.Payments
        "packing_checklist" -> Icons.Rounded.Checklist
        "travel_documents_checklist" -> Icons.Rounded.NoteAlt
        "action_checklist" -> Icons.Rounded.Checklist
        "event_runbook_checklist" -> Icons.Rounded.Checklist
        "goal_milestones_checklist" -> Icons.Rounded.Flag
        "finance_payment_checklist" -> Icons.Rounded.Payments
        "medication_checklist" -> Icons.Rounded.Medication
        "habit_daily" -> Icons.Rounded.Repeat
        "habit_weekly" -> Icons.Rounded.CalendarMonth
        "progress_manual" -> Icons.AutoMirrored.Rounded.TrendingUp
        "progress_milestones" -> Icons.Rounded.Flag
        "progress_budget" -> Icons.Rounded.Savings
        "progress_sprint" -> Icons.Rounded.Bolt
        "goal_progress" -> Icons.Rounded.Flag
        "notes_brain_dump" -> Icons.Rounded.NoteAlt
        "notes_meeting" -> Icons.Rounded.Groups
        "event_notes" -> Icons.Rounded.Event
        "travel_itinerary_notes" -> Icons.Rounded.Map
        "study_plan_notes" -> Icons.Rounded.School
        "goal_notes" -> Icons.Rounded.Flag
        "budget_snapshot_notes" -> Icons.Rounded.AccountBalanceWallet
        "journal_reflection" -> Icons.AutoMirrored.Rounded.MenuBook
        "notes_clinic" -> Icons.Rounded.LocalHospital
        "metric_hydration" -> Icons.Rounded.LocalDrink
        "metric_weight" -> Icons.Rounded.MonitorWeight
        "metric_steps" -> Icons.AutoMirrored.Rounded.DirectionsWalk
        "metric_sleep" -> Icons.Rounded.Bedtime
        "metric_budget_target" -> Icons.Rounded.Savings
        "goal_momentum_metric" -> Icons.AutoMirrored.Rounded.TrendingUp
        "feed_weather" -> Icons.Rounded.Cloud
        "feed_exchange" -> Icons.Rounded.CurrencyExchange
        else -> Icons.Default.AutoAwesome
    }
}
