package org.fenrirs.storage.statement

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.fenrirs.relay.core.nip.nip50.SearchEngine
import org.fenrirs.storage.service.StoredService

import org.slf4j.LoggerFactory

import org.fenrirs.relay.models.Event
import org.fenrirs.relay.models.FiltersX
import org.fenrirs.storage.DatabaseFactory.queryTask
import org.fenrirs.storage.NostrRelayConfig
import org.fenrirs.storage.service.SaveOutcome
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException

import org.fenrirs.storage.table.EVENT
import org.fenrirs.storage.table.EVENT.CONTENT
import org.fenrirs.storage.table.EVENT.CREATED_AT
import org.fenrirs.storage.table.EVENT.EVENT_ID
import org.fenrirs.storage.table.EVENT.KIND
import org.fenrirs.storage.table.EVENT.PUBKEY
import org.fenrirs.storage.table.EVENT.SIG
import org.fenrirs.storage.table.EVENT.TAGS
import org.fenrirs.storage.table.EVENT_TAGS

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*



@Singleton
class StoredServiceImpl @Inject constructor(
    private val ts: SearchEngine,
    private val env: NostrRelayConfig
) : StoredService {

    override suspend fun filterList(filters: FiltersX): List<Event>? {
        return queryTask {
            runCatching {

                /**
                 * สร้างคำสั่ง SQL สำหรับการดึงข้อมูลจากตาราง EVENT โดยพิจารณาจากตัวกรองที่ได้รับ
                 *
                 * SELECT * FROM EVENT
                 * WHERE
                 *   EVENT.EVENT_ID IN (:ids)
                 *   AND EVENT.PUBKEY IN (:authors)
                 *   AND EVENT.KIND IN (:kinds)
                 *   AND (jsonb_field ->> 'key' IN (:values) AND jsonb_field ->> 'key2' IN (:values2) ...)
                 *   AND EVENT.CREATED_AT >= :since
                 *   AND EVENT.CREATED_AT <= :until
                 *   AND to_tsvector('simple', EVENT.CONTENT) @@ plainto_tsquery('simple', :search)
                 * LIMIT :limit
                 */

                // เริ่มสร้างคำสั่ง SQL
                val query: Query = EVENT.selectAll()

                // ถ้ามีการระบุ ids ใน filters ให้เพิ่มเงื่อนไขการค้นหา EVENT_ID ใน ids ที่กำหนด
                filters.ids.takeIf { it.isNotEmpty() }?.let { ids ->
                    val fullLengthIds: List<String> = ids.filter { it.length == 64 }
                    val shortIds: List<String> = ids.filter { it.length < 64 && it.all { char -> char == '0' } }

                    if (fullLengthIds.isNotEmpty()) {
                        query.andWhere { EVENT_ID.inList(fullLengthIds) }
                    }

                    if (shortIds.isNotEmpty()) {
                        shortIds.forEach { shortId ->
                            query.andWhere { EVENT_ID.like("$shortId%") }
                        }
                    }
                }

                // ถ้ามีการระบุ authors ใน filters ให้เพิ่มเงื่อนไขการค้นหา PUBKEY ใน authors ที่กำหนด
                filters.authors.takeIf { it.isNotEmpty() }?.let {
                    query.andWhere { PUBKEY.inList(it) }
                }

                filters.kinds.takeIf { it.isNotEmpty() }?.let { kinds ->

                    when {

                        /**
                         * SELECT *
                         * FROM event
                         * WHERE pubkey = 'e4b2c64f0e4e54abb34d5624cd040e05ecc77f0c467cc46e2cc4d5be98abe3e3'
                         *   AND kind = 3
                         * ORDER BY created_at DESC
                         * LIMIT 1;
                         */

                        // ถ้าค่า kinds เป็น 0 และมีการกำหนด authors สั่งให้ดึงข้อมูลที่มี CREATED_AT มากสุด
                        kinds.contains(0) && filters.authors.isNotEmpty() -> {
                            query.andWhere { KIND eq 0 }
                                .orderBy(CREATED_AT to SortOrder.DESC)
                                .limit(1)
                        }

                        // ถ้าค่า kinds เป็น 3 และมีการกำหนด authors สั่งให้ดึงข้อมูลที่มี KIND เท่ากับ 3 และ CREATED_AT มากสุด
                        kinds.contains(3) && filters.authors.isNotEmpty() -> {
                            query.andWhere { KIND eq 3 }
                                .orderBy(CREATED_AT to SortOrder.DESC)
                                .limit(1)
                        }

                        kinds.contains(10002) && filters.authors.isNotEmpty() -> {
                            query.andWhere { KIND eq 10002 }
                                .orderBy(CREATED_AT to SortOrder.DESC)
                                .limit(10002)
                        }

                        else -> query.andWhere { KIND.inList(kinds.map { it.toInt() }.toSet()) }
                    }

                }


                // ถ้ามีการระบุ tags ใน filters ให้เพิ่มเงื่อนไขการค้นหาผ่านตาราง EVENT_TAGS (มี index บน
                // (tag_name, tag_value)) แทนการ cast TAGS เป็น text แล้ว LIKE สแกนทั้งตาราง
                //
                // ต่อ tag key หนึ่งตัว: event ต้องมี "อย่างน้อยหนึ่ง" ค่าใน values ที่ตรงกัน (OR ภายใน key
                // เดียวกัน ตาม NIP-01) แล้ว AND ข้าม key ที่ต่างกัน - ให้ผลตรงกับ FiltersXMatcher.matches
                // ที่ใช้ตรวจ event สดตอน fan-out ผ่าน EventBus
                filters.tags.forEach { (key, values) ->
                    if (values.isNotEmpty()) {
                        val matchingEventIds = EVENT_TAGS
                            .select(EVENT_TAGS.EVENT_ID)
                            .where { (EVENT_TAGS.TAG_NAME eq key) and (EVENT_TAGS.TAG_VALUE inList values) }
                        query.andWhere { EVENT_ID inSubQuery matchingEventIds }
                    }
                }


                // ถ้ามีการระบุ since ใน filters ให้เพิ่มเงื่อนไขการค้นหา CREATED_AT ที่มีค่ามากกว่าหรือเท่ากับ since ที่กำหนด
                filters.since?.let { query.andWhere { CREATED_AT greaterEq it.toInt() } }

                // ถ้ามีการระบุ until ใน filters ให้เพิ่มเงื่อนไขการค้นหา CREATED_AT ที่มีค่าน้อยกว่าหรือเท่ากับ until ที่กำหนด
                filters.until?.let { query.andWhere { CREATED_AT lessEq it.toInt() } }


                // ถ้ามีการระบุ search ใน filters ให้เพิ่มเงื่อนไขการค้นหา CONTENT ที่ตรงกับ search ที่กำหนดโดยใช้ full-text search
                filters.search?.let {
                    query.andWhere {
                        ts.searchQuery(filters.search)
                    }
                }

                // กำหนด limit ของการดึงข้อมูล และเรียงลำดับจากมากไปน้อย
                val limit = filters.limit?.toInt()?.coerceAtMost(env.MAX_LIMIT) ?: 500
                query.limit(limit).orderBy(CREATED_AT to SortOrder.DESC)

                // ดำเนินการ fetch ข้อมูลตามเงื่อนไขที่กำหนดแล้ว map ข้อมูลที่ได้มาเป็น Event objects
                query.map { row ->
                    Event(
                        id = row[EVENT_ID],
                        pubkey = row[PUBKEY],
                        created_at = row[CREATED_AT].toLong(),
                        kind = row[KIND].toLong(),
                        tags = row[TAGS],
                        content = row[CONTENT],
                        sig = row[SIG]
                    )
                }
            }.getOrElse { e ->
                LOG.error("[DATABASE] Failed to filter events", e)
                null
            }
        }
    }


    override suspend fun saveEvent(event: Event): Boolean {
        return queryTask {
            runCatching {

                /**
                 * INSERT INTO EVENT
                 * (event_id, pubkey, created_at, kind, tags, content, sig)
                 * VALUES
                 * (:eventId, :pubkey, :createdAt, :kind, :tags, :content, :sig)
                 */

                EVENT.insert {
                    it[EVENT_ID] = event.id!!
                    it[PUBKEY] = event.pubkey!!
                    it[CREATED_AT] = event.created_at?.toInt()!!
                    it[KIND] = event.kind?.toInt()!!
                    it[TAGS] = event.tags!!
                    it[CONTENT] = event.content!!
                    it[SIG] = event.sig!!
                }
                indexTags(event.id!!, event.tags!!)
                true
            }.getOrElse { e ->
                LOG.error("[DATABASE] Failed to save event id={}", event.id, e)
                false
            }
        }
    }


    override suspend fun saveIfAbsent(event: Event): SaveOutcome {
        return queryTask {
            runCatching {

                /**
                 * INSERT INTO EVENT (...) VALUES (...)
                 * ไม่ SELECT เช็ค duplicate ก่อน เพื่อลดการขอ connection สองครั้งต่อ event หนึ่งตัว
                 * ให้ unique index บน EVENT_ID เป็นตัวตรวจจับ duplicate ผ่าน constraint violation แทน
                 */

                EVENT.insert {
                    it[EVENT_ID] = event.id!!
                    it[PUBKEY] = event.pubkey!!
                    it[CREATED_AT] = event.created_at?.toInt()!!
                    it[KIND] = event.kind?.toInt()!!
                    it[TAGS] = event.tags!!
                    it[CONTENT] = event.content!!
                    it[SIG] = event.sig!!
                }
                indexTags(event.id!!, event.tags!!)
                SaveOutcome.SAVED
            }.getOrElse { e ->
                if (e is ExposedSQLException && e.sqlState == "23505") {
                    SaveOutcome.DUPLICATE
                } else {
                    LOG.error("[DATABASE] Failed to save event id={}", event.id, e)
                    SaveOutcome.FAILED
                }
            }
        }
    }


    override suspend fun selectById(id: String): Event? {
        return queryTask {
            runCatching {

                /**
                 * SELECT * FROM event
                 * WHERE event_id = :id
                 */

                val record = EVENT.selectAll().where { EVENT_ID eq id }.singleOrNull()

                record?.let {
                    Event(
                        id = it[EVENT_ID],
                        pubkey = it[PUBKEY],
                        created_at = it[CREATED_AT].toLong(),
                        kind = it[KIND].toLong(),
                        tags = it[TAGS],
                        content = it[CONTENT],
                        sig = it[SIG]
                    )
                }
            }.getOrElse { e ->
                LOG.error("[DATABASE] Failed to select event id={}", id, e)
                null
            }
        }
    }

    override suspend fun deleteEvent(eventId: String): Boolean {
        return queryTask {
            runCatching {

                /**
                 * DELETE
                 * FROM event
                 * WHERE event_id = :eventId;
                 */
                val deleted = EVENT.deleteWhere { EVENT_ID eq eventId } > 0
                EVENT_TAGS.deleteWhere { EVENT_TAGS.EVENT_ID eq eventId }
                deleted

            }.getOrElse { e ->
                LOG.error("[DATABASE] Failed to delete event id={}", eventId, e)
                false
            }
        }
    }

    /**
     * เก็บ (event_id, tag_name, tag_value) ของทุก tag ที่มีอย่างน้อย 2 สมาชิกลงใน EVENT_TAGS
     * เพื่อให้ filterList ค้นหาด้วย index แทนการ LIKE สแกน TAGS ทั้งตาราง
     */
    private fun indexTags(eventId: String, tags: List<List<String>>) {
        val pairs = tags.filter { it.size >= 2 }.map { it[0].take(16) to it[1].take(512) }
        if (pairs.isEmpty()) return

        EVENT_TAGS.batchInsert(pairs, shouldReturnGeneratedValues = false) { (name, value) ->
            this[EVENT_TAGS.EVENT_ID] = eventId
            this[EVENT_TAGS.TAG_NAME] = name
            this[EVENT_TAGS.TAG_VALUE] = value
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(StoredServiceImpl::class.java)
    }
}
