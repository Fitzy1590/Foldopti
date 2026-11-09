package com.fitzy.foldopti.ui.mobile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.window.core.layout.WindowWidthSizeClass
import com.fitzy.foldopti.ui.theme.Space

private enum class Dest(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Filled.Home),
    Optimize("Optimize", Icons.Filled.Refresh),
    Security("Security", Icons.Filled.Lock),
    Settings("Settings", Icons.Filled.Settings),
}

/** Folded/compact: bottom bar. Unfolded (medium+ width): navigation rail. Selection survives fold changes. */
@Composable
fun MobileApp() {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val wide = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass != WindowWidthSizeClass.COMPACT
    val dests = Dest.entries

    if (wide) {
        androidx.compose.foundation.layout.Row(Modifier.fillMaxSize()) {
            NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                dests.forEachIndexed { i, d ->
                    NavigationRailItem(
                        selected = selected == i, onClick = { selected = i },
                        icon = { Icon(d.icon, contentDescription = null) }, label = { Text(d.label) },
                    )
                }
            }
            Placeholder(dests[selected].label, Modifier.fillMaxSize())
        }
    } else {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    dests.forEachIndexed { i, d ->
                        NavigationBarItem(
                            selected = selected == i, onClick = { selected = i },
                            icon = { Icon(d.icon, contentDescription = null) }, label = { Text(d.label) },
                        )
                    }
                }
            },
        ) { pad -> Placeholder(dests[selected].label, Modifier.padding(pad).fillMaxSize()) }
    }
}

@Composable
private fun Placeholder(title: String, modifier: Modifier) {
    Box(modifier.padding(Space.x6), contentAlignment = Alignment.TopStart) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
    }
}
