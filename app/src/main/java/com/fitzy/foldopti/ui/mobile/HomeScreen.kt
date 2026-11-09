package com.fitzy.foldopti.ui.mobile

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitzy.foldopti.core.optimizer.Tier
import com.fitzy.foldopti.core.scheduler.ThermalMonitor
import com.fitzy.foldopti.ui.shared.HomeViewModel
import com.fitzy.foldopti.ui.shared.fmtBytes
import com.fitzy.foldopti.ui.theme.*

@Composable
fun HomeScreen(wide: Boolean, tabletop: Boolean, vm: HomeViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshCaps() }
    LaunchedEffect(Unit) { if (s.summary.at == 0L) vm.refresh() }
    val sum = s.summary
    val accent = MaterialTheme.colorScheme.primary

    val gauge: @Composable () -> Unit = {
        Panel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                StatusDot(Thermal.color(s.thermal, accent)); Text(Build.MODEL, style = MaterialTheme.typography.titleMedium)
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ThermalGauge(s.thermal, s.headroom, accent, MaterialTheme.colorScheme.surfaceVariant)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(ThermalMonitor.label(s.thermal), style = MaterialTheme.typography.headlineMedium)
                    Mono(s.headroom?.let { "headroom ${(it * 100).toInt()}%" } ?: "thermal status")
                }
            }
        }
    }
    val actions: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(Space.x3)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                Stat("Free memory", if (sum.ramAvailPct >= 0) "${sum.ramAvailPct}%" else "n/a", "of ${sum.ramTotalMb} MB", Modifier.weight(1f))
                Stat("Cache", sum.cacheBytes?.let(::fmtBytes) ?: "n/a", if (sum.cacheBytes == null) "Usage access needed" else "in unprotected apps", Modifier.weight(1f))
            }
            PrimaryButton("Smart Clean", vm::smartClean, Modifier.fillMaxWidth(), busy = s.busy)
            s.message?.let { Muted(it) }
            when (s.caps?.tier) {
                Tier.T2 -> Mono("Mode: full (Shizuku)")
                Tier.T1 -> Mono("Mode: accessibility, runs only while the screen is on")
                else -> Panel { Text("Limited mode", style = MaterialTheme.typography.titleMedium); Muted("FoldOpti can measure and alert. Turn on Shizuku in Settings to clean and restrict apps.") }
            }
            if (sum.at > 0) Mono("Last scan ${java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(sum.at)}")
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.x6), verticalArrangement = Arrangement.spacedBy(Space.x5)) {
        Title("Home")
        when {
            tabletop -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.x8)) { gauge(); actions() }
            wide -> Row(horizontalArrangement = Arrangement.spacedBy(Space.x6)) { Box(Modifier.weight(1f)) { gauge() }; Box(Modifier.weight(1f)) { actions() } }
            else -> { gauge(); actions() }
        }
    }
}
