package com.theveloper.aura.ui.screen

import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.theveloper.aura.engine.llm.DownloadState
import com.theveloper.aura.engine.llm.LLMTier
import com.theveloper.aura.engine.llm.LocalModelSlot
import com.theveloper.aura.engine.llm.ModelCatalog
import com.theveloper.aura.engine.llm.ModelSpec

@Composable
fun IntelligenceSettingsScreen(
    onNavigateBack: () -> Unit,
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            IntelligenceTopBar(onNavigateBack = onNavigateBack)
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
                )
            ) {
                item {
                    SettingsLeadCard(
                        eyebrow = "Local runtime",
                        title = "Elegí el modelo local con una lectura más clara del costo real.",
                        body = "Aura detecta el tier del dispositivo, te deja elegir el modelo preferido por slot y muestra con más detalle qué conviene descargar."
                    )
                }

                item {
                    CurrentTierCard(uiState = uiState)
                }

                item { SettingsGroupHeader("Model slots") }

                selectedPrimaryModel?.let { model ->
                    item {
                        SlotSelectionCard(
                            title = "Primary slot",
                            body = "El modelo liviano que Aura intenta usar primero para clasificar, organizar y redactar tareas.",
                            selectedModel = model,
                            onChoose = { selectorSlot = LocalModelSlot.PRIMARY }
                        )
                    }
                }

                selectedAdvancedModel?.let { model ->
                    item {
                        SlotSelectionCard(
                            title = "Advanced slot",
                            body = "El modelo más capaz para prompts más ricos, con más presión sobre RAM, almacenamiento y temperatura.",
                            selectedModel = model,
                            onChoose = { selectorSlot = LocalModelSlot.ADVANCED }
                        )
                    }
                }

                item { SettingsGroupHeader("Model library") }

                item {
                    SettingsInfoCard(
                        icon = Icons.Rounded.AutoAwesome,
                        title = "Expand for details",
                        body = "Cada card resume el rol del modelo. Al expandirlo vas a ver fortalezas, tradeoffs, requisitos de acceso y controles de descarga."
                    )
                }

                ModelCatalog.all.forEach { spec ->
                    val model = (uiState.primaryModels + uiState.advancedModels)
                        .firstOrNull { it.spec.id == spec.id }
                    if (model != null) {
                        item(key = "model-${spec.id}") {
                            ModelLibraryCard(
                                uiState = model,
                                onDownloadWifi = { viewModel.downloadModel(spec, wifiOnly = true) },
                                onDownloadMobile = { viewModel.downloadModel(spec, wifiOnly = false) },
                                onDelete = { viewModel.deleteModel(spec) },
                                onCancel = { viewModel.cancelDownload(spec) }
                            )
                        }
                    }
                }

                item { SettingsGroupHeader("Access") }

                item {
                    HuggingFaceAccessCard(
                        uiState = uiState,
                        onValueChange = viewModel::updateHuggingFaceTokenInput,
                        onSave = viewModel::saveHuggingFaceToken,
                        onClear = viewModel::clearHuggingFaceToken
                    )
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

    selectorSlot?.let { slot ->
        val models = if (slot == LocalModelSlot.PRIMARY) {
            uiState.primaryModels
        } else {
            uiState.advancedModels
        }
        ModelSelectionSheet(
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
            }
        )
    }
}

@Composable
private fun IntelligenceTopBar(
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
                text = "Intelligence",
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
        shape = androidx.compose.foundation.shape.CircleShape,
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
            androidx.compose.material3.Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun SlotSelectionCard(
    title: String,
    body: String,
    selectedModel: IntelligenceModelUiState,
    onChoose: () -> Unit
) {
    val context = LocalContext.current
    val sizeLabel = Formatter.formatShortFileSize(
        context,
        selectedModel.sizeOnDisk.takeIf { it > 0 } ?: selectedModel.spec.sizeBytes
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                SettingsIconBadge(icon = iconFor(selectedModel.spec))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = selectedModel.spec.displayName,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SettingsStatusChip(if (selectedModel.isDownloaded) "Installed" else "Not downloaded")
                SettingsStatusChip(sizeLabel)
                if (selectedModel.isActive) {
                    SettingsStatusChip("Active")
                }
            }

            OutlinedButton(onClick = onChoose) {
                Text("Choose from list")
            }
        }
    }
}

