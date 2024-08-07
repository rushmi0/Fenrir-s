package org.fenrirs.stored.statement


import org.fenrirs.relay.modules.Event
import org.fenrirs.relay.modules.FiltersX
import org.fenrirs.stored.table.EVENT
import org.fenrirs.stored.table.EVENT.CONTENT
import org.fenrirs.stored.table.EVENT.CREATED_AT
import org.fenrirs.stored.table.EVENT.EVENT_ID
import org.fenrirs.stored.table.EVENT.KIND
import org.fenrirs.stored.table.EVENT.PUBKEY
import org.fenrirs.stored.table.EVENT.SIG
import org.fenrirs.stored.table.EVENT.TAGS
import org.fenrirs.stored.DatabaseFactory.dbQuery
import org.fenrirs.stored.table.plainToTsquery
import org.fenrirs.stored.table.toTsvector
import org.fenrirs.stored.table.match

import org.fenrirs.utils.ExecTask.runWithVirtualThreads
import org.fenrirs.utils.ExecTask.runWithVirtualThreadsPerTask

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.json.contains
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory


object TestSQL {


    fun filterList(filters: FiltersX): List<Event> {
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

                // เริ่มสร้างคำสั่ง SQL
                val query: Query = EVENT.selectAll()

                // ถ้ามีการระบุ ids ใน filters ให้เพิ่มเงื่อนไขการค้นหา EVENT_ID ใน ids ที่กำหนด
                filters.ids.takeIf { it.isNotEmpty() }?.let { ids ->
                    val fullLengthIds: List<String> = ids.filter { it.length == 64 }
                    val shortIds: List<String> = ids.filter { it.length < 64 && it.all { char -> char == '0' } }

                    if (fullLengthIds.isNotEmpty()) {
                        query.andWhere { EVENT.EVENT_ID.inList(fullLengthIds) }
                    }

                    if (shortIds.isNotEmpty()) {
                        shortIds.forEach { shortId ->
                            query.andWhere { EVENT.EVENT_ID.like("$shortId%") }
                        }
                    }
                }

                // ถ้ามีการระบุ authors ใน filters ให้เพิ่มเงื่อนไขการค้นหา PUBKEY ใน authors ที่กำหนด
                filters.authors.takeIf { it.isNotEmpty() }?.let {
                    query.andWhere { EVENT.PUBKEY.inList(it) }
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
                            query.andWhere { EVENT.KIND eq 0 }
                                .orderBy(EVENT.CREATED_AT to SortOrder.DESC)
                                .limit(1)
                        }

                        // ถ้าค่า kinds เป็น 3 และมีการกำหนด authors สั่งให้ดึงข้อมูลที่มี KIND เท่ากับ 3 และ CREATED_AT มากสุด
                        kinds.contains(3) && filters.authors.isNotEmpty() -> {
                            query.andWhere { EVENT.KIND eq 3 }
                                .orderBy(EVENT.CREATED_AT to SortOrder.DESC)
                                .limit(1)
                        }

                        else -> query.andWhere { EVENT.KIND.inList(kinds.map { it.toInt() }.toSet()) }
                    }

                }

                // ถ้ามีการระบุ tags ใน filters ให้เพิ่มเงื่อนไขการค้นหา TAGS ที่ตรงกับค่าที่กำหนด
                filters.tags.forEach { (key, values) ->
                    values.forEach { value ->
                        val jsonValue = EVENT.TAGS.contains("""[["${key.tag}","$value"]]""")
                        query.andWhere { jsonValue }
                    }
                }


                // ถ้ามีการระบุ since ใน filters ให้เพิ่มเงื่อนไขการค้นหา CREATED_AT ที่มีค่ามากกว่าหรือเท่ากับ since ที่กำหนด
                filters.since?.let { query.andWhere { EVENT.CREATED_AT greaterEq it.toInt() } }

                // ถ้ามีการระบุ until ใน filters ให้เพิ่มเงื่อนไขการค้นหา CREATED_AT ที่มีค่าน้อยกว่าหรือเท่ากับ until ที่กำหนด
                filters.until?.let { query.andWhere { EVENT.CREATED_AT lessEq it.toInt() } }


                // ถ้ามีการระบุ search ใน filters ให้เพิ่มเงื่อนไขการค้นหา CONTENT ที่ตรงกับ search ที่กำหนดโดยใช้ full-text search
                filters.search?.let {
                    //query.andWhere { EVENT.CONTENT.like("%$it%") }

                    val simple = stringLiteral("simple")
                    query.where { toTsvector(simple, CONTENT) match plainToTsquery(simple, stringLiteral(filters.search)) }

                }


                // กำหนด limit ของการดึงข้อมูล ถ้า filters.limit ไม่มีการกำหนดหรือเป็น null ให้ใช้ค่าเริ่มต้นเป็น 500 record
                if (!filters.kinds.contains(0) && !filters.kinds.contains(3)) {
                    query.limit(filters.limit?.toInt() ?: 1_000)
                }

                //LOG.info("SQL Command\n$query")

                // ดำเนินการ fetch ข้อมูลตามเงื่อนไขที่กำหนดแล้ว map ข้อมูลที่ได้มาเป็น Event objects
                transaction {
                    query.map { row ->
                        Event(
                            id = row[EVENT.EVENT_ID],
                            pubkey = row[EVENT.PUBKEY],
                            created_at = row[EVENT.CREATED_AT].toLong(),
                            kind = row[EVENT.KIND].toLong(),
                            tags = row[EVENT.TAGS],
                            content = row[EVENT.CONTENT],
                            sig = row[EVENT.SIG]
                        )
                    }
                }
            } catch (e: Exception) {
                LOG.error("Error filtering events: ${e.stackTrace.joinToString("\n")}")
                emptyList()
            }
        }
    }



    fun saveEvent(event: Event): Boolean {
        return runWithVirtualThreads {
            try {
                /**
                 * INSERT INTO EVENT
                 * (event_id, pubkey, created_at, kind, tags, content, sig)
                 * VALUES
                 * (:eventId, :pubkey, :createdAt, :kind, :tags, :content, :sig)
                 */

                // เริ่มสร้างคำสั่ง SQL
                val query = EVENT.selectAll()
                //LOG.info("\n>> $query")

                dbQuery {
                    EVENT.insert {
                        // Ensure non-null values for required fields
                        it[EVENT_ID] = event.id ?: ""
                        it[PUBKEY] = event.pubkey ?: ""
                        it[CREATED_AT] = event.created_at?.toInt() ?: 0
                        it[KIND] = event.kind?.toInt() ?: 0
                        it[TAGS] = event.tags ?: emptyList()
                        it[CONTENT] = event.content ?: ""
                        it[SIG] = event.sig ?: ""
                    }
                }
                true
            } catch (e: Exception) {
                LOG.error("Error saving event: ${e.message}")
                false
            }
        }
    }


    fun selectById(id: String): Event? {
        return runWithVirtualThreads {
            try {
                /**
                 * SELECT * FROM event
                 * WHERE event_id = :id
                 */

                dbQuery {
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
                }
            } catch (e: Exception) {
                LOG.error("Error selecting event by ID: $id. ${e.message}")
                null
            }
        }
    }






    private val LOG = LoggerFactory.getLogger(TestSQL::class.java)

}
