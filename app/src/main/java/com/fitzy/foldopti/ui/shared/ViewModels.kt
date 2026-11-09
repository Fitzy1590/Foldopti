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

data class HomeUi(
    val summary: ScanSummary = ScanSummary(), val caps: Capabilities? = null,
    val thermal: Int = 0, val headroom: Float? = null, val busy: Boolean = false, val message: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val scan: ScanEngine, private val capsMgr: CapabilityManager, private val thermal: ThermalMonitor,
    private val optimizer: OptimizerEngine, private val updates: UpdateRepository,
) : ViewModel() {
    private val local = MutableStateFlow(HomeUi())
    val state: StateFlow<HomeUi> = combine(local, scan.last, thermal.status) { l, s, t -> l.copy(summary = s, thermal = t) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUi())

    init { thermal.start(); viewModelScope.launch { updates.autoCheck() } }
    override fun onCleared() { thermal.stop() }

    fun refresh() = viewModelScope.launch {
        local.update { it.copy(busy = true, caps = capsMgr.current(), headroom = thermal.headroom()) }
        withContext(Dispatchers.Default) { scan.run() }
        local.update { it.copy(busy = false, headroom = thermal.headroom()) }
    }

    fun refreshCaps() { local.update { it.copy(caps = capsMgr.current(), headroom = thermal.headroom()) } }

    fun smartClean() = viewModelScope.launch {
        local.update { it.copy(busy = true, message = null) }
        val c = optimizer.cleanCaches(); val b = optimizer.safeBoost()
        val msg = listOfNotNull(
            c.note ?: "Cleared ${fmtBytes(c.clearedBytes)} from ${c.cleared} apps" + if (c.skipped > 0) " (${c.skipped} protected)" else "",
            b.note ?: if (b.restricted.isNotEmpty()) "Restricted ${b.restricted.size} idle apps" else null,
        ).joinToString(". ")
        local.update { it.copy(busy = false, message = msg) }
        scan.run()
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

    fun load() = viewModelScope.launch {
        _ui.update { it.copy(busy = true) }
        val caches = optimizer.measureCache(); val dormant = dormancy.scan()
        _ui.update { it.copy(caches = caches, dormant = dormant, tweaks = tweaks.read(), caps = capsMgr.current(),
            disabled = withContext(Dispatchers.IO) { dormancy.disabledApps() }, busy = false, loaded = true) }
    }

    fun clean(pkg: String? = null) = viewModelScope.launch {
        _ui.update { it.copy(busy = true, message = null) }
        val r = optimizer.cleanCaches(pkg?.let { listOf(it) })
        _ui.update { it.copy(message = r.note ?: "Cleared ${fmtBytes(r.clearedBytes)} from ${r.cleared} apps" + if (r.failed > 0) ", ${r.failed} failed" else "") }
        load()
    }

    fun boost() = viewModelScope.launch {
        _ui.update { it.copy(busy = true, message = null) }
        val r = optimizer.safeBoost()
        _ui.update { it.copy(busy = false, message = r.note ?: "Restricted ${r.restricted.size} idle apps, skipped ${r.skipped.size}") }
    }

    fun applyTweak(id: String) = viewModelScope.launch { val ok = tweaks.apply(id); _ui.update { it.copy(message = if (ok) "Applied" else "Could not apply. Shizuku or secure settings access is needed.", tweaks = tweaks.read()) } }
    fun revertTweak(id: String) = viewModelScope.launch { tweaks.revert(id); _ui.update { it.copy(message = "Reverted", tweaks = tweaks.read()) } }
    fun tweakIntent(id: String): Intent = tweaks.deepLink(id)

    fun disable(pkg: String) = viewModelScope.launch {
        val ok = dormancy.disable(pkg)
        _ui.update { it.copy(message = if (ok) "Disabled $pkg. You can re-enable it below." else "Could not disable. Shizuku is needed, and some apps are protected.") }
        load()
    }
    fun enable(pkg: String) = viewModelScope.launch { dormancy.enable(pkg); load() }
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
    fun setLive(v: Boolean) = viewModelScope.launch { store.setLiveMonitor(v); if (v) MonitorService.start(ctx) else MonitorService.stop(ctx) }

    fun requestShizuku() = shell.requestPermission()
    fun usageAccessIntent() = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    fun accessibilityIntent() = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    fun notificationIntent() = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

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
