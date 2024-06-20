package org.fenrirs.relay.modules

import kotlinx.serialization.Serializable

@Serializable
data class FiltersX(
    val ids: Set<String> = emptySet(),
    val authors: Set<String> = emptySet(),
    val kinds: Set<Long> = emptySet(),
    val tags: Map<TagElt, Set<String>> = emptyMap(),
    val since: Long? = null,
    val until: Long? = null,
    val limit: Long? = null,
    val search: String? = null
)

