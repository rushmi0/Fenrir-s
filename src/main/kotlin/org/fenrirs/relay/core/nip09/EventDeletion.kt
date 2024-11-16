package org.fenrirs.relay.core.nip09

import io.micronaut.context.annotation.Bean
import jakarta.inject.Inject

import org.fenrirs.relay.policy.Event

import org.fenrirs.storage.statement.StoredServiceImpl

import org.slf4j.LoggerFactory

@Bean
class EventDeletion @Inject constructor(private val sqlExec: StoredServiceImpl) {

    /**
     * ฟังก์ชันสำหรับลบข้อมูลตามที่กำหนดออกจากฐานข้อมูล
     * @param event เหตุการณ์ที่ต้องการลบ
     * @return Pair(true, "") หากลบเรียบร้อย, Pair(false, "error: message") ถ้าไม่สามารถลบได้
     */
    suspend fun deleteEvent(event: Event): Pair<Boolean, String> {
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
                LOG.info("Event deleted")
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
     * ฟังก์ชันสำหรับตรวจสอบว่ารูปแบบเหตุการณ์ที่ระบุตรงตามรูปแบบการลบหรือไม่
     *
     * @param event เหตุการณ์ที่ต้องการตรวจสอบ
     * @return true หากเหตุการณ์ไม่สามารถลบได้, false ถ้าเหตุการณ์ตรงตามเงื่อนไขการลบ
     *
     * การทำงาน:
     * 1. ตรวจสอบว่า `kind` ของเหตุการณ์ต้องไม่ใช่ 5 (ประเภทที่เกี่ยวข้องกับการลบ)
     *    - หาก `kind` ไม่ใช่ 5 จะคืนค่า `false` แสดงว่าเหตุการณ์นี้ไม่ถูกต้องสำหรับการลบ
     * 2. เรียกใช้ฟังก์ชัน `isDeletable` เพื่อตรวจสอบว่าเหตุการณ์ตรงตามเงื่อนไขการลบหรือไม่
     *    - หาก `isDeletable` คืนค่า `true` หมายความว่าเหตุการณ์สามารถลบได้
     *    - หาก `isDeletable` คืนค่า `false` หมายความว่าเหตุการณ์ไม่สามารถลบได้
     *
     * สรุป:
     * - คืนค่า `true` เฉพาะกรณีที่เหตุการณ์ไม่ตรงตามเงื่อนไขการลบ (ไม่สามารถลบได้)
     * - คืนค่า `false` หากเหตุการณ์ตรงตามเงื่อนไขการลบ (สามารถลบได้)
     */
    fun validDeletion(event: Event): Boolean {
        // ตรวจสอบว่า kind ของเหตุการณ์ไม่ใช่ 5
        if (event.kind?.toInt() != 5) {
            return false
        }

        // ตรวจสอบว่าเหตุการณ์ไม่ตรงตามเงื่อนไขการลบ
        return !isDeletable(event)
    }


    /**
     * ตรวจสอบความป็นเจ้าของ event ที่ระบุ
     * @param event เหตุการณ์ที่ต้องการตรวจสอบ
     * @return true หาก pubkey เป็นเจ้าของ event, false ถ้าไม่ใช่
     */
    suspend fun isOwnership(event: Event): Boolean {
        return event.tags?.any {
            event.kind?.toInt() == 5 &&
                it.size > 1 && it[0] == "e" &&
                    sqlExec.selectById(it[1])?.pubkey == event.pubkey
        } ?: false
    }


    companion object {
        private val LOG = LoggerFactory.getLogger(EventDeletion::class.java)
    }

}
