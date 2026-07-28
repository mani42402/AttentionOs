package com.attentionos.core.di

import android.app.Application
import androidx.room.Room
import com.attentionos.ai.MiniLmLanguageAnalyzer
import com.attentionos.data.settings.AppSettings
import com.attentionos.data.repository.AttentionRepository
import com.attentionos.data.settings.SettingsRepository
import com.attentionos.data.db.AttentionDatabase
import com.attentionos.domain.PriorityEngine
import com.attentionos.training.ExportManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

/**
 * Manual dependency container.
 *
 * Deliberately not Hilt: the graph is small, fully known at compile time, and manual wiring
 * keeps cold start free of annotation-processed setup.
 *
 * Everything except settings is created lazily so that process starts which never touch the
 * database or the ONNX model do not pay for them. [applicationScope] is owned by the
 * [com.attentionos.AttentionApplication] and passed in, so the app has exactly one
 * application-lifetime scope rather than one per component.
 */
class AppContainer(
    application: Application,
    private val applicationScope: CoroutineScope,
) {
    private val database: AttentionDatabase by lazy {
        Room.databaseBuilder(
            application,
            AttentionDatabase::class.java,
            DATABASE_NAME,
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

    /**
     * Hot view of settings for callers that cannot suspend.
     *
     * Note this starts with [AppSettings] defaults and only reflects stored values once
     * DataStore's first read completes. Callers that must not act on defaults should collect
     * [SettingsRepository.settings] directly rather than reading `.value` here.
     */
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

    private companion object {
        const val DATABASE_NAME = "attention-private.db"
    }
}
