package com.theveloper.aura.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.theveloper.aura.domain.model.ComponentType
import com.theveloper.aura.domain.model.ProgressBarConfig
import com.theveloper.aura.domain.model.Task
import com.theveloper.aura.domain.model.TaskType
import com.theveloper.aura.ui.components.EmptyStateView
import com.theveloper.aura.ui.components.ErrorStateView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    onNavigateToTaskDetail: (String) -> Unit,
    onNavigateToCreateTask: (TaskCreationMode) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tasks") }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onNavigateToCreateTask(TaskCreationMode.PROMPT) }) {
                Icon(Icons.Default.Add, contentDescription = "Crear tarea")
            }
        }
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.errorMessage != null && uiState.tasks.isEmpty() -> {
                ErrorStateView(
                    title = "No pudimos cargar tus tareas",
                    body = uiState.errorMessage.orEmpty(),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }
            uiState.tasks.isEmpty() -> {
                EmptyStateView(
                    title = "Todavía no hay tareas",
                    body = "Creá una por prompt o armala manualmente para verla acá.",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 104.dp, top = 8.dp)
                ) {
                    items(uiState.tasks, key = { it.id }) { task ->
                        TaskListCard(
                            task = task,
                            onClick = { onNavigateToTaskDetail(task.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskListCard(task: Task, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text(task.type.name) }
                )
                task.reminders.minByOrNull { it.scheduledAt }?.let { reminder ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = tasksRelativeReminder(reminder.scheduledAt),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            Text(
                text = task.title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                text = task.summaryLine(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "${task.components.size} componentes activos",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun Task.summaryLine(): String {
    return when (type) {
        TaskType.TRAVEL -> {
            val checklist = components.firstOrNull { it.type == ComponentType.CHECKLIST }
            val done = checklist?.checklistItems?.count { it.isCompleted } ?: 0
            val total = checklist?.checklistItems?.size ?: 0
            "Cuenta regresiva activa. Checklist $done/$total."
        }
        TaskType.HABIT -> "Hábito recurrente con reminder programado."
        TaskType.PROJECT -> {
            val progress = components.firstOrNull { it.type == ComponentType.PROGRESS_BAR }?.config as? ProgressBarConfig
            "Progreso ${(((progress?.manualProgress ?: 0f) * 100f).toInt())}%."
        }
        TaskType.HEALTH -> "Seguimiento de métricas con historial."
        TaskType.FINANCE -> "Feed externo listo para consulta."
        TaskType.GENERAL -> "Tarea general con notas y contexto."
    }
}

private fun tasksRelativeReminder(timestamp: Long): String {
    val diffMinutes = ((timestamp - System.currentTimeMillis()) / 60000L).coerceAtLeast(0L)
    return when {
        diffMinutes < 60L -> "en $diffMinutes min"
        diffMinutes < 1440L -> "en ${diffMinutes / 60L} h"
        else -> "en ${diffMinutes / 1440L} d"
    }
}
