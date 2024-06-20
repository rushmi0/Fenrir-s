package org.fenrirs.relay.service.nip01

import org.fenrirs.relay.modules.Event
import org.fenrirs.utils.Schnorr
import org.fenrirs.utils.ShiftTo.generateId
import org.fenrirs.utils.ShiftTo.toJsonString

object VerifyEvent {

    fun Event.isValidEventId(): Pair<Boolean, String> {
        val actualId = generateId(this)
        return if (this.id != actualId) {
            Pair(false, "Invalid: actual event id $actualId")
        } else {
            Pair(true, "")
        }
    }

    fun Event.isValidSignature(): Pair<Boolean, String> {
        val eventId = if (isValidEventId().first) this.id!! else generateId(this)
        if (!Schnorr.verify(eventId, this.pubkey!!, this.sig!!)) {
            val warning = """
                |Invalid: bad signature
                |  Event: ${this.toJsonString()}
                |  Actual: ${generateId(this)}
            """.trimIndent()
            return Pair(false, warning)
        }
        return Pair(true, "")
    }

}
