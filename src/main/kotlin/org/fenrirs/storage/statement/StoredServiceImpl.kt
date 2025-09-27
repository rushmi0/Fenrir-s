package org.fenrirs.storage.statement


import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.fenrirs.relay.core.nip50.SearchEngine
import org.fenrirs.storage.service.StoredService

import org.slf4j.LoggerFactory

import org.fenrirs.relay.policy.Event
import org.fenrirs.relay.policy.FiltersX
import org.fenrirs.storage.DatabaseFactory.queryTask
import org.fenrirs.storage.NostrRelayConfig

import org.fenrirs.storage.table.EVENT
import org.fenrirs.storage.table.EVENT.CONTENT
import org.fenrirs.storage.table.EVENT.CREATED_AT
import org.fenrirs.storage.table.EVENT.EVENT_ID
import org.fenrirs.storage.table.EVENT.IS_ACTIVE
import org.fenrirs.storage.table.EVENT.KIND
import org.fenrirs.storage.table.EVENT.PUBKEY
import org.fenrirs.storage.table.EVENT.SIG
import org.fenrirs.storage.table.EVENT.TAGS

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.json.contains


@Singleton
class StoredServiceImpl @Inject constructor(
    private val ts: SearchEngine,
    private val env: NostrRelayConfig
) : StoredService {

    override suspend fun filterList(filters: FiltersX): Result<List<Event>> {
        return runCatching {
            queryTask {
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


                // ถ้ามีการระบุ tags ใน filters ให้เพิ่มเงื่อนไขการค้นหา TAGS ที่ตรงกับค่าที่กำหนด
                filters.tags.forEach { (key, values) ->
                    values.forEach { value ->
                        val jsonValue = TAGS.contains("""[["${key.tag}","$value"]]""")
                        query.andWhere { jsonValue }
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

                query.andWhere { IS_ACTIVE eq "1" }

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
            }
        }.onFailure {
            LOG.error("Error filtering events: ${it.stackTraceToString()}")
        }

    }


    override suspend fun saveEvent(event: Event): Result<Boolean> {
        return runCatching {
            queryTask {
                EVENT.insert {
                    it[EVENT_ID] = event.id!!
                    it[PUBKEY] = event.pubkey!!
                    it[CREATED_AT] = event.created_at?.toInt()!!
                    it[KIND] = event.kind?.toInt()!!
                    it[TAGS] = event.tags!!
                    it[CONTENT] = event.content!!
                    it[SIG] = event.sig!!
                }
                true
            }
        }.onFailure {
            LOG.error("Error saving event: ${it.message}")
        }
    }



    override suspend fun selectById(id: String): Event? {
        return queryTask {
            try {

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
            } catch (e: Exception) {
                LOG.error("Error selecting event by ID: $id. ${e.message}")
                null
            }
        }
    }

    override suspend fun deleteEvent(eventId: String): Boolean {
        return queryTask {
            try {

                /**
                 * DELETE
                 * FROM event
                 * WHERE event_id = :eventId;
                 */
                EVENT.deleteWhere { EVENT_ID eq eventId } > 0

            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(StoredServiceImpl::class.java)
    }
}
