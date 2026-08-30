package org.fenrirs.storage.table

import org.jetbrains.exposed.v1.core.Table

/**
 * L2 backing store for [org.fenrirs.relay.core.nip.nip01.response.EventJsonCache].
 *
 * Lives in its own private in-memory H2 database (see `DatabaseFactory.eventJsonCacheDb`),
 * not the business/config databases - it holds no durable state and must stay invisible to
 * failover/backup logic.
 *
 * [SEQ] is insertion order, used to evict the oldest rows once the cache grows past its cap
 * (see `DatabaseFactory.trimCachedEventJson`) without paying for a true LRU touch on every read.
 */
object EVENT_JSON_CACHE : Table("event_json_cache") {

    val ID = varchar("id", 64)
    val SEQ = long("seq").autoIncrement().index()
    val BODY = text("body")

    override val primaryKey = PrimaryKey(ID)
}
