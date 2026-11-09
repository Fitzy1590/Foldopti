package com.fitzy.foldopti.core.system

import android.content.Context
import android.util.Log
import java.io.File
import java.util.Date

/** On-device error log so crashes can be read in Settings > Diagnostics without adb. */
object CrashLog {
    private const val FILE = "diagnostics.txt"
    private const val MAX = 24_000
    @Volatile private var app: Context? = null

    fun install(ctx: Context) {
        app = ctx.applicationContext
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e -> write("CRASH", t.name, e); prev?.uncaughtException(t, e) }
    }

    /** Non-fatal errors caught by the app. */
    fun record(where: String, e: Throwable) = write("ERROR", where, e)

    private fun write(kind: String, where: String, e: Throwable) {
        Log.e("FoldOpti", "$kind in $where", e)
        val ctx = app ?: return
        runCatching {
            val f = File(ctx.filesDir, FILE)
            val old = if (f.exists()) f.readText() else ""
            f.writeText((old + "--- ${Date()}  $kind  $where\n${Log.getStackTraceString(e)}\n").takeLast(MAX))
        }
    }

    fun read(ctx: Context): String = runCatching { File(ctx.filesDir, FILE).takeIf { it.exists() }?.readText() }.getOrNull().orEmpty()
    fun clear(ctx: Context) { runCatching { File(ctx.filesDir, FILE).delete() } }
}
