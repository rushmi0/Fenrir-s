package org.fenrirs.storage.table

import org.jetbrains.exposed.v1.core.Table

/**
 * Normalized (event_id, tag_name, tag_value) index for the `tags` column of [EVENT].
 *
 * `EVENT.TAGS` stays the source of truth (serialized JSON, needed to rebuild the full tag
 * list for an [org.fenrirs.relay.models.Event]), but filtering on it directly requires
 * casting the jsonb column to text and running an unindexed LIKE scan. This table exists
 * purely so REQ/COUNT filters with a `#<tag>` clause (by far the most common Nostr filter
 * shape after kind/author) can hit a real index instead. Kept in sync with EVENT by
 * StoredServiceImpl on insert/delete.
 */
object EVENT_TAGS : Table("event_tags") {

    val EVENT_ID = varchar("event_id", 64).index()
    val TAG_NAME = varchar("tag_name", 16)
    val TAG_VALUE = varchar("tag_value", 512)

    init {
        index(false, TAG_NAME, TAG_VALUE)
    }
}
