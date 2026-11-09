package com.fitzy.foldopti.core.optimizer

import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

data class ShellResult(val code: Int, val out: String, val err: String) { val ok get() = code == 0 }

enum class ShizukuState { MISSING, NOT_RUNNING, NO_PERMISSION, READY }

/**
 * Runs shell-uid commands through Shizuku. Binders are fetched per call, never retained.
 * [verify] Shizuku.newProcess is private in API 13.x; reached by reflection, the way other Shizuku apps do.
 */
@Singleton
class ShizukuShell @Inject constructor() {
    private val _state = MutableStateFlow(compute())
    val state: StateFlow<ShizukuState> = _state.asStateFlow()

    private val listenersAdded = java.util.concurrent.atomic.AtomicBoolean(false)

    fun start() {
        if (!listenersAdded.compareAndSet(false, true)) return
        Shizuku.addBinderReceivedListenerSticky { _state.value = compute() }
        Shizuku.addBinderDeadListener { _state.value = compute() }
        Shizuku.addRequestPermissionResultListener { _, _ -> _state.value = compute() }
    }

    fun refresh() { _state.value = compute() }

    private fun compute(): ShizukuState = try {
        when {
            !Shizuku.pingBinder() -> ShizukuState.NOT_RUNNING
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> ShizukuState.NO_PERMISSION
            else -> ShizukuState.READY
        }
    } catch (_: Throwable) { ShizukuState.NOT_RUNNING }

    fun requestPermission() { runCatching { if (Shizuku.pingBinder()) Shizuku.requestPermission(4242) } }

    val ready: Boolean get() = compute() == ShizukuState.READY

    private val newProcess by lazy {
        Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
            .apply { isAccessible = true }
    }

    /**
     * Runs one command. Output is read on daemon threads and the caller polls, so a stuck process can never block
     * the caller past [timeoutMs] and cancelling the coroutine destroys the process.
     */
    suspend fun exec(vararg cmd: String, stdin: InputStream? = null, timeoutMs: Long = 15_000): ShellResult = withContext(Dispatchers.IO) {
        if (!ready) return@withContext ShellResult(-1, "", "Shizuku not ready")
        val p = try { newProcess.invoke(null, cmd, null, null) as Process } catch (t: Throwable) {
            return@withContext ShellResult(-1, "", (t.cause ?: t).let { it.message ?: it.javaClass.simpleName })
        }
        val out = StringBuffer(); val err = StringBuffer()
        fun reader(src: () -> InputStream, into: StringBuffer) = kotlin.concurrent.thread(isDaemon = true) {
            runCatching { src().bufferedReader().use { r -> val b = CharArray(4096); while (true) { val n = r.read(b); if (n < 0) break; into.append(b, 0, n) } } }
        }
        val tOut = reader({ p.inputStream }, out); val tErr = reader({ p.errorStream }, err)
        try {
            runCatching { if (stdin != null) p.outputStream.use { stdin.copyTo(it) } else p.outputStream.close() }
            val start = android.os.SystemClock.elapsedRealtime()
            while (tOut.isAlive || tErr.isAlive) {
                if (android.os.SystemClock.elapsedRealtime() - start > timeoutMs)
                    return@withContext ShellResult(-2, out.toString(), "Timed out after ${timeoutMs / 1000}s: ${cmd.joinToString(" ")}")
                kotlinx.coroutines.delay(30)
            }
            ShellResult(runCatching { p.waitFor() }.getOrDefault(-1), out.toString(), err.toString())
        } finally { runCatching { p.destroy() } }
    }

    /** Runs a few harmless commands and reports what happened and how long each took. */
    suspend fun selfTest(ownPackage: String): String {
        val sb = StringBuilder("Shizuku state: ${compute()}\n")
        for (cmd in listOf(listOf("id"), listOf("getprop", "ro.build.version.sdk"), listOf("pm", "clear", "--cache-only", ownPackage))) {
            val t0 = android.os.SystemClock.elapsedRealtime()
            val r = exec(*cmd.toTypedArray(), timeoutMs = 10_000)
            sb.append("${cmd.joinToString(" ")}\n  exit=${r.code}  ${android.os.SystemClock.elapsedRealtime() - t0} ms\n  out=${r.out.trim().take(160)}\n  err=${r.err.trim().take(160)}\n")
        }
        return sb.toString().trim()
    }
}
