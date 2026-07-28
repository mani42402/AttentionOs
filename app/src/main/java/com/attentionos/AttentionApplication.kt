package com.attentionos

import android.app.Application
import androidx.room.Room
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.attentionos.data.AppSettings
import com.attentionos.data.AttentionRepository
import com.attentionos.data.SettingsRepository
import com.attentionos.data.local.AttentionDatabase
import com.attentionos.domain.PriorityEngine
import com.attentionos.ai.MiniLmLanguageAnalyzer
import com.attentionos.service.DataRetentionWorker
import com.attentionos.service.ReviewReminderScheduler
import com.attentionos.training.ExportManager
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AttentionApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        scheduleMaintenance()
        appScope.launch {
            container.currentSettings
                .map { it.reviewReminderEnabled to it.reviewReminderTimes }
                .distinctUntilChanged()
                .collect { (enabled, times) ->
                    ReviewReminderScheduler.sync(this@AttentionApplication, enabled, times)
                }
        }
    }

    private fun scheduleMaintenance() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresDeviceIdle(true)
            .build()
        val request = PeriodicWorkRequestBuilder<DataRetentionWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "attention-data-retention",
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}

class AppContainer(application: Application) {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database: AttentionDatabase by lazy {
        Room.databaseBuilder(
            application,
            AttentionDatabase::class.java,
            "attention-private.db",
        )
            .addMigrations(
                AttentionDatabase.MIGRATION_1_2,
                AttentionDatabase.MIGRATION_2_3,
                AttentionDatabase.MIGRATION_3_4,
                AttentionDatabase.MIGRATION_4_5,
            )
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    val settingsRepository = SettingsRepository(application)
    val currentSettings = settingsRepository.settings.stateIn(
        scope = applicationScope,
        started = SharingStarted.Eagerly,
        initialValue = AppSettings(),
    )
    val attentionRepository: AttentionRepository by lazy {
        AttentionRepository(database.attentionDao(), PriorityEngine(languageAnalyzer))
    }
    private val languageAnalyzer: MiniLmLanguageAnalyzer by lazy {
        MiniLmLanguageAnalyzer(application)
    }
    val exportManager: ExportManager by lazy {
        ExportManager(application, attentionRepository)
    }

    fun warmLanguageModel() {
        languageAnalyzer.warmUp()
    }
}
