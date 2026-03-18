package com.theveloper.aura.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.theveloper.aura.R
import com.theveloper.aura.engine.classifier.AiExecutionMode

@Composable
fun SettingsScreen(
    onOpenIntelligenceSettings: () -> Unit,
    onOpenCloudSettings: () -> Unit,
    onOpenEcosystemSettings: () -> Unit,
    onOpenDeveloperSettings: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    SettingsPageScaffold(
        title = "Settings",
        bottomPaddingExtra = 216.dp
    ) {
        item {
            SettingsLeadCard(
                eyebrow = "Workspace controls",
                title = "Organize Aura by system, not by toggles.",
                body = "Each category opens as its own screen so AI, sync and developer tooling stay focused."
            )
        }

        item { SettingsGroupHeader("Core") }

        item {
            SettingsNavigationCard(
                icon = Icons.Rounded.AutoAwesome,
                title = "Intelligence",
                summary = "Choose execution mode, manage local models, and configure the API access Aura can use.",
                status = uiState.intelligenceStatus,
                onClick = onOpenIntelligenceSettings
            )
        }

        item {
            SettingsNavigationCard(
                icon = if (uiState.syncEnabled) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff,
                title = "Cloud",
                summary = "Control encrypted cross-device sync and the current replication state.",
                status = if (uiState.syncEnabled) "On" else "Off",
                onClick = onOpenCloudSettings
            )
        }

        item {
            SettingsNavigationCard(
                icon = Icons.Rounded.Lan,
                title = "Ecosystem",
                summary = "Connect desktop devices, configure provider priority and route tasks across your network.",
                status = if (uiState.ecosystemEnabled) "On" else "Off",
                onClick = onOpenEcosystemSettings
            )
        }

        item { SettingsGroupHeader("Developer") }

        item {
            SettingsNavigationCard(
                icon = Icons.Rounded.Build,
                title = "Developer",
                summary = "Preview datasets, UI diagnostics and internal tooling for local verification.",
                status = if (uiState.developerMockHabitDataEnabled) "Mock habits on" else "Tools",
                onClick = onOpenDeveloperSettings
            )
        }
    }
}

