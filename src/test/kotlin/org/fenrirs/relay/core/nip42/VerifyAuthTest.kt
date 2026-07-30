package org.fenrirs.relay.core.nip42

import org.fenrirs.relay.core.nip.nip42.VerifyAuth
import org.fenrirs.relay.models.Event
import org.fenrirs.storage.NostrRelayConfig

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class VerifyAuthTest {

    // ใช้ NostrRelayConfig จริง (อ่านจาก .env) แต่ .env ของโปรเจกต์นี้ไม่ได้ตั้งค่า RELAY_URL ไว้
    // จึง relayUrlMatches() จะข้ามการตรวจสอบ relay tag เสมอ ไม่กระทบกับ test ของ kind/created_at/challenge ด้านล่าง
    private val nip42 = VerifyAuth(NostrRelayConfig())

    private val now = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())
    private val challenge = "test-challenge-123"

    private fun authEvent(
        kind: Long = 22242L,
        createdAt: Long = now,
        challengeTag: String? = challenge,
        relayTag: String? = "wss://relay.example.com/"
    ): Event {
        val tags = mutableListOf<List<String>>()
        challengeTag?.let { tags.add(listOf("challenge", it)) }
        relayTag?.let { tags.add(listOf("relay", it)) }
        return Event(
            id = "a".repeat(64),
            pubkey = "b".repeat(64),
            created_at = createdAt,
            kind = kind,
            tags = tags,
            content = "",
            sig = "c".repeat(128)
        )
    }

    @Test
    fun `accepts a well-formed auth event`() {
        assertTrue(nip42.verify(authEvent(), challenge).isValid)
    }

    @Test
    fun `rejects the wrong kind`() {
        val result = nip42.verify(authEvent(kind = 1L), challenge)
        assertFalse(result.isValid)
        assertTrue(result.reason.contains("kind"))
    }

    @Test
    fun `rejects created_at too far in the past`() {
        val result = nip42.verify(authEvent(createdAt = now - TimeUnit.MINUTES.toSeconds(30)), challenge)
        assertFalse(result.isValid)
        assertTrue(result.reason.contains("created_at"))
    }

    @Test
    fun `rejects created_at too far in the future`() {
        val result = nip42.verify(authEvent(createdAt = now + TimeUnit.MINUTES.toSeconds(30)), challenge)
        assertFalse(result.isValid)
    }

    @Test
    fun `accepts created_at within the 10-minute tolerance`() {
        assertTrue(nip42.verify(authEvent(createdAt = now - TimeUnit.MINUTES.toSeconds(9)), challenge).isValid)
        assertTrue(nip42.verify(authEvent(createdAt = now + TimeUnit.MINUTES.toSeconds(9)), challenge).isValid)
    }

    @Test
    fun `rejects a mismatched challenge`() {
        val result = nip42.verify(authEvent(challengeTag = "wrong-challenge"), challenge)
        assertFalse(result.isValid)
        assertTrue(result.reason.contains("challenge"))
    }

    @Test
    fun `rejects a missing challenge tag`() {
        val result = nip42.verify(authEvent(challengeTag = null), challenge)
        assertFalse(result.isValid)
    }

    // ---------- relay tag / URL domain matching (pure function, ไม่ต้องพึ่ง .env) ----------

    @Test
    fun `relay tag matches when domains are equal`() {
        assertTrue(VerifyAuth.relayUrlMatches("wss://relay.example.com/", "wss://relay.example.com/"))
    }

    @Test
    fun `relay tag matches ignoring case and path`() {
        assertTrue(VerifyAuth.relayUrlMatches("wss://Relay.Example.com/", "wss://relay.example.com/some/path"))
    }

    @Test
    fun `relay tag mismatch is rejected`() {
        assertFalse(VerifyAuth.relayUrlMatches("wss://relay.example.com/", "wss://evil.example.com/"))
    }

    @Test
    fun `blank configured RELAY_URL skips the check entirely`() {
        assertTrue(VerifyAuth.relayUrlMatches("", "wss://anything.example.com/"))
        assertTrue(VerifyAuth.relayUrlMatches("", null))
    }

    @Test
    fun `missing relay tag fails when RELAY_URL is configured`() {
        assertFalse(VerifyAuth.relayUrlMatches("wss://relay.example.com/", null))
    }
}
