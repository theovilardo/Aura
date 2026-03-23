package com.theveloper.aura.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.theveloper.aura.engine.ecosystem.ConnectionState
import com.theveloper.aura.engine.provider.ProviderLocation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcosystemSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: EcosystemSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    state.pendingInvitation?.let { invitation ->
        PairingInvitationDialog(
            desktopName = invitation.desktopName,
            inProgress = state.pairingInProgress,
            onAccept = viewModel::acceptInvitation,
            onDismiss = viewModel::dismissInvitation
        )
    }

    if (state.awaitingPin && state.activeInvitation != null) {
        PairingPinDialog(
            desktopName = state.activeInvitation?.desktopName.orEmpty(),
            pin = state.pairingPin,
            inProgress = state.pairingInProgress,
            result = state.pairingResult,
            onPinChange = viewModel::updatePairingPin,
            onConfirm = viewModel::confirmPairing,
            onDismiss = viewModel::cancelActivePairing
        )
    }

    if (state.pairingResult != null && (!state.awaitingPin || state.pairingResult?.success == true)) {
        PairingStatusDialog(
            result = state.pairingResult,
            onDismiss = viewModel::clearPairingResult
        )
    }

    SettingsPageScaffold(
        title = "Ecosystem",
        onNavigateBack = onNavigateBack
    ) {
        item {
            SettingsLeadCard(
                eyebrow = "Multi-device",
                title = "Route tasks across phone, desktop and cloud.",
                body = "When ecosystem mode is active, Aura scores each request and picks the best provider from your priority tree."
            )
        }

        item {
            SettingsToggleCard(
                icon = Icons.Rounded.Lan,
                title = "Ecosystem mode",
                body = "Enable multi-device routing and desktop connectivity. When off, Aura uses the legacy execution path.",
                checked = state.ecosystemEnabled,
                onCheckedChange = viewModel::setEcosystemEnabled
            )
        }

        if (!state.ecosystemEnabled) return@SettingsPageScaffold

        // --- Devices section ---
        item { SettingsGroupHeader("Devices") }

        item {
            SettingsInfoCard(
                icon = Icons.Rounded.Lan,
                title = "Ready for desktop invitations",
                body = "When this screen is enabled and the app is open, Aura advertises this phone on your local network and waits for pair requests from the desktop app."
            )
        }

        if (state.connectedDevices.isEmpty() && state.pairedDevices.isEmpty()) {
            item {
                SettingsInfoCard(
                    icon = Icons.Rounded.LinkOff,
                    title = "No devices paired",
                    body = "Pair a desktop running the Aura daemon to unlock desktop-side models and file actions."
                )
            }
        }

        state.connectedDevices.values.forEach { device ->
            item(key = "device-${device.id}") {
                ConnectedDeviceCard(
                    name = device.name,
                    platform = device.platform.name,
                    modelCount = device.ollamaModels.size,
                    connectionState = device.connectionState,
                    onDisconnect = { viewModel.disconnectDevice(device.id) }
                )
            }
        }

        state.pairedDevices
            .filterNot { paired -> state.connectedDevices.containsKey(paired.id) }
            .forEach { device ->
                item(key = "paired-${device.id}") {
                    PairedDeviceCard(
                        name = device.name,
                        platform = device.platform,
                        lastSeenAt = device.lastSeenAt
                    )
                }
            }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SettingsIconBadge(icon = Icons.Rounded.Computer)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Start pairing from desktop",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Open Aura Desktop, find this phone and tap Pair. The invitation and PIN flow will appear here automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                        )
                    }
                }
            }
        }

        // --- Provider priority section ---
        item { SettingsGroupHeader("Provider priority") }

        item {
            SettingsInfoCard(
                icon = Icons.Rounded.DragHandle,
                title = "Fallback order",
                body = "Providers are tried top-to-bottom. Complexity thresholds filter which providers qualify for a given request."
            )
        }

        state.fallbackTree.forEachIndexed { index, node ->
            val provider = state.providers.find { it.providerId == node.providerId }
            item(key = "provider-${node.providerId}") {
                ProviderPriorityCard(
                    index = index + 1,
                    name = node.customLabel ?: provider?.displayName ?: node.providerId,
                    location = provider?.location,
                    maxComplexity = node.maxComplexity,
                    isEnabled = node.isEnabled,
                    onToggle = { viewModel.toggleFallbackNode(node.providerId, it) }
                )
            }
        }
    }
}

