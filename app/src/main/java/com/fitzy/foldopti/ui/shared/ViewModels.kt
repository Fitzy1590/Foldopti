package com.fitzy.foldopti.ui.shared

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitzy.foldopti.core.notifications.ActionHandler
import com.fitzy.foldopti.core.optimizer.*
import com.fitzy.foldopti.core.scheduler.*
import com.fitzy.foldopti.core.security.*
import com.fitzy.foldopti.core.settings.AppSettings
import com.fitzy.foldopti.core.settings.SettingsStore
import com.fitzy.foldopti.core.system.Access
import com.fitzy.foldopti.core.update.UpdateRepository
import com.fitzy.foldopti.core.update.UpdateState
import com.fitzy.foldopti.core.whitelist.ProtectedApp
import com.fitzy.foldopti.core.whitelist.WhitelistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

fun fmtBytes(b: Long) = ActionHandler.fmt(b)

/** Runs [block] in the ViewModel scope. Errors are logged to Diagnostics and reported through [onError] instead of crashing. */
internal fun ViewModel.safeLaunch(where: String, onError: (String) -> Unit, block: suspend () -> Unit) = viewModelScope.launch {
    try { block() } catch (c: kotlinx.coroutines.CancellationException) { throw c } catch (t: Throwable) {
        com.fitzy.foldopti.core.system.CrashLog.record(where, t); onError("Something went wrong: ${t.message ?: t.javaClass.simpleName}. Details are in Settings > Diagnostics.")
    }
}

data class HomeUi(
    val summary: ScanSummary = ScanSummary(), val caps: Capabilities? = null,
    val thermal: Int = 0, val headroom: Float? = null, val busy: Boolean = false, val message: String? = null,
    val progress: Progress? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val scan: ScanEngine, private val capsMgr: CapabilityManager, private val thermal: ThermalMonitor,
    private val optimizer: OptimizerEngine, private val updates: UpdateRepository,
) : ViewModel() {
    private val local = MutableStateFlow(HomeUi())
    val state: StateFlow<HomeUi> = combine(local, scan.last, thermal.status, optimizer.progress) { l, s, t, p -> l.copy(summary = s, thermal = t, progress = p) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUi())

    init { thermal.start(); viewModelScope.launch { updates.autoCheck() } }
    override fun onCleared() { thermal.stop() }

    fun refresh() = safeLaunch("home.refresh", { m -> local.update { it.copy(busy = false, message = m) } }) {
        local.update { it.copy(busy = true, caps = capsMgr.current(), headroom = thermal.headroom()) }
        withContext(Dispatchers.Default) { scan.run() }
        local.update { it.copy(busy = false, headroom = thermal.headroom()) }
    }

    fun refreshCaps() { local.update { it.copy(caps = capsMgr.current(), headroom = thermal.headroom()) } }

    private var job: kotlinx.coroutines.Job? = null

    fun cancel() { job?.cancel() }

    fun smartClean() {
        if (local.value.busy) return
        job = safeLaunch("home.smartClean", { m -> local.update { it.copy(busy = false, message = m) } }) {
            try {
                local.update { it.copy(busy = true, message = null, caps = capsMgr.current()) }
                val c = optimizer.cleanCaches()
                val lines = c.describe(::fmtBytes)
                local.update { it.copy(busy = false, message = "Smart Clean finished\n" + lines.joinToString("\n")) }
                runCatching { withContext(Dispatchers.Default) { scan.run() } }
            } catch (c: kotlinx.coroutines.CancellationException) {
                local.update { it.copy(busy = false, message = "Cancelled") }; throw c
            }
        }
    }
}

data class OptimizeUi(
    val caches: List<AppCache> = emptyList(), val tweaks: List<Tweak> = emptyList(), val dormant: List<DormantApp> = emptyList(),
    val caps: Capabilities? = null, val busy: Boolean = false, val message: String? = null, val loaded: Boolean = false,
    val disabled: List<Pair<String, String>> = emptyList(),
)

