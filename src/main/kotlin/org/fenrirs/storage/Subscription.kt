package org.fenrirs.storage

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.micronaut.websocket.WebSocketSession
import org.fenrirs.relay.core.nip01.response.RelayResponse
import org.fenrirs.relay.policy.FiltersX

typealias SubscriptionData = Map<String, List<FiltersX>>

object Subscription {

    private val config: Cache<String, SubscriptionData> = Caffeine.newBuilder()
        .maximumSize(50_000)
        .build()

    private fun <T> set(key: String, value: T) {
        @Suppress("UNCHECKED_CAST")
        (config as Cache<String, T>).put(key, value)
    }

    private fun <T> get(key: String): T? {
        @Suppress("UNCHECKED_CAST")
        return (config as Cache<String, T>).getIfPresent(key)
    }

    private fun remove(key: String) = config.invalidate(key)

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
     * บันทึก subscription ใหม่ หรืออัพเดต subscription ที่มีอยู่แล้วใน session ที่ระบุ
     * @param session session ID ที่ต้องการบันทึก subscription
     * @param subscriptionId ID ของ subscription ที่ต้องการบันทึก
     * @param filtersX รายการของ filters ที่เกี่ยวข้องกับ subscription
     * โดยฟังก์ชันนี้จะใช้ในการบันทึกข้อมูล subscription ที่ระบุลงใน cache
     * หาก subscriptionId มีอยู่แล้ว จะทำการอัพเดตข้อมูล filters ของ subscription นั้น
     */
    fun saveSubscription(session: WebSocketSession, subscriptionId: String, filtersX: List<FiltersX>) {
        val subscriptionData = mapOf(subscriptionId to filtersX)
        addSubscription(session, subscriptionData)
    }

    /**
     * ตรวจสอบว่า subscription ที่ระบุใน session นั้นยังคง active อยู่หรือไม่
     * @param session session ID ที่ต้องการตรวจสอบ subscription
     * @param subscriptionId ID ของ subscription ที่ต้องการตรวจสอบสถานะ
     * @return true ถ้า subscription นั้นยังคง active อยู่ (มี filters ที่เกี่ยวข้อง)
     *         false ถ้า subscription นั้นไม่ active หรือไม่มี filters ที่เกี่ยวข้อง
     * ฟังก์ชันนี้จะทำการดึงข้อมูล filters ของ subscription จาก cache
     * ถ้ามี filters ที่เกี่ยวข้องอยู่ในรายการแสดงว่ามี subscription ที่ยัง active
     */
    fun isSubscriptionActive(session: WebSocketSession, subscriptionId: String): Boolean {
        val filters: List<FiltersX> = getSubscription(session, subscriptionId)
        return filters.isNotEmpty() // ถ้าพบ filters ที่ตรงกัน, subscription นั้นยัง active อยู่
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

    /**
     * ดึงข้อมูลทั้งหมดจาก cache
     * @return ข้อมูลทั้งหมดที่เก็บไว้ใน cache
     */
    fun getAllSessions(): Map<String, SubscriptionData> = config.asMap()

}
