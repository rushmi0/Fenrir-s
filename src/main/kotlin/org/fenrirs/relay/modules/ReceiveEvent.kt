package org.fenrirs.relay.modules

import kotlinx.serialization.Serializable

@Serializable
data class ReceiveEvent(
    val cmd: String,
    val subId: String,
    val event: Event
)