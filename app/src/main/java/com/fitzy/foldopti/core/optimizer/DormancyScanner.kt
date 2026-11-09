package com.fitzy.foldopti.core.optimizer

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import com.fitzy.foldopti.core.system.Access
import com.fitzy.foldopti.core.whitelist.CleanAction
import com.fitzy.foldopti.core.whitelist.WhitelistGuard
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

enum class DormantTier(val days: Int) { D60(60), D30(30), D14(14) }

data class DormantApp(
    val pkg: String, val label: String, val daysIdle: Int, val tier: DormantTier,
    val isSystem: Boolean, val isProtected: Boolean,
)

object Dormancy {
    fun tier(daysIdle: Int): DormantTier? = DormantTier.entries.firstOrNull { daysIdle >= it.days }
}

@Singleton
class DormancyScanner @Inject constructor(
    @ApplicationContext private val ctx: Context, private val access: Access,
    private val guard: WhitelistGuard, private val shell: ShizukuShell,
) {
    private val pm get() = ctx.packageManager

    suspend fun scan(): List<DormantApp> = withContext(Dispatchers.IO) {
        if (!access.hasUsageAccess()) return@withContext emptyList()
        val now = System.currentTimeMillis()
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val stats = usm.queryAndAggregateUsageStats(now - 365L * 86_400_000, now)
        val launchable = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo.packageName }.toSet() +
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0)
                .map { it.activityInfo.packageName }
        launchable.mapNotNull { pkg ->
            val info = runCatching { pm.getPackageInfo(pkg, 0) }.getOrNull() ?: return@mapNotNull null
            val last = maxOf(stats[pkg]?.lastTimeUsed ?: 0L, info.firstInstallTime)
            val days = ((now - last) / 86_400_000L).toInt()
            val tier = Dormancy.tier(days) ?: return@mapNotNull null
            val ai = info.applicationInfo ?: return@mapNotNull null
            DormantApp(pkg, ai.loadLabel(pm).toString(), days, tier, ai.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                !guard.allows(pkg, CleanAction.DISABLE))
        }.sortedByDescending { it.daysIdle }
    }

    /** Reversible. Needs Shizuku. */
    suspend fun disable(pkg: String): Boolean {
        if (!guard.allows(pkg, CleanAction.DISABLE) || !shell.ready) return false
        return shell.exec("pm", "disable-user", "--user", "0", pkg).ok
    }

    suspend fun enable(pkg: String): Boolean = shell.ready && shell.exec("pm", "enable", pkg).ok

    fun disabledApps(): List<Pair<String, String>> =
        pm.getInstalledApplications(0)
            .filter { !it.enabled }.map { it.packageName to it.loadLabel(pm).toString() }

    fun uninstallIntent(pkg: String): Intent =
        Intent(Intent.ACTION_DELETE, android.net.Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
