package com.theveloper.aura.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.theveloper.aura.domain.model.CountdownConfig
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

@Composable
fun CountdownComponent(
    config: CountdownConfig
) {
    if (config.targetDate <= 0L) {
        ComponentCard(
            title = config.label.ifBlank { "Countdown" },
            icon = Icons.Rounded.Timer,
            eyebrow = "Date pending",
            subtitle = "Add the target date when you are ready",
            trailing = {
                ComponentPill("Pending")
            }
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            ) {
                Text(
                    text = "Todavia no hay una fecha definida para este countdown.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            currentTime = System.currentTimeMillis()
        }
    }

    val diff = (config.targetDate - currentTime).coerceAtLeast(0L)
    val daysRemaining = TimeUnit.MILLISECONDS.toDays(diff)
    val hoursRemaining = TimeUnit.MILLISECONDS.toHours(diff)
    val isUrgent = daysRemaining in 0..7
    val headlineValue = if (daysRemaining == 0L) hoursRemaining.toString() else daysRemaining.toString()
    val headlineUnit = if (daysRemaining == 0L) "hours" else "days"
    val statusLabel = when {
        diff <= 0L -> "Now"
        isUrgent -> "Due soon"
        else -> "On track"
    }

    ComponentCard(
        title = config.label.ifBlank { "Countdown" },
        icon = Icons.Rounded.Timer,
        eyebrow = statusLabel,
        subtitle = "Target ${formatDate(config.targetDate)}",
        trailing = {
            ComponentPill(formatDateShort(config.targetDate))
        }
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                color = if (isUrgent) {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.78f)
                } else {
                    MaterialTheme.colorScheme.surface
                },
                border = BorderStroke(
                    1.dp,
                    if (isUrgent) {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    }
                )
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = headlineValue,
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isUrgent) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Text(
                        text = headlineUnit,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isUrgent) {
                            MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.75f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
            ComponentMiniStat(
                value = formatDateShort(config.targetDate),
                label = "Target date",
                modifier = Modifier.weight(1f)
            )
        }

        Text(
            text = when {
                diff <= 0L -> "The target time is here."
                daysRemaining == 0L -> "$hoursRemaining hours remaining."
                else -> "$daysRemaining days remaining."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatDate(timestamp: Long): String {
    val formatter = DateTimeFormatter.ofPattern("EEEE, d MMM")
    return formatter.format(
        Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
    )
}

private fun formatDateShort(timestamp: Long): String {
    val formatter = DateTimeFormatter.ofPattern("d MMM")
    return formatter.format(
        Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
    )
}
