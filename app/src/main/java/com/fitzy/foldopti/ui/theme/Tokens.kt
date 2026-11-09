package com.fitzy.foldopti.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Design tokens adapted from heydamir/unslop (CC BY 4.0). Graphite ramp, one accent, 4dp grid. */
object Palette {
    val DarkPage = Color(0xFF141415)
    val DarkCard = Color(0xFF19191B)
    val DarkRaised = Color(0xFF232325)
    val DarkText = Color(0xFFEDEDED)
    val DarkTextSecondary = Color(0xFFA1A1A6)
    val DarkTextMuted = Color(0xFF85858B)
    val DarkAccent = Color(0xFF3CCFA8)
    val DarkOnAccent = Color(0xFF06231B)

    val LightPage = Color(0xFFFAFAFA)
    val LightCard = Color(0xFFFFFFFF)
    val LightRaised = Color(0xFFF4F4F5)
    val LightText = Color(0xFF171717)
    val LightTextSecondary = Color(0xFF525252)
    val LightTextMuted = Color(0xFF737373)
    val LightAccent = Color(0xFF0B7A63)
    val LightOnAccent = Color(0xFFFFFFFF)

    val Positive = Color(0xFF4CC38A)
    val Negative = Color(0xFFE5484D)
    val Warm = Color(0xFFE0A33A)
}

object Space {
    val x1 = 4.dp; val x2 = 8.dp; val x3 = 12.dp; val x4 = 16.dp
    val x5 = 20.dp; val x6 = 24.dp; val x8 = 32.dp; val x12 = 48.dp
}

object Radius {
    val control = 10.dp; val card = 16.dp; val panel = 20.dp
}

object Motion {
    val Easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    const val PressMs = 150
    const val SheetMs = 400
}
