package com.fitzy.foldopti.ui.mobile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitzy.foldopti.core.security.Band
import com.fitzy.foldopti.core.security.Finding
import com.fitzy.foldopti.ui.shared.SecurityViewModel
import com.fitzy.foldopti.ui.theme.Palette
import com.fitzy.foldopti.ui.theme.Space
import com.fitzy.foldopti.ui.theme.StatusDot

fun bandColor(b: Band) = when (b) { Band.CLEAN -> Palette.Positive; Band.REVIEW -> Palette.Warm; Band.SUSPICIOUS -> Palette.Negative }
fun bandLabel(b: Band) = when (b) { Band.CLEAN -> "Clean"; Band.REVIEW -> "Review"; Band.SUSPICIOUS -> "Suspicious" }

@Composable
fun SecurityScreen(wide: Boolean, vm: SecurityViewModel = hiltViewModel()) {
    val findings by vm.findings.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val msg by vm.message.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val flagged = findings.filter { it.band != Band.CLEAN }
    val sel = flagged.firstOrNull { it.pkg == selected } ?: if (wide) flagged.firstOrNull() else null

    val list: @Composable (Modifier) -> Unit = { mod ->
        LazyColumn(mod, contentPadding = PaddingValues(vertical = Space.x6), verticalArrangement = Arrangement.spacedBy(Space.x3)) {
            item { Title("Security") }
            item {
                Panel {
                    Text(if (findings.isEmpty()) "No scan yet" else "${findings.count { it.band == Band.SUSPICIOUS }} suspicious, ${findings.count { it.band == Band.REVIEW }} to review",
                        style = MaterialTheme.typography.titleMedium)
                    Muted("Runs on this device. Nothing is uploaded and nothing is removed for you. Checks permissions, install source, signing certificates and ${vm.signatureCount} known signatures.")
                    if (progress.running) { LinearProgressIndicator(progress = { if (progress.total == 0) 0f else progress.done / progress.total.toFloat() }, Modifier.fillMaxWidth()); Mono("${progress.done} of ${progress.total} apps") }
                    PrimaryButton("Scan now", vm::scan, Modifier.fillMaxWidth(), busy = progress.running)
                    if (vm.canUpdateSignatures) SecondaryButton("Update signature list", vm::updateSignatures, Modifier.fillMaxWidth())
                    msg?.let { Mono(it) }
                }
            }
            items(flagged, key = { it.pkg }) { f ->
                Panel(Modifier.clickableRow { selected = if (selected == f.pkg && !wide) null else f.pkg }) {
                    FindingHeader(f)
                    if (!wide && selected == f.pkg) FindingDetail(f)
                }
            }
        }
    }
    if (wide) Row(Modifier.fillMaxSize().padding(horizontal = Space.x6), horizontalArrangement = Arrangement.spacedBy(Space.x6)) {
        list(Modifier.weight(1f))
        Column(Modifier.weight(1f).padding(vertical = Space.x6 + Space.x12)) { sel?.let { Panel { FindingHeader(it); FindingDetail(it) } } ?: Muted("Select an app to see why it was flagged") }
    } else list(Modifier.fillMaxSize().padding(horizontal = Space.x6))
}

private fun Modifier.clickableRow(onClick: () -> Unit) = this.then(Modifier.clickable(onClick = onClick))

@Composable
private fun FindingHeader(f: Finding) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.x3), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        StatusDot(bandColor(f.band))
        Column(Modifier.weight(1f)) { Text(f.label, style = MaterialTheme.typography.bodyMedium); Mono(f.pkg) }
        Mono("${bandLabel(f.band)}  ${f.score}")
    }
}

@Composable
private fun FindingDetail(f: Finding) {
    f.reasons.forEach { r -> RowItem(r.text, null, trailing = { Mono("+${r.points}") }) }
    Muted("FoldOpti only flags apps. To remove one, use Settings > Apps.")
}
