package com.fitzy.foldopti.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object Channels {
    const val CRITICAL = "alerts_critical"; const val INFO = "alerts_info"; const val MONITOR = "monitor"
    const val UPDATES = "updates"; const val SCAN = "scan_results"

    fun create(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(listOf(
            NotificationChannel(CRITICAL, "Critical alerts", NotificationManager.IMPORTANCE_HIGH).apply { description = "Overheating and runaway apps" },
            NotificationChannel(INFO, "Device alerts", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Memory and junk cache" },
            NotificationChannel(MONITOR, "Live monitor", NotificationManager.IMPORTANCE_LOW).apply { description = "Shown while live monitoring runs" },
            NotificationChannel(UPDATES, "App updates", NotificationManager.IMPORTANCE_LOW),
            NotificationChannel(SCAN, "Scan results", NotificationManager.IMPORTANCE_DEFAULT),
        ))
    }
}

enum class AlertType(val channel: String, val notifId: Int, val critical: Boolean) {
    HIGH_RAM(Channels.INFO, 101, false), JUNK_CACHE(Channels.INFO, 102, false),
    OVERHEAT_INFO(Channels.INFO, 103, false), OVERHEAT_CRITICAL(Channels.CRITICAL, 104, true),
    RUNAWAY(Channels.CRITICAL, 105, true), BATTERY_DRAIN(Channels.INFO, 106, false),
    SCAN_RESULT(Channels.SCAN, 107, false), UPDATE(Channels.UPDATES, 108, false),
}

enum class AlertAction(val label: String, val wire: String) {
    BOOST("Boost", "BOOST"), CLEAR_CACHE("Clear cache", "CLEAR_CACHE"), COOL_DOWN("Cool down", "COOL_DOWN"),
    WHITELIST("Protect app", "WHITELIST"), SNOOZE("Snooze 2 h", "SNOOZE"), NEVER_ALERT("Never alert", "NEVER_ALERT"),
    RESTRICT("Restrict", "RESTRICT");
    companion object { fun fromWire(s: String?) = entries.firstOrNull { it.wire == s } }
}

data class AlertSpec(
    val type: AlertType, val title: String, val text: String,
    val pkg: String? = null, val actions: List<AlertAction> = emptyList(),
)

/** Pure rate limit used by AlertManager. */
object RateLimit {
    const val WINDOW_MS = 30 * 60 * 1000L
    fun allow(lastShown: Long?, now: Long, windowMs: Long = WINDOW_MS) = lastShown == null || now - lastShown >= windowMs
}
