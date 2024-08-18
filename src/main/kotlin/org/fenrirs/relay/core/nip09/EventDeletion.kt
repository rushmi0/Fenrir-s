package org.fenrirs.relay.core.nip09

import io.micronaut.context.annotation.Bean
import jakarta.inject.Inject
import jakarta.inject.Singleton

import org.fenrirs.relay.modules.Event
import org.fenrirs.relay.modules.FiltersX
import org.fenrirs.relay.modules.TAG_E

import org.fenrirs.stored.statement.StoredServiceImpl
import org.fenrirs.utils.Color.GREEN

import org.slf4j.LoggerFactory

@Bean
@Singleton
class EventDeletion @Inject constructor(private val sqlExec: StoredServiceImpl) {

    /**
     * ฟังก์ชันสำหรับลบข้อมูลตามที่กำหนดออกจากฐานข้อมูล
     * @param event เหตุการณ์ที่ต้องการลบ
     * @return Pair(true, "") หากลบเรียบร้อย, Pair(false, "error: message") ถ้าไม่สามารถลบได้
     */
    fun deleteEvent(event: Event): Pair<Boolean, String> {
        // ตรวจสอบว่าเหตุการณ์ตรงตามเงื่อนไขที่ต้องการลบหรือไม่
        return if (isDeletable(event)) {

            // ดึง event id ในรายการของฟิลด์ tags
            val eventIds: List<String> =
                event.tags?.filter { it.size > 1 && it[0] == "e" }?.map { it[1] } ?: emptyList()

            // ลบ event ทั้งหมดใน eventIds
            val deletionResults = eventIds.map { eventId ->
                sqlExec.deleteEvent(eventId)
            }

            if (deletionResults.all { it }) {
                LOG.info("Event deleted ${GREEN}successfully")
                true to ""
            } else {
                false to "error: could not delete all events"
            }

        } else {
            false to "error: event is not deletable"
        }
    }

    /**
     * ฟังก์ชันสำหรับตรวจสอบว่าเหตุการณ์สามารถลบได้ตามเงื่อนไขที่กำหนดหรือไม่
     * @param event เหตุการณ์ที่ต้องการตรวจสอบ
     * @return true หากเหตุการณ์สามารถลบได้, false ถ้าไม่สามารถลบได้
     */
    fun isDeletable(event: Event): Boolean {
        return event.kind?.toInt() == 5 && event.tags?.any {
            it.size > 1 && it[0] == "e"
        } ?: false
    }


    /**
     * ตรวจสอบความป็นเจ้าของ event ที่ระบุ
     * @param event เหตุการณ์ที่ต้องการตรวจสอบ
     * @return true หาก pubkey เป็นเจ้าของ event, false ถ้าไม่ใช่
     */
    fun isOwnership(event: Event): Boolean {
        return event.tags?.any {
            event.kind?.toInt() == 5 &&
                it.size > 1 && it[0] == "e" &&
                    sqlExec.selectById(it[1])?.pubkey == event.pubkey
        } ?: false
    }


    /**
     * ฟังก์ชันสำหรับตรวจสอบว่า event id เคยถูกลบไปแล้วหรือไม่
     * @param event เหตุการณ์ที่ต้องการตรวจสอบ
     * @return true ถ้าไม่เคยถูกลบ, false ถ้า event id นั้นเคยถูกลบแล้วจะแสดง id
     */
    fun isEventDeleted(eventID: String): Boolean {

        // คำสั่งค้นหาข้อมูล
        val query = FiltersX(
            tags = mapOf(
                TAG_E to setOf(eventID)
            ),
            kinds = setOf(5) // kind 5 หมายถึง event ที่ถูกลบ
        )

        val eventRecord = sqlExec.filterList(query)
            ?.takeIf { it.isNotEmpty() } // ตรวจสอบว่าผลลัพธ์ไม่ใช่ null หรือเป็น list ที่ไม่ว่าง
            ?.let { eventData ->
                eventData[0].tags // ดึงข้อมูล tags จาก eventData
                    ?.filter { it.isNotEmpty() && it[0] == "e" } // ตรวจสอบว่า tag นั้นมีและ tag แรกคือ "e"
                    ?.map { it[1] } // เอาเฉพาะค่าของ tag ที่สองที่เป็น event id
                    ?.first() // คืนค่าตัวแรกถ้ามี หรือ null ถ้าไม่มี
            }

        // คืนค่า true ถ้า eventRecord เป็น null หรือเป็น list ว่าง
        // คืนค่า false ถ้า eventRecord มีค่า
        return eventRecord.isNullOrEmpty()
    }


    companion object {
        private val LOG = LoggerFactory.getLogger(EventDeletion::class.java)
    }

}
