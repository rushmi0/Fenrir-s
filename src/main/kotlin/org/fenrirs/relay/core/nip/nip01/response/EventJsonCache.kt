package org.fenrirs.relay.core.nip.nip01.response

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

import org.fenrirs.relay.models.Event

import java.util.Collections

/**
 * Caches the serialized JSON body of an [Event], keyed by event id.
 *
 * A saved event is routinely re-serialized once per live subscriber it fans out to
 * (EventDispatcher pushes to N subscription channels, each independently calling
 * `RelayResponse.EVENT(subId, event).toClient(session)`) and the same event can also
 * reappear across overlapping REQ backfills. Nostr events are immutable once signed, so
 * the JSON body for a given id never changes - safe to compute once and reuse verbatim.
 *
 * Bounded + LRU: this only needs to cover the "currently fanning out / recently
 * backfilled" working set, not the relay's entire history, so it's capped well below
 * what would matter on a ~1.5 GB heap.
 */
internal object EventJsonCache {

    private const val MAX_ENTRIES = 4096

    private val cache: MutableMap<String, String> =
        Collections.synchronizedMap(object : LinkedHashMap<String, String>(256, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
                size > MAX_ENTRIES
        })

    fun jsonFor(event: Event): String {
        val id = event.id ?: return Json.encodeToString(event)
        return cache.getOrPut(id) { Json.encodeToString(event) }
    }
}
