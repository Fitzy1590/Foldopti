package com.fitzy.foldopti.core.notifications

import com.fitzy.foldopti.core.optimizer.OptimizerEngine
import com.fitzy.foldopti.core.optimizer.describe
import com.fitzy.foldopti.core.settings.SettingsStore
import com.fitzy.foldopti.core.whitelist.WhitelistRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActionHandler @Inject constructor(
    private val engine: OptimizerEngine, private val whitelist: WhitelistRepository,
    private val settings: SettingsStore, private val alerts: AlertManager,
) {
    suspend fun handle(action: AlertAction, type: AlertType, pkg: String?) {
        val msg = when (action) {
            AlertAction.BOOST, AlertAction.COOL_DOWN -> {
                val r = if (action == AlertAction.BOOST) engine.safeBoost() else engine.coolDown()
                r.describe().joinToString(". ")
            }
            AlertAction.CLEAR_CACHE -> engine.cleanCaches().describe(::fmt).joinToString(". ")
            AlertAction.RESTRICT -> if (pkg != null && engine.restrict(pkg)) "Restricted $pkg" else "Could not restrict app (protected or Shizuku off)"
            AlertAction.WHITELIST -> { if (pkg != null) whitelist.add(pkg, "Added from alert"); "Protected ${pkg ?: "app"}" }
            AlertAction.SNOOZE -> { settings.snooze(if (pkg != null) "pkg:$pkg" else type.name, System.currentTimeMillis() + 2 * 3_600_000L); "Snoozed for 2 hours" }
            AlertAction.NEVER_ALERT -> { if (pkg != null) settings.neverAlert(pkg); "Won't alert for ${pkg ?: "this app"}" }
        }
        alerts.showResult(type, pkg, msg)
    }

    companion object {
        fun fmt(b: Long): String = when {
            b >= 1L shl 30 -> "%.1f GB".format(b / (1L shl 30).toDouble())
            b >= 1L shl 20 -> "%.0f MB".format(b / (1L shl 20).toDouble())
            else -> "%.0f KB".format(b / 1024.0)
        }
    }
}
