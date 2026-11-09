package com.fitzy.foldopti.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/** Handles notification buttons. Quick work runs under goAsync(); slower work moves to expedited WorkManager. */
@AndroidEntryPoint
class ActionReceiver : BroadcastReceiver() {
    @Inject lateinit var handler: ActionHandler

    override fun onReceive(ctx: Context, intent: Intent) {
        val action = AlertAction.fromWire(intent.action?.removePrefix(ActionIntents.ACTION_PREFIX)) ?: return
        val type = runCatching { AlertType.valueOf(intent.getStringExtra(ActionIntents.EXTRA_TYPE) ?: "") }.getOrNull() ?: return
        val pkg = intent.getStringExtra(ActionIntents.EXTRA_PKG)
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val done = withTimeoutOrNull(8_000) { handler.handle(action, type, pkg); true }
                if (done == null) WorkManager.getInstance(ctx).enqueueUniqueWork(
                    "action-${action.wire}-$pkg", ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<ActionWorker>().setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .setInputData(workDataOf("action" to action.wire, "type" to type.name, "pkg" to pkg)).build())
            } finally { pending.finish() }
        }
    }
}
