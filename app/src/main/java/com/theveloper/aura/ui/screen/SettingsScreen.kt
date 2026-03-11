package com.theveloper.aura.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.theveloper.aura.engine.sync.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val syncEnabledKey = booleanPreferencesKey("sync_enabled")
    
    var isSyncEnabled by remember { mutableStateOf(false) }
    var showSyncWarning by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isSyncEnabled = context.dataStore.data.map { it[syncEnabledKey] ?: false }.first()
    }

    if (showSyncWarning) {
        AlertDialog(
            onDismissRequest = { showSyncWarning = false },
            title = { Text("Activating Sync") },
            text = { 
                Text("Your data will be End-to-End encrypted (AES-256) inside your device before being uploaded to Supabase. This means we cannot read your data.") 
            },
            confirmButton = {
                Button(onClick = {
                    showSyncWarning = false
                    isSyncEnabled = true
                    coroutineScope.launch {
                        context.dataStore.edit { prefs -> prefs[syncEnabledKey] = true }
                    }
                }) {
                    Text("De acuerdo")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSyncWarning = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "Cloud",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sincronizar entre dispositivos",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = "End-To-End Encrypted via Supabase",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isSyncEnabled,
                    onCheckedChange = { checked ->
                        if (checked) {
                            showSyncWarning = true
                        } else {
                            isSyncEnabled = false
                            coroutineScope.launch {
                                context.dataStore.edit { prefs -> prefs[syncEnabledKey] = false }
                            }
                        }
                    }
                )
            }
        }
    }
}
