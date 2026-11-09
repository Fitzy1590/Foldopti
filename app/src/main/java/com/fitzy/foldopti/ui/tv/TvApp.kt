@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.fitzy.foldopti.ui.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*

private enum class TvDest(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Filled.Home), Clean("Clean", Icons.Filled.Refresh), Apps("Apps", Icons.Filled.Delete),
    Security("Security", Icons.Filled.Lock), Settings("Settings", Icons.Filled.Settings),
}

/** Drawer navigation, D-pad only. Content keeps overscan-safe margins (48dp horizontal, 27dp vertical). */
@Composable
fun TvApp() {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    Surface(Modifier.fillMaxSize()) {
        NavigationDrawer(drawerContent = { value ->
            androidx.compose.foundation.layout.Column(Modifier.padding(12.dp)) {
                TvDest.entries.forEachIndexed { i, d ->
                    NavigationDrawerItem(selected = selected == i, onClick = { selected = i }, leadingContent = { Icon(d.icon, contentDescription = null) }) {
                        Text(d.label)
                    }
                }
            }
        }) {
            Box(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 27.dp)) {
                when (TvDest.entries[selected]) {
                    TvDest.Home -> TvHome(); TvDest.Clean -> TvClean(); TvDest.Apps -> TvApps()
                    TvDest.Security -> TvSecurity(); TvDest.Settings -> TvSettings()
                }
            }
        }
    }
}