@Composable
private fun ModelLibraryCard(
    uiState: IntelligenceModelUiState,
    onDownloadWifi: () -> Unit,
    onDownloadMobile: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val estimatedSize = uiState.sizeOnDisk.takeIf { it > 0 } ?: uiState.spec.sizeBytes
    val sizeLabel = Formatter.formatShortFileSize(context, estimatedSize)
    var expanded by rememberSaveable(uiState.spec.id) {
        mutableStateOf(uiState.isSelected || uiState.isActive)
    }

    LaunchedEffect(uiState.isSelected, uiState.isActive, uiState.downloadState) {
        if (
            uiState.isSelected ||
            uiState.isActive ||
            uiState.downloadState is DownloadState.Downloading ||
            uiState.downloadState is DownloadState.Error ||
            uiState.downloadState == DownloadState.Processing ||
            uiState.downloadState == DownloadState.WaitingForWifi
        ) {
            expanded = true
        }
    }

    val containerColor = if (uiState.isSelected || uiState.isActive) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.surface
    }
    val borderColor = if (uiState.isSelected || uiState.isActive) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(30.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                SettingsIconBadge(icon = iconFor(uiState.spec))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = uiState.spec.displayName,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        androidx.compose.material3.Icon(
                            imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SettingsStatusChip(slotLabel(uiState.spec.slot))
                        SettingsStatusChip(if (uiState.isDownloaded) "Installed" else "Ready to download")
                        if (uiState.isSelected) {
                            SettingsStatusChip("Preferred")
                        }
                        if (uiState.isActive) {
                            SettingsStatusChip("Active")
                        }
                        if (uiState.spec.requiresAuthentication) {
                            SettingsStatusChip("Gated repo")
                        }
                    }

                    Text(
                        text = if (uiState.isDownloaded) {
                            "Installed on device · $sizeLabel"
                        } else {
                            "Download size · $sizeLabel"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = uiState.spec.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

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
                            "Open Hugging Face repo. No token is needed for download."
                        }
                    )

                    if (!uiState.isSupported && !uiState.isDownloaded) {
                        Text(
                            text = if (uiState.spec.slot == LocalModelSlot.ADVANCED) {
                                "This device does not currently meet the recommended advanced tier for this model."
                            } else {
                                "This device does not currently meet the recommended lightweight local tier."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else if (uiState.spec.requiresAuthentication && !uiState.hasCredentials) {
                        Text(
                            text = "Add a Hugging Face token after accepting access to enable this download.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    ModelDownloadStateBlock(uiState = uiState)

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
                text = "Aura is waiting for Wi-Fi before starting this heavier download.",
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
                    androidx.compose.material3.Icon(
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
                    androidx.compose.material3.Icon(
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
                        androidx.compose.material3.Icon(
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
                        androidx.compose.material3.Icon(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSelectionSheet(
    slot: LocalModelSlot,
    models: List<IntelligenceModelUiState>,
    onDismissRequest: () -> Unit,
    onSelect: (ModelSpec) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = if (slot == LocalModelSlot.PRIMARY) "Choose primary model" else "Choose advanced model",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (slot == LocalModelSlot.PRIMARY) {
                    "Pick the lighter model Aura should prefer first."
                } else {
                    "Pick the heavier model Aura should prefer when a richer local response is worth the extra device cost."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            models.forEach { model ->
                ModelSelectionOption(
                    model = model,
                    onClick = { onSelect(model.spec) }
                )
            }

            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun ModelSelectionOption(
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
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
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
            verticalAlignment = Alignment.Top
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
                Text(
                    text = model.spec.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SettingsStatusChip(sizeLabel)
                    SettingsStatusChip(if (model.isDownloaded) "Installed" else "Download needed")
                    if (model.isActive) {
                        SettingsStatusChip("Active now")
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrentTierCard(
    uiState: IntelligenceSettingsUiState
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
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
private fun HuggingFaceAccessCard(
    uiState: IntelligenceSettingsUiState,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
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

private fun slotLabel(slot: LocalModelSlot): String {
    return when (slot) {
        LocalModelSlot.PRIMARY -> "Primary"
        LocalModelSlot.ADVANCED -> "Advanced"
    }
}
