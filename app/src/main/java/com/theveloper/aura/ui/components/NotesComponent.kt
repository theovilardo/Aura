package com.theveloper.aura.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

    ComponentCard(
        title = "Notes",
        icon = Icons.AutoMirrored.Rounded.Notes,
        eyebrow = if (config.isMarkdown) "Markdown" else "Plain text",
        subtitle = "Keep context, blockers or links close to the task",
        trailing = {
            if (text.isNotBlank()) {
                ComponentPill("${text.length} chars")
            }
        }
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { newText -> text = newText },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp),
            label = { Text("Notes") },
            placeholder = { Text("Capture the details the engine should not lose.") },
            textStyle = MaterialTheme.typography.bodyMedium
        )
    }
}
