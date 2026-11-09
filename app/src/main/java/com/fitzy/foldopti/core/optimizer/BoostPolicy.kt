package com.fitzy.foldopti.core.optimizer

data class Candidate(
    val pkg: String,
    val isProtected: Boolean,
    val isCoreRole: Boolean,
    val hasForegroundService: Boolean,
    val hasMediaSession: Boolean,
    val isSystemApp: Boolean,
    val isForegroundApp: Boolean,
    val actedAt: List<Long>,
)

data class Decision(val allow: Boolean, val reason: String)

/** Pure Safe Boost rules. Order matters: first exclusion wins. */
object BoostPolicy {
    const val COOLDOWN_MS = 6 * 60 * 60 * 1000L
    const val LOOP_WINDOW_MS = 24 * 60 * 60 * 1000L
    const val LOOP_LIMIT = 3

    fun decide(c: Candidate, now: Long): Decision = when {
        c.isProtected -> Decision(false, "Protected")
        c.isCoreRole -> Decision(false, "Launcher, keyboard, dialer or SMS")
        c.hasForegroundService -> Decision(false, "Foreground service running")
        c.hasMediaSession -> Decision(false, "Media session active")
        c.isSystemApp -> Decision(false, "System app")
        c.isForegroundApp -> Decision(false, "In use")
        c.actedAt.any { now - it < COOLDOWN_MS } -> Decision(false, "Handled recently")
        c.actedAt.count { now - it < LOOP_WINDOW_MS } >= LOOP_LIMIT -> Decision(false, "Restart loop guard")
        else -> Decision(true, "Idle in background")
    }
}
