package com.fitzy.foldopti.ui.mobile

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
import com.fitzy.foldopti.core.optimizer.DormantApp
import com.fitzy.foldopti.core.optimizer.DormantTier
import com.fitzy.foldopti.core.optimizer.TweakKind
import com.fitzy.foldopti.ui.shared.OptimizeUi
import com.fitzy.foldopti.ui.shared.OptimizeViewModel
import com.fitzy.foldopti.ui.shared.fmtBytes
import com.fitzy.foldopti.ui.theme.Space

@Composable
fun OptimizeScreen(wide: Boolean, vm: OptimizeViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.load() }
    var confirm by remember { mutableStateOf<DormantApp?>(null) }
    val progress by vm.progress.collectAsStateWithLifecycle()

    val left: LazyListScope.() -> Unit = {
        item { Title("Optimize") }
        item { UsageHint(ui) }
        item { CachePanel(ui, vm::clean) }
        item { BoostPanel(vm::boost, ui.busy) }
        if (ui.busy && progress != null) item { Panel { ProgressBlock(progress, vm::cancel) } }
        ui.message?.let { m -> item { Panel { Muted(m) } } }
        item { SectionLabel("Developer tweaks") }
        items(ui.tweaks, key = { it.id }) { t ->
            Panel {
                RowItem(t.title, "Now ${t.current}  ·  Suggested ${t.recommended}", trailing = {
                    when {
                        t.canRevert -> SecondaryButton("Revert", { vm.revertTweak(t.id) })
                        t.canApply -> PrimaryButton("Apply", { vm.applyTweak(t.id) })
                        t.kind == TweakKind.ADVISORY && !t.isOk || t.id == "bg_limit" || t.id == "ram_plus" ->
                            SecondaryButton("Open", { ctx.startActivity(vm.tweakIntent(t.id)) })
                        else -> Mono(if (t.isOk) "Good" else "")
                    }
                })
                Muted(t.hint)
            }
        }
    }
    val right: LazyListScope.() -> Unit = {
        if (wide) item { Spacer(Modifier.height(Space.x12)) }
        item { SectionLabel("Unused apps") }
        if (ui.dormant.isEmpty()) item { Muted(if (ui.loaded) "No unused apps found, or usage access is off" else "Scanning") }
        DormantTier.entries.forEach { tier ->
            val apps = ui.dormant.filter { it.tier == tier }
            if (apps.isNotEmpty()) {
                item { Mono("${tier.days}+ days idle  ·  ${apps.size}") }
                items(apps, key = { it.pkg }) { a ->
                    Panel {
                        RowItem(a.label, "${a.daysIdle} days  ·  ${a.pkg}")
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                            if (a.isProtected) Mono("Protected") else {
                                SecondaryButton("Disable", { confirm = a })
                                if (!a.isSystem) SecondaryButton("Uninstall", { ctx.startActivity(vm.uninstallIntent(a.pkg)) })
                            }
                        }
                    }
                }
            }
        }
        if (ui.disabled.isNotEmpty()) {
            item { SectionLabel("Disabled apps") }
            items(ui.disabled, key = { it.first }) { (pkg, label) -> Panel { RowItem(label, pkg, trailing = { SecondaryButton("Enable", { vm.enable(pkg) }) }) } }
        }
    }

    if (wide) Row(Modifier.fillMaxSize().padding(horizontal = Space.x6), horizontalArrangement = Arrangement.spacedBy(Space.x6)) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = Space.x6), verticalArrangement = Arrangement.spacedBy(Space.x3), content = left)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = Space.x6), verticalArrangement = Arrangement.spacedBy(Space.x3), content = right)
    } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(Space.x6), verticalArrangement = Arrangement.spacedBy(Space.x3)) { left(); right() }

    confirm?.let { a ->
        AlertDialog(onDismissRequest = { confirm = null },
            title = { Text("Disable ${a.label}?") },
            text = { Text("The app stops running and disappears from the launcher. You can enable it again from Disabled apps.") },
            confirmButton = { TextButton({ vm.disable(a.pkg); confirm = null }) { Text("Disable") } },
            dismissButton = { TextButton({ confirm = null }) { Text("Cancel") } })
    }
}

@Composable
private fun UsageHint(ui: OptimizeUi) {
    val ctx = LocalContext.current
    if (ui.caps != null && !ui.caps.usageAccess) Panel {
        Text("Usage access is off", style = MaterialTheme.typography.titleMedium)
        Muted("Needed to measure cache and find unused apps.")
        SecondaryButton("Open usage access", { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) })
    }
}

@Composable
private fun CachePanel(ui: OptimizeUi, clean: (String?) -> Unit) {
    Panel {
        val total = ui.caches.filter { !it.isProtected }.sumOf { it.cacheBytes }
        Text("Cache", style = MaterialTheme.typography.titleMedium)
        Text(fmtBytes(total), style = MaterialTheme.typography.headlineMedium)
        Mono("${ui.caches.count { !it.isProtected }} apps, ${ui.caches.count { it.isProtected }} protected")
        ui.caches.take(8).forEach { c -> RowItem(c.label, if (c.isProtected) "Protected" else null, trailing = { Mono(fmtBytes(c.cacheBytes)) }) }
        PrimaryButton("Clear cache", { clean(null) }, Modifier.fillMaxWidth(), enabled = total > 0, busy = ui.busy)
    }
}

@Composable
private fun BoostPanel(boost: () -> Unit, busy: Boolean) {
    Panel {
        Text("Safe Boost", style = MaterialTheme.typography.titleMedium)
        Muted("Limits idle background apps. It skips media players, services in use, your keyboard and launcher, and protected apps. Nothing is force-stopped.")
        SecondaryButton("Run Safe Boost", boost, Modifier.fillMaxWidth(), enabled = !busy)
    }
}
