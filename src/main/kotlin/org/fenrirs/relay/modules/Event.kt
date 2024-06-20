package org.fenrirs.relay.modules

import kotlinx.serialization.Serializable

@Serializable
data class Event(
    val id: String? = null,
    val pubkey: String? = null,
    val created_at: Long? = null,
    val kind: Long? = null,
    val tags: List<List<String>>? = null,
    val content: String? = null,
    val sig: String? = null
)
