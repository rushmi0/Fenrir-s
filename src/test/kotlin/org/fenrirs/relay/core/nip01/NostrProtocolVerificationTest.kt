package org.fenrirs.relay.core.nip01

import org.fenrirs.relay.core.nip.nip01.ValidationResult
import org.fenrirs.relay.core.nip.nip01.VerifyEvent.isEventPublicKeyValid
import org.fenrirs.relay.core.nip.nip01.VerifyEvent.isValidEventId
import org.fenrirs.relay.core.nip.nip01.VerifyEvent.isValidSignature
import org.fenrirs.relay.core.nip.nip01.VerifyEvent.verifyNip01
import org.fenrirs.relay.core.nip.nip01.VerifyFilterX.validate
import org.fenrirs.relay.core.nip.nip01.command.CommandFactory
import org.fenrirs.relay.core.nip.nip01.command.EVENT
import org.fenrirs.relay.models.Event
import org.fenrirs.relay.models.FiltersX
import org.fenrirs.utils.ShiftTo.fromHex
import org.fenrirs.utils.ShiftTo.toHex

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Real, independently-verified Nostr events (pulled from nbd-wtf/go-nostr's own test fixtures,
 * whose id/signature are asserted valid by that library, and cross-checked here against the
 * NIP-01 canonical-serialization + sha256 formula with a standalone Python script before use).
 */
private object Fixtures {

    val VALID_EVENT_NO_TAGS = Event(
        id = "dc90c95f09947507c1044e8f48bcf6350aa6bff1507dd4acfc755b9239b5c962",
        pubkey = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d",
        created_at = 1644271588,
        kind = 1,
        tags = emptyList(),
        content = "now that https://blueskyweb.org/blog/2-7-2022-overview was announced we can stop working on nostr?",
        sig = "230e9d8f0ddaf7eb70b5f7741ccfa37e87a455c9a469282e3464e2052d3192cd63a167e196e381ef9d7e69e9ea43af2443b839974dc85d8aaab9efe1d9296524"
    )

    val VALID_EVENT_WITH_TAGS = Event(
        id = "9e662bdd7d8abc40b5b15ee1ff5e9320efc87e9274d8d440c58e6eed2dddfbe2",
        pubkey = "373ebe3d45ec91977296a178d9f19f326c70631d2a1b0bbba5c5ecc2eb53b9e7",
        created_at = 1644844224,
        kind = 3,
        tags = listOf(
            listOf("p", "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"),
            listOf("p", "75fc5ac2487363293bd27fb0d14fb966477d0f1dbc6361d37806a6a740eda91e"),
            listOf("p", "46d0dfd3a724a302ca9175163bdf788f3606b3fd1bb12d5fe055d1e418cb60ea")
        ),
        content = "{\"wss://nostr-pub.wellorder.net\":{\"read\":true,\"write\":true},\"wss://nostr.bitcoiner.social\":{\"read\":false,\"write\":true},\"wss://expensive-relay.fiatjaf.com\":{\"read\":true,\"write\":true},\"wss://relayer.fiatjaf.com\":{\"read\":true,\"write\":true},\"wss://relay.bitid.nz\":{\"read\":true,\"write\":true},\"wss://nostr.rocks\":{\"read\":true,\"write\":true}}",
        sig = "811355d3484d375df47581cb5d66bed05002c2978894098304f20b595e571b7e01b2efd906c5650080ffe49cf1c62b36715698e9d88b9e8be43029a2f3fa66be"
    )
}

class NostrProtocolVerificationTest {


    @Test
    fun `Valid is reused and carries no message`() {
        assertTrue(ValidationResult.Valid.isValid)
        assertEquals("", ValidationResult.Valid.reason)
    }

    @Test
    fun `invalid carries the reason and destructures like the old Pair`() {
        val result = ValidationResult.invalid("bad thing")
        val (status, warning) = result
        assertFalse(status)
        assertEquals("bad thing", warning)
    }


    @Test
    fun `toHex and fromHex round trip`() {
        val bytes = byteArrayOf(0x00, 0x0F, 0x10, 0xFF.toByte(), 0x7A)
        val hex = bytes.toHex()
        assertEquals("000f10ff7a", hex)
        assertTrue(hex.fromHex().contentEquals(bytes))
    }

    @Test
    fun `fromHex rejects odd length`() {
        assertThrows(IllegalStateException::class.java) { "abc".fromHex() }
    }

