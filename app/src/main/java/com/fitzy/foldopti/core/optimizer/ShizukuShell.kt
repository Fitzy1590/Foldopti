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

    suspend fun exec(vararg cmd: String, stdin: InputStream? = null, timeoutMs: Long = 20_000): ShellResult = withContext(Dispatchers.IO) {
        if (!ready) return@withContext ShellResult(-1, "", "Shizuku not ready")
        var proc: Process? = null
        try {
            val p = newProcess.invoke(null, cmd, null, null) as Process
            proc = p
            coroutineScope {
                val out = async { p.inputStream.bufferedReader().readText() }
                val err = async { p.errorStream.bufferedReader().readText() }
                if (stdin != null) p.outputStream.use { stdin.copyTo(it) } else p.outputStream.close()
                if (!p.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    p.destroy(); ShellResult(-2, "", "Timed out after ${timeoutMs / 1000}s: ${cmd.joinToString(" ")}")
                } else ShellResult(p.exitValue(), out.await(), err.await())
            }
        } catch (t: Throwable) { runCatching { proc?.destroy() }; ShellResult(-1, "", t.message ?: t.javaClass.simpleName) }
    }
}