@Composable
private fun ConnectedDeviceCard(
    name: String,
    platform: String,
    modelCount: Int,
    connectionState: ConnectionState,
    onDisconnect: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsIconBadge(icon = Icons.Rounded.Computer)

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$platform  ·  $modelCount model${if (modelCount != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val label = when (connectionState) {
                ConnectionState.CONNECTED -> "Connected"
                ConnectionState.RECONNECTING -> "Reconnecting"
                ConnectionState.PAIRING -> "Pairing"
                ConnectionState.DISCONNECTED -> "Offline"
            }
            SettingsStatusChip(label)
        }
    }
}

@Composable
private fun PairedDeviceCard(
    name: String,
    platform: String,
    lastSeenAt: Long
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsIconBadge(icon = Icons.Rounded.Computer)

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$platform  ·  paired desktop",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Last seen: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(lastSeenAt))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SettingsStatusChip("Paired")
        }
    }
}

@Composable
private fun ProviderPriorityCard(
    index: Int,
    name: String,
    location: ProviderLocation?,
    maxComplexity: Float,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val locationIcon: ImageVector
    val locationLabel: String
    when (location) {
        ProviderLocation.LOCAL_PHONE -> {
            locationIcon = Icons.Rounded.PhoneAndroid
            locationLabel = "On-device"
        }
        ProviderLocation.REMOTE_DESKTOP -> {
            locationIcon = Icons.Rounded.Computer
            locationLabel = "Desktop"
        }
        ProviderLocation.CLOUD, null -> {
            locationIcon = Icons.Rounded.Cloud
            locationLabel = "Cloud"
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = if (isEnabled) MaterialTheme.colorScheme.surface
        else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isEnabled) 0.48f else 0.32f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$index",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(24.dp)
            )

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = locationIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isEnabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = locationLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (maxComplexity < 10f) {
                        Text(
                            text = "·  max ${maxComplexity.toInt()}/10",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PairingInvitationDialog(
    desktopName: String,
    inProgress: Boolean,
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    BasicAlertDialog(onDismissRequest = { if (!inProgress) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Desktop wants to pair",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = "Aura Desktop \"$desktopName\" found this phone on your local network and wants to start pairing.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (inProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(20.dp)
                                .padding(end = 0.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    TextButton(
                        onClick = onDismiss,
                        enabled = !inProgress
                    ) {
                        Text("Ignore")
                    }
                    TextButton(
                        onClick = onAccept,
                        enabled = !inProgress
                    ) {
                        Text("Accept")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PairingPinDialog(
    desktopName: String,
    pin: String,
    inProgress: Boolean,
    result: com.theveloper.aura.engine.ecosystem.PairingResult?,
    onPinChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    BasicAlertDialog(onDismissRequest = { if (!inProgress) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Confirm pairing PIN",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = "Enter the 6-digit PIN currently shown by \"$desktopName\" in Aura Desktop.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                TextField(
                    value = pin,
                    onValueChange = onPinChange,
                    placeholder = { Text("482917") },
                    singleLine = true,
                    enabled = !inProgress,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent
                    )
                )

                if (result != null && !result.success && result.error != null) {
                    Text(
                        text = result.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (inProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(20.dp)
                                .padding(end = 0.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    TextButton(
                        onClick = onDismiss,
                        enabled = !inProgress
                    ) {
                        Text("Cancel")
                    }
                    TextButton(
                        onClick = onConfirm,
                        enabled = pin.length == 6 && !inProgress
                    ) {
                        Text("Confirm")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PairingStatusDialog(
    result: com.theveloper.aura.engine.ecosystem.PairingResult?,
    onDismiss: () -> Unit
) {
    val pairingResult = result ?: return

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (pairingResult.success) "Desktop paired" else "Pairing error",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = if (pairingResult.success) {
                        "${pairingResult.deviceName ?: "Aura Desktop"} is now trusted and ready for desktop actions."
                    } else {
                        pairingResult.error ?: "Aura couldn't complete the desktop connection."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (pairingResult.success) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }
}
