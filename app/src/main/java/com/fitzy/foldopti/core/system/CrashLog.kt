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
        logPreviousExits(app!!)
    }

    private fun reasonName(r: Int) = when (r) {
        android.app.ApplicationExitInfo.REASON_CRASH -> "CRASH"; android.app.ApplicationExitInfo.REASON_CRASH_NATIVE -> "NATIVE_CRASH"
        android.app.ApplicationExitInfo.REASON_ANR -> "ANR"; android.app.ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
        android.app.ApplicationExitInfo.REASON_SIGNALED -> "KILLED_BY_SIGNAL"; android.app.ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
        android.app.ApplicationExitInfo.REASON_USER_STOPPED -> "FORCE_STOPPED"; android.app.ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCES"
        android.app.ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INIT_FAILURE"; android.app.ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
        android.app.ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"; android.app.ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
        else -> "OTHER($r)"
    }

    /** Records how the previous run ended (ANR, out-of-memory kill, native crash, force-stop). Java crash handlers never see these. */
    private fun logPreviousExits(ctx: Context) = runCatching {
        val prefs = ctx.getSharedPreferences("crashlog", Context.MODE_PRIVATE)
        val seen = prefs.getLong("last_exit_ts", 0L)
        val am = ctx.getSystemService(android.app.ActivityManager::class.java)
        val exits = am.getHistoricalProcessExitReasons(ctx.packageName, 0, 5).filter { it.timestamp > seen }
        if (exits.isEmpty()) return@runCatching
        val f = File(ctx.filesDir, FILE)
        val lines = exits.reversed().joinToString("") { "--- ${Date(it.timestamp)}  PREVIOUS EXIT  ${reasonName(it.reason)}  ${it.processName}\n${it.description.orEmpty()}\n" }
        f.writeText(((if (f.exists()) f.readText() else "") + lines).takeLast(MAX))
        prefs.edit().putLong("last_exit_ts", exits.maxOf { it.timestamp }).apply()
    }

    /** Plain diagnostic line (no stack trace). */
    fun note(msg: String) { Log.w("FoldOpti", msg); val ctx = app ?: return; runCatching { val f = File(ctx.filesDir, FILE); f.writeText(((if (f.exists()) f.readText() else "") + "--- ${Date()}  NOTE  $msg\n").takeLast(MAX)) } }

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