    @Test
    fun `fromHex rejects non-hex characters`() {
        assertThrows(NumberFormatException::class.java) { "zz".fromHex() }
    }


    @Test
    fun `valid event without tags has matching id`() {
        val result = Fixtures.VALID_EVENT_NO_TAGS.isValidEventId()
        assertTrue(result.isValid)
    }

    @Test
    fun `valid event with tags has matching id`() {
        val result = Fixtures.VALID_EVENT_WITH_TAGS.isValidEventId()
        assertTrue(result.isValid)
    }

    @Test
    fun `tampered content produces a different id and fails validation`() {
        val tampered = Fixtures.VALID_EVENT_NO_TAGS.copy(content = "tampered content")
        val result = tampered.isValidEventId()
        assertFalse(result.isValid)
        assertTrue(result.reason.contains("bad event id"))
    }


    @Test
    fun `pubkey with correct length passes`() {
        assertTrue(Fixtures.VALID_EVENT_NO_TAGS.isEventPublicKeyValid().isValid)
    }

    @Test
    fun `pubkey shorter than 64 chars fails`() {
        val event = Fixtures.VALID_EVENT_NO_TAGS.copy(pubkey = "abcd")
        assertFalse(event.isEventPublicKeyValid().isValid)
    }

    @Test
    fun `pubkey longer than 64 chars fails`() {
        val event = Fixtures.VALID_EVENT_NO_TAGS.copy(pubkey = Fixtures.VALID_EVENT_NO_TAGS.pubkey + "ab")
        assertFalse(event.isEventPublicKeyValid().isValid)
    }


    @Test
    fun `valid signature passes`() {
        assertTrue(Fixtures.VALID_EVENT_NO_TAGS.isValidSignature().isValid)
    }

    @Test
    fun `corrupted signature fails`() {
        val corrupted = Fixtures.VALID_EVENT_NO_TAGS.sig!!.let { it.dropLast(1) + (if (it.last() == '0') '1' else '0') }
        val event = Fixtures.VALID_EVENT_NO_TAGS.copy(sig = corrupted)
        assertFalse(event.isValidSignature().isValid)
    }

    @Test
    fun `signature with wrong length fails without throwing`() {
        val event = Fixtures.VALID_EVENT_NO_TAGS.copy(sig = "abcd")
        assertFalse(event.isValidSignature().isValid)
    }


    @Test
    fun `verifyNip01 accepts a fully valid event`() {
        assertTrue(Fixtures.VALID_EVENT_NO_TAGS.verifyNip01().isValid)
        assertTrue(Fixtures.VALID_EVENT_WITH_TAGS.verifyNip01().isValid)
    }

    @Test
    fun `verifyNip01 rejects bad pubkey before touching id or signature`() {
        val event = Fixtures.VALID_EVENT_NO_TAGS.copy(pubkey = "short", id = "not-even-hex", sig = "not-even-hex")
        val result = event.verifyNip01()
        assertFalse(result.isValid)
        assertTrue(result.reason.contains("public key"))
    }

    @Test
    fun `verifyNip01 rejects bad id when pubkey is fine`() {
        val event = Fixtures.VALID_EVENT_NO_TAGS.copy(content = "different content")
        val result = event.verifyNip01()
        assertFalse(result.isValid)
        assertTrue(result.reason.contains("bad event id"))
    }


    @Test
    fun `parse rejects malformed JSON`() {
        val ex = assertThrows(IllegalArgumentException::class.java) { CommandFactory.parse("not json") }
        assertTrue(ex.message!!.contains("invalid: JSON format"))
    }

    @Test
    fun `parse rejects an empty array`() {
        assertThrows(IllegalArgumentException::class.java) { CommandFactory.parse("[]") }
    }

    @Test
    fun `parse rejects unknown command`() {
        assertThrows(IllegalArgumentException::class.java) { CommandFactory.parse("""["WHATEVER"]""") }
    }

    @Test
    fun `parse accepts a fully valid EVENT command end-to-end`() {
        val e = Fixtures.VALID_EVENT_NO_TAGS
        val payload = """["EVENT",{"id":"${e.id}","pubkey":"${e.pubkey}","created_at":${e.created_at},"kind":${e.kind},"tags":[],"content":${jsonQuote(e.content!!)},"sig":"${e.sig}"}]"""

        val (command, result) = CommandFactory.parse(payload)
        assertTrue(result.isValid)
        assertTrue(command is EVENT)
        assertEquals(e.id, (command as EVENT).event.id)
    }

