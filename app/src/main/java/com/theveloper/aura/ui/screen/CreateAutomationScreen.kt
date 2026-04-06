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
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Splitscreen
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
import com.theveloper.aura.domain.model.AutomationOutputType
import com.theveloper.aura.domain.model.AutomationStep
import com.theveloper.aura.domain.model.AutomationStepType
import com.theveloper.aura.engine.dsl.AutomationDSLOutput
import com.theveloper.aura.ui.components.ClassificationLoadingOverlay

@Composable
fun CreateAutomationScreen(
    onNavigateBack: () -> Unit,
    viewModel: CreateAutomationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var inputText by rememberSaveable { mutableStateOf("") }
    var isConfirming by rememberSaveable { mutableStateOf(false) }
    val isLoading = uiState is CreateAutomationUiState.Loading

    LaunchedEffect(uiState) {
        when (uiState) {
            is CreateAutomationUiState.Created -> onNavigateBack()
            is CreateAutomationUiState.Error,
            is CreateAutomationUiState.Preview,
            CreateAutomationUiState.Idle,
            CreateAutomationUiState.Loading -> isConfirming = false
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
                placeholder = "Every Monday summarize what is still pending for the week",
                inputContentDescription = "Automation prompt",
                submitIcon = Icons.Rounded.Bolt,
                submitContentDescription = "Build automation",
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
                        icon = Icons.Rounded.Bolt,
                        title = "Automation",
                        description = "Schedule a repeatable workflow that gathers context, processes it and delivers a result automatically.",
                        pills = listOf("Scheduled", "Context-aware", "AI output")
                    )
                }

                when (val state = uiState) {
                    CreateAutomationUiState.Idle,
                    CreateAutomationUiState.Loading -> {
                        item {
                            AutomationGuidanceSection()
                        }
                    }

                    is CreateAutomationUiState.Preview -> {
                        item {
                            AutomationPreviewSection(
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

                    is CreateAutomationUiState.Error -> {
                        item {
                            CreationSection(
                                title = "Problem",
                                body = "Aura couldn't turn the prompt into an automation yet."
                            ) {
                                CreationNoticeCard(
                                    title = "Could not build automation",
                                    body = state.message,
                                    icon = Icons.Rounded.WarningAmber,
                                    tone = CreationNoticeTone.Error
                                )
                            }
                        }
                        item {
                            AutomationGuidanceSection()
                        }
                    }

                    is CreateAutomationUiState.Created -> Unit
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
private fun AutomationGuidanceSection() {
    CreationSection(
        title = "What To Include",
        body = "Describe when it should run, what context it needs and how the result should be delivered."
    ) {
        CreationCard {
            CreationDetailRow(label = "Schedule", value = "Day, cadence or window when it should fire")
            CreationDetailRow(label = "Goal", value = "What the automation should produce")
            CreationDetailRow(label = "Context", value = "Tasks, events, metrics or reminders to inspect")
        }

        CreationNoticeCard(
            title = "Good prompt example",
            body = "Every weekday at 6pm summarize unfinished tasks and send me a short priority digest.",
            icon = Icons.Filled.AutoAwesome
        )
    }
}

@Composable
private fun AutomationPreviewSection(
    dsl: AutomationDSLOutput,
    warnings: List<String>,
    isConfirming: Boolean,
    onConfirm: () -> Unit
) {
    CreationSection(
        title = "Automation Draft",
        body = "Make sure the timing, output and execution flow match what you expect."
    ) {
        CreationCard(highlighted = true) {
            Text(
                text = dsl.title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = dsl.prompt,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CreationChip(
                    text = dsl.outputType.displayLabel(),
                    icon = Icons.Rounded.Splitscreen,
                    emphasized = true
                )
                CreationChip(
                    text = dsl.cronExpression.ifBlank { "Schedule pending" },
                    icon = Icons.Rounded.Schedule
                )
                CreationChip(text = "${dsl.executionPlan.steps.size} steps")
            }
        }

        CreationCard {
            CreationDetailRow(
                label = "Schedule",
                value = dsl.cronExpression.ifBlank { "No cron expression inferred" }
            )
            CreationDetailRow(label = "Output", value = dsl.outputType.displayLabel())
            CreationDetailRow(
                label = "Steps",
                value = dsl.executionPlan.steps.size.toString()
            )
        }

        if (dsl.executionPlan.steps.isNotEmpty()) {
            CreationSection(title = "Execution Plan") {
                dsl.executionPlan.steps.forEachIndexed { index, step ->
                    AutomationStepCard(index = index, step = step)
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
            primaryLabel = "Activate automation",
            primaryIcon = Icons.Rounded.Check,
            onPrimaryClick = onConfirm,
            isPrimaryBusy = isConfirming
        )
    }
}

@Composable
private fun AutomationStepCard(
    index: Int,
    step: AutomationStep
) {
    CreationCard {
        Text(
            text = "Step ${index + 1}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = step.type.displayLabel(),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = step.description.ifBlank { "No description provided." },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (step.params.isNotEmpty()) {
            step.params.entries.sortedBy { it.key }.forEach { entry ->
                CreationDetailRow(
                    label = entry.key.replaceFirstChar { it.uppercase() },
                    value = entry.value
                )
            }
        }
    }
}

private fun AutomationOutputType.displayLabel(): String = when (this) {
    AutomationOutputType.NOTIFICATION -> "Notification"
    AutomationOutputType.TASK_UPDATE -> "Task update"
    AutomationOutputType.SUMMARY -> "Summary"
    AutomationOutputType.CUSTOM -> "Custom"
}

private fun AutomationStepType.displayLabel(): String = when (this) {
    AutomationStepType.GATHER_CONTEXT -> "Gather context"
    AutomationStepType.LLM_PROCESS -> "Process with model"
    AutomationStepType.OUTPUT -> "Deliver output"
}
