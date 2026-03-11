package com.theveloper.aura.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = AuraLightPrimary,
    onPrimary = AuraLightOnPrimary,
    primaryContainer = AuraLightPrimaryContainer,
    onPrimaryContainer = AuraLightOnPrimaryContainer,
    inversePrimary = AuraLightInversePrimary,
    secondary = AuraLightSecondary,
    onSecondary = AuraLightOnSecondary,
    secondaryContainer = AuraLightSecondaryContainer,
    onSecondaryContainer = AuraLightOnSecondaryContainer,
    tertiary = AuraLightTertiary,
    onTertiary = AuraLightOnTertiary,
    tertiaryContainer = AuraLightTertiaryContainer,
    onTertiaryContainer = AuraLightOnTertiaryContainer,
    background = AuraLightBackground,
    onBackground = AuraLightOnBackground,
    surface = AuraLightSurface,
    onSurface = AuraLightOnSurface,
    surfaceVariant = AuraLightSurfaceVariant,
    onSurfaceVariant = AuraLightOnSurfaceVariant,
    surfaceTint = AuraLightPrimary,
    inverseSurface = AuraLightInverseSurface,
    inverseOnSurface = AuraLightInverseOnSurface,
    error = AuraLightError,
    onError = AuraLightOnError,
    errorContainer = AuraLightErrorContainer,
    onErrorContainer = AuraLightOnErrorContainer,
    outline = AuraLightOutline,
    outlineVariant = AuraLightOutlineVariant,
    scrim = AuraLightScrim,
    surfaceBright = AuraLightSurfaceBright,
    surfaceContainer = AuraLightSurfaceContainer,
    surfaceContainerHigh = AuraLightSurfaceContainerHigh,
    surfaceContainerHighest = AuraLightSurfaceContainerHighest,
    surfaceContainerLow = AuraLightSurfaceContainerLow,
    surfaceContainerLowest = AuraLightSurfaceContainerLowest,
    surfaceDim = AuraLightSurfaceDim
)

private val DarkColorScheme = darkColorScheme(
    primary = AuraDarkPrimary,
    onPrimary = AuraDarkOnPrimary,
    primaryContainer = AuraDarkPrimaryContainer,
    onPrimaryContainer = AuraDarkOnPrimaryContainer,
    inversePrimary = AuraDarkInversePrimary,
    secondary = AuraDarkSecondary,
    onSecondary = AuraDarkOnSecondary,
    secondaryContainer = AuraDarkSecondaryContainer,
    onSecondaryContainer = AuraDarkOnSecondaryContainer,
    tertiary = AuraDarkTertiary,
    onTertiary = AuraDarkOnTertiary,
    tertiaryContainer = AuraDarkTertiaryContainer,
    onTertiaryContainer = AuraDarkOnTertiaryContainer,
    background = AuraDarkBackground,
    onBackground = AuraDarkOnBackground,
    surface = AuraDarkSurface,
    onSurface = AuraDarkOnSurface,
    surfaceVariant = AuraDarkSurfaceVariant,
    onSurfaceVariant = AuraDarkOnSurfaceVariant,
    surfaceTint = AuraDarkPrimary,
    inverseSurface = AuraDarkInverseSurface,
    inverseOnSurface = AuraDarkInverseOnSurface,
    error = AuraDarkError,
    onError = AuraDarkOnError,
    errorContainer = AuraDarkErrorContainer,
    onErrorContainer = AuraDarkOnErrorContainer,
    outline = AuraDarkOutline,
    outlineVariant = AuraDarkOutlineVariant,
    scrim = AuraDarkScrim,
    surfaceBright = AuraDarkSurfaceBright,
    surfaceContainer = AuraDarkSurfaceContainer,
    surfaceContainerHigh = AuraDarkSurfaceContainerHigh,
    surfaceContainerHighest = AuraDarkSurfaceContainerHighest,
    surfaceContainerLow = AuraDarkSurfaceContainerLow,
    surfaceContainerLowest = AuraDarkSurfaceContainerLowest,
    surfaceDim = AuraDarkSurfaceDim
)

@Composable
fun AuraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    ApplyAuraWindowStyle(
        darkTheme = darkTheme,
        background = colorScheme.background
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
private fun ApplyAuraWindowStyle(
    darkTheme: Boolean,
    background: Color
) {
    val view = LocalView.current
    if (view.isInEditMode) return

    val activity = view.context.findActivity() ?: return

    SideEffect {
        val window = activity.window
        val insetsController = WindowCompat.getInsetsController(window, view)

        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        window.setBackgroundDrawable(ColorDrawable(background.toArgb()))

        insetsController.isAppearanceLightStatusBars = !darkTheme
        insetsController.isAppearanceLightNavigationBars = !darkTheme
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
