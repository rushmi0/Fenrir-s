package org.fenrirs.relay.core.nip01

import org.fenrirs.relay.modules.Event
import org.fenrirs.utils.Schnorr
import org.fenrirs.utils.ShiftTo.generateId

object VerifyEvent {

    fun Event.isValidEventId(): Pair<Boolean, String> {
        val actualId = generateId(this)
        return if (this.id != actualId) {
            false to "invalid: bad event id, actual $actualId"
        } else if (this.id.length != 64) {
            false to "invalid: bad event id, expected 64 characters"
        } else {
            true to ""
        }
    }

    fun Event.isValidSignature(): Pair<Boolean, String> {
        val eventId = if (isValidEventId().first) this.id!! else generateId(this)
        return if (!Schnorr.verify(eventId, this.pubkey!!, this.sig!!) || this.sig.length != 128) {
            false to "invalid: bad signature"
        } else {
            true to ""
        }
    }

    fun Event.isEventPublicKeyValid(): Pair<Boolean, String> {
        return if (this.pubkey?.length != 64) {
            false to "invalid: bad public key length, expected 64 characters"
        } else {
            true to ""
        }
    }

}