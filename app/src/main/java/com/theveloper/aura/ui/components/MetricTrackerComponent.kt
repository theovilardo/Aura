package com.theveloper.aura.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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
    var history by remember(config.history) { mutableStateOf(config.history.takeLast(30)) }
    val lineColor = MaterialTheme.colorScheme.primary

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
                textValue.toFloatOrNull()?.let {
                    history = (history + it).takeLast(30)
                    onSave(it)
                    textValue = ""
                }
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
            if (history.size >= 2) {
                Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    val min = history.minOrNull() ?: 0f
                    val max = history.maxOrNull() ?: (min + 1f)
                    val range = (max - min).takeIf { it > 0f } ?: 1f
                    val xStep = size.width / history.lastIndex.coerceAtLeast(1)
                    val path = Path()

                    history.forEachIndexed { index, value ->
                        val x = index * xStep
                        val normalized = (value - min) / range
                        val y = size.height - (normalized * size.height)
                        if (index == 0) {
                            path.moveTo(x, y)
                        } else {
                            path.lineTo(x, y)
                        }
                    }

                    drawPath(
                        path = path,
                        color = lineColor,
                        style = Stroke(width = 3.dp.toPx())
                    )

                    history.forEachIndexed { index, value ->
                        val x = index * xStep
                        val normalized = (value - min) / range
                        val y = size.height - (normalized * size.height)
                        drawCircle(
                            color = lineColor,
                            radius = 4.dp.toPx(),
                            center = Offset(x, y)
                        )
                    }
                }
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Text("Agregá más registros para ver la curva", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
