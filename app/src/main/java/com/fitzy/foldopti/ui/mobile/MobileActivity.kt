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
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        setContent { FoldOptiTheme { MobileApp() } }
    }
}
