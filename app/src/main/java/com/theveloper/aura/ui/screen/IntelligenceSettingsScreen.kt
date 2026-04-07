package com.theveloper.aura.ui.screen

import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.theveloper.aura.engine.classifier.AiExecutionMode
import com.theveloper.aura.engine.llm.DownloadState
import com.theveloper.aura.engine.llm.LLMTier
import com.theveloper.aura.engine.llm.LocalModelSlot
import com.theveloper.aura.engine.llm.ModelSpec

@Composable
fun IntelligenceSettingsScreen(
    onNavigateBack: () -> Unit,
    onOpenApiSettings: () -> Unit,
    onOpenModelLibrary: () -> Unit,
    viewModel: IntelligenceSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectorSlot by rememberSaveable { mutableStateOf<LocalModelSlot?>(null) }
    val selectedPrimaryModel = remember(uiState.primaryModels) {
        uiState.primaryModels.firstOrNull { it.isSelected } ?: uiState.primaryModels.firstOrNull()
    }
    val selectedAdvancedModel = remember(uiState.advancedModels) {
        uiState.advancedModels.firstOrNull { it.isSelected } ?: uiState.advancedModels.firstOrNull()
    }

    IntelligencePageScaffold(
        title = "Intelligence",
        onNavigateBack = onNavigateBack
    ) {
        item {
            SettingsLeadCard(
                eyebrow = "Execution",
                title = "Choose how Aura balances local models, rules, and cloud help.",
                body = "Set the route order here, then use dedicated screens for API keys and the model library."
            )
        }

        item {
            RuntimeOverviewCard(uiState = uiState)
        }

        item { SettingsGroupHeader("Execution Mode") }

        items(AiExecutionMode.entries, key = { it.storageValue }) { mode ->
            ExecutionModeCard(
                mode = mode,
                selected = mode == uiState.executionMode,
                onClick = { viewModel.setExecutionMode(mode) }
            )
        }

        item { SettingsGroupHeader("Local Models") }

        selectedPrimaryModel?.let { model ->
            item {
                CurrentModelCard(
                    icon = Icons.Rounded.Memory,
                    label = "Current model for tasks",
                    modelName = model.spec.displayName,
                    status = if (model.isDownloaded) "Installed" else "Needs download",
                    supporting = "Used first for organization, task building, and general writing.",
                    onClick = { selectorSlot = LocalModelSlot.PRIMARY }
                )
            }
        }

        if (uiState.supportsAdvancedTier) {
            selectedAdvancedModel?.let { model ->
                item {
                    CurrentModelCard(
                        icon = Icons.Rounded.Speed,
                        label = "Current model for richer writing",
                        modelName = model.spec.displayName,
                        status = if (model.isDownloaded) "Installed" else "Needs download",
                        supporting = "Optional stronger local model for heavier prompts.",
                        onClick = { selectorSlot = LocalModelSlot.ADVANCED }
                    )
                }
            }
        }

        item {
            SettingsNavigationCard(
                icon = Icons.Rounded.Memory,
                title = "Model Library",
                summary = "Download models and compare them in one place.",
                status = "${(uiState.primaryModels + uiState.advancedModels).count { it.isDownloaded }} installed",
                onClick = onOpenModelLibrary
            )
        }

        item { SettingsGroupHeader("APIs") }

        item {
            SettingsNavigationCard(
                icon = if (uiState.groqConfigured || uiState.huggingFaceTokenConfigured) {
                    Icons.Rounded.CloudDone
                } else {
                    Icons.Rounded.CloudOff
                },
                title = "API Settings",
                summary = "Configure Hugging Face for gated downloads and Groq for cloud-first or fallback execution.",
                status = apiStatusLabel(uiState),
                onClick = onOpenApiSettings
            )
        }
    }

    selectorSlot?.let { slot ->
        val models = if (slot == LocalModelSlot.PRIMARY) uiState.primaryModels else uiState.advancedModels
        ModelPickerSheet(
            slot = slot,
            models = models,
            onDismissRequest = { selectorSlot = null },
            onSelect = { spec ->
                if (slot == LocalModelSlot.PRIMARY) {
                    viewModel.selectPrimaryModel(spec)
                } else {
                    viewModel.selectAdvancedModel(spec)
                }
                selectorSlot = null
            },
            onOpenModelLibrary = {
                selectorSlot = null
                onOpenModelLibrary()
            }
        )
    }
}

