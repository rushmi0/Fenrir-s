package org.fenrirs.relay.models

/**
 * In-memory equivalent of the WHERE clause built in StoredServiceImpl.filterList,
 * used to test a single freshly-saved Event against a client-supplied filter
 * without touching the database. NOTE: `search` (NIP-50 full-text) is matched here
 * with a plain case-insensitive substring check on CONTENT, which is only an
 * approximation of the DB-side tsquery/LIKE ranking used for the initial REQ/COUNT
 * result set — live-update push cannot cheaply reproduce that ranking in memory.
 */
fun FiltersX.matches(event: Event): Boolean {
    if (ids.isNotEmpty() && event.id !in ids) return false
    if (authors.isNotEmpty() && event.pubkey !in authors) return false
    if (kinds.isNotEmpty() && event.kind !in kinds) return false

    since?.let { if ((event.created_at ?: Long.MIN_VALUE) < it) return false }
    until?.let { if ((event.created_at ?: Long.MAX_VALUE) > it) return false }

    if (tags.isNotEmpty()) {
        val eventTags = event.tags ?: emptyList()
        for ((key, allowed) in tags) {
            val hit = eventTags.any { it.size >= 2 && it[0] == key && it[1] in allowed }
            if (!hit) return false
        }
    }

    search?.let { term ->
        if (term.isNotBlank() && !(event.content ?: "").contains(term, ignoreCase = true)) return false
    }

    return true
}