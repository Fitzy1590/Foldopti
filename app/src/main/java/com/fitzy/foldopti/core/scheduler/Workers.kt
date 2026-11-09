package com.fitzy.foldopti.core.scheduler

import android.content.Context
import android.os.PowerManager
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.fitzy.foldopti.core.notifications.AlertManager
import com.fitzy.foldopti.core.notifications.AlertSpec
import com.fitzy.foldopti.core.notifications.AlertType
import com.fitzy.foldopti.core.security.Band
import com.fitzy.foldopti.core.security.SecurityRepository
import com.fitzy.foldopti.core.settings.SettingsStore
import com.fitzy.foldopti.core.update.UpdateRepository
import com.fitzy.foldopti.core.update.UpdateState
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@HiltWorker
class ScanWorker @AssistedInject constructor(
    @Assisted ctx: Context, @Assisted p: WorkerParameters, private val engine: ScanEngine, private val thermal: ThermalMonitor,
    private val settings: SettingsStore, private val device: DeviceState,
) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        if (thermal.current() >= PowerManager.THERMAL_STATUS_SEVERE) return Result.success()   // do not add heat
        val s = settings.current()
        val eff = IntervalPolicy.effectiveMinutes(s.scanIntervalMin, device.isCoverScreen, device.isCharging)
        if (System.currentTimeMillis() - s.lastScanAt < (eff - 2) * 60_000L) return Result.success()
        engine.run(measureCache = device.isCharging || !device.isCoverScreen)
        return Result.success()
    }
}

/** Daily, while charging: full cache measure and malware scan. */
@HiltWorker
class HeavyScanWorker @AssistedInject constructor(
    @Assisted ctx: Context, @Assisted p: WorkerParameters, private val engine: ScanEngine,
    private val security: SecurityRepository, private val alerts: AlertManager, private val device: DeviceState,
) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        if (device.batteryPercent in 0 until IntervalPolicy.HEAVY_MIN_BATTERY_PCT) return Result.retry()
        engine.run(measureCache = true)
        val findings = security.scan()
        val bad = findings.count { it.band == Band.SUSPICIOUS }; val review = findings.count { it.band == Band.REVIEW }
        if (bad + review > 0) alerts.post(AlertSpec(AlertType.SCAN_RESULT, "Security scan finished", "$bad suspicious, $review to review"))
        return Result.success()
    }
}

@HiltWorker
class UpdateWorker @AssistedInject constructor(
    @Assisted ctx: Context, @Assisted p: WorkerParameters, private val updates: UpdateRepository,
    private val alerts: AlertManager, private val settings: SettingsStore,
) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        if (!settings.current().autoUpdate) return Result.success()
        updates.autoCheck(force = true)
        (updates.state.value as? UpdateState.Available)?.let {
            alerts.post(AlertSpec(AlertType.UPDATE, "FoldOpti ${it.release.version} is available", "Open FoldOpti and go to Settings to install"))
        }
        return Result.success()
    }
}

@Singleton
class Scheduler @Inject constructor(@ApplicationContext private val ctx: Context) {
    fun ensureScheduled() {
        val wm = WorkManager.getInstance(ctx)
        wm.enqueueUniquePeriodicWork("scan", ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<ScanWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build()).build())
        wm.enqueueUniquePeriodicWork("heavy", ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<HeavyScanWorker>(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiresCharging(true).setRequiresBatteryNotLow(true).build()).build())
        wm.enqueueUniquePeriodicWork("update", ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<UpdateWorker>(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).setRequiresBatteryNotLow(true).build()).build())
    }
}
