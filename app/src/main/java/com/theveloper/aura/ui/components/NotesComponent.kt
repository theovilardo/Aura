package com.theveloper.aura.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.theveloper.aura.domain.model.NotesConfig
import kotlinx.coroutines.delay

@Composable
fun NotesComponent(
    config: NotesConfig,
    onSave: (String) -> Unit
) {
    var text by remember(config.text) { mutableStateOf(config.text) }

    LaunchedEffect(text) {
        delay(500)
        if (text != config.text) {
            onSave(text)
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        if (config.isMarkdown) {
            Text(
                text = "Soporta markdown básico",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        OutlinedTextField(
            value = text,
            onValueChange = { newText -> text = newText },
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            label = { Text("Notas") },
            textStyle = MaterialTheme.typography.bodyMedium
        )
    }
}
