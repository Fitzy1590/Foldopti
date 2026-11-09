package com.fitzy.foldopti.core.scheduler

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceState @Inject constructor(@ApplicationContext private val ctx: Context) {
    /** Cover screen (folded) is narrower than 600dp. */
    val isCoverScreen: Boolean get() = ctx.resources.configuration.smallestScreenWidthDp < 600

    private fun battery(): Intent? = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val isCharging: Boolean get() = (battery()?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
    val batteryPercent: Int get() = battery()?.let {
        val l = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1); val s = it.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (l < 0) -1 else l * 100 / s
    } ?: -1
}
