package com.fitzy.foldopti.core.notifications

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.fitzy.foldopti.R
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class ActionWorker @AssistedInject constructor(
    @Assisted ctx: Context, @Assisted params: WorkerParameters, private val handler: ActionHandler,
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val action = AlertAction.fromWire(inputData.getString("action")) ?: return Result.failure()
        val type = runCatching { AlertType.valueOf(inputData.getString("type") ?: "") }.getOrNull() ?: return Result.failure()
        handler.handle(action, type, inputData.getString("pkg"))
        return Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val n = NotificationCompat.Builder(applicationContext, Channels.MONITOR).setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("FoldOpti").setContentText("Working").setOngoing(true).build()
        return if (android.os.Build.VERSION.SDK_INT >= 34) ForegroundInfo(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else ForegroundInfo(7, n)
    }
}
