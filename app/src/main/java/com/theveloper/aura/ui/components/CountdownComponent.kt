package com.theveloper.aura.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.theveloper.aura.domain.model.CountdownConfig
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

@Composable
fun CountdownComponent(
    config: CountdownConfig
) {
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60000L) // Update every minute
            currentTime = System.currentTimeMillis()
        }
    }

    val diff = config.targetDate - currentTime
    val daysRemaining = TimeUnit.MILLISECONDS.toDays(diff).coerceAtLeast(0)
    val hoursRemaining = TimeUnit.MILLISECONDS.toHours(diff).coerceAtLeast(0)
    val color = if (daysRemaining in 0..7) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val supportingText = if (daysRemaining == 0L) "$hoursRemaining horas restantes" else "días restantes"

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        if (config.label.isNotEmpty()) {
            Text(
                text = config.label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = if (daysRemaining == 0L) hoursRemaining.toString() else daysRemaining.toString(),
                style = MaterialTheme.typography.displayMedium,
                color = color
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
    }
}
