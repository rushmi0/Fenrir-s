package org.fenrirs.relay.core.nip01.response

import io.micronaut.context.annotation.Bean
import io.micronaut.core.annotation.Introspected
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.exceptions.WebSocketSessionException
import jakarta.inject.Inject

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

import org.fenrirs.relay.policy.Event
import org.fenrirs.storage.Subscription.clearSubscription
import org.fenrirs.storage.statement.StoredServiceImpl

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * RelayResponse เป็นคลาสหลักที่ใช้ในการจัดการการตอบกลับของ Relay
 * สามารถมีหลายประเภทของการตอบกลับได้ โดยแต่ละประเภทจะถูกกำหนดเป็น subclass ของ RelayResponse
 */
@Bean
@Introspected
@Serializable(with = RelayResponseSerializer::class)
sealed class RelayResponse<out T> {

    @Inject
    lateinit var sqlExec: StoredServiceImpl

    /**
     * EVENT เป็นการตอบกลับประเภทเหตุการณ์ ใช้ในการส่งเหตุการณ์ที่ได้รับการร้องขอจากไคลเอนต์
     * @param subscriptionId ไอดีที่ใช้ในการติดตามหรืออ้างอิงไปถึงการร้องขอนั้นๆ จากไคลเอนต์
     * @param event เหตุการณ์ที่เกิดขึ้น
     */
    data class EVENT(val subscriptionId: String, val event: Event) : RelayResponse<Unit>()


    /**
     * COUNT ใช้ในการตอบกลับประเภทจำนวน ซึ่งส่งคืนจำนวนเหตุการณ์ที่ตรงกับเงื่อนไขที่กำหนด
     * @param subscriptionId ไอดีที่ใช้ในการติดตามหรืออ้างอิงไปถึงการร้องขอนั้นๆ
     * @param countResponse จำนวนเหตุการณ์ที่ตรงตามเงื่อนไข
     */
    data class COUNT(val subscriptionId: String, val countResponse: Any) : RelayResponse<Unit>()


    /**
     * OK เป็นการตอบกลับประเภทการยืนยันความสำเร็จของการดำเนินการ ใช้ในการบอกสถานะการยอมรับหรือปฏิเสธข้อความ EVENT จากไคลเอนต์
     * จะมีพารามิเตอร์ที่ 2 เป็น true เมื่อเหตุการณ์ได้รับการยอมรับจาก Relay และ false ในกรณีอื่นๆ เช่นการปฏิเสธ EVENT จากไคลเอนต์
     * พารามิเตอร์ที่ 3 จะต้องมีเสมอ อาจจะเป็นสตริงว่างเมื่อพารามิเตอร์ที่ 2 เป็น true หรือเป็น false และแจ้งเหตุผลที่ปฏิเสธ EVENT นั้นๆ
     * @param eventId ไอดีของเหตุการณ์ที่ได้รับจากไคลเอนต์
     * @param isSuccess ผลลัพธ์ว่าการดำเนินการสำเร็จหรือไม่
     * @param message ข้อความเพิ่มเติม
     */
    data class OK(val eventId: String, val isSuccess: Boolean, val message: String = "") : RelayResponse<Unit>()


    /**
     * EOSE เป็นการตอบกลับเมื่อสิ้นสุดการส่งข้อมูลของการร้องขอข้อมูลนั้นๆ ที่ทางฝั่งไคลเอนต์ต้องการ
     * ใช้ในการบอกว่าจบการส่งเหตุการณ์ที่ Relay เก็บไว้แล้ว และจะเริ่มส่งเหตุการณ์ใหม่ๆ ที่ได้รับตามเวลาจริง
     * @param subscriptionId ไอดีที่ใช้ในการติดตามหรืออ้างอิงไปถึงการร้องขอนั้นๆ ขอจากไคลเอนต์
     */
    data class EOSE(val subscriptionId: String) : RelayResponse<Unit>()


    /**
     * CANCEL ใช้ในการยืนยันว่าคำขอจากไคลเอนต์ได้ถูกยกเลิกแล้ว
     * @param subscriptionId ไอดีที่ใช้ติดตามหรืออ้างอิงถึงคำร้องขอจากไคลเอนต์
     */
    data class CANCEL(val subscriptionId: String) : RelayResponse<Unit>()


    /**
     * CLOSED ใช้สำหรับแจ้งว่าการเชื่อมต่อถูกปิดโดย Relay
     * @param subscriptionId ไอดีที่ใช้ติดตามหรืออ้างอิงถึงคำร้องขอจากไคลเอนต์
     * @param message ข้อความเพิ่มเติมเกี่ยวกับการปิดการเชื่อมต่อ
     */
    data class CLOSED(val subscriptionId: String, val message: String) : RelayResponse<Unit>()


    /**
     * NOTICE เป็นการตอบกลับประเภทการแจ้งเตือน
     * @param message ข้อความแจ้งเตือนหรือข้อความเกี่ยวกับข้อผิดพลาดที่ต้องแจ้งให้ไคลเอนต์ทราบ
     */
    data class NOTICE(val message: String) : RelayResponse<Unit>()


    data class SCRIPT(val lang: String, val script: String) : RelayResponse<Unit>()


    /**
     * ฟังก์ชัน toJson ใช้ในการแปลงข้อมูล ที่ใช้ในการตอบกลับจากรูปแบบ Kotlin Object ไปเป็น JSON string
     * @return JSON string ที่ใช้ในการตอบกลับ
     */
    fun toJson(): String = Json.encodeToString(RelayResponseSerializer, this)


    /**
     * ฟังก์ชัน toClient ใช้ในการส่งการตอบกลับไปยังไคลเอนต์ผ่าน WebSocket
     * @param session ใช้ในการสื่อสารกับไคลเอนต์
     */
    fun toClient(session: WebSocketSession) {
        when {
            session.isOpen -> {
                try {
                    val payload = this@RelayResponse.toJson()
                    //LOG.info("$session payload: $payload")
                    session.sendAsync(payload)

                    if (this@RelayResponse is CANCEL) {
                        clearSubscription(session, subscriptionId)
                    }
                } catch (e: WebSocketSessionException) {
                    LOG.info("$session is closed, cannot send message")
                }
            }

            else -> LOG.info("$session is closed")
        }
    }


    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(RelayResponse::class.java)
    }

}