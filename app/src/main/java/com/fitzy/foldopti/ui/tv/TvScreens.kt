@file:OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)

package com.fitzy.foldopti.ui.tv

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.*
import com.fitzy.foldopti.core.optimizer.DormantApp
import com.fitzy.foldopti.core.optimizer.DormantTier
import com.fitzy.foldopti.core.optimizer.ShizukuState
import com.fitzy.foldopti.core.optimizer.Tier
import com.fitzy.foldopti.core.scheduler.ThermalMonitor
import com.fitzy.foldopti.core.security.Band
import com.fitzy.foldopti.core.update.UpdateState
import com.fitzy.foldopti.core.whitelist.ProtectedSource
import com.fitzy.foldopti.ui.mobile.LICENSES
import com.fitzy.foldopti.ui.mobile.bandLabel
import com.fitzy.foldopti.ui.mobile.tierText
import com.fitzy.foldopti.ui.mobile.updateText
import com.fitzy.foldopti.ui.shared.*
import com.fitzy.foldopti.ui.theme.*
import kotlinx.coroutines.launch

@Composable
private fun rememberInitialFocus(): FocusRequester {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    return fr
}

@Composable
fun TvHome(vm: HomeViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshCaps() }
    LaunchedEffect(Unit) { if (s.summary.at == 0L) vm.refresh() }
    val fr = rememberInitialFocus(); val sum = s.summary; val accent = MaterialTheme.colorScheme.primary
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(Space.x12), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.x4)) {
            TvTitle("Home")
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x2), verticalAlignment = Alignment.CenterVertically) {
                StatusDot(Thermal.color(s.thermal, accent)); Text(Build.MODEL, style = MaterialTheme.typography.titleMedium)
            }
            Box(contentAlignment = Alignment.Center) {
                ThermalGauge(s.thermal, s.headroom, accent, MaterialTheme.colorScheme.surfaceVariant, size = 220.dp)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(ThermalMonitor.label(s.thermal), style = MaterialTheme.typography.headlineMedium)
                    TvMuted(s.headroom?.let { "headroom ${(it * 100).toInt()}%" } ?: "thermal status")
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.x4)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x4)) {
                TvStat("Free memory", if (sum.ramAvailPct >= 0) "${sum.ramAvailPct}%" else "n/a", "of ${sum.ramTotalMb} MB", Modifier.weight(1f))
                TvStat("Cache", sum.cacheBytes?.let(::fmtBytes) ?: "n/a", if (sum.cacheBytes == null) "Usage access needed" else "unprotected apps", Modifier.weight(1f))
            }
            TvAction(if (s.busy) "Working" else "Clean now", Modifier.focusRequester(fr), enabled = !s.busy) { vm.smartClean() }
            if (s.busy) TvMuted(s.progress?.let { (if (it.total > 0) "${it.label}: ${it.done} of ${it.total}" else it.label) + (if (it.detail.isNotBlank()) "  ${it.detail}" else "") } ?: "Working")
            s.message?.let { TvMuted(it) }
            TvMuted(when (s.caps?.tier) { Tier.T2 -> "Mode: full (Shizuku)"; else -> "Limited mode. Turn on Shizuku in Settings to clean and restrict apps." })
        }
    }
}

@Composable
fun TvClean(vm: OptimizeViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.load() }
    val fr = remember { FocusRequester() }
    val total = ui.caches.filter { !it.isProtected }.sumOf { it.cacheBytes }
    val progress by vm.progress.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize().focusRestorer { fr }, verticalArrangement = Arrangement.spacedBy(Space.x3)) {
        item { TvTitle("Clean") }
        item { TvMuted("${fmtBytes(total)} of cache in ${ui.caches.count { !it.isProtected }} apps, ${ui.caches.count { it.isProtected }} protected") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                TvAction("Clear cache", Modifier.focusRequester(fr), enabled = total > 0 && !ui.busy) { vm.clean() }
                TvAction("Safe Boost", enabled = !ui.busy) { vm.boost() }
            }
        }
        if (ui.busy) item { TvMuted(progress?.let { if (it.total > 0) "${it.label}: ${it.done} of ${it.total}" else it.label } ?: "Working") }
        ui.message?.let { m -> item { TvMuted(m) } }
        if (ui.caps?.usageAccess == false) item { TvMuted("Usage access is off. Enable it in system settings to measure cache.") }
        item { TvSection("Largest caches") }
        items(ui.caches.take(15), key = { it.pkg }) { c -> TvRow(c.label, if (c.isProtected) "Protected" else c.pkg, fmtBytes(c.cacheBytes)) { if (!c.isProtected) vm.clean(c.pkg) } }
    }
}

