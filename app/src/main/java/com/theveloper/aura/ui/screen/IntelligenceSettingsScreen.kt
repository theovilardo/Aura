package com.theveloper.aura.ui.screen

import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.theveloper.aura.engine.llm.DownloadState
import com.theveloper.aura.engine.llm.LLMTier
import com.theveloper.aura.engine.llm.LocalModelSlot
import com.theveloper.aura.engine.llm.ModelSpec

@Composable
fun IntelligenceSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: IntelligenceSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    SettingsPageScaffold(
        title = "Intelligence",
        onNavigateBack = onNavigateBack
    ) {
        item {
            SettingsLeadCard(
                eyebrow = "Local runtime",
                title = "Descargá el modelo que tu dispositivo puede sostener.",
                body = "Aura detecta el tier disponible, resuelve el backend activo y te deja administrar los modelos locales desde esta pantalla."
            )
        }

        item {
            CurrentTierCard(uiState = uiState)
        }

        item { SettingsGroupHeader("Local models") }

        item {
            HuggingFaceAccessCard(
                uiState = uiState,
                onValueChange = viewModel::updateHuggingFaceTokenInput,
                onSave = viewModel::saveHuggingFaceToken,
                onClear = viewModel::clearHuggingFaceToken
            )
        }

        item {
            SettingsInfoCard(
                icon = Icons.Rounded.Memory,
                title = "Primary slot",
                body = "Choose the lighter model Aura should prefer for classification, task organization, and general writing."
            )
        }

        uiState.primaryModels.forEach { model ->
            item(key = "primary-${model.spec.id}") {
                ModelManagementCard(
                    icon = iconFor(model.spec),
                    title = model.spec.displayName,
                    description = model.spec.summary,
                    uiState = model,
                    optionalLabel = null,
                    selectionLabel = "Use as primary",
                    onSelect = { viewModel.selectPrimaryModel(model.spec) },
                    onDownloadWifi = { viewModel.downloadModel(model.spec, wifiOnly = true) },
                    onDownloadMobile = { viewModel.downloadModel(model.spec, wifiOnly = false) },
                    onDelete = { viewModel.deleteModel(model.spec) },
                    onCancel = { viewModel.cancelDownload(model.spec) }
                )
            }
        }

        item {
            SettingsInfoCard(
                icon = Icons.Rounded.Speed,
                title = "Advanced slot",
                body = "These models are better for richer completions, but they ask more from the phone in RAM, thermals, and storage."
            )
        }

        uiState.advancedModels.forEach { model ->
            item(key = "advanced-${model.spec.id}") {
                ModelManagementCard(
                    icon = iconFor(model.spec),
                    title = model.spec.displayName,
                    description = model.spec.summary,
                    uiState = model,
                    optionalLabel = "Optional",
                    selectionLabel = "Use as advanced",
                    onSelect = { viewModel.selectAdvancedModel(model.spec) },
                    onDownloadWifi = { viewModel.downloadModel(model.spec, wifiOnly = true) },
                    onDownloadMobile = { viewModel.downloadModel(model.spec, wifiOnly = false) },
                    onDelete = { viewModel.deleteModel(model.spec) },
                    onCancel = { viewModel.cancelDownload(model.spec) }
                )
            }
        }

        item { SettingsGroupHeader("Cloud") }

        item {
            SettingsInfoCard(
                icon = if (uiState.groqConfigured) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff,
                title = if (uiState.groqConfigured) "Groq available" else "Groq unavailable",
                body = if (uiState.groqConfigured) {
                    "Cloud fallback is available when ${uiState.executionModeLabel} mode allows it or when no local model is downloaded."
                } else {
                    "No Groq API key configured. Aura will rely on the downloaded local model or the rules-based engine."
                }
            )
        }
    }
}

