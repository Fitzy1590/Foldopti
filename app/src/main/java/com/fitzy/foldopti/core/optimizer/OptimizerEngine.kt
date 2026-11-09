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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class AppCache(val pkg: String, val label: String, val cacheBytes: Long, val isProtected: Boolean)
data class CleanResult(val clearedBytes: Long, val cleared: Int, val skipped: Int, val failed: Int, val tier: Tier, val note: String?, val firstError: String? = null)
data class Progress(val label: String, val done: Int, val total: Int, val detail: String = "")

/** Plain-language lines for the UI. */
fun CleanResult.describe(fmt: (Long) -> String): List<String> = buildList {
    note?.let { add(it) }
    if (cleared > 0 || (note == null && failed == 0)) add("Cleared ${fmt(clearedBytes)} from $cleared app${if (cleared == 1) "" else "s"}")
    if (failed > 0) add("$failed app${if (failed == 1) "" else "s"} could not be cleared" + (firstError?.let { ": $it" } ?: ""))
    if (skipped > 0) add("$skipped protected app${if (skipped == 1) "" else "s"} skipped")
}

fun BoostResult.describe(): List<String> = buildList {
    note?.let { add(it) }
    if (note == null) {
        add(if (restricted.isEmpty()) "No idle apps needed restricting" else "Restricted ${restricted.size} idle app${if (restricted.size == 1) "" else "s"}")
        if (skipped.isNotEmpty()) add("${skipped.size} left alone (in use, protected or recently handled)")
    }
}
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
    private val _progress = kotlinx.coroutines.flow.MutableStateFlow<Progress?>(null)
    /** Live progress of the running clean or boost, null when idle. */
    val progress: kotlinx.coroutines.flow.StateFlow<Progress?> = _progress


    /** Cache size per app via StorageStats. Needs usage access; returns empty without it. */
    suspend fun measureCache(): List<AppCache> = withContext(Dispatchers.IO) {
        if (!access.hasUsageAccess()) return@withContext emptyList()
        val ssm = ctx.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
        val user: UserHandle = Process.myUserHandle()
        pm.getInstalledApplications(0).mapNotNull { ai ->
            val bytes = runCatching { ssm.queryStatsForPackage(ai.storageUuid, ai.packageName, user).cacheBytes }.getOrNull()
                ?: return@mapNotNull null
            if (bytes <= 0) null else {
                val sys = ai.flags and ApplicationInfo.FLAG_SYSTEM != 0; val upd = ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
                val safe = SafetyPolicy.canClearCache(ai.packageName, sys, upd, hasLauncher(ai.packageName))
                AppCache(ai.packageName, ai.loadLabel(pm).toString(), bytes, !safe || !guard.allows(ai.packageName, CleanAction.CLEAR_CACHE))
            }
        }.sortedByDescending { it.cacheBytes }
    }

    /** Clears cache only (never user data). T2: `pm clear --cache-only` (API 31+), four at a time. T1: accessibility, foreground only. */
    suspend fun cleanCaches(only: Collection<String>? = null): CleanResult {
        val tier = caps.current().tier
        try {
            _progress.value = Progress("Measuring cache", 0, 0)
            val all = measureCache()
            val targets = all.filter { only == null || it.pkg in only }
            val allowed = targets.filter { !it.isProtected && it.cacheBytes >= MIN_CACHE_BYTES }.take(SafetyPolicy.MAX_CACHE_CLEARS_PER_RUN)
            val skipped = targets.count { it.isProtected }
            if (allowed.isEmpty()) return CleanResult(0, 0, skipped, 0, tier,
                if (!access.hasUsageAccess()) "Usage access is off, so cache size can't be measured. Turn it on in Settings." else "No cache worth clearing")
            when {
                tier == Tier.T2 && Build.VERSION.SDK_INT >= 31 -> {
                    val done = java.util.concurrent.atomic.AtomicInteger(0); val ok = java.util.concurrent.atomic.AtomicInteger(0)
                    val failedLogged = java.util.concurrent.atomic.AtomicInteger(0); val bytes = java.util.concurrent.atomic.AtomicLong(0); val firstErr = java.util.concurrent.atomic.AtomicReference<String?>(null)
                    _progress.value = Progress("Clearing cache", 0, allowed.size)
                    val sem = kotlinx.coroutines.sync.Semaphore(1)
                    kotlinx.coroutines.coroutineScope {
                        allowed.map { a -> async {
                            sem.withPermit {
                                _progress.value = Progress("Clearing cache", done.get(), allowed.size, a.label)
                                val r = if (guard.allows(a.pkg, CleanAction.CLEAR_CACHE)) shell.exec("pm", "clear", "--cache-only", a.pkg) else null
                                if (r != null && r.ok && r.out.contains("Success", true)) { ok.incrementAndGet(); bytes.addAndGet(a.cacheBytes) }
                                else {
                                    val msg = (r?.err?.ifBlank { r.out } ?: "protected").trim().take(120)
                                    firstErr.compareAndSet(null, msg)
                                    if (failedLogged.incrementAndGet() <= 5) com.fitzy.foldopti.core.system.CrashLog.note("pm clear --cache-only ${a.pkg} failed (exit=${r?.code}): $msg")
                                }
                                _progress.value = Progress("Clearing cache", done.incrementAndGet(), allowed.size, a.label)
                            }
                        } }.awaitAll()
                    }
                    return CleanResult(bytes.get(), ok.get(), skipped, allowed.size - ok.get(), tier, null, firstErr.get())
                }
                tier == Tier.T2 -> return CleanResult(0, 0, skipped, 0, tier, "Silent cache clearing needs Android 12 or newer. Use the accessibility option or clear from system settings.")
                tier == Tier.T1 -> {
                    val batch = allowed.take(15)
                    _progress.value = Progress("Clearing cache", 0, batch.size)
                    val done = accessibility.clearCaches(batch.map { it.pkg }) { n -> _progress.value = Progress("Clearing cache", n, batch.size) }
                    return CleanResult(batch.filter { it.pkg in done }.sumOf { it.cacheBytes }, done.size, skipped, batch.size - done.size, tier,
                        if (allowed.size > 15) "Cleared the 15 largest. Run again for the rest." else null)
                }
                else -> return CleanResult(0, 0, skipped, 0, tier, "Cache can't be cleared in limited mode. Turn on Shizuku in Settings.")
            }
        } catch (t: kotlinx.coroutines.CancellationException) { throw t
        } catch (t: Throwable) {
            com.fitzy.foldopti.core.system.CrashLog.record("cleanCaches", t)
            return CleanResult(0, 0, 0, 0, tier, "Clean stopped: ${t.message ?: t.javaClass.simpleName}")
        } finally { _progress.value = null }
    }

    private fun hasLauncher(pkg: String) = pm.getLaunchIntentForPackage(pkg) != null || pm.getLeanbackLaunchIntentForPackage(pkg) != null

    private fun exposesBoundService(pkg: String): Boolean = runCatching {
        val pi = pm.getPackageInfo(pkg, android.content.pm.PackageManager.GET_SERVICES or android.content.pm.PackageManager.GET_RECEIVERS)
        (pi.services.orEmpty().mapNotNull { it.permission } + pi.receivers.orEmpty().mapNotNull { it.permission }).any { it in SafetyPolicy.bindingPermissions }
    }.getOrDefault(true)   // if we can't tell, treat as unsafe

    private fun roleApps(): Set<String> = buildSet {
        pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName?.let(::add)
        Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/')?.let(::add)
        (ctx.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager)?.defaultDialerPackage?.let(::add)
        runCatching { Telephony.Sms.getDefaultSmsPackage(ctx) }.getOrNull()?.let(::add)
    }

    /** Restricts idle background apps (standby bucket + background run). Does not force-stop, so nothing restarts in a loop. */
    suspend fun safeBoost(): BoostResult {
        if (caps.current().tier != Tier.T2) return BoostResult(emptyList(), emptyMap(), "Safe Boost needs Shizuku. In limited mode FoldOpti can only measure and alert.")
        try {
            _progress.value = Progress("Checking running apps", 0, 0)
            val snap = probe.snapshot()
            val roles = roleApps(); val now = System.currentTimeMillis()
            val done = mutableListOf<String>(); val skipped = linkedMapOf<String, String>()
            val list = snap.running.toList()
            list.forEachIndexed { i, pkg ->
                _progress.value = Progress("Restricting idle apps", i, list.size, pkg)
                val ai = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: return@forEachIndexed
                val system = !SafetyPolicy.canRestrict(pkg, ai.flags and ApplicationInfo.FLAG_SYSTEM != 0, hasLauncher(pkg), exposesBoundService(pkg))
                if (done.size >= SafetyPolicy.MAX_RESTRICTIONS_PER_RUN) { skipped[pkg] = "Limit per run"; return@forEachIndexed }
                val d = BoostPolicy.decide(
                    Candidate(pkg, !guard.allows(pkg, CleanAction.RESTRICT) || ProtectedPackages.isCore(pkg), pkg in roles,
                        pkg in snap.foregroundServices, pkg in snap.mediaSessions, system, pkg in snap.topApp, stopLog.history(pkg, now)), now)
                if (!d.allow) { skipped[pkg] = d.reason; return@forEachIndexed }
                if (restrictInternal(pkg)) { done += pkg; stopLog.record(pkg, now) }
            }
            return BoostResult(done, skipped, if (snap.running.isEmpty()) "Couldn't read the running app list from Shizuku" else null)
        } catch (t: kotlinx.coroutines.CancellationException) { throw t
        } catch (t: Throwable) {
            com.fitzy.foldopti.core.system.CrashLog.record("safeBoost", t)
            return BoostResult(emptyList(), emptyMap(), "Boost stopped: ${t.message ?: t.javaClass.simpleName}")
        } finally { _progress.value = null }
    }

    private suspend fun restrictInternal(pkg: String): Boolean {
        shell.exec("am", "set-standby-bucket", pkg, "restricted")
        return shell.exec("cmd", "appops", "set", pkg, "RUN_ANY_IN_BACKGROUND", "ignore").ok
    }

    suspend fun restrict(pkg: String): Boolean {
        if (!guard.allows(pkg, CleanAction.RESTRICT) || SafetyPolicy.isSystemCritical(pkg) || !shell.ready) return false
        val ok = restrictInternal(pkg); if (ok) stopLog.record(pkg); return ok
    }

    /** Last resort for runaway processes only (alert action). Honors whitelist and cooldown. */
    suspend fun forceStop(pkg: String): Boolean {
        if (!guard.allows(pkg, CleanAction.STOP) || SafetyPolicy.isSystemCritical(pkg) || !shell.ready) return false
        if (stopLog.history(pkg).any { System.currentTimeMillis() - it < BoostPolicy.COOLDOWN_MS }) return false
        val ok = shell.exec("am", "force-stop", pkg).ok; if (ok) stopLog.record(pkg); return ok
    }

    private companion object { const val MIN_CACHE_BYTES = 256L * 1024 }

    /** Reverts every restriction FoldOpti has applied: background run back to default, standby bucket back to active. */
    suspend fun undoRestrictions(): Int {
        if (!shell.ready) return -1
        var n = 0
        for (pkg in stopLog.allPackages()) {
            val a = shell.exec("cmd", "appops", "set", pkg, "RUN_ANY_IN_BACKGROUND", "default")
            shell.exec("am", "set-standby-bucket", pkg, "active")
            if (a.ok) n++
        }
        stopLog.clear(); return n
    }

    /** Cool down = Safe Boost now; scans pause while thermal status stays high (see ScanWorker). */
    suspend fun coolDown(): BoostResult = safeBoost()
}
