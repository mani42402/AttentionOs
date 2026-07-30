package com.attentionos

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.attentionos.core.di.AppContainer
import com.attentionos.service.DataRetentionWorker
import com.attentionos.service.ReviewReminderScheduler
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AttentionApplication : Application() {
    /** The single application-lifetime scope; also drives [AppContainer]'s hot flows. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this, appScope)

        appScope.launch {
            scheduleMaintenance()
        }
        appScope.launch {
            container.settingsRepository.settings
                .map { it.reviewReminderEnabled to it.reviewReminderTimes }
                .distinctUntilChanged()
                .collect { (enabled, times) ->
                    ReviewReminderScheduler.sync(this@AttentionApplication, enabled, times)
                }
        }
    }

    /**
     * Housekeeping runs once a day and only when the battery is healthy. Off the main thread:
     * WorkManager initialisation plus an enqueue is disk I/O.
     */
    private fun scheduleMaintenance() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<DataRetentionWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            RETENTION_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private companion object {
        const val RETENTION_WORK_NAME = "attention-data-retention"
    }
}
