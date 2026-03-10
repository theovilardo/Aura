package com.theveloper.aura.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.theveloper.aura.domain.model.NotesConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun NotesComponent(
    config: NotesConfig,
    onSave: (String) -> Unit
) {
    var text by remember(config.text) { mutableStateOf(config.text) }
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { newText ->
                text = newText
                coroutineScope.launch {
                    delay(500) // debounce
                    if (text == newText) {
                        onSave(text)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            label = { Text("Notas") },
            textStyle = MaterialTheme.typography.bodyMedium
        )
    }
}
