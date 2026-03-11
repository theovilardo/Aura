package com.theveloper.aura.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.theveloper.aura.domain.model.ChecklistConfig
import com.theveloper.aura.domain.model.ChecklistItem

@Composable
fun ChecklistComponent(
    config: ChecklistConfig,
    initialItems: List<ChecklistItem> = emptyList(),
    onItemToggle: (ChecklistItem, Boolean) -> Unit
) {
    var items by remember(initialItems) { mutableStateOf(initialItems) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        if (config.label.isNotEmpty()) {
            Text(
                text = config.label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        if (items.isEmpty()) {
            Text(
                text = "Todavía no hay items para esta checklist.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items.forEach { item ->
            val alpha by animateFloatAsState(targetValue = if (item.isCompleted) 0.6f else 1f, label = "alpha")
            val textDecoration = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val newValue = !item.isCompleted
                        val updated = item.copy(isCompleted = newValue)
                        items = items.map { if (it.id == item.id) updated else it }
                        onItemToggle(updated, newValue)
                    }
                    .padding(vertical = 4.dp)
                    .alpha(alpha),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = item.isCompleted,
                    onCheckedChange = { checked ->
                        val updated = item.copy(isCompleted = checked)
                        items = items.map { if (it.id == item.id) updated else it }
                        onItemToggle(updated, checked) 
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = item.text,
                    textDecoration = textDecoration,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}