@Composable
fun IntelligenceApiSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: IntelligenceSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    IntelligencePageScaffold(
        title = "API Settings",
        onNavigateBack = onNavigateBack
    ) {
        item {
            SettingsLeadCard(
                eyebrow = "Credentials",
                title = "Only add external access when you want a capability local models cannot provide.",
                body = "Hugging Face unlocks gated model downloads. Groq enables cloud fallback and Cloud-first routing."
            )
        }

        item {
            ApiCredentialCard(
                icon = Icons.Rounded.Download,
                title = "Hugging Face",
                summary = "Needed for gated repos such as Gemma. Public Qwen models can still download without it.",
                status = if (uiState.huggingFaceTokenConfigured) "Saved" else "Missing",
                value = uiState.huggingFaceTokenInput,
                onValueChange = viewModel::updateHuggingFaceTokenInput,
                onSave = viewModel::saveHuggingFaceToken,
                onClear = viewModel::clearHuggingFaceToken,
                fieldLabel = "Read token",
                placeholder = "hf_...",
                helperText = "Accept access on the gated model page first, then paste a read token here."
            )
        }

        item {
            ApiCredentialCard(
                icon = if (uiState.groqConfigured) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff,
                title = "Groq",
                summary = "Used for cloud-first routing and cloud fallback when local execution is unavailable.",
                status = when {
                    uiState.groqTokenConfigured -> "Saved"
                    uiState.groqConfigured -> "Embedded"
                    else -> "Missing"
                },
                value = uiState.groqTokenInput,
                onValueChange = viewModel::updateGroqTokenInput,
                onSave = viewModel::saveGroqToken,
                onClear = viewModel::clearGroqToken,
                fieldLabel = "API key",
                placeholder = "gsk_...",
                helperText = if (uiState.groqConfigured && !uiState.groqTokenConfigured) {
                    "This build already has a Groq key available. Saving one here overrides it on this device."
                } else {
                    "Add a key if you want Aura to use Groq for cloud execution on this device."
                }
            )
        }

        item {
            SettingsInfoCard(
                icon = Icons.Rounded.AutoAwesome,
                title = "On-device storage",
                body = "These credentials stay on this device. Model downloads and cloud requests only use the key that belongs to the service they need."
            )
        }
    }
}

