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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Event
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEventScreen(
    onNavigateBack: () -> Unit,
    viewModel: CreateEventViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var inputText by rememberSaveable { mutableStateOf("") }
    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
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
            Icon(Icons.Rounded.Event, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("New Event", style = MaterialTheme.typography.titleLarge)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Text(
                "Describe your event",
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
                            "e.g. Lollapalooza next weekend, 3 days, track my spending",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                    innerTextField()
                }
            )

            Spacer(Modifier.height(24.dp))

            when (val state = uiState) {
                is CreateEventUiState.Idle -> {}
                is CreateEventUiState.Loading -> {
                    Text("Building event plan...", style = MaterialTheme.typography.bodyMedium)
                }
                is CreateEventUiState.Preview -> {
                    Text("Preview", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("Title: ${state.dsl.title}", style = MaterialTheme.typography.bodyLarge)
                    if (state.dsl.description.isNotBlank()) {
                        Text("Description: ${state.dsl.description}", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (state.dsl.startAtMs > 0) {
                        Text("Start: ${dateFormat.format(Date(state.dsl.startAtMs))}",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    if (state.dsl.endAtMs > 0) {
                        Text("End: ${dateFormat.format(Date(state.dsl.endAtMs))}",
                            style = MaterialTheme.typography.bodyMedium)
                    }

                    if (state.dsl.subActions.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text("Sub-actions:", style = MaterialTheme.typography.titleSmall)
                        state.dsl.subActions.forEach { sa ->
                            Text(
                                "  [${sa.type}] ${sa.title.ifBlank { sa.prompt }}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }

                    if (state.dsl.components.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Components: ${state.dsl.components.joinToString { it.type.name }}",
                            style = MaterialTheme.typography.bodySmall)
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
                        label = { Text("Create Event") },
                        leadingIcon = { Icon(Icons.Rounded.Check, contentDescription = null) }
                    )
                }
                is CreateEventUiState.Error -> {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                }
                is CreateEventUiState.Created -> {
                    Text("Event created!", style = MaterialTheme.typography.titleMedium)
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
                enabled = inputText.isNotBlank() && uiState !is CreateEventUiState.Loading
            ) {
                Icon(Icons.Rounded.Send, contentDescription = "Submit")
            }
        }
    }
}
