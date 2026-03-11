package com.theveloper.aura.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

@Immutable
data class AuraFloatingBarColors(
    val container: Color,
    val outline: Color,
    val activeOutline: Color,
    val mutedCircle: Color,
    val selectedCircle: Color,
    val accentCircle: Color,
    val promptText: Color,
    val placeholder: Color,
    val mutedIcon: Color,
    val selectedIcon: Color,
    val accentIcon: Color,
    val assistantIcon: Color
)

@Composable
@ReadOnlyComposable
fun auraFloatingBarColors(): AuraFloatingBarColors {
    return if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        AuraFloatingBarColors(
            container = AuraDarkSurfaceContainerHigh,
            outline = AuraDarkOutlineVariant,
            activeOutline = AuraDarkPrimary.copy(alpha = 0.78f),
            mutedCircle = AuraDarkSurfaceBright,
            selectedCircle = AuraDarkInverseSurface,
            accentCircle = AuraDarkFloatingAccent,
            promptText = AuraDarkOnSurface,
            placeholder = AuraDarkOnSurfaceVariant,
            mutedIcon = AuraDarkOnSurfaceVariant.copy(alpha = 0.9f),
            selectedIcon = AuraDarkInverseOnSurface,
            accentIcon = AuraDarkOnFloatingAccent,
            assistantIcon = AuraDarkOnSurfaceVariant
        )
    } else {
        AuraFloatingBarColors(
            container = AuraLightSurfaceContainerHigh,
            outline = AuraLightOutline,
            activeOutline = AuraLightPrimary.copy(alpha = 0.72f),
            mutedCircle = AuraLightSurfaceDim,
            selectedCircle = AuraLightSurfaceBright,
            accentCircle = AuraLightFloatingAccent,
            promptText = AuraLightOnSurface,
            placeholder = AuraLightOnSurfaceVariant,
            mutedIcon = AuraLightOnSurfaceVariant.copy(alpha = 0.92f),
            selectedIcon = AuraLightOnSurface,
            accentIcon = AuraLightOnFloatingAccent,
            assistantIcon = AuraLightOnSurfaceVariant
        )
    }
}