@Composable
fun IntelligenceModelLibraryScreen(
    onNavigateBack: () -> Unit,
    viewModel: IntelligenceSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedPrimaryModel = remember(uiState.primaryModels) {
        uiState.primaryModels.firstOrNull { it.isSelected } ?: uiState.primaryModels.firstOrNull()
    }
    val selectedAdvancedModel = remember(uiState.advancedModels) {
        uiState.advancedModels.firstOrNull { it.isSelected } ?: uiState.advancedModels.firstOrNull()
    }

    IntelligencePageScaffold(
        title = "Model Library",
        onNavigateBack = onNavigateBack
    ) {
        item {
            SettingsLeadCard(
                eyebrow = "On-device models",
                title = "Build a local toolkit that matches this phone and your workload.",
                body = "Browse every supported model, see the tradeoff, and assign each slot directly from the card that explains it."
            )
        }

        if (selectedPrimaryModel != null && selectedAdvancedModel != null) {
            item {
                ModelLibraryOverviewCard(
                    primaryModel = selectedPrimaryModel,
                    advancedModel = selectedAdvancedModel,
                    supportsAdvancedTier = uiState.supportsAdvancedTier
                )
            }
        }

        item { SettingsGroupHeader("Primary Models") }

        items(uiState.primaryModels, key = { it.spec.id }) { model ->
            ModelLibraryCard(
                slot = LocalModelSlot.PRIMARY,
                uiState = model,
                supportsAdvancedTier = uiState.supportsAdvancedTier,
                onSelect = { viewModel.selectPrimaryModel(model.spec) },
                onDownloadWifi = { viewModel.downloadModel(model.spec, wifiOnly = true) },
                onDownloadMobile = { viewModel.downloadModel(model.spec, wifiOnly = false) },
                onDelete = { viewModel.deleteModel(model.spec) },
                onCancel = { viewModel.cancelDownload(model.spec) }
            )
        }

        item { SettingsGroupHeader("Advanced Models") }

        items(uiState.advancedModels, key = { it.spec.id }) { model ->
            ModelLibraryCard(
                slot = LocalModelSlot.ADVANCED,
                uiState = model,
                supportsAdvancedTier = uiState.supportsAdvancedTier,
                onSelect = { viewModel.selectAdvancedModel(model.spec) },
                onDownloadWifi = { viewModel.downloadModel(model.spec, wifiOnly = true) },
                onDownloadMobile = { viewModel.downloadModel(model.spec, wifiOnly = false) },
                onDelete = { viewModel.deleteModel(model.spec) },
                onCancel = { viewModel.cancelDownload(model.spec) }
            )
        }
    }
}

@Composable
private fun IntelligencePageScaffold(
    title: String,
    onNavigateBack: () -> Unit,
    content: LazyListScope.() -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            IntelligenceTopBar(
                title = title,
                onNavigateBack = onNavigateBack
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    end = 16.dp,
                    bottom = innerPadding.calculateBottomPadding() + 40.dp
                ),
                content = content
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .height(78.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
            )
        }
    }
}

@Composable
private fun IntelligenceTopBar(
    title: String,
    onNavigateBack: () -> Unit
) {
    AuraGradientTopBarContainer(
        style = AuraGradientTopBarStyle.Linear,
        bottomFadePadding = 12.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            IntelligenceChromeIconButton(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                onClick = onNavigateBack,
                modifier = Modifier.align(Alignment.CenterStart)
            )

            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 30.sp,
                    lineHeight = 30.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
private fun IntelligenceChromeIconButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
        )
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun RuntimeOverviewCard(
    uiState: IntelligenceSettingsUiState
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Current Runtime",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                SettingsIconBadge(icon = Icons.Rounded.AutoAwesome)

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RuntimeMetaBlock(
                        label = "Active backend",
                        value = uiState.activeTier.displayName(),
                        trailingChip = uiState.executionMode.title
                    )
                    Text(
                        text = uiState.activeReason,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            RuntimeMetaBlock(
                label = "Recommended for this device",
                value = uiState.recommendedTier.displayName(),
                supporting = uiState.recommendedReason
            )
        }
    }
}

