package com.theveloper.aura.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.theveloper.aura.domain.model.DataFeedConfig
import com.theveloper.aura.domain.model.DataFeedStatus

@Composable
fun DataFeedComponent(
    config: DataFeedConfig,
    viewModel: DataFeedViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(config.fetcherConfigId) {
        if (config.fetcherConfigId.isNotBlank()) {
            viewModel.initialize(config.fetcherConfigId)
            viewModel.refreshFeed()
        }
    }

    ComponentCard(
        title = config.displayLabel.ifBlank { "Live data" },
        icon = Icons.Rounded.Sync,
        eyebrow = feedStatusLabel(uiState.status),
        subtitle = uiState.lastUpdatedAt?.let(::feedTimestampText) ?: "Waiting for the first sync",
        trailing = {
            Surface(
                modifier = Modifier.clip(CircleShape).clickable { viewModel.refreshFeed() },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Refresh feed",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
    ) {
        when (uiState.status) {
            DataFeedStatus.LOADING -> {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surface
                    )
                    Text(
                        text = "Fetching external source...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            DataFeedStatus.DATA -> {
                FeedValueBlock(
                    value = uiState.value ?: config.value ?: "No data",
                    supporting = uiState.lastUpdatedAt?.let(::feedTimestampText) ?: "Updated"
                )
            }

            DataFeedStatus.ERROR -> {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.28f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = uiState.errorMessage ?: config.errorMessage ?: "Could not load the feed",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = uiState.lastUpdatedAt?.let(::feedTimestampText) ?: "No cache available",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.72f)
                        )
                    }
                }
            }

            DataFeedStatus.STALE -> {
                FeedValueBlock(
                    value = uiState.lastValue ?: config.lastValue ?: "No cache available",
                    supporting = "Showing the last known value"
                )
            }
        }
    }
}

@Composable
private fun FeedValueBlock(
    value: String,
    supporting: String
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun feedStatusLabel(status: DataFeedStatus): String = when (status) {
    DataFeedStatus.LOADING -> "Refreshing"
    DataFeedStatus.DATA -> "Live"
    DataFeedStatus.ERROR -> "Error"
    DataFeedStatus.STALE -> "Cached"
}

private fun feedTimestampText(timestamp: Long): String {
    return "Updated ${relativeTime(timestamp)}"
}

private fun relativeTime(timestamp: Long): String {
    val diffMinutes = ((System.currentTimeMillis() - timestamp) / 60000L).coerceAtLeast(0L)
    return when {
        diffMinutes < 1L -> "just now"
        diffMinutes < 60L -> "${diffMinutes}m ago"
        diffMinutes < 1440L -> "${diffMinutes / 60L}h ago"
        else -> "${diffMinutes / 1440L}d ago"
    }
}
