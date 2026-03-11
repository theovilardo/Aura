package com.theveloper.aura.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.theveloper.aura.R

private val AuraLocalFontResId = R.font.google_sans_flex_variable_local

@OptIn(ExperimentalTextApi::class)
private fun auraLocalFont(
    weight: FontWeight,
    style: FontStyle = FontStyle.Normal
): Font {
    return Font(
        resId = AuraLocalFontResId,
        weight = weight,
        style = style,
        loadingStrategy = FontLoadingStrategy.Blocking,
        variationSettings = FontVariation.Settings(
            weight = weight,
            style = style
        )
    )
}

val AuraFontFamily = FontFamily(
    auraLocalFont(FontWeight.Normal),
    auraLocalFont(FontWeight.Medium),
    auraLocalFont(FontWeight.SemiBold),
    auraLocalFont(FontWeight.Bold)
)

private val BaseTypography = Typography()

private fun TextStyle.withAuraFont(
    fontWeight: FontWeight = this.fontWeight ?: FontWeight.Normal,
    fontSize: TextUnit = this.fontSize,
    lineHeight: TextUnit = this.lineHeight,
    letterSpacing: TextUnit = this.letterSpacing
): TextStyle = copy(
    fontFamily = AuraFontFamily,
    fontWeight = fontWeight,
    fontSize = fontSize,
    lineHeight = lineHeight,
    letterSpacing = letterSpacing
)

val Typography = Typography(
    displayLarge = BaseTypography.displayLarge.withAuraFont(),
    displayMedium = BaseTypography.displayMedium.withAuraFont(),
    displaySmall = BaseTypography.displaySmall.withAuraFont(),
    headlineLarge = BaseTypography.headlineLarge.withAuraFont(),
    headlineMedium = BaseTypography.headlineMedium.withAuraFont(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineSmall = BaseTypography.headlineSmall.withAuraFont(),
    titleLarge = BaseTypography.titleLarge.withAuraFont(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp
    ),
    titleMedium = BaseTypography.titleMedium.withAuraFont(
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    titleSmall = BaseTypography.titleSmall.withAuraFont(),
    bodyLarge = BaseTypography.bodyLarge.withAuraFont(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp
    ),
    bodyMedium = BaseTypography.bodyMedium.withAuraFont(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp
    ),
    bodySmall = BaseTypography.bodySmall.withAuraFont(),
    labelLarge = BaseTypography.labelLarge.withAuraFont(),
    labelMedium = BaseTypography.labelMedium.withAuraFont(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = BaseTypography.labelSmall.withAuraFont()
)
