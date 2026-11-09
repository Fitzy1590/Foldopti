package com.fitzy.foldopti.core.optimizer

import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import com.fitzy.foldopti.core.system.Access
import com.fitzy.foldopti.core.whitelist.CleanAction
import com.fitzy.foldopti.core.whitelist.ProtectedPackages
import com.fitzy.foldopti.core.whitelist.WhitelistGuard
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class AppCache(val pkg: String, val label: String, val cacheBytes: Long, val isProtected: Boolean)
data class CleanResult(val clearedBytes: Long, val cleared: Int, val skipped: Int, val failed: Int, val tier: Tier, val note: String?)
data class BoostResult(val restricted: List<String>, val skipped: Map<String, String>, val note: String?)

@Singleton
class OptimizerEngine @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val shell: ShizukuShell,
    private val caps: CapabilityManager,
    private val guard: WhitelistGuard,
    private val probe: ProcessProbe,
    private val stopLog: StopLog,
    private val access: Access,
    private val accessibility: AccessibilityBridge,
) {
    private val pm get() = ctx.packageManager

    /** Cache size per app via StorageStats. Needs usage access; returns empty without it. */
    suspend fun measureCache(): List<AppCache> = withContext(Dispatchers.IO) {
        if (!access.hasUsageAccess()) return@withContext emptyList()
        val ssm = ctx.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
        val user: UserHandle = Process.myUserHandle()
        pm.getInstalledApplications(0).mapNotNull { ai ->
            val bytes = runCatching { ssm.queryStatsForPackage(ai.storageUuid, ai.packageName, user).cacheBytes }.getOrNull()
                ?: return@mapNotNull null
            if (bytes <= 0) null else AppCache(ai.packageName, ai.loadLabel(pm).toString(), bytes, !guard.allows(ai.packageName, CleanAction.CLEAR_CACHE))
        }.sortedByDescending { it.cacheBytes }
    }

    /** Clears cache only (never user data). T2: `pm clear --cache-only` (API 31+). T1: accessibility, foreground only. */
    suspend fun cleanCaches(only: Collection<String>? = null): CleanResult {
        val tier = caps.current().tier
        val all = measureCache()
        val targets = all.filter { only == null || it.pkg in only }
        val allowed = targets.filter { !it.isProtected }
        val skipped = targets.size - allowed.size
        if (allowed.isEmpty()) return CleanResult(0, 0, skipped, 0, tier, if (access.hasUsageAccess()) "Nothing to clear" else "Usage access is needed to measure cache")
        var bytes = 0L; var ok = 0; var failed = 0; var note: String? = null
        when {
            tier == Tier.T2 && Build.VERSION.SDK_INT >= 31 -> for (a in allowed) {
                guard.assertAllowed(a.pkg, CleanAction.CLEAR_CACHE)
                val r = shell.exec("pm", "clear", "--cache-only", a.pkg)
                if (r.ok && r.out.contains("Success", true)) { ok++; bytes += a.cacheBytes } else failed++
            }
            tier == Tier.T2 -> note = "Silent cache clearing needs Android 12 or newer. Use the accessibility option or clear from system settings."
            tier == Tier.T1 -> {
                val done = accessibility.clearCaches(allowed.take(15).map { it.pkg })
                ok = done.size; bytes = allowed.filter { it.pkg in done }.sumOf { it.cacheBytes }; failed = allowed.take(15).size - ok
                if (allowed.size > 15) note = "Cleared the 15 largest. Run again for the rest."
            }
            else -> note = "Enable Shizuku or the accessibility service to clear caches."
        }
        return CleanResult(bytes, ok, skipped, failed, tier, note)
    }

    private fun roleApps(): Set<String> = buildSet {
        pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName?.let(::add)
        Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/')?.let(::add)
        (ctx.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager)?.defaultDialerPackage?.let(::add)
        runCatching { Telephony.Sms.getDefaultSmsPackage(ctx) }.getOrNull()?.let(::add)
    }

    /** Restricts idle background apps (standby bucket + background run). Does not force-stop, so nothing restarts in a loop. */
    suspend fun safeBoost(): BoostResult {
        if (caps.current().tier != Tier.T2) return BoostResult(emptyList(), emptyMap(), "Safe Boost needs Shizuku. Without it FoldOpti can only measure and alert.")
        val snap = probe.snapshot()
        val roles = roleApps(); val now = System.currentTimeMillis()
        val done = mutableListOf<String>(); val skipped = linkedMapOf<String, String>()
        for (pkg in snap.running) {
            val ai = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: continue
            val system = ai.flags and ApplicationInfo.FLAG_SYSTEM != 0 && ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0
            val d = BoostPolicy.decide(
                Candidate(pkg, !guard.allows(pkg, CleanAction.RESTRICT) || ProtectedPackages.isCore(pkg), pkg in roles,
                    pkg in snap.foregroundServices, pkg in snap.mediaSessions, system, pkg in snap.topApp, stopLog.history(pkg, now)), now)
            if (!d.allow) { skipped[pkg] = d.reason; continue }
            if (restrictInternal(pkg)) { done += pkg; stopLog.record(pkg, now) }
        }
        return BoostResult(done, skipped, null)
    }

    private suspend fun restrictInternal(pkg: String): Boolean {
        shell.exec("am", "set-standby-bucket", pkg, "restricted")
        return shell.exec("cmd", "appops", "set", pkg, "RUN_ANY_IN_BACKGROUND", "ignore").ok
    }

    suspend fun restrict(pkg: String): Boolean {
        if (!guard.allows(pkg, CleanAction.RESTRICT) || !shell.ready) return false
        val ok = restrictInternal(pkg); if (ok) stopLog.record(pkg); return ok
    }

    /** Last resort for runaway processes only (alert action). Honors whitelist and cooldown. */
    suspend fun forceStop(pkg: String): Boolean {
        if (!guard.allows(pkg, CleanAction.STOP) || !shell.ready) return false
        if (stopLog.history(pkg).any { System.currentTimeMillis() - it < BoostPolicy.COOLDOWN_MS }) return false
        val ok = shell.exec("am", "force-stop", pkg).ok; if (ok) stopLog.record(pkg); return ok
    }

    /** Cool down = Safe Boost now; scans pause while thermal status stays high (see ScanWorker). */
    suspend fun coolDown(): BoostResult = safeBoost()
}
