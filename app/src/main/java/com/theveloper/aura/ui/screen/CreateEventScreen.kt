package com.theveloper.aura.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.theveloper.aura.domain.model.ComponentType
import com.theveloper.aura.domain.model.EventSubActionType
import com.theveloper.aura.engine.dsl.ComponentDSL
import com.theveloper.aura.engine.dsl.EventDSLOutput
import com.theveloper.aura.engine.dsl.EventSubActionDSL
import com.theveloper.aura.ui.components.ClassificationLoadingOverlay

@Composable
fun CreateEventScreen(
    onNavigateBack: () -> Unit,
    viewModel: CreateEventViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var inputText by rememberSaveable { mutableStateOf("") }
    var isConfirming by rememberSaveable { mutableStateOf(false) }
    val isLoading = uiState is CreateEventUiState.Loading

    LaunchedEffect(uiState) {
        when (uiState) {
            is CreateEventUiState.Created -> onNavigateBack()
            is CreateEventUiState.Error,
            is CreateEventUiState.Preview,
            CreateEventUiState.Idle,
            CreateEventUiState.Loading -> isConfirming = false
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CreationTopBar(
                title = "Create",
                onNavigateBack = onNavigateBack
            )
        },
        bottomBar = {
            CreationPromptBar(
                prompt = inputText,
                placeholder = "Three-day conference next month, remind me to log expenses and prep each morning",
                inputContentDescription = "Event prompt",
                submitIcon = Icons.Rounded.Event,
                submitContentDescription = "Build event",
                canSubmit = inputText.isNotBlank(),
                isBusy = isLoading,
                onPromptChange = { inputText = it },
                onSubmit = { viewModel.submitPrompt(inputText) }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = paddingValues.calculateTopPadding(),
                    end = 16.dp,
                    bottom = paddingValues.calculateBottomPadding()
                ),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                item {
                    CreationHeroCard(
                        icon = Icons.Rounded.Event,
                        title = "Event Timeline",
                        description = "Bundle a time-bound plan with its date range, supporting automations and tracking components.",
                        pills = listOf("Date range", "Sub-actions", "Tracking")
                    )
                }

                when (val state = uiState) {
                    CreateEventUiState.Idle,
                    CreateEventUiState.Loading -> {
                        item {
                            EventGuidanceSection()
                        }
                    }

                    is CreateEventUiState.Preview -> {
                        item {
                            EventPreviewSection(
                                dsl = state.dsl,
                                warnings = state.warnings,
                                isConfirming = isConfirming,
                                onConfirm = {
                                    isConfirming = true
                                    viewModel.confirmPreview()
                                }
                            )
                        }
                    }

                    is CreateEventUiState.Error -> {
                        item {
                            CreationSection(
                                title = "Problem",
                                body = "Aura couldn't shape the event from the prompt yet."
                            ) {
                                CreationNoticeCard(
                                    title = "Could not build event",
                                    body = state.message,
                                    icon = Icons.Rounded.WarningAmber,
                                    tone = CreationNoticeTone.Error
                                )
                            }
                        }
                        item {
                            EventGuidanceSection()
                        }
                    }

                    is CreateEventUiState.Created -> Unit
                }
            }

            ClassificationLoadingOverlay(
                visible = isLoading,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = paddingValues.calculateTopPadding(),
                        bottom = paddingValues.calculateBottomPadding()
                    )
            )
        }
    }
}

@Composable
private fun EventGuidanceSection() {
    CreationSection(
        title = "What To Include",
        body = "Events work best when the prompt includes a clear window plus any automations or tracking you expect during it."
    ) {
        CreationCard {
            CreationDetailRow(label = "Range", value = "Start date, end date or duration")
            CreationDetailRow(label = "Sub-actions", value = "Notifications, prompts or automations during the event")
            CreationDetailRow(label = "Tracking", value = "Components like checklist, notes or metrics")
        }

        CreationNoticeCard(
            title = "Good prompt example",
            body = "Festival next weekend for 3 days, remind me each morning to pack essentials and track what I spend.",
            icon = Icons.Filled.AutoAwesome
        )
    }
}

