package com.theveloper.aura.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal enum class AuraGradientTopBarStyle {
    Linear,
    Extended
}

@Composable
internal fun AuraGradientTopBarContainer(
    modifier: Modifier = Modifier,
    style: AuraGradientTopBarStyle = AuraGradientTopBarStyle.Linear,
    bottomFadePadding: Dp = 0.dp,
    onHeightChanged: (Int) -> Unit = {},
    content: @Composable BoxScope.() -> Unit
) {
    val backgroundColor = MaterialTheme.colorScheme.background
    val topBarBrush = remember(backgroundColor, style) {
        when (style) {
            AuraGradientTopBarStyle.Linear -> Brush.verticalGradient(
                colorStops = arrayOf(
                    0.0f to backgroundColor,
                    0.10f to backgroundColor,
                    0.20f to backgroundColor.copy(alpha = 0.99f),
                    0.30f to backgroundColor.copy(alpha = 0.94f),
                    0.40f to backgroundColor.copy(alpha = 0.86f),
                    0.50f to backgroundColor.copy(alpha = 0.74f),
                    0.60f to backgroundColor.copy(alpha = 0.60f),
                    0.70f to backgroundColor.copy(alpha = 0.30f),
                    0.80f to backgroundColor.copy(alpha = 0.20f),
                    0.90f to backgroundColor.copy(alpha = 0.10f),
                    1.0f to Color.Transparent
                )
            )
            AuraGradientTopBarStyle.Extended -> Brush.verticalGradient(
                colorStops = arrayOf(
                    0.0f to backgroundColor,
                    0.56f to backgroundColor,
                    0.78f to backgroundColor.copy(alpha = 0.92f),
                    0.9f to backgroundColor.copy(alpha = 0.54f),
                    1f to Color.Transparent
                )
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(topBarBrush)
            .onSizeChanged { onHeightChanged(it.height) }
            .padding(bottom = bottomFadePadding),
        content = content
    )
}
