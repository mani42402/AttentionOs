package com.attentionos.security

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Pseudonymises sender identities with a keyed MAC.
 *
 * The previous scheme was an unsalted SHA-256 of `package:title`, truncated to 96 bits. Because
 * it was unkeyed, the preimage space — installed packages crossed with common contact names —
 * is small enough to enumerate offline in seconds, so anyone holding the database or an export
 * could recover the plaintext sender list along with per-sender open rates. HMAC under a
 * per-install secret removes that: without the secret there is nothing to enumerate against.
 *
 * The output stays 96 bits of hex so the existing column width and indexes are unchanged.
 */
class SenderHasher(secret: ByteArray) {

    private val key = SecretKeySpec(secret, ALGORITHM)

    /**
     * Stable pseudonym for a conversation.
     *
     * [conversationId] should come from [SenderIdentity], which prefers a real person or
     * shortcut identifier over the notification title.
     */
    fun hash(conversationId: String): String {
        val mac = Mac.getInstance(ALGORITHM).apply { init(key) }
        return mac.doFinal(conversationId.toByteArray(Charsets.UTF_8))
            .take(OUTPUT_BYTES)
            .joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val ALGORITHM = "HmacSHA256"
        const val OUTPUT_BYTES = 12
    }
}

/**
 * Builds the string that identifies "who" a notification is from.
 *
 * Sources are prefixed so that a person key and a shortcut id that happen to share a value
 * cannot collide into the same pseudonym.
 */
object SenderIdentity {

    fun of(packageName: String, personKey: String?, shortcutId: String?, title: String?): String =
        when {
            !personKey.isNullOrBlank() -> "$packageName|person:$personKey"
            !shortcutId.isNullOrBlank() -> "$packageName|shortcut:$shortcutId"
            !title.isNullOrBlank() -> "$packageName|title:$title"
            // Nothing identifying: group everything from this app together rather than
            // inventing a distinct identity per notification.
            else -> "$packageName|app"
        }
}
