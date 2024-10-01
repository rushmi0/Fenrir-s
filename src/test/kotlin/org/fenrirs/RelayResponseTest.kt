package org.fenrirs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

import org.fenrirs.relay.core.nip01.response.RelayResponse
import org.fenrirs.relay.policy.Event

class RelayResponseTest {

    @Test
    fun `test EVENT response to JSON String`() {

        val event = Event(
            id = "0000005b0fc51e70b66db99ba1708b1a1b008c30db35d19d35146b3e09756029",
            pubkey = "161498ed3277aa583c301288de5aafda4f317d2bf1ad0a880198a9dede37a6aa",
            created_at = 1716617176,
            kind = 1,
            tags = listOf(
                listOf("nonce", "19735841", "23")
            ),
            content = "My custom content",
            sig = "954c662c9ee29ccad8a1f30d22b9a5cefcea774f72428ec7344b65e4f31fff24fc4dd0b7874a4d10a1a4c012de013df19a7c33018dda5f1207280f9a28193498"
        )

        val expectedJson = """["EVENT","hsZEOtaDsENYkP5H",{"id":"0000005b0fc51e70b66db99ba1708b1a1b008c30db35d19d35146b3e09756029","pubkey":"161498ed3277aa583c301288de5aafda4f317d2bf1ad0a880198a9dede37a6aa","created_at":1716617176,"kind":1,"tags":[["nonce","19735841","23"]],"content":"My custom content","sig":"954c662c9ee29ccad8a1f30d22b9a5cefcea774f72428ec7344b65e4f31fff24fc4dd0b7874a4d10a1a4c012de013df19a7c33018dda5f1207280f9a28193498"}]"""

        val response = RelayResponse.EVENT("hsZEOtaDsENYkP5H", event)
        val actualJson = response.toJson()

        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `test OK response to JSON String`() {
        val response = RelayResponse.OK("0000005b0fc51e70b66db99ba1708b1a1b008c30db35d19d35146b3e09756029", false, "duplicate: already have this event")
        val expectedJson = """["OK","0000005b0fc51e70b66db99ba1708b1a1b008c30db35d19d35146b3e09756029",false,"duplicate: already have this event"]"""

        val actualJson = response.toJson()

        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `test EOSE response to JSON String`() {
        val response = RelayResponse.EOSE("hsZEOtaDsENYkP5H")
        val expectedJson = """["EOSE","hsZEOtaDsENYkP5H"]"""

        val actualJson = response.toJson()
        println(actualJson)
        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `test CLOSED response to JSON String`() {
        val response = RelayResponse.CANCEL("hsZEOtaDsENYkP5H", "Connection closed")
        val expectedJson = """["CLOSED","hsZEOtaDsENYkP5H","Connection closed"]"""

        val actualJson = response.toJson()

        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `test NOTICE response to JSON String`() {
        val response = RelayResponse.NOTICE("This is a notice")
        val expectedJson = """["NOTICE","This is a notice"]"""

        val actualJson = response.toJson()

        assertEquals(expectedJson, actualJson)
    }
}
