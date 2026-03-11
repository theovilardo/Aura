package com.theveloper.aura.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.theveloper.aura.domain.model.ProgressBarConfig
import kotlin.math.roundToInt

@Composable
fun ProgressBarComponent(
    config: ProgressBarConfig,
    progress: Float? = null
) {
    val finalProgress = (progress ?: config.manualProgress ?: 0f).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = finalProgress,
        animationSpec = tween(durationMillis = 650),
        label = "progress"
    )
    val progressPercent = (animatedProgress * 100).roundToInt()
    val sourceLabel = when (config.source.uppercase()) {
        "SUBTASKS" -> "Subtasks"
        else -> "Manual"
    }

    ComponentCard(
        title = config.label.ifBlank { "Progress" },
        icon = Icons.Rounded.DonutLarge,
        eyebrow = sourceLabel,
        subtitle = if (progressPercent >= 100) "Ready to close" else "Keep moving the task forward",
        trailing = {
            ComponentPill("$progressPercent%")
        }
    ) {
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(CircleShape),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surface
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ComponentMiniStat(
                value = "$progressPercent%",
                label = "Completed",
                modifier = Modifier.weight(1f)
            )
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = sourceLabel,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Progress source",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
