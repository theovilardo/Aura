package com.theveloper.aura.ui.skill

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.ui.graphics.vector.ImageVector
import com.theveloper.aura.domain.model.ComponentType
import com.theveloper.aura.engine.skill.UiSkillRegistry

fun ComponentType.uiSkillDisplayName(): String {
    return UiSkillRegistry.resolve(this)?.displayName ?: fallbackDisplayName()
}

fun ComponentType.uiSkillShortLabel(): String {
    return UiSkillRegistry.resolve(this)?.shortLabel ?: fallbackShortLabel()
}

fun ComponentType.uiSkillIcon(): ImageVector = when (this) {
    ComponentType.CHECKLIST -> Icons.Rounded.DoneAll
    ComponentType.PROGRESS_BAR -> Icons.Rounded.DonutLarge
    ComponentType.COUNTDOWN -> Icons.Rounded.Timer
    ComponentType.HABIT_RING -> Icons.Rounded.Autorenew
    ComponentType.NOTES -> Icons.AutoMirrored.Rounded.Notes
    ComponentType.METRIC_TRACKER -> Icons.AutoMirrored.Rounded.ShowChart
    ComponentType.DATA_FEED -> Icons.Rounded.Sync
}

private fun ComponentType.fallbackDisplayName(): String = when (this) {
    ComponentType.CHECKLIST -> "Checklist"
    ComponentType.PROGRESS_BAR -> "Progress"
    ComponentType.COUNTDOWN -> "Countdown"
    ComponentType.HABIT_RING -> "Habit ring"
    ComponentType.NOTES -> "Notes"
    ComponentType.METRIC_TRACKER -> "Metric tracker"
    ComponentType.DATA_FEED -> "Data feed"
}

private fun ComponentType.fallbackShortLabel(): String = when (this) {
    ComponentType.CHECKLIST -> "List"
    ComponentType.PROGRESS_BAR -> "Track"
    ComponentType.COUNTDOWN -> "Time"
    ComponentType.HABIT_RING -> "Habit"
    ComponentType.NOTES -> "Text"
    ComponentType.METRIC_TRACKER -> "Metric"
    ComponentType.DATA_FEED -> "Live"
}
