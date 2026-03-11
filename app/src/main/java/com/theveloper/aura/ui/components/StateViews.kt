package com.theveloper.aura.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun EmptyStateView(
    title: String,
    body: String,
    modifier: Modifier = Modifier
) {
    StateScaffold(
        title = title,
        body = body,
        modifier = modifier,
        icon = { Icon(Icons.Default.Inbox, contentDescription = null) }
    )
}

@Composable
fun ErrorStateView(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onRetry: (() -> Unit)? = null
) {
    StateScaffold(
        title = title,
        body = body,
        modifier = modifier,
        icon = { Icon(Icons.Default.ErrorOutline, contentDescription = null) },
        footer = {
            if (actionLabel != null && onRetry != null) {
                Button(onClick = onRetry) {
                    Text(actionLabel)
                }
            }
        }
    )
}

@Composable
private fun StateScaffold(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    footer: @Composable (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        icon()
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        footer?.invoke()
    }
}
