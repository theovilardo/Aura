package com.theveloper.aura.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.theveloper.aura.domain.model.AuraAutomation
import com.theveloper.aura.domain.model.AuraEvent
import com.theveloper.aura.domain.model.AuraReminder
import com.theveloper.aura.domain.model.AutomationStatus
import com.theveloper.aura.domain.model.EventStatus
import com.theveloper.aura.domain.model.AutomationOutputType
import com.theveloper.aura.domain.model.ReminderType
import com.theveloper.aura.domain.model.ReminderStatus

@Composable
fun SystemPanelScreen(
    onNavigateBack: () -> Unit,
    viewModel: SystemPanelViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val automations = uiState.automations.sortedWith(
        compareByDescending<AuraAutomation> { it.status == AutomationStatus.ACTIVE }
            .thenBy { it.title.lowercase() }
    )
    val reminders = uiState.reminders.sortedBy { it.scheduledAt }
    val events = uiState.events.sortedBy { it.startAt }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CreationTopBar(
                title = "System",
                onNavigateBack = onNavigateBack
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = paddingValues.calculateTopPadding(),
                end = 16.dp,
                bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                CreationHeroCard(
                    icon = Icons.Rounded.Tune,
                    title = "System Panel",
                    description = "Inspect the live state of automations, reminders and events from a single, coherent overview.",
                    pills = listOf(
                        "${automations.count { it.status == AutomationStatus.ACTIVE }} active automations",
                        "${reminders.count { it.status == ReminderStatus.PENDING }} pending reminders",
                        "${events.size} events"
                    )
                )
            }

            if (automations.isEmpty() && reminders.isEmpty() && events.isEmpty()) {
                item {
                    CreationNoticeCard(
                        title = "Nothing is running yet",
                        body = "When you create automations, reminders or events, Aura will surface them here with their current status.",
                        icon = Icons.Rounded.Tune
                    )
                }
            }

            item {
                CreationSection(
                    title = "Automations",
                    body = "Toggle recurring flows and keep an eye on their schedule, failures and latest execution."
                ) {
                    if (automations.isEmpty()) {
                        CreationNoticeCard(
                            title = "No automations yet",
                            body = "Create an automation to monitor it from this panel.",
                            icon = Icons.Rounded.Bolt
                        )
                    } else {
                        automations.forEach { automation ->
                            AutomationPanelCard(
                                automation = automation,
                                onToggle = { enabled ->
                                    viewModel.toggleAutomation(automation.id, enabled)
                                }
                            )
                        }
                    }
                }
            }

            item {
                CreationSection(
                    title = "Reminders",
                    body = "Upcoming standalone reminders appear here with their timing and any attached context."
                ) {
                    if (reminders.isEmpty()) {
                        CreationNoticeCard(
                            title = "No upcoming reminders",
                            body = "Standalone reminders will show up here once you create them.",
                            icon = Icons.Rounded.Alarm
                        )
                    } else {
                        reminders.forEach { reminder ->
                            ReminderPanelCard(reminder = reminder)
                        }
                    }
                }
            }

            item {
                CreationSection(
                    title = "Events",
                    body = "Track the lifecycle of time-bound plans and the amount of automation attached to each one."
                ) {
                    if (events.isEmpty()) {
                        CreationNoticeCard(
                            title = "No events",
                            body = "Events and their sub-actions will appear here after creation.",
                            icon = Icons.Rounded.Event
                        )
                    } else {
                        events.forEach { event ->
                            EventPanelCard(event = event)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AutomationPanelCard(
    automation: AuraAutomation,
    onToggle: (Boolean) -> Unit
) {
    val isActive = automation.status == AutomationStatus.ACTIVE
    CreationCard(
        modifier = Modifier.fillMaxWidth(),
        highlighted = isActive
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = automation.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = automation.prompt,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Switch(
                checked = isActive,
                onCheckedChange = onToggle,
                modifier = Modifier.semantics {
                    stateDescription = if (isActive) "Active" else "Paused"
                }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CreationChip(
                text = automation.status.displayLabel(),
                icon = Icons.Rounded.Bolt,
                emphasized = isActive
            )
            CreationChip(
                text = automation.outputType.displayLabel(),
                icon = Icons.Rounded.Tune
            )
            CreationChip(text = "${automation.executionPlan.steps.size} steps")
        }

        CreationDetailRow(
            label = "Schedule",
            value = automation.cronExpression.ifBlank { "No cron expression saved" }
        )
        CreationDetailRow(
            label = "Last run",
            value = automation.lastExecutionAt?.let(::formatCreationDateTime) ?: "Never"
        )
        if (automation.failureCount > 0) {
            CreationDetailRow(
                label = "Failures",
                value = "${automation.failureCount}/${automation.maxRetries}"
            )
        }
    }
}

@Composable
private fun ReminderPanelCard(reminder: AuraReminder) {
    CreationCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = reminder.title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
        if (reminder.body.isNotBlank()) {
            Text(
                text = reminder.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CreationChip(
                text = reminder.status.displayLabel(),
                icon = Icons.Rounded.Alarm,
                emphasized = reminder.status == ReminderStatus.PENDING
            )
            CreationChip(text = reminder.reminderType.displayLabel(), icon = Icons.Rounded.Schedule)
            if (reminder.checklistItems.isNotEmpty()) {
                CreationChip(
                    text = "${reminder.checklistItems.size} checklist items",
                    icon = Icons.Rounded.Checklist
                )
            }
        }

        CreationDetailRow(label = "Scheduled", value = formatCreationDateTime(reminder.scheduledAt))
        if (reminder.repeatCount > 0) {
            CreationDetailRow(label = "Repeats", value = reminder.repeatCount.toString())
        }
        if (reminder.intervalMs > 0L) {
            CreationDetailRow(label = "Interval", value = formatCreationDuration(reminder.intervalMs))
        }
        if (reminder.links.isNotEmpty()) {
            CreationDetailRow(label = "Links", value = reminder.links.size.toString())
        }
    }
}

@Composable
private fun EventPanelCard(event: AuraEvent) {
    CreationCard(
        modifier = Modifier.fillMaxWidth(),
        highlighted = event.status == EventStatus.ACTIVE
    ) {
        Text(
            text = event.title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
        if (event.description.isNotBlank()) {
            Text(
                text = event.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CreationChip(
                text = event.status.displayLabel(),
                icon = Icons.Rounded.Event,
                emphasized = event.status == EventStatus.ACTIVE
            )
            CreationChip(text = "${event.subActions.size} sub-actions")
            if (event.components.isNotEmpty()) {
                CreationChip(text = "${event.components.size} components")
            }
        }

        CreationDetailRow(label = "Starts", value = formatCreationDateTime(event.startAt))
        CreationDetailRow(label = "Ends", value = formatCreationDateTime(event.endAt))
        if (event.endAt > event.startAt) {
            CreationDetailRow(
                label = "Duration",
                value = formatCreationDuration(event.endAt - event.startAt)
            )
        }
    }
}

private fun AutomationStatus.displayLabel(): String = when (this) {
    AutomationStatus.ACTIVE -> "Active"
    AutomationStatus.PAUSED -> "Paused"
    AutomationStatus.FAILED -> "Failed"
    AutomationStatus.COMPLETED -> "Completed"
}

private fun ReminderStatus.displayLabel(): String = when (this) {
    ReminderStatus.PENDING -> "Pending"
    ReminderStatus.TRIGGERED -> "Triggered"
    ReminderStatus.COMPLETED -> "Completed"
    ReminderStatus.CANCELLED -> "Cancelled"
}

private fun EventStatus.displayLabel(): String = when (this) {
    EventStatus.UPCOMING -> "Upcoming"
    EventStatus.ACTIVE -> "Active"
    EventStatus.COMPLETED -> "Completed"
}

private fun ReminderType.displayLabel(): String = when (this) {
    ReminderType.ONE_TIME -> "One time"
    ReminderType.REPEATING -> "Repeating"
    ReminderType.CYCLICAL -> "Cyclical"
}

private fun AutomationOutputType.displayLabel(): String = when (this) {
    AutomationOutputType.NOTIFICATION -> "Notification"
    AutomationOutputType.TASK_UPDATE -> "Task update"
    AutomationOutputType.SUMMARY -> "Summary"
    AutomationOutputType.CUSTOM -> "Custom"
}
