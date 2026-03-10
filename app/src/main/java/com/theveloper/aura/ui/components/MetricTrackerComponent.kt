package com.theveloper.aura.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.theveloper.aura.domain.model.MetricTrackerConfig

@Composable
fun MetricTrackerComponent(
    config: MetricTrackerConfig,
    currentValue: Float? = null,
    onSave: (Float) -> Unit
) {
    var textValue by remember(currentValue) { mutableStateOf(currentValue?.toString() ?: "") }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        if (config.label.isNotEmpty()) {
            Text(
                text = config.label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = textValue,
                onValueChange = { textValue = it },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                trailingIcon = {
                    if (config.unit.isNotEmpty()) {
                        Text(config.unit, style = MaterialTheme.typography.labelSmall)
                    }
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                textValue.toFloatOrNull()?.let { onSave(it) }
            }) {
                Text("Log")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Surface(
            modifier = Modifier.fillMaxWidth().height(60.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.small
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("LineChart Placeholder (Vico o Canvas)", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
