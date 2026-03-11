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

private val LightAuraFloatingBarColors = AuraFloatingBarColors(
    container = Color(0xFF2D2B2D),
    outline = Color(0xFF232224),
    activeOutline = Color(0xFF4A494C),
    mutedCircle = Color(0xB08E8E8F),
    selectedCircle = Color(0xFFF0EFF0),
    accentCircle = Color(0xFFE67938),
    promptText = Color(0xFFF3F3F3),
    placeholder = Color(0xFFC9C7C8),
    mutedIcon = Color(0xFFE6F0F7),
    selectedIcon = Color(0xFF232224),
    accentIcon = Color(0xFF6F2E0F),
    assistantIcon = Color(0xFFCAC8CB)
)

private val DarkAuraFloatingBarColors = AuraFloatingBarColors(
    container = Color(0xFF242224),
    outline = Color(0xFF181719),
    activeOutline = Color(0xFF5A565A),
    mutedCircle = Color(0xB07F7E82),
    selectedCircle = Color(0xFFEFEAEC),
    accentCircle = Color(0xFFE08A52),
    promptText = Color(0xFFF4F0F1),
    placeholder = Color(0xFFC6C0C2),
    mutedIcon = Color(0xFFE4ECF5),
    selectedIcon = Color(0xFF201D1F),
    accentIcon = Color(0xFF5E250A),
    assistantIcon = Color(0xFFD6CFD3)
)

@Composable
@ReadOnlyComposable
fun auraFloatingBarColors(): AuraFloatingBarColors {
    return if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        DarkAuraFloatingBarColors
    } else {
        LightAuraFloatingBarColors
    }
}
