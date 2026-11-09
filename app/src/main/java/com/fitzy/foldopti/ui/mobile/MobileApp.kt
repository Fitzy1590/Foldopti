package com.fitzy.foldopti.ui.mobile

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowWidthSizeClass
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo

private enum class Dest(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Filled.Home), Optimize("Optimize", Icons.Filled.Refresh),
    Security("Security", Icons.Filled.Lock), Settings("Settings", Icons.Filled.Settings),
}

private tailrec fun Context.activity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.activity(); else -> null }

/** True when the device is half-open with a horizontal hinge (tabletop). */
@Composable
fun rememberTabletop(): Boolean {
    val activity = LocalContext.current.activity() ?: return false
    val info: WindowLayoutInfo? by remember(activity) { WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity) }
        .collectAsStateWithLifecycle(null)
    return (info?.displayFeatures ?: emptyList()).filterIsInstance<FoldingFeature>()
        .any { it.state == FoldingFeature.State.HALF_OPENED && it.orientation == FoldingFeature.Orientation.HORIZONTAL }
}

/** Folded (compact): bottom bar. Unfolded (medium+): navigation rail and two-pane screens. Selection survives fold changes. */
@Composable
fun MobileApp() {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val wide = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass != WindowWidthSizeClass.COMPACT
    val tabletop = rememberTabletop()
    val dests = Dest.entries

    val content: @Composable (Modifier) -> Unit = { mod ->
        androidx.compose.foundation.layout.Box(mod) {
            when (dests[selected]) {
                Dest.Home -> HomeScreen(wide, tabletop)
                Dest.Optimize -> OptimizeScreen(wide)
                Dest.Security -> SecurityScreen(wide)
                Dest.Settings -> SettingsScreen()
            }
        }
    }

    if (wide) {
        Row(Modifier.fillMaxSize()) {
            NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                dests.forEachIndexed { i, d ->
                    NavigationRailItem(selected == i, { selected = i }, { Icon(d.icon, contentDescription = null) }, label = { Text(d.label) })
                }
            }
            content(Modifier.fillMaxSize())
        }
    } else {
        Scaffold(containerColor = MaterialTheme.colorScheme.background, bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                dests.forEachIndexed { i, d -> NavigationBarItem(selected == i, { selected = i }, { Icon(d.icon, contentDescription = null) }, label = { Text(d.label) }) }
            }
        }) { pad -> content(Modifier.padding(pad).fillMaxSize()) }
    }
}