@Composable
private fun EventPreviewSection(
    dsl: EventDSLOutput,
    warnings: List<String>,
    isConfirming: Boolean,
    onConfirm: () -> Unit
) {
    CreationSection(
        title = "Event Draft",
        body = "Review the time window, runtime actions and tracking structure before saving it."
    ) {
        CreationCard(highlighted = true) {
            Text(
                text = dsl.title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            if (dsl.description.isNotBlank()) {
                Text(
                    text = dsl.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (dsl.startAtMs > 0L) {
                    CreationChip(
                        text = formatCreationDate(dsl.startAtMs),
                        icon = Icons.Rounded.Schedule,
                        emphasized = true
                    )
                }
                CreationChip(text = "${dsl.subActions.size} sub-actions")
                if (dsl.components.isNotEmpty()) {
                    CreationChip(
                        text = "${dsl.components.size} components",
                        icon = Icons.Rounded.Tune
                    )
                }
            }
        }

        CreationCard {
            CreationDetailRow(label = "Starts", value = formatCreationDateTime(dsl.startAtMs))
            CreationDetailRow(label = "Ends", value = formatCreationDateTime(dsl.endAtMs))
            if (dsl.startAtMs > 0L && dsl.endAtMs > dsl.startAtMs) {
                CreationDetailRow(
                    label = "Duration",
                    value = formatCreationDuration(dsl.endAtMs - dsl.startAtMs)
                )
            }
        }

        if (dsl.subActions.isNotEmpty()) {
            CreationSection(title = "Runtime Actions") {
                dsl.subActions.forEachIndexed { index, subAction ->
                    EventSubActionCard(index = index, subAction = subAction)
                }
            }
        }

        if (dsl.components.isNotEmpty()) {
            CreationSection(title = "Tracking Components") {
                CreationCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        dsl.components.forEach { component ->
                            CreationChip(text = component.type.displayLabel())
                        }
                    }
                }
            }
        }

        if (warnings.isNotEmpty()) {
            CreationNoticeCard(
                title = "Review Before Saving",
                body = warnings.joinToString(separator = "\n") { "• $it" },
                icon = Icons.Rounded.WarningAmber,
                tone = CreationNoticeTone.Warning
            )
        }

        CreationActionRow(
            primaryLabel = "Create event",
            primaryIcon = Icons.Rounded.Check,
            onPrimaryClick = onConfirm,
            isPrimaryBusy = isConfirming
        )
    }
}

@Composable
private fun EventSubActionCard(
    index: Int,
    subAction: EventSubActionDSL
) {
    CreationCard {
        Text(
            text = "Action ${index + 1}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = subAction.type.displayLabel(),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = subAction.title.ifBlank { subAction.prompt.ifBlank { "Untitled action" } },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (subAction.cronExpression.isNotBlank()) {
            CreationDetailRow(label = "Schedule", value = subAction.cronExpression)
        }
        if (subAction.intervalMs > 0L) {
            CreationDetailRow(label = "Interval", value = formatCreationDuration(subAction.intervalMs))
        }
        CreationDetailRow(
            label = "Enabled",
            value = if (subAction.enabled) "Yes" else "No"
        )
    }
}

private fun EventSubActionType.displayLabel(): String = when (this) {
    EventSubActionType.NOTIFICATION -> "Notification"
    EventSubActionType.METRIC_PROMPT -> "Metric prompt"
    EventSubActionType.AUTOMATION -> "Automation"
    EventSubActionType.CHECKLIST_REMIND -> "Checklist reminder"
}

private fun ComponentDSL.typeLabel(): String = type.displayLabel()

private fun ComponentType.displayLabel(): String = when (this) {
    ComponentType.CHECKLIST -> "Checklist"
    ComponentType.PROGRESS_BAR -> "Progress"
    ComponentType.COUNTDOWN -> "Countdown"
    ComponentType.HABIT_RING -> "Habit ring"
    ComponentType.NOTES -> "Notes"
    ComponentType.METRIC_TRACKER -> "Metrics"
    ComponentType.DATA_FEED -> "Data feed"
}
