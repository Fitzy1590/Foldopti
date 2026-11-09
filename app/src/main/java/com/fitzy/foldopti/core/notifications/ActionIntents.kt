package com.fitzy.foldopti.core.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object ActionIntents {
    const val ACTION_PREFIX = "com.fitzy.foldopti.action."
    const val EXTRA_PKG = "pkg"; const val EXTRA_TYPE = "alertType"; const val EXTRA_VERSION = "schemaVersion"
    const val SCHEMA = 1

    /** Explicit, immutable broadcast. Unique request code per (action, pkg, type) so buttons stay distinct. */
    fun build(ctx: Context, action: AlertAction, type: AlertType, pkg: String?): PendingIntent {
        val i = Intent(ACTION_PREFIX + action.wire).setClass(ctx, ActionReceiver::class.java)
            .putExtra(EXTRA_PKG, pkg).putExtra(EXTRA_TYPE, type.name).putExtra(EXTRA_VERSION, SCHEMA)
        return PendingIntent.getBroadcast(ctx, "${action.wire}|$pkg|${type.name}".hashCode(), i,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
