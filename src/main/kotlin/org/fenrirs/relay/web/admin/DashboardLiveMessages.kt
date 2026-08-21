package org.fenrirs.relay.web.admin

import kotlinx.serialization.Serializable

/** Message envelopes pushed down [AdminStatsSocket] - discriminated by [type] so the frontend can
 * dispatch on one field without a polymorphic (de)serializer, matching what a plain `JSON.parse`
 * + switch reads most naturally on the client.
 *
 * [type] deliberately has no default value even though every call site passes the same literal -
 * kotlinx.serialization's `Json` (`encodeDefaults = false` by default) silently omits a property
 * from the output whenever it's left at its declared default, which would strip this exact field
 * from every message. Making it required forces every construction site to pass it, which forces
 * it onto the wire. */
@Serializable
data class SnapshotMessage(val type: String, val data: RelayStatsResponse)

/** Batched per-kind counts for events saved since the last flush - see [AdminStatsSocket]'s
 * flush interval. The client applies these as deltas onto its own running totals rather than
 * waiting for the next periodic [SnapshotMessage]. */
@Serializable
data class EventsDeltaMessage(val type: String, val kinds: List<KindCountDto>)

@Serializable
data class ConnectionsMessage(val type: String, val count: Int)
