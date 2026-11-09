package com.fitzy.foldopti.core.settings

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.store by preferencesDataStore("settings")

data class AppSettings(
    val autoUpdate: Boolean = true,
    val lastUpdateCheck: Long = 0L,
    val alertsEnabled: Boolean = true,
    val scanIntervalMin: Int = 30,
    val quietEnabled: Boolean = false,
    val quietStartMin: Int = 22 * 60,
    val quietEndMin: Int = 7 * 60,
    val ramThresholdPct: Int = 15,
    val cacheThresholdMb: Int = 1024,
    val liveMonitor: Boolean = false,
    val seeded: Boolean = false,
    val lastScanAt: Long = 0L,
    val experimentalCacheClear: Boolean = false,
)

@Singleton
class SettingsStore @Inject constructor(@ApplicationContext private val ctx: Context) {
    private object K {
        val autoUpdate = booleanPreferencesKey("auto_update")
        val lastUpdateCheck = longPreferencesKey("last_update_check")
        val alerts = booleanPreferencesKey("alerts")
        val interval = intPreferencesKey("interval")
        val quiet = booleanPreferencesKey("quiet")
        val quietStart = intPreferencesKey("quiet_start")
        val quietEnd = intPreferencesKey("quiet_end")
        val ram = intPreferencesKey("ram_pct")
        val cache = intPreferencesKey("cache_mb")
        val live = booleanPreferencesKey("live")
        val seeded = booleanPreferencesKey("seeded")
        val lastScan = longPreferencesKey("last_scan")
        val expCache = booleanPreferencesKey("exp_cache")
        val snoozes = stringSetPreferencesKey("snoozes")
        val never = stringSetPreferencesKey("never_alert")
    }

    val settings: Flow<AppSettings> = ctx.store.data.map { p ->
        val d = AppSettings()
        AppSettings(
            autoUpdate = p[K.autoUpdate] ?: d.autoUpdate, lastUpdateCheck = p[K.lastUpdateCheck] ?: 0L,
            alertsEnabled = p[K.alerts] ?: d.alertsEnabled, scanIntervalMin = p[K.interval] ?: d.scanIntervalMin,
            quietEnabled = p[K.quiet] ?: d.quietEnabled, quietStartMin = p[K.quietStart] ?: d.quietStartMin,
            quietEndMin = p[K.quietEnd] ?: d.quietEndMin, ramThresholdPct = p[K.ram] ?: d.ramThresholdPct,
            cacheThresholdMb = p[K.cache] ?: d.cacheThresholdMb, liveMonitor = p[K.live] ?: false,
            seeded = p[K.seeded] ?: false, lastScanAt = p[K.lastScan] ?: 0L, experimentalCacheClear = p[K.expCache] ?: false,
        )
    }

    suspend fun current(): AppSettings = settings.first()
    private suspend fun <T> set(k: Preferences.Key<T>, v: T) { ctx.store.edit { it[k] = v } }

    suspend fun setAutoUpdate(v: Boolean) = set(K.autoUpdate, v)
    suspend fun setLastUpdateCheck(v: Long) = set(K.lastUpdateCheck, v)
    suspend fun setAlertsEnabled(v: Boolean) = set(K.alerts, v)
    suspend fun setScanInterval(v: Int) = set(K.interval, v)
    suspend fun setQuiet(enabled: Boolean) = set(K.quiet, enabled)
    suspend fun setQuietWindow(start: Int, end: Int) { ctx.store.edit { it[K.quietStart] = start; it[K.quietEnd] = end } }
    suspend fun setRamThreshold(v: Int) = set(K.ram, v)
    suspend fun setCacheThresholdMb(v: Int) = set(K.cache, v)
    suspend fun setLiveMonitor(v: Boolean) = set(K.live, v)
    suspend fun setSeeded(v: Boolean) = set(K.seeded, v)
    suspend fun setLastScan(v: Long) = set(K.lastScan, v)
    suspend fun setExperimentalCacheClear(v: Boolean) = set(K.expCache, v)

    /** Snoozes are stored as "key|untilMillis"; keys are an alert type name or "pkg:<package>". */
    suspend fun snooze(key: String, until: Long) {
        ctx.store.edit { p -> p[K.snoozes] = (p[K.snoozes] ?: emptySet()).filterNot { it.startsWith("$key|") }.toSet() + "$key|$until" }
    }
    suspend fun isSnoozed(key: String, now: Long): Boolean {
        val s = ctx.store.data.first()[K.snoozes] ?: return false
        return s.any { it.startsWith("$key|") && (it.substringAfter('|').toLongOrNull() ?: 0L) > now }
    }
    suspend fun neverAlert(pkg: String) { ctx.store.edit { it[K.never] = (it[K.never] ?: emptySet()) + pkg } }
    suspend fun isNeverAlert(pkg: String): Boolean = (ctx.store.data.first()[K.never] ?: emptySet()).contains(pkg)
}