    @Test
    fun `parse flags missing required fields`() {
        val e = Fixtures.VALID_EVENT_NO_TAGS
        // "sig" field omitted entirely
        val payload = """["EVENT",{"id":"${e.id}","pubkey":"${e.pubkey}","created_at":${e.created_at},"kind":${e.kind},"tags":[],"content":${jsonQuote(e.content!!)}}]"""

        val (_, result) = CommandFactory.parse(payload)
        assertFalse(result.isValid)
        assertTrue(result.reason.contains("missing fields"))
    }

    @Test
    fun `parse flags an unsupported extra field`() {
        val e = Fixtures.VALID_EVENT_NO_TAGS
        val payload = """["EVENT",{"id":"${e.id}","pubkey":"${e.pubkey}","created_at":${e.created_at},"kind":${e.kind},"tags":[],"content":${jsonQuote(e.content!!)},"sig":"${e.sig}","extra_field":true}]"""

        val (_, result) = CommandFactory.parse(payload)
        assertFalse(result.isValid)
        assertTrue(result.reason.contains("unsupported"))
    }

    @Test
    fun `parse flags kind with the wrong JSON type`() {
        val e = Fixtures.VALID_EVENT_NO_TAGS
        // kind sent as a string instead of a number
        val payload = """["EVENT",{"id":"${e.id}","pubkey":"${e.pubkey}","created_at":${e.created_at},"kind":"1","tags":[],"content":${jsonQuote(e.content!!)},"sig":"${e.sig}"}]"""

        val (_, result) = CommandFactory.parse(payload)
        assertFalse(result.isValid)
        assertTrue(result.reason.contains("data type"))
    }

    @Test
    fun `parse flags tags with the wrong JSON type`() {
        val e = Fixtures.VALID_EVENT_NO_TAGS
        // tags sent as an object instead of an array
        val payload = """["EVENT",{"id":"${e.id}","pubkey":"${e.pubkey}","created_at":${e.created_at},"kind":${e.kind},"tags":{},"content":${jsonQuote(e.content!!)},"sig":"${e.sig}"}]"""

        val (_, result) = CommandFactory.parse(payload)
        assertFalse(result.isValid)
        assertTrue(result.reason.contains("data type"))
    }

    @Test
    fun `parse rejects an EVENT with a tampered id`() {
        val e = Fixtures.VALID_EVENT_NO_TAGS
        val badId = "0".repeat(64)
        val payload = """["EVENT",{"id":"$badId","pubkey":"${e.pubkey}","created_at":${e.created_at},"kind":${e.kind},"tags":[],"content":${jsonQuote(e.content!!)},"sig":"${e.sig}"}]"""

        val (_, result) = CommandFactory.parse(payload)
        assertFalse(result.isValid)
        assertTrue(result.reason.contains("bad event id"))
    }

    private fun jsonQuote(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""


    @Test
    fun `valid filter passes`() {
        val filter = FiltersX(ids = setOf("a".repeat(64)), authors = setOf("b".repeat(64)), since = 1L, until = 2L, limit = 10L)
        assertTrue(filter.validate().isValid)
    }

    @Test
    fun `filter id must be 64 chars or all zeros`() {
        assertTrue(FiltersX(ids = setOf("0".repeat(10))).validate().isValid)
        assertTrue(FiltersX(ids = setOf("a".repeat(64))).validate().isValid)
        assertFalse(FiltersX(ids = setOf("a".repeat(10))).validate().isValid)
    }

    @Test
    fun `filter rejects an empty-string id`() {
        // Regression guard: an empty id must not be silently treated as "all zeros".
        assertFalse(FiltersX(ids = setOf("")).validate().isValid)
    }

    @Test
    fun `filter author must be 64 chars`() {
        assertFalse(FiltersX(authors = setOf("short")).validate().isValid)
    }

    @Test
    fun `filter since must be less than or equal to until`() {
        assertFalse(FiltersX(since = 10L, until = 5L).validate().isValid)
        assertTrue(FiltersX(since = 5L, until = 10L).validate().isValid)
        assertTrue(FiltersX(since = 7L, until = 7L).validate().isValid)
    }

    @Test
    fun `filter limit must not be negative`() {
        assertFalse(FiltersX(limit = -1L).validate().isValid)
        assertTrue(FiltersX(limit = 0L).validate().isValid)
    }
}