@Composable
private fun RuntimeMetaBlock(
    label: String,
    value: String,
    trailingChip: String? = null,
    supporting: String? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary
            )
            trailingChip?.let { SettingsStatusChip(it) }
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
        supporting?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ExecutionModeCard(
    mode: AiExecutionMode,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            1.dp,
            if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = mode.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = mode.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.84f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                text = mode.orderSummary,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun CurrentModelCard(
    icon: ImageVector,
    label: String,
    modelName: String,
    status: String,
    supporting: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            SettingsIconBadge(icon = icon)

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = modelName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                SettingsStatusChip(status)
            }

            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(22.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPickerSheet(
    slot: LocalModelSlot,
    models: List<IntelligenceModelUiState>,
    onDismissRequest: () -> Unit,
    onSelect: (ModelSpec) -> Unit,
    onOpenModelLibrary: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = if (slot == LocalModelSlot.PRIMARY) {
                    "Choose model for tasks"
                } else {
                    "Choose model for richer writing"
                },
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )

            models.forEach { model ->
                ModelPickerOption(
                    model = model,
                    onClick = { onSelect(model.spec) }
                )
            }

            TextButton(onClick = onOpenModelLibrary) {
                Text("Open model library")
            }

            Box(modifier = Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun ModelPickerOption(
    model: IntelligenceModelUiState,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val sizeLabel = Formatter.formatShortFileSize(
        context,
        model.sizeOnDisk.takeIf { it > 0 } ?: model.spec.sizeBytes
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = if (model.isSelected) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            1.dp,
            if (model.isSelected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = model.isSelected,
                onClick = onClick
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = model.spec.displayName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SettingsStatusChip(sizeLabel)
                    SettingsStatusChip(if (model.isDownloaded) "Installed" else "Download later")
                    if (model.spec.requiresAuthentication) {
                        SettingsStatusChip("Gated")
                    }
                }
            }
        }
    }
}

@Composable
private fun ApiCredentialCard(
    icon: ImageVector,
    title: String,
    summary: String,
    status: String,
    value: String,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    fieldLabel: String,
    placeholder: String,
    helperText: String
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
                SettingsIconBadge(icon = icon)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                SettingsStatusChip(status)
            }

            Text(
                text = helperText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(fieldLabel) },
                placeholder = { Text(placeholder) },
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
                    enabled = value.isNotBlank()
                ) {
                    Text("Save")
                }
                TextButton(onClick = onClear) {
                    Text("Clear")
                }
            }
        }
    }
}

