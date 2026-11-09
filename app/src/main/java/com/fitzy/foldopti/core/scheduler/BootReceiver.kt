package com.fitzy.foldopti.core.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fitzy.foldopti.core.settings.SettingsStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var scheduler: Scheduler
    @Inject lateinit var settings: SettingsStore

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try { scheduler.ensureScheduled(); if (settings.current().liveMonitor) MonitorService.start(ctx) } finally { pending.finish() }
        }
    }
}
