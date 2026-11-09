package com.fitzy.foldopti.ui.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitzy.foldopti.ui.shared.TroubleshootViewModel
import com.fitzy.foldopti.ui.theme.Palette
import com.fitzy.foldopti.ui.theme.Space

/** Lists disabled packages (system ones included) and apps with background run blocked, with one-tap fixes. */
@Composable
fun TroubleshootPanel(vm: TroubleshootViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    Panel {
        Text("Find disabled apps", style = MaterialTheme.typography.titleMedium)
        Muted("Lists every disabled app, including system apps, and apps blocked from running in the background. Use it when something such as the notification shade or Quick Settings stopped working.")
        PrimaryButton("Scan", vm::scan, Modifier.fillMaxWidth(), busy = ui.busy)
        ui.message?.let { Muted(it) }
        val r = ui.report
        if (r != null) {
            if (r.disabled.isNotEmpty()) {
                Text("Disabled (${r.disabled.size})", style = MaterialTheme.typography.titleMedium)
                r.disabled.forEach { a ->
                    RowItem(a.label, listOfNotNull(a.pkg, a.state, if (a.isSystem) "system app" else null, if (a.suspect) "may affect the notification shade" else null).joinToString("  ·  "), trailing = {
                        if (r.shizukuAvailable) SecondaryButton("Enable", { vm.enable(a.pkg) }) else SecondaryButton("Open", { ctx.startActivity(vm.appInfoIntent(a.pkg)) })
                    })
                }
            }
            if (!r.shizukuAvailable) Muted("Start Shizuku to also check apps blocked from background run and to enable apps in one tap.")
            else if (r.restricted.isNotEmpty()) {
                Text("Background run blocked (${r.restricted.size})", style = MaterialTheme.typography.titleMedium)
                Muted("Some of these may be your own choice from battery settings. Reset only the ones you don't recognise.")
                r.restricted.forEach { (pkg, label) -> RowItem(label, pkg, trailing = { SecondaryButton("Reset", { vm.resetBackground(pkg) }) }) }
            } else if (r.shizukuAvailable) Muted("No apps are blocked from background run.")
        }
    }
}
