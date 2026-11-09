package com.fitzy.foldopti.ui.tv

import androidx.compose.runtime.Composable
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.fitzy.foldopti.ui.theme.Palette

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.DarkAccent, onPrimary = Palette.DarkOnAccent,
            background = Palette.DarkPage, onBackground = Palette.DarkText,
            surface = Palette.DarkCard, onSurface = Palette.DarkText,
            surfaceVariant = Palette.DarkRaised, onSurfaceVariant = Palette.DarkTextSecondary,
        ),
        content = content,
    )
}
