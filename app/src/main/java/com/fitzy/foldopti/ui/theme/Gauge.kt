package com.fitzy.foldopti.ui.theme

import android.os.PowerManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object Thermal {
    /** 0..1 position on the gauge. Prefers the OS headroom, falls back to the coarse status. */
    fun fraction(status: Int, headroom: Float?): Float = (headroom?.coerceIn(0f, 1f)) ?: when {
        status >= PowerManager.THERMAL_STATUS_CRITICAL -> 1f
        status == PowerManager.THERMAL_STATUS_SEVERE -> 0.85f
        status == PowerManager.THERMAL_STATUS_MODERATE -> 0.6f
        status == PowerManager.THERMAL_STATUS_LIGHT -> 0.35f
        else -> 0.12f
    }

    fun color(status: Int, accent: Color): Color = when {
        status >= PowerManager.THERMAL_STATUS_SEVERE -> Palette.Negative
        status >= PowerManager.THERMAL_STATUS_MODERATE -> Palette.Warm
        else -> accent
    }
}

/** The one rich element on the home screen: a 240 degree arc. Text is drawn by the caller on top. */
@Composable
fun ThermalGauge(status: Int, headroom: Float?, accent: Color, track: Color, size: Dp = 200.dp, stroke: Dp = 14.dp, modifier: Modifier = Modifier) {
    val f by animateFloatAsState(Thermal.fraction(status, headroom), tween(Motion.SheetMs, easing = Motion.Easing), label = "gauge")
    val color = Thermal.color(status, accent)
    Canvas(modifier.size(size)) {
        val s = stroke.toPx(); val d = this.size.minDimension - s
        val tl = Offset(s / 2, s / 2); val sz = Size(d, d)
        drawArc(track, 150f, 240f, false, tl, sz, style = Stroke(s, cap = StrokeCap.Round))
        drawArc(color, 150f, 240f * f.coerceAtLeast(0.02f), false, tl, sz, style = Stroke(s, cap = StrokeCap.Round))
    }
}

@Composable
fun StatusDot(color: Color, size: Dp = 8.dp) { Box(Modifier.size(size).background(color, CircleShape)) }