@Composable
private fun ModelLibraryOverviewCard(
    primaryModel: IntelligenceModelUiState,
    advancedModel: IntelligenceModelUiState,
    supportsAdvancedTier: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Current assignment",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )

            ModelAssignmentSummary(label = "Primary", model = primaryModel.spec.displayName)
            ModelAssignmentSummary(label = "Advanced", model = advancedModel.spec.displayName)

            if (!supportsAdvancedTier) {
                Text(
                    text = "Advanced models are visible here, but this device is not currently flagged for the advanced local tier.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun ModelAssignmentSummary(
    label: String,
    model: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = model,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ModelLibraryCard(
    slot: LocalModelSlot,
    uiState: IntelligenceModelUiState,
    supportsAdvancedTier: Boolean,
    onSelect: () -> Unit,
    onDownloadWifi: () -> Unit,
    onDownloadMobile: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val sizeLabel = Formatter.formatShortFileSize(
        context,
        uiState.sizeOnDisk.takeIf { it > 0 } ?: uiState.spec.sizeBytes
    )
    var expanded by rememberSaveable(uiState.spec.id) {
        mutableStateOf(false)
    }

    LaunchedEffect(uiState.isSelected, uiState.downloadState, uiState.hasCredentials) {
        if (
            uiState.isSelected ||
            uiState.downloadState is DownloadState.Downloading ||
            uiState.downloadState is DownloadState.Error ||
            uiState.downloadState == DownloadState.Processing ||
            uiState.downloadState == DownloadState.WaitingForWifi ||
            (uiState.spec.requiresAuthentication && !uiState.hasCredentials)
        ) {
            expanded = true
        }
    }

    val accentBorder = when {
        uiState.isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        uiState.isActive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accentBorder)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                SettingsIconBadge(icon = iconFor(uiState.spec))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = uiState.spec.displayName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = uiState.spec.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null
                    )
                    Text(if (expanded) "Less" else "More")
                }
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SettingsStatusChip(sizeLabel)
                SettingsStatusChip(if (uiState.isDownloaded) "Installed" else "Download available")
                SettingsStatusChip(if (slot == LocalModelSlot.PRIMARY) "Primary slot" else "Advanced slot")
                if (uiState.isSelected) {
                    SettingsStatusChip(if (slot == LocalModelSlot.PRIMARY) "Current primary" else "Current advanced")
                }
                if (uiState.isActive) {
                    SettingsStatusChip("Active")
                }
                if (uiState.spec.requiresAuthentication) {
                    SettingsStatusChip("Gated")
                }
                if (!uiState.isRuntimeCompatible) {
                    SettingsStatusChip("Runtime update required")
                }
            }

            if (!uiState.isRuntimeCompatible) {
                OutlinedButton(onClick = {}, enabled = false) {
                    Text("Runtime update required")
                }
            } else if (uiState.isSelected) {
                SettingsStatusChip(if (slot == LocalModelSlot.PRIMARY) "Aura prefers this for the primary slot" else "Aura prefers this for the advanced slot")
            } else {
                OutlinedButton(onClick = onSelect) {
                    Text(if (slot == LocalModelSlot.PRIMARY) "Use for primary" else "Use for advanced")
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    ModelDetailRow(
                        label = "Best for",
                        value = uiState.spec.bestFor
                    )
                    ModelDetailRow(
                        label = "Tradeoff",
                        value = uiState.spec.caution
                    )
                    ModelDetailRow(
                        label = "Access",
                        value = if (uiState.spec.requiresAuthentication) {
                            "Requires accepted access on Hugging Face plus a read token."
                        } else {
                            "Open Hugging Face repo. No token is required for download."
                        }
                    )

                    when {
                        !uiState.isRuntimeCompatible -> {
                            Text(
                                text = uiState.spec.runtimeCompatibilityNote,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        slot == LocalModelSlot.ADVANCED && !supportsAdvancedTier -> {
                            Text(
                                text = "This device is not currently flagged for the advanced local tier, so Aura may stay on the primary path.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        !uiState.isSupported && !uiState.isDownloaded -> {
                            Text(
                                text = "This model is not currently recommended for the detected device tier.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    if (uiState.spec.requiresAuthentication && !uiState.hasCredentials) {
                        Text(
                            text = "Add a Hugging Face token in API Settings to enable this download.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    ModelDownloadStateBlock(uiState = uiState)

                    ModelDownloadActionRow(
                        uiState = uiState,
                        onDownloadWifi = onDownloadWifi,
                        onDownloadMobile = onDownloadMobile,
                        onDelete = onDelete,
                        onCancel = onCancel
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelDetailRow(
    label: String,
    value: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ModelDownloadStateBlock(
    uiState: IntelligenceModelUiState
) {
    val context = LocalContext.current
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
                text = "Aura is waiting for Wi-Fi before starting this download.",
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
}

@Composable
private fun ModelDownloadActionRow(
    uiState: IntelligenceModelUiState,
    onDownloadWifi: () -> Unit,
    onDownloadMobile: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (uiState.downloadState) {
            is DownloadState.Downloading,
            DownloadState.Processing -> {
                TextButton(onClick = onCancel) {
                    Text("Cancel download")
                }
            }

            DownloadState.WaitingForWifi -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = onDownloadMobile) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text("Use cellular")
                    }
                    TextButton(onClick = onCancel) {
                        Text("Cancel")
                    }
                }
            }

            is DownloadState.Error -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                            Text("Use cellular")
                        }
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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
                                Text("Use cellular")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun apiStatusLabel(uiState: IntelligenceSettingsUiState): String {
    val configuredCount = listOf(
        uiState.huggingFaceTokenConfigured,
        uiState.groqConfigured
    ).count { it }
    return when (configuredCount) {
        2 -> "Ready"
        1 -> "Partial"
        else -> "Missing"
    }
}

private fun LLMTier.displayName(): String {
    return when (this) {
        LLMTier.GEMINI_NANO -> "Gemini Nano"
        LLMTier.GEMMA_4_E4B -> "Gemma 4 E4B"
        LLMTier.GEMMA_4_E2B -> "Gemma 4 E2B"
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
