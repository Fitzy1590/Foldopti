package com.fitzy.foldopti.core.optimizer

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class DisabledApp(val pkg: String, val label: String, val state: String, val isSystem: Boolean, val suspect: Boolean)
data class DisabledReport(val disabled: List<DisabledApp>, val restricted: List<Pair<String, String>>, val shizukuAvailable: Boolean)

/** Read-only troubleshooting: which packages are disabled, and which have background run blocked. */
@Singleton
class DisabledScanner @Inject constructor(@ApplicationContext private val ctx: Context, private val shell: ShizukuShell) {
    private val pm get() = ctx.packageManager

    suspend fun scan(): DisabledReport = withContext(Dispatchers.IO) {
        @Suppress("DEPRECATION")
        val disabled = pm.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS).mapNotNull { ai ->
            val st = runCatching { pm.getApplicationEnabledSetting(ai.packageName) }.getOrNull() ?: return@mapNotNull null
            val name = when (st) {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> "Disabled"
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER -> "Disabled by user or tool"
                else -> return@mapNotNull null                      // default and "disabled until used" are normal
            }
            DisabledApp(ai.packageName, ai.loadLabel(pm).toString(), name, ai.flags and ApplicationInfo.FLAG_SYSTEM != 0, isShadeRelated(ai.packageName))
        }.sortedWith(compareByDescending<DisabledApp> { it.suspect }.thenByDescending { it.isSystem }.thenBy { it.label.lowercase() })

        val ready = shell.ready
        val restricted = if (!ready) emptyList() else {
            val installed = pm.getInstalledApplications(0).map { it.packageName }.toSet()
            val r = shell.exec("cmd", "appops", "query-op", "RUN_ANY_IN_BACKGROUND", "ignore")
            parseQueryOp(r.out, installed).map { it to (runCatching { pm.getApplicationLabel(pm.getApplicationInfo(it, 0)).toString() }.getOrDefault(it)) }
        }
        DisabledReport(disabled, restricted, ready)
    }

    suspend fun enable(pkg: String): Boolean = shell.ready && shell.exec("pm", "enable", pkg).ok
    suspend fun resetBackground(pkg: String): Boolean = shell.ready && shell.exec("cmd", "appops", "set", pkg, "RUN_ANY_IN_BACKGROUND", "default").ok

    fun appInfoIntent(pkg: String): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    companion object {
        private val keywords = listOf("systemui", "quickpanel", "quickstar", "statusbar", "qstile", "cocktail", "goodlock", "notification", "navstar", "keyguard")
        fun isShadeRelated(pkg: String) = keywords.any { pkg.lowercase().contains(it) }

        /** Package names from `cmd appops query-op <op> <mode>` output. Only names that are installed are kept. */
        fun parseQueryOp(out: String, installed: Set<String>): List<String> =
            out.lineSequence().map { it.trim() }.filter { Regex("^[A-Za-z][\\w.]*\\.[\\w.]+$").matches(it) && it in installed }.distinct().toList()
    }
}