@Composable
private fun CurrentTierCard(
    uiState: IntelligenceSettingsUiState
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SettingsIconBadge(icon = Icons.Rounded.AutoAwesome)
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Active backend",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = uiState.activeTier.displayName(),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                SettingsStatusChip(uiState.executionModeLabel)
            }

            Text(
                text = uiState.activeReason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (uiState.recommendedTier != uiState.activeTier || uiState.recommendedReason.isNotBlank()) {
                Text(
                    text = "Recommended for this device: ${uiState.recommendedTier.displayName()}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                if (uiState.recommendedReason.isNotBlank()) {
                    Text(
                        text = uiState.recommendedReason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelManagementCard(
    icon: ImageVector,
    title: String,
    description: String,
    uiState: IntelligenceModelUiState,
    optionalLabel: String?,
    selectionLabel: String,
    onSelect: () -> Unit,
    onDownloadWifi: () -> Unit,
    onDownloadMobile: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val estimatedSize = uiState.sizeOnDisk.takeIf { it > 0 } ?: uiState.spec.sizeBytes
    val sizeLabel = Formatter.formatShortFileSize(context, estimatedSize)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                SettingsIconBadge(icon = icon)

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        optionalLabel?.let { SettingsStatusChip(it) }
                        if (uiState.isSelected) {
                            SettingsStatusChip("Preferred")
                        }
                        if (uiState.isActive) {
                            SettingsStatusChip("Active")
                        }
                    }
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (uiState.isDownloaded) {
                            "Installed on device · $sizeLabel"
                        } else {
                            "Download size · $sizeLabel"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (!uiState.isSupported && !uiState.isDownloaded) {
                        Text(
                            text = if (uiState.spec.slot == LocalModelSlot.ADVANCED) {
                                "This device does not meet the recommended advanced local tier."
                            } else {
                                "This device does not meet the recommended lightweight local tier."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else if (uiState.spec.requiresAuthentication && !uiState.hasCredentials) {
                        Text(
                            text = "Accepted access plus a Hugging Face read token are required before downloading this model.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (!uiState.isSelected) {
                        TextButton(onClick = onSelect) {
                            Text(selectionLabel)
                        }
                    }
                }
            }

            when (val state = uiState.downloadState) {
                is DownloadState.Downloading -> {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        progress = { state.progress }
                    )
                    Text(
                        text = "Downloading ${Formatter.formatShortFileSize(context, state.bytesDownloaded)} / ${Formatter.formatShortFileSize(context, state.totalBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DownloadState.Processing -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = "Processing model files...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DownloadState.WaitingForWifi -> {
                    Text(
                        text = "Aura espera una red Wi-Fi para evitar una descarga pesada por datos móviles.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is DownloadState.Error -> {
                    Text(
                        text = state.reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                DownloadState.Idle,
                DownloadState.Complete -> Unit
            }

            ModelActionRow(
                uiState = uiState,
                onDownloadWifi = onDownloadWifi,
                onDownloadMobile = onDownloadMobile,
                onDelete = onDelete,
                onCancel = onCancel
            )
        }
    }
}

@Composable
private fun ModelActionRow(
    uiState: IntelligenceModelUiState,
    onDownloadWifi: () -> Unit,
    onDownloadMobile: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (uiState.downloadState) {
            is DownloadState.Downloading,
            DownloadState.Processing -> {
                TextButton(onClick = onCancel) {
                    Text("Cancel")
                }
            }

            DownloadState.WaitingForWifi -> {
                Button(onClick = onDownloadMobile) {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text("Use data")
                }
                TextButton(onClick = onCancel) {
                    Text("Cancel")
                }
            }

            is DownloadState.Error -> {
                Button(
                    onClick = onDownloadWifi,
                    enabled = (uiState.isSupported || uiState.isDownloaded) && uiState.hasCredentials
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text("Retry")
                }
                if (uiState.hasCredentials) {
                    TextButton(onClick = onDownloadMobile) {
                        Text("Use data")
                    }
                }
            }

            DownloadState.Idle,
            DownloadState.Complete -> {
                if (uiState.isDownloaded) {
                    OutlinedButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text("Delete")
                    }
                } else {
                    Button(
                        onClick = onDownloadWifi,
                        enabled = uiState.isSupported && uiState.hasCredentials
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text("Download")
                    }
                    if (uiState.isSupported && uiState.hasCredentials) {
                        TextButton(onClick = onDownloadMobile) {
                            Text("Use data")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HuggingFaceAccessCard(
    uiState: IntelligenceSettingsUiState,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SettingsIconBadge(icon = Icons.Rounded.Download)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Hugging Face access",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Gemma repos require accepted access and a read token. Qwen downloads can run directly from Hugging Face.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                SettingsStatusChip(if (uiState.huggingFaceTokenConfigured) "Configured" else "Missing")
            }

            Text(
                text = "1. Open the gated model repo on Hugging Face and accept access.\n2. Create a read token.\n3. Paste it here to enable gated downloads. Ungated repos can be downloaded without this token.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = uiState.huggingFaceTokenInput,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Hugging Face token") },
                placeholder = { Text("hf_...") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false
                )
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onSave,
                    enabled = uiState.huggingFaceTokenInput.isNotBlank()
                ) {
                    Text("Save token")
                }
                if (uiState.huggingFaceTokenConfigured) {
                    TextButton(onClick = onClear) {
                        Text("Clear")
                    }
                }
            }
        }
    }
}

private fun LLMTier.displayName(): String {
    return when (this) {
        LLMTier.GEMINI_NANO -> "Gemini Nano"
        LLMTier.GEMMA_3N_E2B -> "Gemma 3n E2B"
        LLMTier.GEMMA_3_1B -> "Gemma 3 1B"
        LLMTier.QWEN_2_5_1_5B -> "Qwen 2.5 1.5B"
        LLMTier.QWEN_3_0_6B -> "Qwen 3 0.6B"
        LLMTier.GROQ_API -> "Groq API"
        LLMTier.RULES_ONLY -> "Rules only"
    }
}

private fun iconFor(spec: ModelSpec): ImageVector {
    return when (spec.slot) {
        LocalModelSlot.PRIMARY -> Icons.Rounded.Memory
        LocalModelSlot.ADVANCED -> Icons.Rounded.Speed
    }
}
