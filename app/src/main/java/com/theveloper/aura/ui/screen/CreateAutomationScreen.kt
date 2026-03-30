package com.theveloper.aura.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateAutomationScreen(
    onNavigateBack: () -> Unit,
    viewModel: CreateAutomationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var inputText by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Top bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp)
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.Bolt, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "New Automation",
                style = MaterialTheme.typography.titleLarge
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Describe what you want automated",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            BasicTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = { viewModel.submitPrompt(inputText) }
                ),
                decorationBox = { innerTextField ->
                    if (inputText.isEmpty()) {
                        Text(
                            "e.g. Every Monday summarize what I have pending this week",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                    innerTextField()
                }
            )

            Spacer(Modifier.height(24.dp))

            when (val state = uiState) {
                is CreateAutomationUiState.Idle -> {}
                is CreateAutomationUiState.Loading -> {
                    Text("Building automation plan...", style = MaterialTheme.typography.bodyMedium)
                }
                is CreateAutomationUiState.Preview -> {
                    Text("Preview", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("Title: ${state.dsl.title}", style = MaterialTheme.typography.bodyLarge)
                    Text("Schedule: ${state.dsl.cronExpression}", style = MaterialTheme.typography.bodyMedium)
                    Text("Output: ${state.dsl.outputType}", style = MaterialTheme.typography.bodyMedium)

                    Spacer(Modifier.height(12.dp))
                    Text("Execution Plan:", style = MaterialTheme.typography.titleSmall)
                    state.dsl.executionPlan.steps.forEachIndexed { index, step ->
                        Text(
                            "${index + 1}. [${step.type}] ${step.description}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                        )
                    }

                    if (state.warnings.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        state.warnings.forEach { w ->
                            Text(w, color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    FilterChip(
                        selected = true,
                        onClick = {
                            viewModel.confirmPreview()
                            onNavigateBack()
                        },
                        label = { Text("Activate Automation") },
                        leadingIcon = { Icon(Icons.Rounded.Check, contentDescription = null) }
                    )
                }
                is CreateAutomationUiState.Error -> {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                }
                is CreateAutomationUiState.Created -> {
                    Text("Automation created and active!", style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = { viewModel.submitPrompt(inputText) },
                enabled = inputText.isNotBlank() && uiState !is CreateAutomationUiState.Loading
            ) {
                Icon(Icons.Rounded.Send, contentDescription = "Submit")
            }
        }
    }
}
