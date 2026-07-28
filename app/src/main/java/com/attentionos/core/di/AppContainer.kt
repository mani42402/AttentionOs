package com.attentionos.core.di

import android.app.Application
import androidx.room.Room
import com.attentionos.ai.MiniLmLanguageAnalyzer
import com.attentionos.data.db.AttentionDatabase
import com.attentionos.data.repository.AttentionRepository
import com.attentionos.data.settings.AppSettings
import com.attentionos.data.settings.SettingsRepository
import com.attentionos.domain.PriorityEngine
import com.attentionos.security.DatabaseEncryptionMigrator
import com.attentionos.security.KeyManager
import com.attentionos.security.rawKeyLiteral
import com.attentionos.training.ExportManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

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
    val keyManager: KeyManager by lazy { KeyManager(application) }

    private val database: AttentionDatabase by lazy {
        // SQLCipher ships its own SQLite; the native library must be loaded before any of its
        // classes are touched, including by the migrator below.
        System.loadLibrary("sqlcipher")

        // Convert a pre-encryption install before Room touches the file. Failing here is
        // preferable to silently continuing: the alternative is running against a plaintext
        // database while telling the user their data is encrypted.
        DatabaseEncryptionMigrator(application, keyManager).migrateIfNeeded(DATABASE_NAME)

        // The key must be handed over in exactly the form the migrator used to write the file.
        // SQLCipher reads the `x'<hex>'` literal as a raw key and skips PBKDF2 entirely, which
        // is correct here: the key is already 256 bits of SecureRandom output, not a password.
        // Passing the raw bytes instead would make SQLCipher treat them as a passphrase and
        // derive a different key, which fails as "file is not a database".
        val databaseKey = keyManager.databaseKey()
        val factory = SupportOpenHelperFactory(
            rawKeyLiteral(databaseKey).toByteArray(Charsets.UTF_8),
        )

        Room.databaseBuilder(
            application,
            AttentionDatabase::class.java,
            DATABASE_NAME,
        )
            .openHelperFactory(factory)
            .addMigrations(
                AttentionDatabase.MIGRATION_1_2,
                AttentionDatabase.MIGRATION_2_3,
                AttentionDatabase.MIGRATION_3_4,
                AttentionDatabase.MIGRATION_4_5,
                AttentionDatabase.MIGRATION_5_6,
            )
            // No destructive fallback. Dropping every table would erase the user's history,
            // learned sender memory and personalized model without warning; a migration defect
            // must surface as a crash we can fix, not as silent data loss. Migrations are
            // covered by instrumented tests.
            .build()
            .also { databaseKey.fill(0) }
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
