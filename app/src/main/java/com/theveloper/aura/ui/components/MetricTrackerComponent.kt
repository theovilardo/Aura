package com.theveloper.aura.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
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
    val latest = history.lastOrNull() ?: currentValue
    val chartColor = MaterialTheme.colorScheme.primary

    ComponentCard(
        title = config.label.ifBlank { "Metric tracker" },
        icon = Icons.AutoMirrored.Rounded.ShowChart,
        eyebrow = "Track input",
        subtitle = if (config.unit.isNotBlank()) "Unit ${config.unit}" else "Add values over time",
        trailing = {
            if (config.unit.isNotBlank()) {
                ComponentPill(config.unit)
            }
        }
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ComponentMiniStat(
                value = latest?.let { formatMetric(it) } ?: "--",
                label = "Latest",
                modifier = Modifier.weight(1f)
            )
            ComponentMiniStat(
                value = history.size.toString(),
                label = "Entries",
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = textValue,
                onValueChange = { textValue = it },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                label = { Text("Log value") },
                trailingIcon = {
                    if (config.unit.isNotBlank()) {
                        Text(
                            text = config.unit,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )

            Surface(
                modifier = Modifier
                    .clickable {
                        textValue.toFloatOrNull()?.let { parsed ->
                            history = (history + parsed).takeLast(30)
                            onSave(parsed)
                            textValue = ""
                        }
                    },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary
            ) {
                Text(
                    text = "Log",
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(108.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f))
        ) {
            if (history.size >= 2) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
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
                        color = chartColor,
                        style = Stroke(width = 3.dp.toPx())
                    )

                    history.forEachIndexed { index, value ->
                        val x = index * xStep
                        val normalized = (value - min) / range
                        val y = size.height - (normalized * size.height)
                        drawCircle(
                            color = chartColor,
                            radius = 4.dp.toPx(),
                            center = Offset(x, y)
                        )
                    }
                }
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "Add another entry to reveal the trend.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun formatMetric(value: Float): String {
    return if (value % 1f == 0f) {
        value.toInt().toString()
    } else {
        String.format("%.1f", value)
    }
}
