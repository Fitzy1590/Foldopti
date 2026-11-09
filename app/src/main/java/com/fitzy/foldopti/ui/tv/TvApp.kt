package com.fitzy.foldopti.ui.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

/** Shell only. Drawer navigation with focusRestorer() lands in Stage 10. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvApp() {
    Surface(Modifier.fillMaxSize()) {
        // Overscan-safe margins: 48dp horizontal, 27dp vertical.
        Box(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 27.dp)) {
            Column { Text("Home", style = MaterialTheme.typography.headlineMedium) }
        }
    }
}
