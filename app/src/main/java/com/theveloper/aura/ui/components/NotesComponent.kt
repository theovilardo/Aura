package com.theveloper.aura.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theveloper.aura.domain.model.NotesConfig
import kotlinx.coroutines.delay

@Composable
fun NotesComponent(
    config: NotesConfig,
    onSave: (String) -> Unit,
    onOpenEditor: (() -> Unit)? = null,
    saveDelayMillis: Long = 500L
) {
    if (onOpenEditor != null) {
        NotesLauncherCard(
            config = config,
            onOpenEditor = onOpenEditor
        )
        return
    }

    var text by remember(config.text) { mutableStateOf(config.text) }

    LaunchedEffect(text) {
        delay(saveDelayMillis)
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

@Composable
private fun NotesLauncherCard(
    config: NotesConfig,
    onOpenEditor: () -> Unit
) {
    val previewText = remember(config.text) { notesPreviewText(config.text) }
    val lineCount = remember(config.text) { if (config.text.isBlank()) 0 else config.text.lines().size }

    ComponentCard(
        title = "Notes",
        icon = Icons.AutoMirrored.Rounded.Notes,
        modifier = Modifier.clickable(onClick = onOpenEditor),
        eyebrow = if (config.isMarkdown) "Markdown" else "Plain text",
        subtitle = "Tap to open the full-screen editor",
        trailing = {
            ComponentPill(if (config.text.isBlank()) "Empty" else "$lineCount lines")
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                )
            ) {
                Text(
                    text = previewText,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = if (config.isMarkdown) {
                    "Open a larger editor with markdown shortcuts and preview."
                } else {
                    "Open a larger editor to work on the note without leaving the task."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun notesPreviewText(text: String): String {
    if (text.isBlank()) {
        return "No notes yet. Tap to start writing."
    }

    return text.lineSequence()
        .map(String::trimEnd)
        .filter(String::isNotBlank)
        .take(6)
        .joinToString(separator = "\n")
}
