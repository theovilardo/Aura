package com.theveloper.aura.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.theveloper.aura.engine.dsl.TaskDSLOutput

@Composable
fun TaskPreviewBottomSheet(
    preview: TaskDSLOutput,
    isSaving: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Vista previa",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = previewNarrative(preview),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        preview.components.forEach { component ->
            Text(
                text = "- ${component.type.name.replace('_', ' ')}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (preview.reminders.isNotEmpty()) {
            Text(
                text = "Incluye ${preview.reminders.size} reminder(s).",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onCancel,
                enabled = !isSaving
            ) {
                Text("Cancelar")
            }
            Button(
                onClick = onConfirm,
                enabled = !isSaving
            ) {
                Text(if (isSaving) "Guardando..." else "Confirmar")
            }
        }
    }
}

private fun previewNarrative(preview: TaskDSLOutput): String {
    val components = preview.components.joinToString { it.type.name.replace('_', ' ').lowercase() }
    val dueDate = preview.targetDateMs?.let { " con fecha objetivo definida" }.orEmpty()
    return "Vamos a crear una tarea ${preview.type.name.lowercase()} llamada \"${preview.title}\"$dueDate y estos componentes: $components."
}
