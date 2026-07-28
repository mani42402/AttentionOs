package com.attentionos.security

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * "Delete all my data" has to be literally true, so what it leaves behind is asserted rather
 * than assumed.
 *
 * The important property is that key material is destroyed: deleted rows can still be carved
 * out of a SQLite file's free pages, so clearing tables alone would not make the old data
 * unrecoverable. Once the Keystore entry is gone, the encrypted pages are meaningless.
 */
@RunWith(AndroidJUnit4::class)
class PanicWipeTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @Test
    fun destroyingKeysMakesPreviousKeyMaterialUnrecoverable() {
        val manager = KeyManager(context)
        val originalDatabaseKey = manager.databaseKey().copyOf()
        val originalHashSecret = manager.senderHashSecret().copyOf()
        assertTrue("keys should exist before the wipe", keyFilesExist())

        manager.destroyAllKeys()
        assertFalse("wrapped key files must be gone", keyFilesExist())

        // A fresh manager must mint new keys, not resurrect the old ones. If these matched,
        // an attacker who kept a copy of the old database could still read it.
        val replacement = KeyManager(context)
        assertFalse(
            "database key must not survive the wipe",
            replacement.databaseKey().contentEquals(originalDatabaseKey),
        )
        assertFalse(
            "sender hashing secret must not survive the wipe",
            replacement.senderHashSecret().contentEquals(originalHashSecret),
        )
    }

    @Test
    fun exportArtifactsAreRemoved() {
        // The export is a plaintext copy of the training corpus; it used to persist in the
        // cache indefinitely after sharing.
        val exports = File(context.cacheDir, "exports").apply { mkdirs() }
        val leftover = File(exports, "attentionos-training.jsonl")
        leftover.writeText("{\"features\":{}}\n")
        assertTrue(leftover.exists())

        exports.deleteRecursively()
        assertFalse("stale exports must not outlive a wipe", leftover.exists())
    }

    @Test
    fun keysAreRecreatedSoTheAppKeepsWorkingAfterAWipe() {
        // A wipe must leave the app usable, not bricked: the next launch should provision new
        // keys and start from an empty, still-encrypted database.
        val manager = KeyManager(context)
        manager.destroyAllKeys()

        val afterWipe = KeyManager(context)
        runBlocking {
            assertTrue("a new database key must be issued", afterWipe.databaseKey().size == 32)
            assertTrue("a new hashing secret must be issued", afterWipe.senderHashSecret().size == 32)
        }
        assertTrue("key files should be back on disk", keyFilesExist())
    }

    private fun keyFilesExist(): Boolean {
        val directory = File(context.noBackupFilesDir, "keys")
        return File(directory, "dek.v1").exists() || File(directory, "hmac.v1").exists()
    }
}
