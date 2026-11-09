package com.fitzy.foldopti.ui.mobile

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitzy.foldopti.core.optimizer.ShizukuState
import com.fitzy.foldopti.core.optimizer.Tier
import com.fitzy.foldopti.core.update.UpdateState
import com.fitzy.foldopti.core.whitelist.ProtectedSource
import com.fitzy.foldopti.ui.shared.InstalledApp
import com.fitzy.foldopti.ui.shared.SettingsViewModel
import com.fitzy.foldopti.ui.theme.Space
import kotlinx.coroutines.launch
import java.text.DateFormat

fun updateText(u: UpdateState): String = when (u) {
    UpdateState.Idle -> "Not checked yet"
    UpdateState.Checking -> "Checking"
    is UpdateState.UpToDate -> "Up to date. Checked ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(u.checkedAt)}"
    is UpdateState.NoReleases -> "No releases published yet"
    is UpdateState.Available -> "Version ${u.release.version.let { "${it.major}.${it.minor}.${it.patch}" }} is available"
    is UpdateState.Downloading -> "Downloading ${(u.progress * 100).toInt()}%"
    is UpdateState.Ready -> "Downloaded and verified. Ready to install"
    is UpdateState.Installing -> "Installing"
    is UpdateState.Failed -> u.message
}

fun tierText(t: Tier?) = when (t) { Tier.T2 -> "Full (Shizuku)"; Tier.T1 -> "Accessibility"; else -> "Limited" }

