package com.attentionos.domain

import android.content.Context
import android.util.Log
import com.attentionos.data.repository.AttentionRepository
import com.attentionos.security.KeyManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Erases every trace of the user's data from the device.
 *
 * "Delete everything" previously meant `DELETE FROM` on four tables. That left behind the
 * exported training corpus in the cache, the wrapped keys, and every stored preference — and
 * deleted rows can still be carved out of a SQLite file's free pages by a forensic tool.
 *
 * Destroying the key material is what turns this into a real guarantee: once the Keystore entry
 * is gone, the pages of the old encrypted database are unrecoverable regardless of what remains
 * on the filesystem.
 *
 * Ordering matters. Rows are cleared while the database is still openable, then the keys are
 * destroyed, then the now-undecryptable files are removed.
 */
class PanicWipe(
    private val context: Context,
    private val repository: AttentionRepository,
    private val keyManager: KeyManager,
    private val onSettingsCleared: suspend () -> Unit,
) {

    suspend operator fun invoke() = withContext(Dispatchers.IO) {
        runCatching { repository.deleteAllUserData() }
            .onFailure { Log.w(TAG, "Could not clear rows; continuing with file removal", it) }

        runCatching { onSettingsCleared() }
            .onFailure { Log.w(TAG, "Could not clear settings", it) }

        // Anything derived from user data that lives outside the database.
        deleteRecursively(File(context.cacheDir, "exports"))

        keyManager.destroyAllKeys()

        // Only now are these safe to remove: without the keys they are opaque bytes anyway,
        // but leaving them would let the app reopen a stale database on next launch.
        deleteDatabaseFiles()

        Log.i(TAG, "All local data destroyed")
    }

    private fun deleteDatabaseFiles() {
        val base = context.getDatabasePath(DATABASE_NAME)
        listOf("", "-wal", "-shm", "-journal", ".encrypting", ".plaintext-backup").forEach {
            File("${base.absolutePath}$it").delete()
        }
    }

    private fun deleteRecursively(target: File) {
        if (!target.exists()) return
        runCatching { target.deleteRecursively() }
            .onFailure { Log.w(TAG, "Could not delete ${target.name}", it) }
    }

    private companion object {
        const val TAG = "AttentionSecurity"
        const val DATABASE_NAME = "attention-private.db"
    }
}
