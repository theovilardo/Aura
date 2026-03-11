package com.theveloper.aura.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.theveloper.aura.domain.model.TaskStatus
import com.theveloper.aura.ui.renderer.TaskRenderer
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    taskId: String,
    onNavigateBack: () -> Unit,
    viewModel: TaskDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collectLatest { event ->
            if (event is TaskDetailEvent.TaskDeleted) {
                onNavigateBack()
            }
        }
    }

    if (showDeleteConfirmation && uiState.task != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Eliminar tarea") },
            text = { Text("Se va a eliminar \"${uiState.task?.title.orEmpty()}\" de forma permanente.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        viewModel.onDeleteTask()
                    }
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalle de tarea") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                }
            )
        },
        bottomBar = {
            val task = uiState.task
            if (task != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (task.status == TaskStatus.ACTIVE) {
                        FilledTonalButton(
                            onClick = viewModel::onCompleteTask,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.TaskAlt,
                                contentDescription = null
                            )
                            Text(
                                text = " Completed"
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = viewModel::onReopenTask,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Reopen task")
                        }
                    }

                    OutlinedButton(
                        onClick = { showDeleteConfirmation = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = null
                        )
                        Text("Delete")
                    }
                }
            }
        }
    ) { paddingValues ->
        TaskRenderer(
            task = uiState.task,
            isLoading = uiState.isLoading,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            onSignal = viewModel::onSignal
        )
    }
}
