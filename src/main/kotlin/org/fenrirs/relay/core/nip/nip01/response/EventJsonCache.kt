package org.fenrirs.relay.core.nip.nip01.response

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

import org.fenrirs.relay.models.Event
import org.fenrirs.storage.DatabaseFactory

import java.util.concurrent.atomic.AtomicLong

/**
 * Caches the serialized JSON body of an [Event], keyed by event id.
 *
 * A saved event is routinely re-serialized once per live subscriber it fans out to
 * (EventDispatcher pushes to N subscription channels, each independently calling
 * `RelayResponse.EVENT(subId, event).toClient(session)`) and the same event can also
 * reappear across overlapping REQ backfills. Nostr events are immutable once signed, so
 * the JSON body for a given id never changes - safe to compute once and reuse verbatim.
 *
 * Backed entirely by [DatabaseFactory]'s `mem:` H2 cache table, reached only through its
 * suspend API ([DatabaseFactory.getCachedEventJson] / `putCachedEventJson` /
 * `trimCachedEventJson`) so the blocking JDBC work runs on the virtual-thread dispatcher
 * instead of whatever coroutine/event-loop thread called in here - same discipline as
 * every other DB access in this codebase.
 *
 * Bounded well below what would matter on a ~1.5 GB heap, so this only needs to cover the
 * "currently fanning out / recently backfilled" working set, not the relay's entire history.
 */
internal object EventJsonCache {

    private const val MAX_ENTRIES = 4096

    /** Trim only every Nth write so the DELETE cost is amortized, not paid per write. */
    private const val EVICT_EVERY = 256

    private val writeCount = AtomicLong(0)

    suspend fun jsonFor(event: Event): String {
        val id = event.id ?: return Json.encodeToString(event)

        DatabaseFactory.getCachedEventJson(id)?.let { return it }

        val body = Json.encodeToString(event)
        DatabaseFactory.putCachedEventJson(id, body)
        if (writeCount.incrementAndGet() % EVICT_EVERY == 0L) {
            DatabaseFactory.trimCachedEventJson(MAX_ENTRIES)
        }
        return body
    }
}