@Composable
fun TvApps(vm: OptimizeViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.load() }
    var confirm by remember { mutableStateOf<DormantApp?>(null) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(ui.dormant.size) { runCatching { fr.requestFocus() } }
    LazyColumn(Modifier.fillMaxSize().focusRestorer { fr }, verticalArrangement = Arrangement.spacedBy(Space.x3)) {
        item { TvTitle("Unused apps") }
        item { TvMuted(if (ui.dormant.isEmpty()) (if (ui.loaded) "Nothing unused, or usage access is off" else "Scanning") else "Select an app to disable it. Disabled apps can be enabled again below.") }
        ui.message?.let { m -> item { TvMuted(m) } }
        var first = true
        DormantTier.entries.forEach { tier ->
            val apps = ui.dormant.filter { it.tier == tier }
            if (apps.isNotEmpty()) {
                item { TvSection("${tier.days}+ days idle") }
                items(apps, key = { it.pkg }) { a ->
                    val mod = if (first) Modifier.focusRequester(fr) else Modifier; first = false
                    TvRow(a.label, if (a.isProtected) "Protected" else a.pkg, "${a.daysIdle} days", mod) { if (!a.isProtected) confirm = a }
                }
            }
        }
        if (ui.disabled.isNotEmpty()) {
            item { TvSection("Disabled") }
            items(ui.disabled, key = { "d" + it.first }) { (pkg, label) -> TvRow(label, pkg, "Enable") { vm.enable(pkg) } }
        }
    }
    confirm?.let { a -> ConfirmDialog("Disable ${a.label}?", "The app stops running and leaves the launcher. You can enable it again from this screen.", "Disable", { vm.disable(a.pkg); confirm = null }) { confirm = null } }
}

@Composable
private fun ConfirmDialog(title: String, text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val cancel = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Radius.panel)).padding(Space.x6), verticalArrangement = Arrangement.spacedBy(Space.x4)) {
            Text(title, style = MaterialTheme.typography.titleMedium); TvMuted(text)
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                TvAction("Cancel", Modifier.focusRequester(cancel)) { onDismiss() }   // focus starts on the safe option
                TvAction(confirmLabel) { onConfirm() }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { cancel.requestFocus() } }
}

@Composable
fun TvSecurity(vm: SecurityViewModel = hiltViewModel()) {
    val findings by vm.findings.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val fr = remember { FocusRequester() }
    val flagged = findings.filter { it.band != Band.CLEAN }
    LazyColumn(Modifier.fillMaxSize().focusRestorer { fr }, verticalArrangement = Arrangement.spacedBy(Space.x3)) {
        item { TvTitle("Security") }
        item { TvMuted(if (findings.isEmpty()) "No scan yet. Runs on this device, nothing is uploaded." else "${findings.count { it.band == Band.SUSPICIOUS }} suspicious, ${findings.count { it.band == Band.REVIEW }} to review") }
        item { TvAction(if (progress.running) "Scanning ${progress.done} of ${progress.total}" else "Scan now", Modifier.focusRequester(fr), enabled = !progress.running) { vm.scan() } }
        items(flagged, key = { it.pkg }) { f ->
            TvRow(f.label, f.pkg, "${bandLabel(f.band)}  ${f.score}") { open = if (open == f.pkg) null else f.pkg }
            if (open == f.pkg) Column(Modifier.padding(start = Space.x4, top = Space.x2)) { f.reasons.forEach { TvMuted("+${it.points}  ${it.text}") }; TvMuted("FoldOpti only flags apps. Remove one from system settings.") }
        }
    }
}

