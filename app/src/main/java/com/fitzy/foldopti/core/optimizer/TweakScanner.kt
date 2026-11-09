package com.fitzy.foldopti.core.optimizer

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.fitzy.foldopti.core.system.Access
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class TweakKind { FLOAT, ADVISORY }

data class Tweak(
    val id: String, val title: String, val current: String, val recommended: String,
    val isOk: Boolean, val kind: TweakKind, val hint: String, val canApply: Boolean, val canRevert: Boolean,
)

@Singleton
class TweakScanner @Inject constructor(
    @ApplicationContext private val ctx: Context, private val shell: ShizukuShell, private val access: Access,
) {
    private val prefs = ctx.getSharedPreferences("tweak_prev", Context.MODE_PRIVATE)
    private val floats = listOf(
        Triple(Settings.Global.WINDOW_ANIMATION_SCALE, "Window animation scale", "window_animation_scale"),
        Triple(Settings.Global.TRANSITION_ANIMATION_SCALE, "Transition animation scale", "transition_animation_scale"),
        Triple(Settings.Global.ANIMATOR_DURATION_SCALE, "Animator duration scale", "animator_duration_scale"),
    )
    private val canWrite get() = access.canWriteSecureSettings() || shell.ready

    fun read(): List<Tweak> {
        val out = mutableListOf<Tweak>()
        for ((key, title, _) in floats) {
            val v = Settings.Global.getFloat(ctx.contentResolver, key, 1f)
            out += Tweak(key, title, fmt(v), "0.5x", v <= 0.5f, TweakKind.FLOAT,
                "Shorter animations feel faster. Scale 0.5x is a common choice.", canWrite && v > 0.5f, prefs.contains(key))
        }
        val standby = Settings.Global.getInt(ctx.contentResolver, "app_standby_enabled", 1)
        out += Tweak("app_standby", "App standby", if (standby == 1) "On" else "Off", "On", standby == 1, TweakKind.ADVISORY,
            "Standby limits background work for apps you rarely open. Keep it on.", false, false)
        val stay = Settings.Global.getInt(ctx.contentResolver, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, 0)
        out += Tweak("stay_awake", "Stay awake while charging", if (stay == 0) "Off" else "On", "Off", stay == 0, TweakKind.ADVISORY,
            "Leaving the screen on while charging adds heat and wear.", false, false)
        out += Tweak("bg_limit", "Background process limit", "See Developer options", "Standard limit", true, TweakKind.ADVISORY,
            "Lowering the limit can hurt messaging and sync. Review in Developer options.", false, false)
        out += Tweak("ram_plus", "RAM Plus", "See Device care", "Review", true, TweakKind.ADVISORY,
            "Samsung only. Virtual RAM uses storage and can slow heavy apps. Check it in Device care.", false, false)
        return out
    }

    private fun fmt(v: Float) = if (v == 0f) "Off" else "${"%.2g".format(v)}x"

    suspend fun apply(id: String): Boolean {
        val (key, _, shellKey) = floats.firstOrNull { it.first == id } ?: return false
        val prev = Settings.Global.getFloat(ctx.contentResolver, key, 1f)
        val ok = write(key, shellKey, 0.5f)
        if (ok) prefs.edit().putFloat(id, prev).apply()
        return ok
    }

    suspend fun revert(id: String): Boolean {
        val (key, _, shellKey) = floats.firstOrNull { it.first == id } ?: return false
        if (!prefs.contains(id)) return false
        val ok = write(key, shellKey, prefs.getFloat(id, 1f))
        if (ok) prefs.edit().remove(id).apply()
        return ok
    }

    private suspend fun write(key: String, shellKey: String, v: Float): Boolean =
        if (access.canWriteSecureSettings()) runCatching { Settings.Global.putFloat(ctx.contentResolver, key, v) }.getOrDefault(false)
        else shell.exec("settings", "put", "global", shellKey, v.toString()).ok

    /** Opens the most relevant system screen for advisory items. */
    fun deepLink(id: String): Intent {
        val i = when (id) {
            "ram_plus" -> ctx.packageManager.getLaunchIntentForPackage("com.samsung.android.lool") ?: Intent(Settings.ACTION_SETTINGS)
            else -> Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        }
        return i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
