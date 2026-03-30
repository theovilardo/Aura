package com.theveloper.aura.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.theveloper.aura.domain.model.ReminderType
import com.theveloper.aura.engine.dsl.ReminderDSLOutput
import com.theveloper.aura.ui.components.ClassificationLoadingOverlay

@Composable
fun CreateReminderScreen(
    onNavigateBack: () -> Unit,
    viewModel: CreateReminderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var inputText by rememberSaveable { mutableStateOf("") }
    var isConfirming by rememberSaveable { mutableStateOf(false) }
    val isLoading = uiState is CreateReminderUiState.Loading

    LaunchedEffect(uiState) {
        when (uiState) {
            is CreateReminderUiState.Created -> onNavigateBack()
            is CreateReminderUiState.Error,
            is CreateReminderUiState.Preview,
            CreateReminderUiState.Idle,
            CreateReminderUiState.Loading -> isConfirming = false
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
                placeholder = "Remind me to stretch every weekday at 8:30",
                inputContentDescription = "Reminder prompt",
                submitIcon = Icons.Rounded.Alarm,
                submitContentDescription = "Build reminder",
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
                        icon = Icons.Rounded.Alarm,
                        title = "Standalone Reminder",
                        description = "Turn natural language into one-off, repeating or cyclical reminders with optional checklist context.",
                        pills = listOf("Time-aware", "Repeat rules", "Checklist ready")
                    )
                }

                when (val state = uiState) {
                    CreateReminderUiState.Idle,
                    CreateReminderUiState.Loading -> {
                        item {
                            ReminderGuidanceSection()
                        }
                    }

                    is CreateReminderUiState.Preview -> {
                        item {
                            ReminderPreviewSection(
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

                    is CreateReminderUiState.Error -> {
                        item {
                            CreationSection(
                                title = "Problem",
                                body = "Aura couldn't turn the prompt into a reminder yet."
                            ) {
                                CreationNoticeCard(
                                    title = "Could not build reminder",
                                    body = state.message,
                                    icon = Icons.Rounded.WarningAmber,
                                    tone = CreationNoticeTone.Error
                                )
                            }
                        }
                        item {
                            ReminderGuidanceSection()
                        }
                    }

                    is CreateReminderUiState.Created -> Unit
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
private fun ReminderGuidanceSection() {
    CreationSection(
        title = "What To Include",
        body = "The better the prompt, the better Aura can infer timing and recurrence."
    ) {
        CreationCard {
            CreationDetailRow(label = "When", value = "Exact time, part of day or cadence")
            CreationDetailRow(label = "Repeat", value = "Once, every day, weekdays or custom cycle")
            CreationDetailRow(label = "Context", value = "Checklist items, link or optional note")
        }

        CreationNoticeCard(
            title = "Good prompt example",
            body = "Remind me every Monday at 8am to prepare the weekly team update and include the KPI sheet link.",
            icon = Icons.Filled.AutoAwesome
        )
    }
}

@Composable
private fun ReminderPreviewSection(
    dsl: ReminderDSLOutput,
    warnings: List<String>,
    isConfirming: Boolean,
    onConfirm: () -> Unit
) {
    CreationSection(
        title = "Reminder Draft",
        body = "Review the title, schedule and any extra context before creating it."
    ) {
        CreationCard(highlighted = true) {
            Text(
                text = dsl.title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            if (dsl.body.isNotBlank()) {
                Text(
                    text = dsl.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ReminderChipRow(dsl = dsl)
        }

        CreationCard {
            CreationDetailRow(label = "Type", value = dsl.reminderType.displayLabel())
            CreationDetailRow(label = "Schedule", value = formatCreationDateTime(dsl.scheduledAtMs))
            dsl.repeatSummary()?.let { summary ->
                CreationDetailRow(label = "Repetition", value = summary)
            }
            if (dsl.linkedTaskId != null) {
                CreationDetailRow(label = "Linked task", value = "Attached")
            }
        }

        if (dsl.checklistItems.isNotEmpty()) {
            CreationSection(title = "Checklist") {
                CreationCard {
                    dsl.checklistItems.forEachIndexed { index, item ->
                        Text(
                            text = "${index + 1}. $item",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        if (dsl.links.isNotEmpty()) {
            CreationSection(title = "Links") {
                CreationCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        dsl.links.forEach { link ->
                            CreationChip(
                                text = link,
                                icon = Icons.Rounded.Link
                            )
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
            primaryLabel = "Create reminder",
            primaryIcon = Icons.Rounded.Check,
            onPrimaryClick = onConfirm,
            isPrimaryBusy = isConfirming
        )
    }
}

@Composable
private fun ReminderChipRow(dsl: ReminderDSLOutput) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CreationChip(
            text = dsl.reminderType.displayLabel(),
            icon = Icons.Rounded.Repeat,
            emphasized = true
        )
        CreationChip(
            text = formatCreationDateTime(dsl.scheduledAtMs),
            icon = Icons.Rounded.Schedule
        )
        if (dsl.checklistItems.isNotEmpty()) {
            CreationChip(
                text = "${dsl.checklistItems.size} items",
                icon = Icons.Rounded.Checklist
            )
        }
    }
}

private fun ReminderType.displayLabel(): String = when (this) {
    ReminderType.ONE_TIME -> "One time"
    ReminderType.REPEATING -> "Repeating"
    ReminderType.CYCLICAL -> "Cyclical"
}

private fun ReminderDSLOutput.repeatSummary(): String? = when (reminderType) {
    ReminderType.ONE_TIME -> null
    ReminderType.REPEATING -> buildString {
        if (intervalMs > 0L) {
            append("Every ")
            append(formatCreationDuration(intervalMs))
        } else {
            append("Recurring interval")
        }
        if (repeatCount > 0) {
            append(" · ")
            append(repeatCount)
            append(if (repeatCount == 1) " repeat" else " repeats")
        }
    }
    ReminderType.CYCLICAL -> cronExpression.ifBlank { "Cron schedule" }
}
