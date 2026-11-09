package com.fitzy.foldopti

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.fitzy.foldopti.core.notifications.Channels
import com.fitzy.foldopti.core.optimizer.ShizukuShell
import com.fitzy.foldopti.core.scheduler.Scheduler
import com.fitzy.foldopti.core.whitelist.WhitelistRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class FoldOptiApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var scheduler: Scheduler
    @Inject lateinit var shizuku: ShizukuShell
    @Inject lateinit var whitelist: WhitelistRepository

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        Channels.create(this)
        shizuku.start()
        scheduler.ensureScheduled()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { whitelist.seedDefaults() }
    }
}
