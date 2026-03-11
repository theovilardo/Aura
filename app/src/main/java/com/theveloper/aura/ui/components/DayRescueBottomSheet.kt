package com.theveloper.aura.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.theveloper.aura.domain.model.Suggestion
import com.theveloper.aura.domain.model.Task

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayRescueBottomSheet(
    tasks: List<Task>,
    suggestions: List<Suggestion>,
    onApply: (List<Suggestion>) -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    if (suggestions.isEmpty()) return
    
    // Day rescue items mapped from suggestions
    var selectedSuggestions by remember { mutableStateOf(suggestions.toSet()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Day Rescue",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Parece que el día se complicó. Hemos reorganizado tus tareas según tus patrones y el tiempo restante.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                contentPadding = PaddingValues(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(suggestions, key = { it.id }) { suggestion ->
                    val isChecked = selectedSuggestions.contains(suggestion)
                    val task = tasks.find { it.id == suggestion.taskId }
                    
                    if (task != null) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable { 
                                if (isChecked) selectedSuggestions = selectedSuggestions - suggestion else selectedSuggestions = selectedSuggestions + suggestion 
                            },
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        selectedSuggestions = if (checked) selectedSuggestions + suggestion else selectedSuggestions - suggestion
                                    }
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = task.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = suggestion.reasoning,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancelar")
                }
                Button(
                    onClick = { onApply(selectedSuggestions.toList()) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Aplicar selección")
                }
            }
        }
    }
}
