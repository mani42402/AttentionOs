package com.attentionos.security

import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sender pseudonym is the one identifier that leaves the device in exports, so its
 * irreversibility is a property worth pinning rather than assuming.
 */
class SenderHasherTest {

    private val secret = ByteArray(32) { it.toByte() }
    private val hasher = SenderHasher(secret)

    @Test
    fun `is deterministic for the same identity`() {
        assertEquals(hasher.hash("com.example|person:alice"), hasher.hash("com.example|person:alice"))
    }

    @Test
    fun `distinguishes different identities`() {
        assertNotEquals(hasher.hash("com.example|person:alice"), hasher.hash("com.example|person:bob"))
    }

    @Test
    fun `produces a different pseudonym under a different secret`() {
        // This is the property that defeats offline enumeration: without the per-install
        // secret, precomputing hashes for common names buys an attacker nothing.
        val other = SenderHasher(ByteArray(32) { (it + 1).toByte() })
        assertNotEquals(hasher.hash("com.example|person:alice"), other.hash("com.example|person:alice"))
    }

    @Test
    fun `does not match the old unkeyed digest`() {
        // The previous scheme was a plain SHA-256 of the identity, reversible by enumerating
        // packages against common contact names. Any accidental return to it must fail here.
        val identity = "com.example|person:alice"
        val unkeyed = MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray())
            .take(12)
            .joinToString("") { "%02x".format(it) }
        assertNotEquals(unkeyed, hasher.hash(identity))
    }

    @Test
    fun `output width is unchanged so the column and indexes still fit`() {
        assertEquals(24, hasher.hash("com.example|app").length)
        assertTrue(hasher.hash("com.example|app").all { it in "0123456789abcdef" })
    }

    @Test
    fun `prefers a person key over a shortcut or title`() {
        val identity = SenderIdentity.of("com.chat", "person-1", "shortcut-1", "Subject line")
        assertEquals("com.chat|person:person-1", identity)
    }

    @Test
    fun `falls back through shortcut then title`() {
        assertEquals(
            "com.chat|shortcut:shortcut-1",
            SenderIdentity.of("com.chat", null, "shortcut-1", "Subject line"),
        )
        assertEquals(
            "com.chat|title:Subject line",
            SenderIdentity.of("com.chat", null, null, "Subject line"),
        )
    }

    @Test
    fun `groups anonymous notifications per app rather than inventing identities`() {
        // Apps that put a counter or timestamp in the title used to mint a new "sender" per
        // notification, fragmenting learned history; with nothing identifying we group by app.
        assertEquals("com.chat|app", SenderIdentity.of("com.chat", null, null, null))
        assertEquals("com.chat|app", SenderIdentity.of("com.chat", "", "", ""))
    }

    @Test
    fun `sources cannot collide across identity kinds`() {
        assertNotEquals(
            SenderIdentity.of("com.chat", "same", null, null),
            SenderIdentity.of("com.chat", null, "same", null),
        )
    }
}
