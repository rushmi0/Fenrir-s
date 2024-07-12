package org.fenrirs.stored.statement

import io.micronaut.context.annotation.Bean
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

import org.fenrirs.relay.modules.Event
import org.fenrirs.relay.modules.FiltersX
import org.fenrirs.stored.service.StoredService

import nostr.relay.infra.database.tables.Event.EVENT
import nostr.relay.infra.database.tables.records.EventRecord

import org.jooq.DSLContext
import org.jooq.SelectWhereStep
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType

import org.slf4j.LoggerFactory

import jakarta.inject.Inject
import org.fenrirs.utils.ExecTask.runWithVirtualThreads
import org.fenrirs.utils.ExecTask.runWithVirtualThreadsPerTask

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@Bean
class StoredServiceImpl @Inject constructor(
    private val enforceSQL: DSLContext
) : StoredService {


    override suspend fun saveEvent(event: Event): Boolean {
        return runWithVirtualThreads {
            try {

                /**
                 * INSERT INTO EVENT
                 * (event_id, pubkey, created_at, kind, tags, content, sig)
                 * VALUES
                 * (:eventId, :pubkey, :createdAt, :kind, :tags, :content, :sig)
                 */

                enforceSQL.insertInto(
                    EVENT,
                    EVENT.EVENT_ID,
                    EVENT.PUBKEY,
                    EVENT.CREATED_AT,
                    EVENT.KIND,
                    EVENT.TAGS,
                    EVENT.CONTENT,
                    EVENT.SIG
                )
                    .values(
                        DSL.`val`(event.id).cast(SQLDataType.VARCHAR.length(64)),
                        DSL.`val`(event.pubkey).cast(SQLDataType.VARCHAR.length(64)),
                        DSL.`val`(event.created_at).cast(SQLDataType.INTEGER),
                        DSL.`val`(event.kind).cast(SQLDataType.INTEGER),
                        DSL.`val`(Json.encodeToString(event.tags)).cast(SQLDataType.JSONB),
                        DSL.`val`(event.content).cast(SQLDataType.CLOB),
                        DSL.`val`(event.sig).cast(SQLDataType.VARCHAR.length(128))
                    ).execute() > 0

            } catch (e: Exception) {
                LOG.error("Error saving event: ${e.message}")
                false
            }
        }
    }


    override fun selectById(id: String): Event? {
        return runWithVirtualThreads {
            try {

                /**
                 * SELECT * FROM event
                 * WHERE event_id = :id
                 */

                val record = enforceSQL.selectFrom(EVENT)
                    .where(EVENT.EVENT_ID.eq(DSL.`val`(id)))
                    .fetchOne()

                record?.let {
                    Event(
                        id = it[EVENT.EVENT_ID],
                        pubkey = it[EVENT.PUBKEY],
                        created_at = it[EVENT.CREATED_AT].toLong(),
                        kind = it[EVENT.KIND].toLong(),
                        tags = Json.decodeFromString<List<List<String>>>(it[EVENT.TAGS].toString()),
                        content = it[EVENT.CONTENT],
                        sig = it[EVENT.SIG]
                    )
                }
            } catch (e: Exception) {
                LOG.error("Error selecting event by ID: $id. ${e.message}")
                null
            }
        }
    }


    override suspend fun filterList(filters: FiltersX): List<Event> {
        return runWithVirtualThreadsPerTask {
            try {

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

                val query: SelectWhereStep<EventRecord> = enforceSQL.selectFrom(EVENT)


                // ถ้ามีการระบุ ids ใน filters ให้เพิ่มเงื่อนไขการค้นหา EVENT_ID ใน ids ที่กำหนด
                filters.ids.takeIf { it.isNotEmpty() }?.let { ids ->
                    val fullLengthIds = ids.filter { it.length == 64 }
                    val shortIds = ids.filter { it.length < 64 && it.all { char -> char == '0' } }

                    if (fullLengthIds.isNotEmpty()) {
                        query.where(EVENT.EVENT_ID.`in`(fullLengthIds))
                    }

                    if (shortIds.isNotEmpty()) {
                        shortIds.forEach { shortId ->
                            query.where(EVENT.EVENT_ID.like("$shortId%"))
                        }
                    }
                }


                // ถ้ามีการระบุ authors ใน filters ให้เพิ่มเงื่อนไขการค้นหา PUBKEY ใน authors ที่กำหนด
                filters.authors.takeIf { it.isNotEmpty() }?.let { query.where(EVENT.PUBKEY.`in`(it)) }

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
                        kinds.contains(0) && filters.authors.isNotEmpty() -> query.where(EVENT.KIND.eq(0))
                            .orderBy(EVENT.CREATED_AT.desc()).limit(1)

                        // ถ้าค่า kinds เป็น 3 และมีการกำหนด authors สั่งให้ดึงข้อมูลที่มี KIND เท่ากับ 3 และ CREATED_AT มากสุด
                        kinds.contains(3) && filters.authors.isNotEmpty() -> query.where(EVENT.KIND.eq(3))
                            .orderBy(EVENT.CREATED_AT.desc()).limit(1)

                        else -> query.where(EVENT.KIND.`in`(kinds))
                    }

                }

                // ถ้ามีการระบุ tags ใน filters ให้เพิ่มเงื่อนไขการค้นหา TAGS ที่ตรงกับค่าที่กำหนด
                filters.tags.forEach { (key, values) ->
                    values.forEach { value ->

                        /**
                         * สร้างเงื่อนไขการค้นหาสำหรับฟิลด์ TAGS ที่เป็นประเภท JSONB
                         * โดยใช้ JSONB containment operator (@>) ใน PostgreSQL
                         *
                         * jsonValue ใช้ DSL.field เพื่อสร้างเงื่อนไขการค้นหา โดยใช้โครงสร้าง {0} @> {1}::jsonb
                         * - {0} คือฟิลด์ TAGS ในตาราง EVENT
                         * - {1} คือค่า JSON ที่ต้องการเช็คการครอบคลุม
                         *
                         * ตัวอย่าง JSON ที่ใช้เช็ค: [["key","value"]]
                         * DSL.inline ใช้เพื่อแปลงสตริงเป็นประเภท JSONB ใน SQL
                         */

                        val jsonValue = DSL.field(
                            "{0} @> {1}::jsonb",
                            Boolean::class.java,
                            EVENT.TAGS,
                            DSL.inline("""[["${key.tag}","$value"]]""", SQLDataType.JSONB)
                        )
                        query.where(jsonValue)
                    }
                }

                // ถ้ามีการระบุ since ใน filters ให้เพิ่มเงื่อนไขการค้นหา CREATED_AT ที่มีค่ามากกว่าหรือเท่ากับ since ที่กำหนด
                filters.since?.let { query.where(EVENT.CREATED_AT.greaterOrEqual(it.toInt())) }

                // ถ้ามีการระบุ until ใน filters ให้เพิ่มเงื่อนไขการค้นหา CREATED_AT ที่มีค่าน้อยกว่าหรือเท่ากับ until ที่กำหนด
                filters.until?.let { query.where(EVENT.CREATED_AT.lessOrEqual(it.toInt())) }

                // ถ้ามีการระบุ search ใน filters ให้เพิ่มเงื่อนไขการค้นหา CONTENT ที่ตรงกับ search ที่กำหนดโดยใช้ full-text search
                filters.search?.let {

                    /**
                     * สร้างเงื่อนไขการค้นหาสำหรับฟิลด์ CONTENT โดยใช้ full-text search ใน PostgreSQL
                     *
                     * tsQuery ใช้ DSL.field เพื่อสร้างเงื่อนไขการค้นหา โดยใช้โครงสร้าง to_tsvector('simple', {0}) @@ plainto_tsquery('simple', {1})
                     * - {0} คือฟิลด์ CONTENT ในตาราง EVENT
                     * - {1} คือข้อความค้นหาที่ต้องการค้นหา
                     *
                     * - to_tsvector ใช้แปลงข้อความในฟิลด์ CONTENT เป็นเวกเตอร์ของคำ
                     * - plainto_tsquery ใช้แปลงข้อความค้นหาเป็น tsquery object
                     * - @@ เป็น operator ที่ใช้เช็คว่า tsquery object match กับ to_tsvector หรือไม่
                     */

                    val tsQuery = DSL.field(
                        "to_tsvector('simple', {0}) @@ plainto_tsquery('simple', {1})",
                        Boolean::class.java,
                        EVENT.CONTENT,
                        DSL.inline(it)
                    )
                    query.where(tsQuery)
                }

                // กำหนด limit ของการดึงข้อมูล ถ้า filters.limit ไม่มีการกำหนดหรือเป็น null ให้ใช้ค่าเริ่มต้นเป็น 500 record
                if (!filters.kinds.contains(0) && !filters.kinds.contains(3)) {
                    query.limit(filters.limit?.toInt() ?: 1_000)
                }

                //LOG.info("SQL Command\n$query")

                // ดำเนินการ fetch ข้อมูลตามเงื่อนไขที่กำหนดแล้ว map ข้อมูลที่ได้มาเป็น Event objects
                query.fetch().map { record ->
                    Event(
                        id = record[EVENT.EVENT_ID],
                        pubkey = record[EVENT.PUBKEY],
                        created_at = record[EVENT.CREATED_AT].toLong(),
                        kind = record[EVENT.KIND].toLong(),
                        tags = Json.decodeFromString(record[EVENT.TAGS].toString()),
                        content = record[EVENT.CONTENT],
                        sig = record[EVENT.SIG]
                    )
                }
            } catch (e: Exception) {
                LOG.error("Error filtering events: ${e.stackTrace.joinToString("\n")}")
                emptyList()
            }
        }
    }


    override suspend fun deleteEvent(eventId: String): Boolean {
        return suspendCoroutine { continuation ->
            try {

                /**
                 * DELETE
                 * FROM event
                 * WHERE event_id = :eventId;
                 */

                val deletedCount = enforceSQL.deleteFrom(EVENT)
                    .where(EVENT.EVENT_ID.eq(DSL.`val`(eventId)))
                    .execute() > 0

                continuation.resume(deletedCount)
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(StoredServiceImpl::class.java)
    }
}