@Composable
fun AiSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    SettingsPageScaffold(
        title = "AI",
        onNavigateBack = onNavigateBack
    ) {
        item {
            SettingsLeadCard(
                eyebrow = "Execution",
                title = "Route prompts with a clear fallback order.",
                body = "Each mode changes how Aura moves between rules, local inference and Groq."
            )
        }

        item {
            AiStatusCard(
                mode = uiState.aiExecutionMode,
                groqConfigured = uiState.groqConfigured,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item { SettingsGroupHeader("Mode") }

        items(AiExecutionMode.entries, key = { it.storageValue }) { mode ->
            AiModeCard(
                mode = mode,
                selected = mode == uiState.aiExecutionMode,
                onClick = { viewModel.setAiExecutionMode(mode) }
            )
        }
    }
}

@Composable
fun CloudSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSyncWarning by rememberSaveable { mutableStateOf(false) }

    if (showSyncWarning) {
        AlertDialog(
            onDismissRequest = { showSyncWarning = false },
            title = { Text("Enable sync") },
            text = {
                Text("Aura encrypts your data on-device before upload, so synced data stays end-to-end encrypted with your local key material.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSyncWarning = false
                        viewModel.setSyncEnabled(true)
                    }
                ) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSyncWarning = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    SettingsPageScaffold(
        title = "Cloud",
        onNavigateBack = onNavigateBack
    ) {
        item {
            SettingsLeadCard(
                eyebrow = "Encrypted sync",
                title = "Keep state consistent across devices.",
                body = "When sync is enabled, Aura pushes encrypted changes to Supabase and restores remote updates on the next sync cycle."
            )
        }

        item {
            SettingsToggleCard(
                icon = if (uiState.syncEnabled) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff,
                title = "Sync between devices",
                body = "End-to-end encrypted via Supabase before any payload leaves this device.",
                checked = uiState.syncEnabled,
                onCheckedChange = { checked ->
                    if (checked) {
                        showSyncWarning = true
                    } else {
                        viewModel.setSyncEnabled(false)
                    }
                }
            )
        }

        item {
            SettingsInfoCard(
                icon = Icons.Rounded.Lock,
                title = "Security model",
                body = "Remote storage cannot read plaintext task data. Sync only mirrors encrypted payloads and metadata required for transport."
            )
        }
    }
}

@Composable
fun DeveloperSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    SettingsPageScaffold(
        title = "Developer",
        onNavigateBack = onNavigateBack
    ) {
        item {
            SettingsLeadCard(
                eyebrow = "Preview tools",
                title = "Inspect UI states without mutating your real habit data.",
                body = "These toggles are local to this device and meant for quick visual verification while building the app."
            )
        }

        item {
            SettingsToggleCard(
                icon = Icons.Rounded.BugReport,
                title = "Mock habit data",
                body = "Replaces the Habits tab dataset with seeded streaks and completion grids so charts and streak badges stay easy to test.",
                checked = uiState.developerMockHabitDataEnabled,
                onCheckedChange = viewModel::setDeveloperMockHabitDataEnabled
            )
        }

        item {
            SettingsInfoCard(
                icon = Icons.Rounded.Build,
                title = "Scope",
                body = "This override only affects the Habits screen presentation. Your real tasks and stored habit signals remain untouched."
            )
        }
    }
}

@Composable
internal fun SettingsPageScaffold(
    title: String,
    onNavigateBack: (() -> Unit)? = null,
    bottomPaddingExtra: Dp = 32.dp,
    content: LazyListScope.() -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SettingsTopBar(
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
                    top = innerPadding.calculateTopPadding() + 12.dp,
                    end = 16.dp,
                    bottom = innerPadding.calculateBottomPadding() + bottomPaddingExtra
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
private fun SettingsTopBar(
    title: String,
    onNavigateBack: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val titleStyle = rememberSettingsTitleStyle()

    AuraGradientTopBarContainer(
        modifier = modifier.fillMaxWidth(),
        bottomFadePadding = 20.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            if (onNavigateBack != null) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            Text(
                text = title,
                style = titleStyle,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun rememberSettingsTitleStyle(): TextStyle {
    return remember {
        TextStyle(
            fontFamily = FontFamily(
                Font(
                    resId = R.font.google_sans_flex_variable_local,
                    weight = FontWeight(720),
                    style = FontStyle.Normal,
                    loadingStrategy = FontLoadingStrategy.Blocking,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(670),
                        FontVariation.width(138f),
                        FontVariation.opticalSizing(30.sp),
                        FontVariation.grade(28),
                        FontVariation.Setting("ROND", 28f)
                    )
                )
            ),
            fontWeight = FontWeight(720),
            fontSize = 30.sp,
            lineHeight = 30.sp
        )
    }
}

@Composable
internal fun SettingsLeadCard(
    eyebrow: String,
    title: String,
    body: String
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = eyebrow.uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = title,
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
}

@Composable
internal fun SettingsGroupHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 2.dp, start = 4.dp)
    )
}

@Composable
internal fun SettingsNavigationCard(
    icon: ImageVector,
    title: String,
    summary: String,
    status: String,
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
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (status.isNotBlank()) {
                    SettingsStatusChip(status)
                }
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

@Composable
internal fun SettingsToggleCard(
    icon: ImageVector,
    title: String,
    body: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
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
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
internal fun SettingsInfoCard(
    icon: ImageVector,
    title: String,
    body: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            SettingsIconBadge(icon = icon)

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun SettingsIconBadge(icon: ImageVector) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .padding(10.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
internal fun SettingsStatusChip(label: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AiStatusCard(
    mode: AiExecutionMode,
    groqConfigured: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Memory,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = "Current order",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = mode.orderSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (groqConfigured) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff,
                    contentDescription = null,
                    tint = if (groqConfigured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                Text(
                    text = if (groqConfigured) {
                        "Groq configured and available as a cloud fallback."
                    } else {
                        "Groq missing. Aura will stay on local execution until you add groq.api.key."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AiModeCard(
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
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = mode.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = mode.summary,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                text = mode.orderSummary,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
            )
        }
    }
}
