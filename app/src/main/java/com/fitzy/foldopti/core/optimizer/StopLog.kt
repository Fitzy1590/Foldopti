package com.fitzy.foldopti.core.optimizer

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Remembers when the optimizer acted on each package (cooldown and loop guard). */
@Singleton
class StopLog @Inject constructor(@ApplicationContext ctx: Context) {
    private val prefs = ctx.getSharedPreferences("stop_log", Context.MODE_PRIVATE)

    fun history(pkg: String, now: Long = System.currentTimeMillis()): List<Long> =
        (prefs.getString(pkg, "") ?: "").split(',').mapNotNull { it.toLongOrNull() }
            .filter { now - it < BoostPolicy.LOOP_WINDOW_MS }

    fun record(pkg: String, now: Long = System.currentTimeMillis()) {
        prefs.edit().putString(pkg, (history(pkg, now) + now).joinToString(",")).apply()
    }
}
