package com.fitzy.foldopti.core.scheduler

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.fitzy.foldopti.R
import com.fitzy.foldopti.core.notifications.AlertAction
import com.fitzy.foldopti.core.notifications.AlertManager
import com.fitzy.foldopti.core.notifications.AlertSpec
import com.fitzy.foldopti.core.notifications.AlertType
import com.fitzy.foldopti.core.notifications.Channels
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

/** Optional live monitor. Foreground, specialUse. Stops 10 minutes after the screen turns off. */
@AndroidEntryPoint
class MonitorService : Service() {
    @Inject lateinit var thermal: ThermalMonitor
    @Inject lateinit var alerts: AlertManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val handler = Handler(Looper.getMainLooper())
    private val policy = ThermalPolicy()
    private val stopRunnable = Runnable { stopSelf() }
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.action == Intent.ACTION_SCREEN_OFF) handler.postDelayed(stopRunnable, 10 * 60_000L) else handler.removeCallbacks(stopRunnable)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Channels.create(this)
        val n = NotificationCompat.Builder(this, Channels.MONITOR).setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("FoldOpti is monitoring").setContentText("Thermals and memory").setOngoing(true).build()
        if (android.os.Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(1, n)
        registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF).apply { addAction(Intent.ACTION_SCREEN_ON) })
        thermal.start()
        scope.launch {
            while (isActive) {
                val s = thermal.current()
                when (policy.update(s, System.currentTimeMillis())) {
                    ThermalEvent.INFO -> alerts.post(AlertSpec(AlertType.OVERHEAT_INFO, "Device is warm", "Thermal status: ${ThermalMonitor.label(s)}", actions = listOf(AlertAction.COOL_DOWN, AlertAction.SNOOZE)))
                    ThermalEvent.CRITICAL -> alerts.post(AlertSpec(AlertType.OVERHEAT_CRITICAL, "Device is overheating", "Thermal status: ${ThermalMonitor.label(s)}", actions = listOf(AlertAction.COOL_DOWN, AlertAction.SNOOZE)))
                    else -> Unit
                }
                delay(if (s >= 2) 15_000L else 30_000L)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel(); thermal.stop(); handler.removeCallbacks(stopRunnable)
        runCatching { unregisterReceiver(screenReceiver) }
        super.onDestroy()
    }

    companion object {
        fun start(ctx: Context) = ctx.startForegroundService(Intent(ctx, MonitorService::class.java))
        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, MonitorService::class.java)) }
    }
}