@Composable
fun SettingsScreen(vm: SettingsViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshCaps() }
    var picker by remember { mutableStateOf<List<InstalledApp>?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { scope.launch { ctx.contentResolver.openOutputStream(it)?.use { o -> o.write(vm.exportWhitelist().toByteArray()) } } }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { scope.launch { runCatching { vm.importWhitelist(ctx.contentResolver.openInputStream(it)!!.bufferedReader().readText()) } } }
    }
    val c = ui.caps; val s = ui.settings

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(Space.x6), verticalArrangement = Arrangement.spacedBy(Space.x3)) {
        item { Title("Settings") }
        item { SectionLabel("Access") }
        item {
            Panel {
                RowItem("Mode", tierText(c?.tier))
                RowItem("Shizuku", when (c?.shizuku) { ShizukuState.READY -> "Connected"; ShizukuState.NO_PERMISSION -> "Running, permission needed"; ShizukuState.NOT_RUNNING -> "Not running"; else -> "Checking" },
                    trailing = { if (c?.shizuku == ShizukuState.NO_PERMISSION) SecondaryButton("Allow", vm::requestShizuku) })
                var test by remember { mutableStateOf<String?>(null) }
                SecondaryButton("Test Shizuku", { scope.launch { test = "Running"; test = vm.shizukuTest() } }, Modifier.fillMaxWidth(), enabled = c?.shizuku == ShizukuState.READY)
                test?.let { Mono(it) }
                RowItem("Accessibility", if (c?.accessibility == true) "On" else "Off, optional fallback", trailing = { SecondaryButton("Open", { ctx.startActivity(vm.accessibilityIntent()) }) })
                RowItem("Usage access", if (c?.usageAccess == true) "On" else "Off", trailing = { if (c?.usageAccess != true) SecondaryButton("Open", { ctx.startActivity(vm.usageAccessIntent()) }) })
                RowItem("Notifications", if (c?.notifications == true) "On" else "Off", trailing = { if (c?.notifications != true) SecondaryButton("Open", { ctx.startActivity(vm.notificationIntent()) }) })
            }
        }
        item { SectionLabel("Monitoring") }
        item {
            Panel {
                ToggleRow("Alerts", "Notifications with action buttons", s.alertsEnabled, vm::setAlerts)
                ToggleRow("Live monitor", "Foreground service. Stops 10 minutes after the screen turns off", s.liveMonitor, vm::setLive)
                ToggleRow("Quiet hours", "Mutes non-critical alerts, 22:00 to 07:00", s.quietEnabled, vm::setQuiet)
                Mono("Scan every ${s.scanIntervalMin} min (doubled on the cover screen when unplugged)")
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                    listOf(15, 30, 60, 120).forEach { m -> FilterChip(s.scanIntervalMin == m, { vm.setInterval(m) }, { Text("$m") }) }
                }
                var ram by remember(s.ramThresholdPct) { mutableFloatStateOf(s.ramThresholdPct.toFloat()) }
                Mono("Alert when free memory is below ${ram.toInt()}%")
                Slider(ram, { ram = it }, valueRange = 5f..40f, onValueChangeFinished = { vm.setRam(ram.toInt()) })
                var mb by remember(s.cacheThresholdMb) { mutableFloatStateOf(s.cacheThresholdMb.toFloat()) }
                Mono("Alert when cache passes ${mb.toInt()} MB")
                Slider(mb, { mb = it }, valueRange = 256f..4096f, onValueChangeFinished = { vm.setCacheMb((mb / 64).toInt() * 64) })
            }
        }
        item { SectionLabel("Protected apps") }
        item {
            Panel {
                Muted("Protected apps are never cleaned, restricted, stopped, disabled or uninstalled.")
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                    SecondaryButton("Add app", { scope.launch { picker = vm.installedApps() } })
                    SecondaryButton("Export", { export.launch("foldopti-protected.json") })
                    SecondaryButton("Import", { import.launch(arrayOf("application/json")) })
                }
                ui.suggestions.forEach { (pkg, why) -> RowItem(pkg, "Suggested: $why", trailing = { SecondaryButton("Protect", { vm.addToWhitelist(pkg, why) }) }) }
            }
        }
        items(ui.whitelist, key = { it.pkg }) { a ->
            Panel { RowItem(a.pkg, a.reason, trailing = { if (a.source != ProtectedSource.DEFAULT) SecondaryButton("Remove", { vm.removeFromWhitelist(a) }) else Mono("Default") }) }
        }
        item { SectionLabel("Updates") }
        item {
            Panel {
                RowItem("Version", ui.version)
                ToggleRow("Check automatically", "On launch, at most every 6 hours", s.autoUpdate, vm::setAutoUpdate)
                Muted(updateText(ui.update))
                when (ui.update) {
                    is UpdateState.Available -> PrimaryButton("Download", vm::downloadUpdate, Modifier.fillMaxWidth())
                    is UpdateState.Ready -> PrimaryButton("Install", vm::installUpdate, Modifier.fillMaxWidth())
                    else -> SecondaryButton("Check for updates", vm::checkUpdates, Modifier.fillMaxWidth(), enabled = ui.update != UpdateState.Checking)
                }
            }
        }
        item { SectionLabel("Troubleshooting") }
        item { TroubleshootPanel() }
        item { SectionLabel("Diagnostics") }
        item {
            var log by remember { mutableStateOf(vm.diagnostics()) }
            val clip = androidx.compose.ui.platform.LocalClipboardManager.current
            Panel {
                if (log.isBlank()) Muted("No errors recorded") else {
                    Muted("Last errors, newest at the bottom. Copy this when reporting a problem.")
                    Mono(log.takeLast(1800))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                        SecondaryButton("Copy", { clip.setText(androidx.compose.ui.text.AnnotatedString(log)) })
                        SecondaryButton("Clear", { vm.clearDiagnostics(); log = "" })
                    }
                }
            }
        }
        item {
            var qs by remember { mutableStateOf<String?>(null) }
            Panel {
                Text("Quick Settings test", style = MaterialTheme.typography.titleMedium)
                Muted("Asks the system to open Quick Settings through Shizuku. Use it to tell a missing panel from a missing swipe gesture.")
                SecondaryButton("Open Quick Settings", { scope.launch { qs = vm.openQuickSettings() } }, Modifier.fillMaxWidth(), enabled = c?.shizuku == ShizukuState.READY)
                qs?.let { Muted(it) }
            }
        }
        item { SectionLabel("About") }
        item { Panel { Muted(LICENSES) } }
    }

    picker?.let { apps ->
        AlertDialog(onDismissRequest = { picker = null }, title = { Text("Protect an app") },
            text = { LazyColumn(Modifier.heightIn(max = 400.dp)) { items(apps, key = { it.pkg }) { a -> RowItem(a.label, a.pkg, onClick = { vm.addToWhitelist(a.pkg, "Added by you"); picker = null }) } } },
            confirmButton = { TextButton({ picker = null }) { Text("Close") } })
    }
}

const val LICENSES = "FoldOpti. Design system adapted from unslop by Damir Sokolovsky (@heydamir), licensed CC BY 4.0, github.com/heydamir/unslop. " +
    "Shizuku API by RikkaApps, Apache 2.0. AndroidX, Jetpack Compose, Hilt and Room, Apache 2.0."
