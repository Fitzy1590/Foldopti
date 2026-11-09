package com.fitzy.foldopti.core.scheduler

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.PowerManager
import com.fitzy.foldopti.core.notifications.ActionHandler
import com.fitzy.foldopti.core.notifications.AlertAction
import com.fitzy.foldopti.core.notifications.AlertManager
import com.fitzy.foldopti.core.notifications.AlertSpec
import com.fitzy.foldopti.core.notifications.AlertType
import com.fitzy.foldopti.core.optimizer.CpuUse
import com.fitzy.foldopti.core.optimizer.OptimizerEngine
import com.fitzy.foldopti.core.optimizer.ProcessProbe
import com.fitzy.foldopti.core.settings.SettingsStore
import com.fitzy.foldopti.core.system.Access
import com.fitzy.foldopti.core.whitelist.CleanAction
import com.fitzy.foldopti.core.whitelist.WhitelistGuard
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class ScanSummary(
    val at: Long = 0L, val ramAvailPct: Int = -1, val ramTotalMb: Int = 0, val cacheBytes: Long? = null,
    val thermal: Int = 0, val headroom: Float? = null, val topCpu: List<CpuUse> = emptyList(),
    val topUsagePkg: String? = null, val topUsageShare: Double = 0.0, val alertsPosted: Int = 0,
)

@Singleton
class ScanEngine @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val optimizer: OptimizerEngine, private val probe: ProcessProbe, private val thermal: ThermalMonitor,
    private val alerts: AlertManager, private val settings: SettingsStore, private val access: Access,
    private val guard: WhitelistGuard,
) {
    private val _last = MutableStateFlow(ScanSummary())
    val last: StateFlow<ScanSummary> = _last.asStateFlow()
    private val counters = ctx.getSharedPreferences("scan_counters", Context.MODE_PRIVATE)

    private fun bump(key: String, hit: Boolean): Int {
        val n = if (hit) counters.getInt(key, 0) + 1 else 0
        counters.edit().putInt(key, n).apply(); return n
    }

    suspend fun run(measureCache: Boolean = true): ScanSummary {
        val s = settings.current(); val now = System.currentTimeMillis()
        val mi = ActivityManager.MemoryInfo().also { (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it) }
        val ramPct = if (mi.totalMem > 0) (mi.availMem * 100 / mi.totalMem).toInt() else -1
        val status = thermal.current(); val headroom = thermal.headroom()
        var posted = 0

        if (bump("ram", ramPct in 0 until s.ramThresholdPct) >= 2 &&
            alerts.post(AlertSpec(AlertType.HIGH_RAM, "High memory use", "$ramPct% of ${mi.totalMem shr 20} MB is free", actions = listOf(AlertAction.BOOST, AlertAction.SNOOZE)))) posted++

        val cache = if (measureCache && access.hasUsageAccess()) optimizer.measureCache().filter { !it.isProtected }.sumOf { it.cacheBytes } else null
        if (cache != null && cache >= s.cacheThresholdMb * 1_048_576L &&
            alerts.post(AlertSpec(AlertType.JUNK_CACHE, "Junk cache found", "${ActionHandler.fmt(cache)} of cache in unprotected apps", actions = listOf(AlertAction.CLEAR_CACHE, AlertAction.SNOOZE)))) posted++

        if (status >= PowerManager.THERMAL_STATUS_MODERATE) {
            val critical = status >= PowerManager.THERMAL_STATUS_SEVERE
            val spec = AlertSpec(if (critical) AlertType.OVERHEAT_CRITICAL else AlertType.OVERHEAT_INFO,
                if (critical) "Device is overheating" else "Device is warm",
                "Thermal status: ${ThermalMonitor.label(status)}", actions = listOf(AlertAction.COOL_DOWN, AlertAction.SNOOZE))
            if (alerts.post(spec)) posted++
        }

        val cpu = probe.cpu().filter { it.percent >= 1.0 }.take(8)
        val hot = cpu.filter { it.percent >= 25.0 && guard.allows(it.pkg, CleanAction.STOP) }.map { it.pkg }.toSet()
        for (c in cpu) {
            val n = bump("cpu:${c.pkg}", c.pkg in hot)
            if (n >= 3 && alerts.post(AlertSpec(AlertType.RUNAWAY, "Runaway app", "${c.pkg} is using ${c.percent.toInt()}% CPU in the background",
                    c.pkg, listOf(AlertAction.RESTRICT, AlertAction.WHITELIST, AlertAction.NEVER_ALERT)))) posted++
        }

        var topPkg: String? = null; var topShare = 0.0
        if (access.hasUsageAccess()) {
            val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val totals = usm.queryAndAggregateUsageStats(now - 3 * 3_600_000L, now).mapValues { it.value.totalTimeInForeground }.filterValues { it > 0 }
            UsageShare.top(totals)?.let { (p, share) ->
                topPkg = p; topShare = share
                if (share >= 0.6 && totals.values.sum() >= 45 * 60_000L && guard.allows(p, CleanAction.RESTRICT) &&
                    alerts.post(AlertSpec(AlertType.BATTERY_DRAIN, "One app dominates usage", "$p took ${(share * 100).toInt()}% of screen time in the last 3 hours",
                        p, listOf(AlertAction.RESTRICT, AlertAction.WHITELIST)))) posted++
            }
        }

        settings.setLastScan(now)
        return ScanSummary(now, ramPct, (mi.totalMem shr 20).toInt(), cache, status, headroom, cpu, topPkg, topShare, posted).also { _last.value = it }
    }
}
