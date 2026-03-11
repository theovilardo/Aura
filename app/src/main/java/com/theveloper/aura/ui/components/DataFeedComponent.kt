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

import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun DataFeedComponent(
    config: DataFeedConfig,
    viewModel: DataFeedViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(config.fetcherConfigId) {
        if (config.fetcherConfigId.isNotBlank()) {
            viewModel.initialize(config.fetcherConfigId)
            viewModel.refreshFeed() // Autorefresh on first load per phase request
        }
    }
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
                    onClick = { viewModel.refreshFeed() },
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
            
            when (uiState.status) {
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
                        text = uiState.value ?: config.value ?: "Sin datos",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    FeedTimestamp(timestamp = uiState.lastUpdatedAt)
                }
                DataFeedStatus.ERROR -> {
                    Text(
                        text = uiState.errorMessage ?: config.errorMessage ?: "No se pudo cargar el feed",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    FeedTimestamp(timestamp = uiState.lastUpdatedAt)
                }
                DataFeedStatus.STALE -> {
                    Text(
                        text = uiState.lastValue ?: config.lastValue ?: "Sin cache disponible",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "Mostrando el último valor conocido",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    FeedTimestamp(timestamp = uiState.lastUpdatedAt)
                }
            }
        }
    }
}

@Composable
private fun FeedTimestamp(timestamp: Long?) {
    Text(
        text = timestamp?.let { "Actualizado ${relativeTime(it)}" } ?: "Sin actualización registrada",
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
