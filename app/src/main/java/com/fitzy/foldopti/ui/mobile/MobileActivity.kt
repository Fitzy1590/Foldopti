package com.fitzy.foldopti.ui.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fitzy.foldopti.ui.theme.FoldOptiTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MobileActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { FoldOptiTheme { MobileApp() } }
    }
}
