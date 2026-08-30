package org.fenrirs.relay.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class FiltersX(
    val ids: Set<String> = emptySet(),
    val authors: Set<String> = emptySet(),
    val kinds: Set<Long> = emptySet(),
    val tags: Map<String, Set<String>> = emptyMap(),
    val since: Long? = null,
    val until: Long? = null,
    val limit: Long? = null,
    val search: String? = null
) {
    class Builder {
        var ids: Set<String> = emptySet()
        var authors: Set<String> = emptySet()
        var kinds: Set<Long> = emptySet()
        var tags: Map<String, Set<String>> = emptyMap()
        var since: Long? = null
        var until: Long? = null
        var limit: Long? = null
        var search: String? = null

        fun build(): FiltersX = FiltersX(ids, authors, kinds, tags, since, until, limit, search)
    }
}

fun FiltersX(block: FiltersX.Builder.() -> Unit): FiltersX = FiltersX.Builder().apply(block).build()

/** Serializes this filter to its NIP-01 wire form, e.g. for splicing into a REQ/COUNT envelope. */
fun FiltersX.toJson(): String = Json.encodeToString(this)