@HiltViewModel
class OptimizeViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context, private val optimizer: OptimizerEngine, private val tweaks: TweakScanner,
    private val dormancy: DormancyScanner, private val capsMgr: CapabilityManager,
) : ViewModel() {
    private val _ui = MutableStateFlow(OptimizeUi())
    val ui: StateFlow<OptimizeUi> = _ui.asStateFlow()
    val progress: StateFlow<Progress?> = optimizer.progress

    private fun err(m: String) = _ui.update { it.copy(busy = false, message = m) }

    fun load() = safeLaunch("optimize.load", ::err) {
        _ui.update { it.copy(busy = true) }
        val caches = optimizer.measureCache(); val dormant = dormancy.scan()
        _ui.update { it.copy(caches = caches, dormant = dormant, tweaks = tweaks.read(), caps = capsMgr.current(),
            disabled = withContext(Dispatchers.IO) { dormancy.disabledApps() }, busy = false, loaded = true) }
    }

    private var job: kotlinx.coroutines.Job? = null
    fun cancel() { job?.cancel(); _ui.update { it.copy(busy = false, message = "Cancelled") } }

    fun clean(pkg: String? = null) {
        if (_ui.value.busy) return
        job = safeLaunch("optimize.clean", ::err) {
            _ui.update { it.copy(busy = true, message = null) }
            val r = optimizer.cleanCaches(pkg?.let { listOf(it) })
            val lines = r.describe(::fmtBytes)
            val caches = optimizer.measureCache()
            _ui.update { it.copy(busy = false, caches = caches, message = "Clean finished\n" + lines.joinToString("\n")) }
        }
    }

    fun undoBoost() {
        if (_ui.value.busy) return
        job = safeLaunch("optimize.undo", ::err) {
            _ui.update { it.copy(busy = true, message = null) }
            val recorded = optimizer.restrictionCount()
            val msg = when {
                recorded == 0 -> "Nothing to undo. FoldOpti has no background restrictions on record (they may already be reverted)."
                else -> optimizer.undoRestrictions().let { n -> if (n < 0) "Undo needs Shizuku running. Start Shizuku and try again." else "Undo finished\nReverted background restrictions on $n of $recorded apps" }
            }
            _ui.update { it.copy(busy = false, message = msg) }
        }
    }

    fun boost() {
        if (_ui.value.busy) return
        job = safeLaunch("optimize.boost", ::err) {
            _ui.update { it.copy(busy = true, message = null) }
            val r = optimizer.safeBoost()
            _ui.update { it.copy(busy = false, message = "Safe Boost finished\n" + r.describe().joinToString("\n")) }
        }
    }

    fun applyTweak(id: String) = safeLaunch("tweak.apply", ::err) { val ok = tweaks.apply(id); _ui.update { it.copy(message = if (ok) "Applied" else "Could not apply. Shizuku or secure settings access is needed.", tweaks = tweaks.read()) } }
    fun revertTweak(id: String) = safeLaunch("tweak.revert", ::err) { tweaks.revert(id); _ui.update { it.copy(message = "Reverted", tweaks = tweaks.read()) } }
    fun tweakIntent(id: String): Intent = tweaks.deepLink(id)

    fun disable(pkg: String) = safeLaunch("dormancy.disable", ::err) {
        val ok = dormancy.disable(pkg)
        _ui.update { it.copy(message = if (ok) "Disabled $pkg. You can re-enable it below." else "Could not disable. Shizuku is needed, and some apps are protected.") }
        load()
    }
    fun enable(pkg: String) = safeLaunch("dormancy.enable", ::err) { dormancy.enable(pkg); load() }
    fun uninstallIntent(pkg: String) = dormancy.uninstallIntent(pkg)
    fun dismissMessage() = _ui.update { it.copy(message = null) }
}

@HiltViewModel
class SecurityViewModel @Inject constructor(
    private val repo: SecurityRepository, private val sigs: SignatureUpdater, private val db: SignatureDb,
) : ViewModel() {
    val findings = repo.findings; val progress = repo.progress; val lastScan = repo.lastScan
    private val _msg = MutableStateFlow<String?>(null); val message = _msg.asStateFlow()
    val signatureCount get() = db.count
    val canUpdateSignatures get() = sigs.enabled

    fun scan() = viewModelScope.launch(Dispatchers.Default) { if (!repo.progress.value.running) repo.scan() }
    fun updateSignatures() = viewModelScope.launch {
        _msg.value = when (val r = sigs.update()) {
            SigUpdate.Disabled -> "Signature updates are not configured in this build"
            SigUpdate.NoAsset -> "No signature file in the latest release"
            is SigUpdate.Updated -> "Signature list updated (${r.count} entries)"
            is SigUpdate.Failed -> r.message
        }
    }
}

data class SettingsUi(
    val settings: AppSettings = AppSettings(), val caps: Capabilities? = null, val whitelist: List<ProtectedApp> = emptyList(),
    val suggestions: Map<String, String> = emptyMap(), val update: UpdateState = UpdateState.Idle, val version: String = "",
)

