package com.fitzy.foldopti.core.scheduler

import android.os.PowerManager

enum class ThermalEvent { NONE, INFO, CRITICAL, CLEARED }

/**
 * Escalates immediately, downgrades only after [holdMs] below the current level (2 min default).
 * Levels: 0 normal, 1 moderate, 2 severe or worse.
 */
class ThermalPolicy(private val holdMs: Long = 120_000L) {
    private var level = 0
    private var belowSince: Long? = null

    fun update(status: Int, now: Long): ThermalEvent {
        val target = when { status >= PowerManager.THERMAL_STATUS_SEVERE -> 2; status >= PowerManager.THERMAL_STATUS_MODERATE -> 1; else -> 0 }
        if (target > level) { level = target; belowSince = null; return if (target == 2) ThermalEvent.CRITICAL else ThermalEvent.INFO }
        if (target == level) { belowSince = null; return ThermalEvent.NONE }
        val since = belowSince ?: now.also { belowSince = it }
        if (now - since < holdMs) return ThermalEvent.NONE
        level = target; belowSince = null
        return if (target == 0) ThermalEvent.CLEARED else ThermalEvent.NONE
    }
}

object IntervalPolicy {
    /** On the cover screen and unplugged, scans run half as often. */
    fun effectiveMinutes(baseMin: Int, coverScreen: Boolean, charging: Boolean) = if (coverScreen && !charging) baseMin * 2 else baseMin
    const val HEAVY_MIN_BATTERY_PCT = 80
}

object UsageShare {
    /** Top app share of total foreground time. Input: package to foreground millis. */
    fun top(totals: Map<String, Long>): Pair<String, Double>? {
        val sum = totals.values.sum(); if (sum <= 0) return null
        val e = totals.maxByOrNull { it.value } ?: return null
        return e.key to e.value.toDouble() / sum
    }
}
