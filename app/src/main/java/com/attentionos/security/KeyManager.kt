package com.attentionos.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Owns every secret the app holds.
 *
 * ```
 * AndroidKeyStore KEK (AES-256-GCM, hardware-backed, non-exportable)
 *   ├─ wraps DEK          -> SQLCipher database key
 *   └─ wraps HMAC secret  -> keyed sender pseudonymisation
 * ```
 *
 * Wrapped keys are stored as files under `files/keys`; the KEK itself never leaves the
 * Keystore, so an attacker with a copy of the app's data directory still cannot read the
 * database without the device.
 *
 * **Why the KEK is not user-authentication bound.** `setUnlockedDeviceRequired` and
 * `setUserAuthenticationRequired` sound strictly safer, but this app's primary workload is a
 * NotificationListenerService that runs while the screen is locked — that is precisely when
 * most notifications arrive. Either flag would make `Cipher.init` throw on nearly every write.
 * Confidentiality at rest is therefore delegated to file-based encryption plus a hardware-bound
 * KEK, which is the correct trade for this threat model.
 */
class KeyManager(context: Context) {

    private val keyDirectory = File(context.noBackupFilesDir, KEY_DIRECTORY)
    private val random = SecureRandom()

    /** 32-byte key for SQLCipher, in raw-key mode so no PBKDF2 runs on open. */
    @Synchronized
    fun databaseKey(): ByteArray = loadOrCreate(DEK_FILE)

    /** 32-byte secret for HMAC sender hashing. */
    @Synchronized
    fun senderHashSecret(): ByteArray = loadOrCreate(HMAC_FILE)

    /**
     * Destroys all key material, including the Keystore entry.
     *
     * After this the database is cryptographically unrecoverable, which is the point: it makes
     * "delete my data" a real guarantee rather than a delete of rows that a forensic tool could
     * still carve out of free pages.
     */
    @Synchronized
    fun destroyAllKeys() {
        keyDirectory.listFiles()?.forEach { it.delete() }
        runCatching {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(KEK_ALIAS)
        }.onFailure { Log.w(TAG, "Could not delete Keystore entry", it) }
    }

    private fun loadOrCreate(fileName: String): ByteArray {
        if (!keyDirectory.exists()) keyDirectory.mkdirs()
        val file = File(keyDirectory, fileName)
        if (file.exists()) {
            unwrap(file.readBytes())?.let { return it }
            // Unwrapping failed: the Keystore entry was invalidated (factory reset, restore onto
            // new hardware, OEM bug). The wrapped bytes are now permanently undecryptable, so
            // keeping them only guarantees repeated failure.
            Log.w(TAG, "Key $fileName could not be unwrapped; regenerating")
            file.delete()
        }
        val fresh = ByteArray(KEY_BYTES).also(random::nextBytes)
        writeAtomically(file, wrap(fresh))
        return fresh
    }

    private fun wrap(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, kek())
        val ciphertext = cipher.doFinal(plaintext)
        // [version][12-byte IV][ciphertext || 16-byte GCM tag]
        return byteArrayOf(FORMAT_VERSION) + cipher.iv + ciphertext
    }

    private fun unwrap(stored: ByteArray): ByteArray? = runCatching {
        require(stored.isNotEmpty() && stored[0] == FORMAT_VERSION) { "unknown key format" }
        val iv = stored.copyOfRange(1, 1 + IV_BYTES)
        val ciphertext = stored.copyOfRange(1 + IV_BYTES, stored.size)
        Cipher.getInstance(TRANSFORMATION).run {
            init(Cipher.DECRYPT_MODE, kek(), GCMParameterSpec(TAG_BITS, iv))
            doFinal(ciphertext)
        }
    }.getOrNull()

    /** The hardware-backed key-encryption key, created on first use. */
    private fun kek(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEK_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        // StrongBox is a discrete secure element. Prefer it, but most devices — and every
        // emulator — lack one, so a failure here is expected rather than exceptional.
        //
        // Each attempt builds its own spec: KeyGenParameterSpec.Builder is mutable and
        // setIsStrongBoxBacked returns the same instance, so reusing one builder would leave
        // StrongBox requested on the fallback attempt and fail identically.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { return generate(strongBox = true) }
                .onFailure { Log.i(TAG, "StrongBox unavailable; using the standard Keystore") }
        }
        return generate(strongBox = false)
    }

    private fun generate(strongBox: Boolean): SecretKey {
        val builder = KeyGenParameterSpec.Builder(
            KEK_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_BITS)
        if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setIsStrongBoxBacked(true)
        }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(builder.build()) }
            .generateKey()
    }

    /** Writes via a temp file and rename so a crash cannot leave a half-written key. */
    private fun writeAtomically(target: File, bytes: ByteArray) {
        val temporary = File(target.parentFile, "${target.name}.tmp")
        temporary.writeBytes(bytes)
        if (!temporary.renameTo(target)) {
            temporary.delete()
            error("Could not install key file ${target.name}")
        }
    }

    private companion object {
        const val TAG = "AttentionSecurity"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEK_ALIAS = "attentionos.kek.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_DIRECTORY = "keys"
        const val DEK_FILE = "dek.v1"
        const val HMAC_FILE = "hmac.v1"
        const val KEY_BITS = 256
        const val KEY_BYTES = KEY_BITS / 8
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val FORMAT_VERSION: Byte = 1
    }
}
