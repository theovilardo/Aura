package com.theveloper.aura.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.theveloper.aura.domain.model.ChecklistConfig
import com.theveloper.aura.domain.model.ChecklistItem
import kotlin.math.roundToInt

@Composable
fun ChecklistComponent(
    config: ChecklistConfig,
    initialItems: List<ChecklistItem> = emptyList(),
    onItemToggle: (ChecklistItem, Boolean) -> Unit
) {
    var items by remember(initialItems) {
        mutableStateOf(initialItems.sortedBy(ChecklistItem::sortOrder))
    }

    val completedCount = items.count(ChecklistItem::isCompleted)
    val suggestedCount = items.count(ChecklistItem::isSuggested)
    val progress = if (items.isEmpty()) 0 else ((completedCount / items.size.toFloat()) * 100).roundToInt()

    ComponentCard(
        title = config.label.ifBlank { "Checklist" },
        icon = Icons.Rounded.DoneAll,
        eyebrow = when {
            items.isEmpty() -> "No items yet"
            suggestedCount > 0 -> "$completedCount of ${items.size} done · $suggestedCount suggested"
            else -> "$completedCount of ${items.size} done"
        },
        subtitle = if (config.allowAddItems) "Flexible task structure" else null,
        trailing = {
            if (items.isNotEmpty()) {
                ComponentPill("$progress%")
            }
        }
    ) {
        if (items.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Text(
                    text = "Todavia no hay items para esta checklist.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items.forEach { item ->
                    val alpha by animateFloatAsState(
                        targetValue = if (item.isCompleted) 0.72f else 1f,
                        label = "checklist_alpha"
                    )
                    val updatedTextDecoration = if (item.isCompleted) {
                        TextDecoration.LineThrough
                    } else {
                        TextDecoration.None
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(alpha)
                            .clickable {
                                val newValue = !item.isCompleted
                                val updated = item.copy(
                                    isCompleted = newValue,
                                    isSuggested = false
                                )
                                items = items.map { current ->
                                    if (current.id == item.id) updated else current
                                }
                                onItemToggle(updated, newValue)
                            },
                        shape = RoundedCornerShape(22.dp),
                        color = if (item.isCompleted) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        border = BorderStroke(
                            1.dp,
                            if (item.isCompleted) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = item.isCompleted,
                                onCheckedChange = { checked ->
                                    val updated = item.copy(
                                        isCompleted = checked,
                                        isSuggested = false
                                    )
                                    items = items.map { current ->
                                        if (current.id == item.id) updated else current
                                    }
                                    onItemToggle(updated, checked)
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = item.text,
                                modifier = Modifier.weight(1f),
                                textDecoration = updatedTextDecoration,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (item.isCompleted) {
                                ComponentPill("Done")
                            } else if (item.isSuggested) {
                                ComponentPill("Suggested")
                            }
                        }
                    }
                }
            }
        }
    }
}
