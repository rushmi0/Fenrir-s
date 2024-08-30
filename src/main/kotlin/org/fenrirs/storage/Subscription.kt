package org.fenrirs.storage

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
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
     * @param sessionId session ID ที่ต้องการเพิ่ม subscription
     * @param subscriptionData ข้อมูล subscription ที่ต้องการเพิ่ม ซึ่งประกอบด้วย subscriptionId และ filters
     */
    fun addSubscription(sessionId: String, subscriptionData: SubscriptionData) {
        val existingSubscriptions = get<SubscriptionData>(sessionId)?.toMutableMap() ?: mutableMapOf()
        existingSubscriptions.putAll(subscriptionData)
        set(sessionId, existingSubscriptions)
    }

    /**
     * ดึงข้อมูลทั้งหมดของ session ที่ระบุ หรือข้อมูลเฉพาะของ subscription ที่ระบุ
     * @param sessionId session ID ที่ต้องการดึงข้อมูล
     * @param subscriptionId ID ของ subscription ที่ต้องการดึงข้อมูล (เป็น null หากต้องการข้อมูลทั้งหมด)
     * @return ข้อมูล subscriptions ที่เก็บไว้ใน cache หรือ null หากไม่มีข้อมูล
     */
    fun getSubscription(sessionId: String, subscriptionId: String? = null): Any? {
        val subscriptions = get<SubscriptionData>(sessionId) ?: return null

        return if (subscriptionId != null) {
            subscriptions[subscriptionId]
        } else {
            subscriptions
        }
    }

    /**
     * ลบ subscription จาก session ที่ระบุ
     * @param sessionId session ID ที่ต้องการลบ subscription
     * @param subscriptionId ID ของ subscription ที่ต้องการลบ
     */
    fun clearSubscription(sessionId: String, subscriptionId: String) {
        val existingSubscriptions = get<SubscriptionData>(sessionId)?.toMutableMap()
        existingSubscriptions?.remove(subscriptionId)
        set(sessionId, existingSubscriptions ?: mapOf())
    }

    /**
     * ลบข้อมูลทั้งหมดของ session ที่ระบุ
     * @param sessionId session ID ที่ต้องการลบข้อมูลทั้งหมด
     */
    fun clearSession(sessionId: String) = remove(sessionId)

    /**
     * ดึงข้อมูลทั้งหมดจาก cache
     * @return ข้อมูลทั้งหมดที่เก็บไว้ใน cache
     */
    fun getAllSessions(): Map<String, SubscriptionData> {
        return config.asMap()
    }
}
