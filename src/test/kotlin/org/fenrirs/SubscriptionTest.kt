package org.fenrirs

import io.micronaut.websocket.WebSocketSession
import org.fenrirs.relay.policy.FiltersX
import org.fenrirs.storage.Subscription.addSubscription
import org.fenrirs.storage.Subscription.clearSession
import org.fenrirs.storage.Subscription.clearSubscription
import org.fenrirs.storage.Subscription.getAllSessions
import org.fenrirs.storage.Subscription.getSession
import org.fenrirs.storage.Subscription.getSubscription
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class SubscriptionTest {

    private lateinit var mockSession: WebSocketSession
    private val sessionId = "sessionId123"
    private val subscriptionId1 = "sub_id_01"
    private val subscriptionId2 = "sub_id_02"

    private val filters1 = FiltersX(
        kinds = setOf(0),
        authors = setOf("e4b2c64f0e4e54abb34d5624cd040e05ecc77f0c467cc46e2cc4d5be98abe3e3")
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
        mockSession = mock(WebSocketSession::class.java)
        `when`(mockSession.id).thenReturn(sessionId)
        clearSession(mockSession)
    }

    @Test
    fun `test add and get all subscriptions`() {
        // เพิ่ม subscription ใหม่ให้กับ session ที่ระบุ
        addSubscription(mockSession, subscriptionData1)
        addSubscription(mockSession, subscriptionData2)

        // ดึงข้อมูล subscription ทั้งหมดจาก session
        val allSubscriptions = getSession(mockSession)
        assertNotNull(allSubscriptions)

        val subscriptionMap = allSubscriptions as Map<*, *>
        assertTrue(subscriptionMap.containsKey(subscriptionId1))
        assertTrue(subscriptionMap.containsKey(subscriptionId2))

        // เปรียบเทียบข้อมูลที่ดึงออกมากับข้อมูลที่บันทึกไว้
        val retrievedSubscriptionData1 = subscriptionMap[subscriptionId1]
        val retrievedSubscriptionData2 = subscriptionMap[subscriptionId2]

        assertEquals(subscriptionData1[subscriptionId1], retrievedSubscriptionData1)
        assertEquals(subscriptionData2[subscriptionId2], retrievedSubscriptionData2)

        // เปรียบเทียบแต่ละ filter ใน subscriptionData1
        val expectedFilters1 = subscriptionData1[subscriptionId1]
        val expectedFilters2 = subscriptionData2[subscriptionId2]

        // เปรียบเทียบแต่ละ filter ใน subscriptionData1 โดยการใช้ assertEquals
        assertEquals(expectedFilters1, retrievedSubscriptionData1)
        assertEquals(expectedFilters2, retrievedSubscriptionData2)
    }

    @Test
    fun `test add and get specific subscription`() {
        // เพิ่ม subscription ใหม่ให้กับ session ที่ระบุ
        addSubscription(mockSession, subscriptionData1)

        // ดึงข้อมูล subscription เฉพาะที่ระบุ
        val specificSubscription = getSubscription(mockSession, subscriptionId1)
        println(specificSubscription)
        assertNotNull(specificSubscription)

        val filtersList = specificSubscription as List<*>
        assertTrue(filtersList.contains(filters1))
        assertTrue(filtersList.contains(filters2))
    }

    @Test
    fun `test clear subscription`() {
        // เพิ่ม subscription ใหม่ให้กับ session ที่ระบุ
        addSubscription(mockSession, subscriptionData1)
        addSubscription(mockSession, subscriptionData2)

        // ลบ subscription ที่ระบุออกจาก session
        clearSubscription(mockSession, subscriptionId1)

        // ตรวจสอบว่า subscription ที่ระบุถูกลบออกไปแล้ว
        val remainingSubscriptions = getSession(mockSession)
        assertNotNull(remainingSubscriptions)

        val subscriptionMap = remainingSubscriptions as Map<*, *>
        assertFalse(subscriptionMap.containsKey(subscriptionId1))
        assertTrue(subscriptionMap.containsKey(subscriptionId2))
    }

    @Test
    fun `test get all sessions`() {
        // เพิ่ม subscription ให้กับหลาย session
        val anotherSessionId = "sessionId456"
        val anotherMockSession = mock(WebSocketSession::class.java)
        `when`(anotherMockSession.id).thenReturn(anotherSessionId)

        addSubscription(mockSession, subscriptionData1)
        addSubscription(anotherMockSession, subscriptionData2)

        // ดึงข้อมูลทั้งหมดจาก cache
        val allSessions = getAllSessions()
        assertTrue(allSessions.containsKey(sessionId))
        assertTrue(allSessions.containsKey(anotherSessionId))

        // ตรวจสอบข้อมูลในแต่ละ session
        assertNotNull(allSessions[sessionId])
        assertNotNull(allSessions[anotherSessionId])
    }

    @Test
    fun `test clear session`() {
        // เพิ่ม subscription ใหม่ให้กับ session ที่ระบุ
        addSubscription(mockSession, subscriptionData1)
        addSubscription(mockSession, subscriptionData2)

        // ลบข้อมูลทั้งหมดของ session
        clearSession(mockSession)

        // ตรวจสอบว่าข้อมูลทั้งหมดของ session ถูกลบออก
        val clearedData = getSession(mockSession)
        assertTrue(clearedData.isEmpty())
    }

}