@Composable
fun TvSettings(vm: SettingsViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshCaps() }
    var picker by remember { mutableStateOf<List<InstalledApp>?>(null) }
    val fr = remember { FocusRequester() }
    val s = ui.settings; val c = ui.caps
    fun open(i: android.content.Intent) { runCatching { ctx.startActivity(i) } }
    LazyColumn(Modifier.fillMaxSize().focusRestorer { fr }, verticalArrangement = Arrangement.spacedBy(Space.x2)) {
        item { TvTitle("Settings") }
        item { TvSection("Access") }
        item { TvRow("Mode", tierText(c?.tier), modifier = Modifier.focusRequester(fr)) {} }
        item { TvRow("Shizuku", when (c?.shizuku) { ShizukuState.READY -> "Connected"; ShizukuState.NO_PERMISSION -> "Select to allow"; ShizukuState.NOT_RUNNING -> "Not running. Start Shizuku first"; else -> "Checking" }) { vm.requestShizuku() } }
        item { TvRow("Usage access", if (c?.usageAccess == true) "On" else "Off. Select to open settings") { if (c?.usageAccess != true) open(vm.usageAccessIntent()) } }
        item { TvRow("Notifications", if (c?.notifications == true) "On" else "Off. Select to open settings") { if (c?.notifications != true) open(vm.notificationIntent()) } }
        item { TvSection("Monitoring") }
        item { TvRow("Alerts", if (s.alertsEnabled) "On" else "Off") { vm.setAlerts(!s.alertsEnabled) } }
        item { TvRow("Live monitor", if (s.liveMonitor) "On" else "Off") { vm.setLive(!s.liveMonitor) } }
        item { TvRow("Scan every", "${s.scanIntervalMin} min. Select to change") { vm.setInterval(when (s.scanIntervalMin) { 15 -> 30; 30 -> 60; 60 -> 120; else -> 15 }) } }
        item { TvRow("Free memory alert", "below ${s.ramThresholdPct}%. Select to change") { vm.setRam(if (s.ramThresholdPct >= 30) 10 else s.ramThresholdPct + 5) } }
        item { TvRow("Cache alert", "above ${s.cacheThresholdMb} MB. Select to change") { vm.setCacheMb(if (s.cacheThresholdMb >= 4096) 512 else s.cacheThresholdMb * 2) } }
        item { TvSection("Protected apps") }
        item { TvRow("Add app", "Protected apps are never cleaned, restricted, disabled or uninstalled") { scope.launch { picker = vm.installedApps() } } }
        items(ui.suggestions.entries.toList(), key = { "s" + it.key }) { (pkg, why) -> TvRow(pkg, "Suggested: $why", "Protect") { vm.addToWhitelist(pkg, why) } }
        items(ui.whitelist, key = { it.pkg }) { a -> TvRow(a.pkg, a.reason, if (a.source == ProtectedSource.DEFAULT) "Default" else "Remove") { if (a.source != ProtectedSource.DEFAULT) vm.removeFromWhitelist(a) } }
        item { TvSection("Updates") }
        item { TvRow("Version", ui.version) {} }
        item { TvRow("Check automatically", if (s.autoUpdate) "On" else "Off") { vm.setAutoUpdate(!s.autoUpdate) } }
        item {
            when (ui.update) {
                is UpdateState.Available -> TvRow("Download update", updateText(ui.update)) { vm.downloadUpdate() }
                is UpdateState.Ready -> TvRow("Install update", updateText(ui.update)) { vm.installUpdate() }
                else -> TvRow("Check for updates", updateText(ui.update)) { vm.checkUpdates() }
            }
        }
        item { TvSection("Diagnostics") }
        item { val log = vm.diagnostics(); TvRow(if (log.isBlank()) "No errors recorded" else "Last error", log.takeLast(300).ifBlank { null }, if (log.isBlank()) null else "Clear") { vm.clearDiagnostics() } }
        item { TvSection("About") }
        item { TvMuted(LICENSES) }
    }
    picker?.let { apps ->
        Dialog(onDismissRequest = { picker = null }) {
            LazyColumn(Modifier.heightIn(max = 480.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Radius.panel)).padding(Space.x4), verticalArrangement = Arrangement.spacedBy(Space.x2)) {
                items(apps, key = { it.pkg }) { a -> TvRow(a.label, a.pkg) { vm.addToWhitelist(a.pkg, "Added by you"); picker = null } }
            }
        }
    }
}
