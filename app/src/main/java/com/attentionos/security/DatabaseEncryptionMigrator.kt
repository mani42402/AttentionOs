package com.attentionos.security

import android.content.Context
import android.database.Cursor
import android.util.Log
import java.io.File
import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * Converts an existing plaintext database to an encrypted one, once.
 *
 * Runs before Room opens the file. Deliberately a copy-and-swap rather than an in-place
 * rewrite: the original is untouched until a fully-formed, integrity-checked encrypted copy
 * exists, so a crash or a low-storage failure at any point leaves the user's data intact and
 * the conversion simply retries on the next launch.
 *
 * A user installing fresh has no plaintext file and skips all of this.
 */
class DatabaseEncryptionMigrator(
    private val context: Context,
    private val keyManager: KeyManager,
) {

    /** @return true when a conversion happened, false when there was nothing to convert. */
    fun migrateIfNeeded(databaseName: String): Boolean {
        val plaintext = context.getDatabasePath(databaseName)
        if (!plaintext.exists()) return false
        if (!isPlaintextSqlite(plaintext)) return false

        Log.i(TAG, "Converting plaintext database to encrypted storage")
        val encrypted = File(plaintext.parentFile, "$databaseName$ENCRYPTED_SUFFIX")
        // A previous attempt may have died midway; its output is not trustworthy.
        deleteDatabaseFiles(encrypted)

        val key = keyManager.databaseKey()
        try {
            exportToEncrypted(plaintext, encrypted, key)
            verify(encrypted, key)
            swap(plaintext, encrypted)
        } catch (failure: Throwable) {
            Log.e(
                TAG,
                "Encryption migration failed (encrypted artifact was ${encrypted.length()} bytes); " +
                    "leaving plaintext database untouched",
                failure,
            )
            deleteDatabaseFiles(encrypted)
            throw failure
        }
        Log.i(TAG, "Database is now encrypted")
        return true
    }

    /**
     * SQLite writes a fixed ASCII header. An encrypted database starts with random bytes, so
     * the header is an unambiguous discriminator that costs one 16-byte read.
     */
    private fun isPlaintextSqlite(file: File): Boolean = runCatching {
        file.inputStream().use { input ->
            val header = ByteArray(SQLITE_HEADER.size)
            input.read(header) == header.size && header.contentEquals(SQLITE_HEADER)
        }
    }.getOrDefault(false)

    /**
     * Copies schema and rows from the plaintext database into a new encrypted one.
     *
     * `sqlcipher_export` would be the obvious tool, but it is unusable through this API:
     * `ATTACH` is per-connection, and SQLiteDatabase routes statements across a pool of
     * connections by whether they look read-only. Executing the export as a statement never
     * steps the function (it produced an empty file), and executing it as a query steps it on a
     * reader that never saw the `ATTACH` ("invalid target database"). Copying explicitly avoids
     * depending on which connection serves which statement.
     */
    private fun exportToEncrypted(plaintext: File, encrypted: File, key: ByteArray) {
        val source = SQLiteDatabase.openDatabase(
            plaintext.absolutePath,
            "",
            null,
            SQLiteDatabase.OPEN_READWRITE,
            null,
            null,
        )
        source.use { from ->
            // Fold any WAL content into the main file so nothing committed is missed.
            from.rawExecSQL("PRAGMA wal_checkpoint(TRUNCATE)")
            val userVersion = from.version
            val schema = from.rawQuery(
                "SELECT type, name, sql FROM sqlite_master " +
                    "WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%'",
                null,
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(SchemaObject(cursor.getString(0), cursor.getString(1), cursor.getString(2)))
                    }
                }
            }

            val target = SQLiteDatabase.openDatabase(
                encrypted.absolutePath,
                rawKeyLiteral(key),
                null,
                SQLiteDatabase.CREATE_IF_NECESSARY,
                null,
                null,
            )
            target.use { to ->
                // Tables first: indexes and triggers reference them.
                schema.filter { it.type == "table" }.forEach { to.rawExecSQL(it.sql) }
                schema.filter { it.type == "table" }.forEach { copyRows(from, to, it.name) }
                schema.filterNot { it.type == "table" }.forEach { to.rawExecSQL(it.sql) }
                to.version = userVersion
            }
            Log.i(
                TAG,
                "Copied ${schema.count { it.type == "table" }} tables " +
                    "(${encrypted.length()} bytes) at schema version $userVersion",
            )
        }

        check(encrypted.exists() && encrypted.length() > 0) {
            "encrypted copy is empty (${encrypted.length()} bytes)"
        }
    }

    private fun copyRows(from: SQLiteDatabase, to: SQLiteDatabase, table: String) {
        from.rawQuery("SELECT * FROM \"${table.sqlIdentifier()}\"", null).use { cursor ->
            if (!cursor.moveToFirst()) return
            val columns = cursor.columnNames
            val columnList = columns.joinToString(", ") { "\"${it.sqlIdentifier()}\"" }
            val placeholders = columns.joinToString(", ") { "?" }
            val statement = to.compileStatement(
                "INSERT INTO \"${table.sqlIdentifier()}\" ($columnList) VALUES ($placeholders)",
            )
            to.beginTransaction()
            try {
                statement.use {
                    do {
                        it.clearBindings()
                        for (index in columns.indices) {
                            val position = index + 1
                            when (cursor.getType(index)) {
                                Cursor.FIELD_TYPE_NULL -> it.bindNull(position)
                                Cursor.FIELD_TYPE_INTEGER -> it.bindLong(position, cursor.getLong(index))
                                Cursor.FIELD_TYPE_FLOAT -> it.bindDouble(position, cursor.getDouble(index))
                                Cursor.FIELD_TYPE_BLOB -> it.bindBlob(position, cursor.getBlob(index))
                                else -> it.bindString(position, cursor.getString(index))
                            }
                        }
                        it.executeInsert()
                    } while (cursor.moveToNext())
                }
                to.setTransactionSuccessful()
            } finally {
                to.endTransaction()
            }
        }
    }

    private data class SchemaObject(val type: String, val name: String, val sql: String)

    private fun verify(encrypted: File, key: ByteArray) {
        val database = SQLiteDatabase.openDatabase(
            encrypted.absolutePath,
            rawKeyLiteral(key),
            null,
            SQLiteDatabase.OPEN_READONLY,
            null,
            null,
        )
        database.use {
            it.rawQuery("PRAGMA cipher_integrity_check", null).use { cursor ->
                check(!cursor.moveToFirst()) {
                    "cipher_integrity_check reported problems in the converted database"
                }
            }
            it.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                check(cursor.moveToFirst() && cursor.getString(0) == "ok") {
                    "integrity_check failed on the converted database"
                }
            }
        }
    }

    /**
     * Replaces the plaintext file with the encrypted one.
     *
     * The plaintext is renamed aside rather than deleted first, so the window in which neither
     * file is in place is a single rename long.
     */
    private fun swap(plaintext: File, encrypted: File) {
        val retired = File(plaintext.parentFile, "${plaintext.name}$RETIRED_SUFFIX")
        deleteDatabaseFiles(retired)
        check(plaintext.renameTo(retired)) { "could not move the plaintext database aside" }
        // The old WAL/SHM describe the plaintext file and must not be seen next to the new one.
        File("${plaintext.absolutePath}-wal").delete()
        File("${plaintext.absolutePath}-shm").delete()
        if (!encrypted.renameTo(plaintext)) {
            retired.renameTo(plaintext)
            error("could not install the encrypted database")
        }
        deleteDatabaseFiles(retired)
    }

    private fun deleteDatabaseFiles(database: File) {
        database.delete()
        File("${database.absolutePath}-wal").delete()
        File("${database.absolutePath}-shm").delete()
        File("${database.absolutePath}-journal").delete()
    }

    private companion object {
        const val TAG = "AttentionSecurity"
        const val ENCRYPTED_SUFFIX = ".encrypting"
        const val RETIRED_SUFFIX = ".plaintext-backup"
        val SQLITE_HEADER = "SQLite format 3 ".toByteArray(Charsets.US_ASCII)
    }
}

/**
 * SQLCipher accepts a raw key as the literal `x'<hex>'`, which skips key derivation entirely —
 * the right choice here because the key is already 256 bits of [java.security.SecureRandom]
 * output rather than a password.
 */
internal fun rawKeyLiteral(key: ByteArray): String =
    "x'" + key.joinToString("") { "%02x".format(it) } + "'"

/** Escapes a double-quoted SQL identifier (table or column name). */
private fun String.sqlIdentifier(): String = replace("\"", "\"\"")
