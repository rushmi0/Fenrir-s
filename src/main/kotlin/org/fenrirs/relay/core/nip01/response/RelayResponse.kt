package org.fenrirs.relay.core.nip01.response

import io.micronaut.websocket.WebSocketSession

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext

import org.fenrirs.relay.modules.Event
import org.fenrirs.utils.ExecTask.runWithVirtualThreads
import org.fenrirs.utils.ShiftTo.toJsonString

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * RelayResponse เป็นคลาสหลักที่ใช้ในการจัดการการตอบกลับของ Relay
 * สามารถมีหลายประเภทของการตอบกลับได้ โดยแต่ละประเภทจะถูกกำหนดเป็น subclass ของ RelayResponse
 */
sealed class RelayResponse<out T> {

    /**
     * EVENT เป็นการตอบกลับประเภทเหตุการณ์
     * @param subscriptionId ไอดีที่ใช้ในการติดตามหรืออ้างอิงไปถึงการร้องขอนั้นๆ จากไคลเอนต์
     * @param event เหตุการณ์ที่เกิดขึ้น
     * ใช้ในการส่งเหตุการณ์ที่ได้รับการร้องขอจากไคลเอนต์
     */
    data class EVENT(val subscriptionId: String, val event: Event) : RelayResponse<Unit>()

    /**
     * OK เป็นการตอบกลับประเภทการยืนยันความสำเร็จของการดำเนินการ
     * @param eventId ไอดีของเหตุการณ์ที่ได้รับจากไคลเอนต์
     * @param isSuccess ผลลัพธ์ว่าการดำเนินการสำเร็จหรือไม่
     * @param message ข้อความเพิ่มเติม
     * ใช้ในการบอกสถานะการยอมรับหรือปฏิเสธข้อความ EVENT จากไคลเอนต์
     * จะมีพารามิเตอร์ที่ 2 เป็น true เมื่อเหตุการณ์ได้รับการยอมรับจาก Relay และ false ในกรณีอื่นๆ เช่นการปฏิเสธ EVENT จากไคลเอนต์
     * พารามิเตอร์ที่ 3 จะต้องมีเสมอ อาจจะเป็นสตริงว่างเมื่อพารามิเตอร์ที่ 2 เป็น true หรือเป็น false และแจ้งเหตุผลที่ปฏิเสธ EVENT นั้นๆ
     */
    data class OK(val eventId: String, val isSuccess: Boolean, val message: String = "") : RelayResponse<Unit>()

    /**
     * EOSE เป็นการตอบกลับเมื่อสิ้นสุดการส่งข้อมูลของการร้องขอข้อมูลนั้นๆ ที่ทางฝั่งคลเอนต์ต้องการ
     * @param subscriptionId ไอดีที่ใช้ในการติดตามหรืออ้างอิงไปถึงการร้องขอนั้นๆ ขอจากไคลเอนต์
     * ใช้ในการบอกว่าจบการส่งเหตุการณ์ที่ Relay เก็บไว้แล้ว และจะเริ่มส่งเหตุการณ์ใหม่ๆ ที่ได้รับตามเวลาจริง
     */
    data class EOSE(val subscriptionId: String) : RelayResponse<Unit>()

    /**
     * CLOSED เป็นการตอบกลับเมื่อการปิดการเชื่อต่อการสื่อสาร
     * @param subscriptionId ไอดีที่ใช้ในการติดตามหรืออ้างอิงไปถึงการร้องขอนั้นๆ ขอจากไคลเอนต์
     * @param message ข้อความเพิ่มเติม
     * ใช้ในการบอกว่าการเชื่อมต่อถูกปิดจากฝั่ง Relay
     * สามารถส่งได้เมื่อ Relay ปฏิเสธการตอบรับการการเชื่อมต่อการสื่อสารหรือเมื่อ Relay ตัดสินใจยกเลิกการเชื่อมต่อก่อนที่ไคลเอนต์จะยกเลิกหรือส่ง CLOSE
     */
    data class CLOSED(val subscriptionId: String, val message: String = "") : RelayResponse<Unit>()

    /**
     * NOTICE เป็นการตอบกลับประเภทการแจ้งเตือน
     * @param message ข้อความแจ้งเตือน
     * ใช้ในการส่งข้อความแจ้งเตือนที่อ่านได้โดยมนุษย์หรือข้อความแจ้งปัญหาหรือข้อผิดพลาดอื่นๆ ที่ต้องการไปยังไคลเอนต์
     */
    data class NOTICE(val message: String) : RelayResponse<Unit>()

    /**
     * ฟังก์ชัน toJson ใช้ในการแปลงข้อมูล ที่ใช้ในการตอบกลับจากรูปแบบ Kotlin Object ไปเป็น JSON string
     * @return JSON string ที่ใช้ในการตอบกลับ
     */
    fun toJson(): String {
        return when (this) {
            is EVENT -> listOf("EVENT", subscriptionId, event).toJsonString()
            is OK -> listOf("OK", eventId, isSuccess, message).toJsonString()
            is EOSE -> listOf("EOSE", subscriptionId).toJsonString()
            is CLOSED -> listOf("CLOSED", subscriptionId, message).toJsonString()
            is NOTICE -> listOf("NOTICE", message).toJsonString()
        }
    }

    /**
     * ฟังก์ชัน toClient ใช้ในการส่งการตอบกลับไปยังไคลเอนต์ผ่าน WebSocket
     * @param session ใช้ในการสื่อสารกับไคลเอนต์
     */
    fun toClient(session: WebSocketSession) {
        runWithVirtualThreads {
            if (session.isOpen) {
                val payload = this.toJson()
                try {
                    session.sendSync(payload)
                    if (this is CLOSED) {
                        session.close()
                    }
                } catch (e: Exception) {
                    LOG.error("Error sending WebSocket message: ${e.message}")
                }
            } else {
                LOG.warn("Attempted to send message to closed WebSocket session.")
            }
        }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(RelayResponse::class.java)
    }

}
