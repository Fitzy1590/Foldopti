package com.fitzy.foldopti.core.scheduler

import android.content.Context
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThermalMonitor @Inject constructor(@ApplicationContext ctx: Context) {
    private val pm = ctx.getSystemService(PowerManager::class.java)
    private val _status = MutableStateFlow(pm.currentThermalStatus)
    val status: StateFlow<Int> = _status.asStateFlow()
    private val listener = PowerManager.OnThermalStatusChangedListener { _status.value = it }
    private val executor = Executors.newSingleThreadExecutor()
    private var registered = false

    fun current(): Int = pm.currentThermalStatus.also { _status.value = it }

    /** 0..1+ where 1.0 means severe throttling is expected. Null when unsupported. */
    fun headroom(secondsAhead: Int = 10): Float? =
        runCatching { pm.getThermalHeadroom(secondsAhead) }.getOrNull()?.takeIf { !it.isNaN() }

    @Synchronized fun start() { if (!registered) { pm.addThermalStatusListener(executor, listener); registered = true } }
    @Synchronized fun stop() { if (registered) { pm.removeThermalStatusListener(listener); registered = false } }

    companion object {
        fun label(status: Int) = when {
            status >= PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"
            status == PowerManager.THERMAL_STATUS_SEVERE -> "Hot"
            status == PowerManager.THERMAL_STATUS_MODERATE -> "Warm"
            status == PowerManager.THERMAL_STATUS_LIGHT -> "Slightly warm"
            else -> "Normal"
        }
    }
}
