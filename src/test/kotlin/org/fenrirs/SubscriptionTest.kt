package org.fenrirs

import org.fenrirs.relay.policy.FiltersX
import org.fenrirs.storage.Subscription.addSubscription
import org.fenrirs.storage.Subscription.clearSession
import org.fenrirs.storage.Subscription.clearSubscription
import org.fenrirs.storage.Subscription.getAllSessions
import org.fenrirs.storage.Subscription.getSubscription
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SubscriptionTest {

    private val sessionId = "sessionId123"
    private val subscriptionId1 = "subscription1"
    private val subscriptionId2 = "subscription2"

    private val filters1 = FiltersX(
        kinds = setOf(0),
        authors = setOf("author1")
    )

    private val filters2 = FiltersX(
        kinds = setOf(1),
        limit = 5
    )

    private val filters3 = FiltersX(
        kinds = setOf(2),
        limit = 10
    )

    private val subscriptionData1 = mapOf(
        subscriptionId1 to listOf(filters1, filters2)
    )

    private val subscriptionData2 = mapOf(
        subscriptionId2 to listOf(filters3)
    )

    @BeforeEach
    fun setup() {
        // ทำการล้างข้อมูลทั้งหมดใน session ก่อนการทดสอบ
        clearSession(sessionId)
    }

    @Test
    fun `test add and get all subscriptions`() {
        // เพิ่ม subscription ใหม่ให้กับ session ที่ระบุ
        addSubscription(sessionId, subscriptionData1)
        addSubscription(sessionId, subscriptionData2)

        // ดึงข้อมูล subscription ทั้งหมดจาก session
        val allSubscriptions = getSubscription(sessionId)
        assertNotNull(allSubscriptions)
        assertTrue(allSubscriptions is Map<*, *>)

        val subscriptionMap = allSubscriptions as Map<*, *>
        assertTrue(subscriptionMap.containsKey(subscriptionId1))
        assertTrue(subscriptionMap.containsKey(subscriptionId2))
    }

    @Test
    fun `test add and get specific subscription`() {
        // เพิ่ม subscription ใหม่ให้กับ session ที่ระบุ
        addSubscription(sessionId, subscriptionData1)

        // ดึงข้อมูล subscription เฉพาะที่ระบุ
        val specificSubscription = getSubscription(sessionId, subscriptionId1)
        assertNotNull(specificSubscription)
        assertTrue(specificSubscription is List<*>)

        val filtersList = specificSubscription as List<*>
        assertTrue(filtersList.contains(filters1))
        assertTrue(filtersList.contains(filters2))
    }

    @Test
    fun `test clear subscription`() {
        // เพิ่ม subscription ใหม่ให้กับ session ที่ระบุ
        addSubscription(sessionId, subscriptionData1)
        addSubscription(sessionId, subscriptionData2)

        // ลบ subscription ที่ระบุออกจาก session
        clearSubscription(sessionId, subscriptionId1)

        // ตรวจสอบว่า subscription ที่ระบุถูกลบออกไปแล้ว
        val remainingSubscriptions = getSubscription(sessionId)
        assertNotNull(remainingSubscriptions)
        val subscriptionMap = remainingSubscriptions as Map<*, *>
        assertFalse(subscriptionMap.containsKey(subscriptionId1))
        assertTrue(subscriptionMap.containsKey(subscriptionId2))
    }

    @Test
    fun `test clear session`() {
        // เพิ่ม subscription ใหม่ให้กับ session ที่ระบุ
        addSubscription(sessionId, subscriptionData1)
        addSubscription(sessionId, subscriptionData2)

        // ลบข้อมูลทั้งหมดของ session
        clearSession(sessionId)

        // ตรวจสอบว่าข้อมูลทั้งหมดของ session ถูกลบออก
        val clearedData = getSubscription(sessionId)
        assertNull(clearedData)
    }

    @Test
    fun `test get all sessions`() {
        // เพิ่ม subscription ให้กับหลาย session
        val anotherSessionId = "sessionId456"
        addSubscription(sessionId, subscriptionData1)
        addSubscription(anotherSessionId, subscriptionData2)

        // ดึงข้อมูลทั้งหมดจาก cache
        val allSessions = getAllSessions()
        assertTrue(allSessions.containsKey(sessionId))
        assertTrue(allSessions.containsKey(anotherSessionId))

        // ตรวจสอบข้อมูลในแต่ละ session
        assertNotNull(allSessions[sessionId])
        assertNotNull(allSessions[anotherSessionId])
    }
}