data class InstalledApp(val pkg: String, val label: String)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context, private val store: SettingsStore, private val capsMgr: CapabilityManager,
    private val whitelist: WhitelistRepository, private val updates: UpdateRepository, private val shell: ShizukuShell,
    private val access: Access,
) : ViewModel() {
    private val caps = MutableStateFlow<Capabilities?>(null)
    private val suggestions = MutableStateFlow<Map<String, String>>(emptyMap())
    val isTv get() = access.isTv

    val ui: StateFlow<SettingsUi> = combine(store.settings, caps, whitelist.all, suggestions, updates.state) { s, c, w, sg, u ->
        SettingsUi(s, c, w, sg.filterKeys { k -> w.none { it.pkg == k } }, u, updates.currentVersion)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUi(version = updates.currentVersion))

    fun refreshCaps() { caps.value = capsMgr.current(); suggestions.value = whitelist.suggestions() }

    fun setAutoUpdate(v: Boolean) = viewModelScope.launch { store.setAutoUpdate(v) }
    fun setAlerts(v: Boolean) = viewModelScope.launch { store.setAlertsEnabled(v) }
    fun setInterval(v: Int) = viewModelScope.launch { store.setScanInterval(v) }
    fun setQuiet(v: Boolean) = viewModelScope.launch { store.setQuiet(v) }
    fun setRam(v: Int) = viewModelScope.launch { store.setRamThreshold(v) }
    fun setCacheMb(v: Int) = viewModelScope.launch { store.setCacheThresholdMb(v) }
    fun setExperimentalCache(v: Boolean) = viewModelScope.launch { store.setExperimentalCacheClear(v) }
    fun setLive(v: Boolean) = viewModelScope.launch { store.setLiveMonitor(v); if (v) MonitorService.start(ctx) else MonitorService.stop(ctx) }

    fun requestShizuku() = shell.requestPermission()
    fun usageAccessIntent() = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    fun accessibilityIntent() = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    fun notificationIntent() = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    suspend fun shizukuTest(): String = shell.selfTest(ctx.packageName).also { com.fitzy.foldopti.core.system.CrashLog.note("Shizuku test\n$it") }
    suspend fun openQuickSettings(): String {
        val r = shell.exec("cmd", "statusbar", "expand-settings")
        return if (r.ok) "Sent. If Quick Settings did not appear, the panel itself is unavailable (SystemUI), not just the swipe gesture."
        else "Command failed: ${(r.err.ifBlank { r.out }).trim().take(160)}"
    }
    suspend fun closeQuickSettings() { shell.exec("cmd", "statusbar", "collapse") }
    fun diagnostics(): String = com.fitzy.foldopti.core.system.CrashLog.read(ctx)
    fun clearDiagnostics() = com.fitzy.foldopti.core.system.CrashLog.clear(ctx)

    fun checkUpdates() = viewModelScope.launch { updates.check() }
    fun downloadUpdate() = viewModelScope.launch { (updates.state.value as? UpdateState.Available)?.let { updates.download(it.release) } }
    fun installUpdate() = viewModelScope.launch { (updates.state.value as? UpdateState.Ready)?.let { updates.install(it.release, it.file) } }

    fun addToWhitelist(pkg: String, reason: String) = viewModelScope.launch { whitelist.add(pkg, reason) }
    fun removeFromWhitelist(a: ProtectedApp) = viewModelScope.launch { whitelist.remove(a) }
    suspend fun installedApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = ctx.packageManager
        pm.getInstalledApplications(0).filter { pm.getLaunchIntentForPackage(it.packageName) != null || pm.getLeanbackLaunchIntentForPackage(it.packageName) != null }
            .map { InstalledApp(it.packageName, it.loadLabel(pm).toString()) }.sortedBy { it.label.lowercase() }
    }
    suspend fun exportWhitelist(): String = whitelist.exportJson(ui.value.whitelist)
    suspend fun importWhitelist(json: String): Int = whitelist.importJson(json)
}

data class TroubleshootUi(val report: DisabledReport? = null, val busy: Boolean = false, val message: String? = null)

@HiltViewModel
class TroubleshootViewModel @Inject constructor(private val scanner: DisabledScanner) : ViewModel() {
    private val _ui = MutableStateFlow(TroubleshootUi())
    val ui: StateFlow<TroubleshootUi> = _ui.asStateFlow()
    private fun err(m: String) = _ui.update { it.copy(busy = false, message = m) }

    fun scan() = safeLaunch("troubleshoot.scan", ::err) {
        _ui.update { it.copy(busy = true, message = null) }
        val r = scanner.scan()
        _ui.update { it.copy(report = r, busy = false, message = if (r.disabled.isEmpty()) "No disabled apps found" else null) }
    }
    fun enable(pkg: String) = safeLaunch("troubleshoot.enable", ::err) {
        val ok = scanner.enable(pkg)
        _ui.update { it.copy(message = if (ok) "Enabled $pkg. Restart the phone if the panel is still missing." else "Could not enable. Start Shizuku, or use Open settings.") }
        if (ok) scan()
    }
    fun resetBackground(pkg: String) = safeLaunch("troubleshoot.reset", ::err) {
        val ok = scanner.resetBackground(pkg)
        _ui.update { it.copy(message = if (ok) "Background run reset for $pkg" else "Could not reset. Is Shizuku running?") }
        if (ok) scan()
    }
    fun appInfoIntent(pkg: String) = scanner.appInfoIntent(pkg)
}
