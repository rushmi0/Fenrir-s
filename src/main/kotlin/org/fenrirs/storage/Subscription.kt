package org.fenrirs.storage

import io.github.reactivecircus.cache4k.Cache
import io.micronaut.context.annotation.Bean
import io.micronaut.websocket.WebSocketSession

import org.fenrirs.relay.core.nip01.SubscriptionData
import org.fenrirs.relay.policy.FiltersX

//import org.slf4j.LoggerFactory

@Bean
object Subscription {

    private val config: Cache<String, SubscriptionData> = Cache.Builder<String, SubscriptionData>()
        .maximumCacheSize(50_000)
        .build()

    private fun <T : Any> set(key: String, value: T) {
        @Suppress("UNCHECKED_CAST")
        (config as Cache<String, T>).put(key, value)
    }

    private fun <T : Any> get(key: String): T? {
        @Suppress("UNCHECKED_CAST")
        return (config as Cache<String, T>).get(key)
    }

    private fun remove(key: String) {
        config.invalidate(key)
    }

    /**
     * เพิ่ม subscription ใหม่ให้กับ session ที่ระบุ
     * @param session session ID ที่ต้องการเพิ่ม subscription
     * @param subscriptionData ข้อมูล subscription ที่ต้องการเพิ่ม ซึ่งประกอบด้วย subscriptionId และ filters
     */
    fun addSubscription(session: WebSocketSession, subscriptionData: SubscriptionData) {
        val existingSubscriptions = get<SubscriptionData>(session.id)?.toMutableMap() ?: mutableMapOf()
        existingSubscriptions.putAll(subscriptionData)
        set(session.id, existingSubscriptions)
    }

    /**
     * ดึงข้อมูลทั้งหมดของ session ที่ระบุ
     * @param session session ID ที่ต้องการดึงข้อมูล
     * @return ข้อมูลทั้งหมดของ session หรือ map ว่างหากไม่มีข้อมูล
     */
    fun getSession(session: WebSocketSession): SubscriptionData = get<SubscriptionData>(session.id) ?: mapOf()

    /**
     * ดึงข้อมูลเฉพาะของ subscriptionId ใน session ที่ระบุ
     * @param session session ID ที่ต้องการดึงข้อมูล
     * @param subscriptionId ID ของ subscription ที่ต้องการดึงข้อมูล
     * @return ข้อมูลของ subscription ที่ระบุ หรือ list ว่างหากไม่พบข้อมูล
     */
    fun getSubscription(session: WebSocketSession, subscriptionId: String): List<FiltersX> {
        return getSession(session)[subscriptionId] ?: emptyList()
    }

    /**
     * บันทึก subscription ใหม่ใน session ที่ระบุ
     * @param session session ID ที่ต้องการบันทึก subscription
     * @param subscriptionId ID ของ subscription ที่ต้องการบันทึก
     * @param filtersX รายการของ filters ที่เกี่ยวข้องกับ subscription
     * โดยฟังก์ชันนี้จะใช้ในการบันทึกข้อมูล subscription ที่ระบุลงในระบบ
     * หาก subscriptionId มีอยู่แล้ว จะทำการอัพเดตข้อมูล filters ของ subscription นั้น
     */
    fun saveSubscription(session: WebSocketSession, subscriptionId: String, filtersX: List<FiltersX>) {
        val subscriptionData = mapOf(subscriptionId to filtersX)
        addSubscription(session, subscriptionData)
    }

    /**
     * ตรวจสอบว่า subscription ที่ระบุใน session นั้นยังคงมีอยู่หรือไม่
     * และตรวจสอบว่า WebSocketSession นั้นยังเปิดอยู่หรือไม่
     * @param session session ID ที่ต้องการตรวจสอบ subscription
     * @param subscriptionId ID ของ subscription ที่ต้องการตรวจสอบสถานะ
     * @return true ถ้า subscription นั้นยังคงอยู่ (มี filters ที่เกี่ยวข้องและ WebSocketSession ยังเปิดอยู่)
     *         false ถ้า subscription นั้นไม่มีอยู่ในระบบแล้วหรือ WebSocketSession ถูกปิดไปแล้ว
     */
    fun isSubscriptionActive(session: WebSocketSession, subscriptionId: String): Boolean {
        // ตรวจสอบว่า session ยังเปิดอยู่หรือไม่
        if (!session.isOpen) {
            // ถ้า session ถูกปิดไปแล้ว ให้ล้างข้อมูล session นั้นออก
            clearSession(session)
            //LOG.info("${PURPLE}clear: $RESET$session")
            return false
        }
        //LOG.info("session: $session")

        // ตรวจสอบว่ามี subscription ที่เกี่ยวข้องหรือไม่
        val filters: List<FiltersX> = getSubscription(session, subscriptionId)
        //LOG.info("Subscription ${filters.size}: $filters")
        return filters.isNotEmpty()
    }


    /**
     * ลบ subscription จาก session ที่ระบุ
     * @param session session ID ที่ต้องการลบ subscription
     * @param subscriptionId ID ของ subscription ที่ต้องการลบ
     */
    fun clearSubscription(session: WebSocketSession, subscriptionId: String) {
        val existingSubscriptions = get<SubscriptionData>(session.id)?.toMutableMap()
        existingSubscriptions?.remove(subscriptionId)
        set(session.id, existingSubscriptions ?: mapOf())
    }

    /**
     * ลบข้อมูลทั้งหมดของ session ที่ระบุ
     * @param session session ID ที่ต้องการลบข้อมูลทั้งหมด
     */
    fun clearSession(session: WebSocketSession) = remove(session.id)


    //private val LOG = LoggerFactory.getLogger(Subscription::class.java)

}
