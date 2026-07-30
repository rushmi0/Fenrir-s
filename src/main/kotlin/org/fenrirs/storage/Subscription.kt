package org.fenrirs.storage


import io.micronaut.websocket.WebSocketSession

import org.fenrirs.relay.core.nip.nip01.SubscriptionData
import org.fenrirs.relay.models.FiltersX

import org.fenrirs.storage.DatabaseFactory.cacheTask
import org.fenrirs.storage.table.SUBSCRIPTION

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert

import kotlin.time.Duration.Companion.minutes

object Subscription {

    private val TTL_MILLIS = 20.minutes.inWholeMilliseconds

    private fun isExpired(updatedAt: Long): Boolean = System.currentTimeMillis() - updatedAt > TTL_MILLIS

    /**
     * เพิ่ม subscription ใหม่ให้กับ session ที่ระบุ
     * @param session session ID ที่ต้องการเพิ่ม subscription
     * @param subscriptionData ข้อมูล subscription ที่ต้องการเพิ่ม ซึ่งประกอบด้วย subscriptionId และ filters
     */
    suspend fun addSubscription(session: WebSocketSession, subscriptionData: SubscriptionData) = cacheTask {
        val now = System.currentTimeMillis()

        subscriptionData.forEach { (subscriptionId, filters) ->
            val existingFilters = SUBSCRIPTION
                .selectAll()
                .where { (SUBSCRIPTION.SESSION_ID eq session.id) and (SUBSCRIPTION.SUBSCRIPTION_ID eq subscriptionId) }
                .firstOrNull()
                ?.let { row -> if (isExpired(row[SUBSCRIPTION.UPDATED_AT])) null else row[SUBSCRIPTION.FILTERS] }

            // ตรวจสอบว่าข้อมูล subscription ที่เพิ่มเข้ามาไม่มีการซ้ำซ้อน
            SUBSCRIPTION.upsert {
                it[SESSION_ID] = session.id
                it[SUBSCRIPTION_ID] = subscriptionId
                it[FILTERS] = (existingFilters ?: emptyList()) + filters
                it[UPDATED_AT] = now
            }
        }
    }

    /**
     * ดึงข้อมูลทั้งหมดของ session ที่ระบุ
     * @param session session ID ที่ต้องการดึงข้อมูล
     * @return ข้อมูลทั้งหมดของ session หรือ map ว่างหากไม่มีข้อมูล
     */
    suspend fun getSession(session: WebSocketSession): SubscriptionData = cacheTask {
        SUBSCRIPTION
            .selectAll()
            .where { SUBSCRIPTION.SESSION_ID eq session.id }
            .filterNot { isExpired(it[SUBSCRIPTION.UPDATED_AT]) }
            .associate { it[SUBSCRIPTION.SUBSCRIPTION_ID] to it[SUBSCRIPTION.FILTERS] }
    }

    /**
     * ดึงข้อมูลเฉพาะของ subscriptionId ใน session ที่ระบุ
     * @param session session ID ที่ต้องการดึงข้อมูล
     * @param subscriptionId ID ของ subscription ที่ต้องการดึงข้อมูล
     * @return ข้อมูลของ subscription ที่ระบุ หรือ list ว่างหากไม่พบข้อมูล
     */
    suspend fun getSubscription(session: WebSocketSession, subscriptionId: String): List<FiltersX> {
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
    suspend fun saveSubscription(session: WebSocketSession, subscriptionId: String, filtersX: List<FiltersX>) {
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
    suspend fun isSubscriptionActive(session: WebSocketSession, subscriptionId: String): Boolean {
        // ตรวจสอบว่า session ยังเปิดอยู่หรือไม่
        if (!session.isOpen) {
            // ถ้า session ถูกปิดไปแล้ว ให้ล้างข้อมูล session นั้นออก
            clearSession(session)
            return false
        }

        // ตรวจสอบว่ามี subscription ที่เกี่ยวข้องหรือไม่
        val filters: List<FiltersX> = getSubscription(session, subscriptionId)
        return filters.isNotEmpty()
    }

    /**
     * ลบ subscription จาก session ที่ระบุ
     * @param session session ID ที่ต้องการลบ subscription
     * @param subscriptionId ID ของ subscription ที่ต้องการลบ
     */
    suspend fun clearSubscription(session: WebSocketSession, subscriptionId: String) = cacheTask {
        SUBSCRIPTION.deleteWhere { (SESSION_ID eq session.id) and (SUBSCRIPTION_ID eq subscriptionId) }
    }

    /**
     * ลบข้อมูลทั้งหมดของ session ที่ระบุ
     * @param session session ID ที่ต้องการลบข้อมูลทั้งหมด
     */
    suspend fun clearSession(session: WebSocketSession) = cacheTask {
        SUBSCRIPTION.deleteWhere { SESSION_ID eq session.id }
    }
}