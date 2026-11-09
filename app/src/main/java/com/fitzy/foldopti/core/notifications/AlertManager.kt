package com.fitzy.foldopti.core.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.fitzy.foldopti.R
import com.fitzy.foldopti.core.settings.QuietHours
import com.fitzy.foldopti.core.settings.SettingsStore
import com.fitzy.foldopti.core.system.Access
import com.fitzy.foldopti.ui.mobile.MobileActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlertManager @Inject constructor(
    @ApplicationContext private val ctx: Context, private val settings: SettingsStore, private val access: Access,
) {
    private val nm get() = ctx.getSystemService(NotificationManager::class.java)
    private val times = ctx.getSharedPreferences("alert_times", Context.MODE_PRIVATE)

    /** Returns true when the notification was posted. Applies snooze, never-alert, quiet hours and the 30 min rate limit. */
    suspend fun post(spec: AlertSpec, now: Long = System.currentTimeMillis()): Boolean {
        val s = settings.current()
        if (!s.alertsEnabled || !access.hasNotificationPermission()) return false
        if (spec.pkg != null && settings.isNeverAlert(spec.pkg)) return false
        if (settings.isSnoozed(spec.type.name, now) || (spec.pkg != null && settings.isSnoozed("pkg:${spec.pkg}", now))) return false
        if (!spec.type.critical && s.quietEnabled) {
            val c = Calendar.getInstance().apply { timeInMillis = now }
            if (QuietHours.isQuiet(c[Calendar.HOUR_OF_DAY] * 60 + c[Calendar.MINUTE], s.quietStartMin, s.quietEndMin)) return false
        }
        val key = spec.type.name + (spec.pkg?.let { ":$it" } ?: "")
        val last = times.getLong(key, -1L).takeIf { it >= 0 }
        if (!RateLimit.allow(last, now)) return false
        times.edit().putLong(key, now).apply()
        nm.notify(spec.type.notifId + (spec.pkg?.hashCode()?.and(0xFFFF) ?: 0) * 1000, build(spec))
        return true
    }

    private fun build(spec: AlertSpec) = NotificationCompat.Builder(ctx, spec.type.channel)
        .setSmallIcon(R.drawable.ic_stat).setContentTitle(spec.title).setContentText(spec.text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(spec.text))
        .setPriority(if (spec.type.critical) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
        .setCategory(NotificationCompat.CATEGORY_STATUS).setAutoCancel(true)
        .setContentIntent(PendingIntent.getActivity(ctx, 0, Intent(ctx, MobileActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
        .also { b -> spec.actions.take(3).forEach { b.addAction(0, it.label, ActionIntents.build(ctx, it, spec.type, spec.pkg)) } }
        .build()

    /** Replaces an alert with its result and clears it after five seconds. */
    fun showResult(type: AlertType, pkg: String?, text: String) {
        nm.notify(type.notifId + (pkg?.hashCode()?.and(0xFFFF) ?: 0) * 1000,
            NotificationCompat.Builder(ctx, Channels.INFO).setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("FoldOpti").setContentText(text).setTimeoutAfter(5000).setAutoCancel(true).build())
    }
}
