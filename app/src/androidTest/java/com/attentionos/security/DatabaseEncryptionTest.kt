package com.attentionos.security

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the plaintext-to-encrypted conversion end to end against real Keystore-backed keys.
 *
 * The migration runs once, silently, on upgrade. If it loses rows or leaves the file readable,
 * the failure is invisible until a user notices missing history or an attacker reads the file —
 * so it is covered here rather than trusted.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseEncryptionTest {

    private lateinit var context: Context
    private lateinit var keyManager: KeyManager

    @Before
    fun setUp() {
        System.loadLibrary("sqlcipher")
        context = InstrumentationRegistry.getInstrumentation().targetContext
        keyManager = KeyManager(context)
        context.getDatabasePath(TEST_DB).parentFile?.mkdirs()
        deleteTestDatabases()
    }

    @Test
    fun keysAreStableAcrossManagerInstances() {
        // Every launch constructs a new KeyManager; if it did not return the same key the
        // database would be unopenable after the first restart.
        val first = KeyManager(context).databaseKey()
        val second = KeyManager(context).databaseKey()
        assertTrue("database key must survive a new KeyManager", first.contentEquals(second))

        val hmacFirst = KeyManager(context).senderHashSecret()
        assertEquals(32, hmacFirst.size)
        assertFalse(
            "the hashing secret must differ from the database key",
            hmacFirst.contentEquals(first),
        )
    }

    @Test
    fun migrationPreservesRowsAndLeavesTheFileUnreadable() {
        val databaseFile = context.getDatabasePath(TEST_DB)
        seedPlaintextDatabase(databaseFile)

        val plaintextHeader = databaseFile.readBytes().copyOfRange(0, 15)
        assertEquals(
            "fixture should start as plain SQLite",
            "SQLite format 3",
            String(plaintextHeader, Charsets.US_ASCII),
        )

        val migrated = DatabaseEncryptionMigrator(context, keyManager).migrateIfNeeded(TEST_DB)
        assertTrue("conversion should have run", migrated)

        // The header must no longer identify the file as SQLite.
        val encryptedHeader = databaseFile.readBytes().copyOfRange(0, 15)
        assertNotEquals(
            "encrypted file must not keep the SQLite header",
            String(plaintextHeader, Charsets.US_ASCII),
            String(encryptedHeader, Charsets.US_ASCII),
        )

        // Opening without a key must fail; opening with it must return every row unchanged.
        assertFalse("file must not open unkeyed", opensWithoutKey(databaseFile))

        SQLiteDatabase.openDatabase(
            databaseFile.absolutePath,
            rawKeyLiteral(keyManager.databaseKey()),
            null,
            SQLiteDatabase.OPEN_READONLY,
            null,
            null,
        ).use { database ->
            database.rawQuery("SELECT id, label FROM sample ORDER BY id", null).use { cursor ->
                assertEquals("all rows must survive", ROW_COUNT, cursor.count)
                cursor.moveToFirst()
                assertEquals(1L, cursor.getLong(0))
                assertEquals("row-1", cursor.getString(1))
            }
            assertEquals("schema version must carry over", USER_VERSION, database.version)
        }
    }

    @Test
    fun migrationIsNotAttemptedTwice() {
        val databaseFile = context.getDatabasePath(TEST_DB)
        seedPlaintextDatabase(databaseFile)
        val migrator = DatabaseEncryptionMigrator(context, keyManager)

        assertTrue("first run converts", migrator.migrateIfNeeded(TEST_DB))
        assertFalse("an encrypted database must be left alone", migrator.migrateIfNeeded(TEST_DB))
    }

    @Test
    fun freshInstallHasNothingToConvert() {
        deleteTestDatabases()
        assertFalse(
            "no database file means no conversion",
            DatabaseEncryptionMigrator(context, keyManager).migrateIfNeeded(TEST_DB),
        )
    }

    private fun seedPlaintextDatabase(file: File) {
        deleteTestDatabases()
        SQLiteDatabase.openDatabase(
            file.absolutePath,
            "",
            null,
            SQLiteDatabase.CREATE_IF_NECESSARY,
            null,
            null,
        ).use { database ->
            database.rawExecSQL("CREATE TABLE sample (id INTEGER PRIMARY KEY, label TEXT, blob BLOB)")
            for (index in 1..ROW_COUNT) {
                database.compileStatement("INSERT INTO sample VALUES (?, ?, ?)").use { statement ->
                    statement.bindLong(1, index.toLong())
                    statement.bindString(2, "row-$index")
                    statement.bindBlob(3, byteArrayOf(index.toByte(), 0x7f))
                    statement.executeInsert()
                }
            }
            database.version = USER_VERSION
        }
    }

    private fun opensWithoutKey(file: File): Boolean = runCatching {
        SQLiteDatabase.openDatabase(
            file.absolutePath,
            "",
            null,
            SQLiteDatabase.OPEN_READONLY,
            null,
            null,
        ).use { it.rawQuery("SELECT COUNT(*) FROM sample", null).use { c -> c.moveToFirst() } }
        true
    }.getOrDefault(false)

    private fun deleteTestDatabases() {
        val base = context.getDatabasePath(TEST_DB)
        listOf("", "-wal", "-shm", "-journal", ".encrypting", ".plaintext-backup").forEach {
            File("${base.absolutePath}$it").delete()
        }
    }

    private companion object {
        const val TEST_DB = "encryption-test.db"
        const val ROW_COUNT = 25
        const val USER_VERSION = 6
    }
}
