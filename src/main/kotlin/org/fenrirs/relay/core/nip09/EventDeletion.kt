package org.fenrirs.relay.core.nip09

import jakarta.inject.Inject
import jakarta.inject.Singleton

import kotlinx.coroutines.runBlocking

import org.fenrirs.relay.modules.Event
import org.fenrirs.stored.statement.StoredServiceImpl
import org.fenrirs.utils.ExecTask.runWithVirtualThreads

import org.slf4j.LoggerFactory

@Singleton
class EventDeletion @Inject constructor(private val sqlExec: StoredServiceImpl) {

    /**
     * ฟังก์ชันสำหรับลบข้อมูลตามที่กำหนดออกจากฐานข้อมูล
     * @param event เหตุการณ์ที่ต้องการลบ
     * @return Pair(true, "") หากลบเรียบร้อย, Pair(false, "error: message") ถ้าไม่สามารถลบได้
     */
    fun deleteEvent(event: Event): Pair<Boolean, String> {
        return runBlocking {
            // ตรวจสอบว่าเหตุการณ์ตรงตามเงื่อนไขที่ต้องการลบหรือไม่
            if (isDeletable(event)) {
                // ดึง event id ในรายการของฟิลด์ tags
                val eventIds: List<String> = event.tags?.filter { it.size > 1 && it[0] == "e" }?.map { it[1] } ?: emptyList()
                // ลบ event ทั้งหมดใน eventIds
                val deletionResults = eventIds.map { eventId ->
                    LOG.info("Deleting event id: $eventId")
                    sqlExec.deleteEvent(eventId)
                }
                if (deletionResults.all { it }) {
                    true to ""
                } else {
                    false to "error: could not delete all events"
                }
            } else {
                false to "error: event is not deletable"
            }
        }
    }


    /**
     * ฟังก์ชันสำหรับตรวจสอบว่าเหตุการณ์สามารถลบได้ตามเงื่อนไขที่กำหนดหรือไม่
     * @param event เหตุการณ์ที่ต้องการตรวจสอบ
     * @return true หากเหตุการณ์สามารถลบได้, false ถ้าไม่สามารถลบได้
     */
    fun isDeletable(event: Event): Boolean {
        return event.kind?.toInt() == 5 && event.tags?.any {
            it.size > 1 && it[0] == "e" && checkPubKeyOwnership(it.subList(1, it.size), event.pubkey!!)
        } ?: false
    }

    /**
     * เมธอดสำหรับตรวจสอบว่า pubkey เป็นเจ้าของ eventIds ที่ระบุ
     * @param eventIds รายการของ eventIds ที่ต้องการตรวจสอบ
     * @param publicKey กุญแจสาธารณะที่ต้องการยืนยัน
     * @return true หาก pubkey เป็นเจ้าของทุก eventIds, false ถ้าไม่ใช่
     */
    private fun checkPubKeyOwnership(eventIds: List<String>, publicKey: String): Boolean {
        return runWithVirtualThreads {
            eventIds.all { eventId ->
                sqlExec.selectById(eventId)?.pubkey == publicKey
            }
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(EventDeletion::class.java)
    }

}
