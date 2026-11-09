package com.fitzy.foldopti.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Dark = darkColorScheme(
    primary = Palette.DarkAccent, onPrimary = Palette.DarkOnAccent,
    background = Palette.DarkPage, onBackground = Palette.DarkText,
    surface = Palette.DarkCard, onSurface = Palette.DarkText,
    surfaceVariant = Palette.DarkRaised, onSurfaceVariant = Palette.DarkTextSecondary,
    outline = Palette.DarkText.copy(alpha = 0.08f), error = Palette.Negative,
)
private val Light = lightColorScheme(
    primary = Palette.LightAccent, onPrimary = Palette.LightOnAccent,
    background = Palette.LightPage, onBackground = Palette.LightText,
    surface = Palette.LightCard, onSurface = Palette.LightText,
    surfaceVariant = Palette.LightRaised, onSurfaceVariant = Palette.LightTextSecondary,
    outline = Palette.LightText.copy(alpha = 0.08f), error = Palette.Negative,
)

// Two weights only (400, 500). Bundled Geist/IBM Plex fonts are a tracked open item; system sans for now.
private val Ui = FontFamily.SansSerif
val FoldOptiTypography = Typography(
    headlineMedium = TextStyle(fontFamily = Ui, fontWeight = FontWeight.Medium, fontSize = 28.sp, lineHeight = 32.sp),
    titleMedium = TextStyle(fontFamily = Ui, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Ui, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
)

@Composable
fun FoldOptiTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = FoldOptiTypography, content = content)
}
