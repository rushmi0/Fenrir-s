package org.fenrirs.relay.core.nip01

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

import org.fenrirs.relay.core.nip.nip01.response.RelayResponse
import org.fenrirs.relay.models.Event

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * RelayResponse.EVENT.toJson() fast-paths the event body through EventJsonCache instead of
 * going through RelayResponseSerializer - these pin the resulting wire format so that
 * optimization can't silently drift from what clients actually expect.
 */
class RelayResponseSerializationTest {

    private val event = Event(
        id = "a".repeat(64),
        pubkey = "b".repeat(64),
        created_at = 1700000000L,
        kind = 1L,
        tags = listOf(listOf("e", "c".repeat(64))),
        content = "hello",
        sig = "d".repeat(128)
    )

    @Test
    fun `EVENT envelope matches expected NIP-01 wire format`() {
        val expectedEventJson = Json.encodeToString(event)
        val expected = "[\"EVENT\",\"sub1\",$expectedEventJson]"

        assertEquals(expected, RelayResponse.EVENT("sub1", event).toJson())
    }

    @Test
    fun `subscription id is JSON-escaped in the envelope`() {
        val subId = "weird\"id\\"
        val expectedPrefix = "[\"EVENT\",${Json.encodeToString(subId)},"

        assertEquals(expectedPrefix, RelayResponse.EVENT(subId, event).toJson().removeSuffix(Json.encodeToString(event) + "]"))
    }

    @Test
    fun `repeated serialization of the same event id is stable`() {
        val firstBody = RelayResponse.EVENT("sub1", event).toJson().removePrefix("[\"EVENT\",\"sub1\",")
        val secondBody = RelayResponse.EVENT("sub2", event).toJson().removePrefix("[\"EVENT\",\"sub2\",")

        assertEquals(firstBody, secondBody)
    }
}
