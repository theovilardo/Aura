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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Alarm
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
fun CreateReminderScreen(
    onNavigateBack: () -> Unit,
    viewModel: CreateReminderViewModel = hiltViewModel()
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
            Icon(Icons.Rounded.Alarm, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "New Reminder",
                style = MaterialTheme.typography.titleLarge
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // Prompt input
            Text(
                text = "Describe your reminder",
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
                            "e.g. Remind me to go to the gym every Monday at 8am",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                    innerTextField()
                }
            )

            Spacer(Modifier.height(24.dp))

            // Preview state
            when (val state = uiState) {
                is CreateReminderUiState.Idle -> {
                    // Empty state
                }
                is CreateReminderUiState.Loading -> {
                    Text("Analyzing your reminder...", style = MaterialTheme.typography.bodyMedium)
                }
                is CreateReminderUiState.Preview -> {
                    Text(
                        text = "Preview",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Title: ${state.dsl.title}", style = MaterialTheme.typography.bodyLarge)
                    Text("Type: ${state.dsl.reminderType}", style = MaterialTheme.typography.bodyMedium)
                    if (state.dsl.body.isNotBlank()) {
                        Text("Body: ${state.dsl.body}", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (state.dsl.checklistItems.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        state.dsl.checklistItems.forEach { item ->
                            Text("  - $item", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (state.warnings.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        state.warnings.forEach { warning ->
                            Text(warning, color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Row {
                        FilterChip(
                            selected = true,
                            onClick = {
                                viewModel.confirmPreview()
                                onNavigateBack()
                            },
                            label = { Text("Create Reminder") },
                            leadingIcon = { Icon(Icons.Rounded.Check, contentDescription = null) }
                        )
                    }
                }
                is CreateReminderUiState.Error -> {
                    Text(
                        state.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                is CreateReminderUiState.Created -> {
                    Text("Reminder created!", style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        // Bottom send bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = { viewModel.submitPrompt(inputText) },
                enabled = inputText.isNotBlank() && uiState !is CreateReminderUiState.Loading
            ) {
                Icon(Icons.Rounded.Send, contentDescription = "Submit")
            }
        }
    }
}
