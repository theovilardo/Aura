package com.theveloper.aura.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.theveloper.aura.domain.model.DataFeedConfig
import com.theveloper.aura.domain.model.DataFeedStatus

@Composable
fun DataFeedComponent(
    config: DataFeedConfig,
    onRefresh: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = config.displayLabel.ifEmpty { "External Data" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            when (config.status) {
                DataFeedStatus.LOADING -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = "Consultando fuente externa...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                DataFeedStatus.DATA -> {
                    Text(
                        text = config.value ?: "Sin datos",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    FeedTimestamp(config = config)
                }
                DataFeedStatus.ERROR -> {
                    Text(
                        text = config.errorMessage ?: "No se pudo cargar el feed",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    FeedTimestamp(config = config)
                }
                DataFeedStatus.STALE -> {
                    Text(
                        text = config.lastValue ?: "Sin cache disponible",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "Mostrando el último valor conocido",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    FeedTimestamp(config = config)
                }
            }
        }
    }
}

@Composable
private fun FeedTimestamp(config: DataFeedConfig) {
    Text(
        text = config.lastUpdatedAt?.let { "Actualizado ${relativeTime(it)}" } ?: "Sin actualización registrada",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
    )
}

private fun relativeTime(timestamp: Long): String {
    val diffMinutes = ((System.currentTimeMillis() - timestamp) / 60000L).coerceAtLeast(0L)
    return when {
        diffMinutes < 1L -> "recién"
        diffMinutes < 60L -> "hace ${diffMinutes} min"
        diffMinutes < 1440L -> "hace ${diffMinutes / 60L} h"
        else -> "hace ${diffMinutes / 1440L} d"
    }
}
