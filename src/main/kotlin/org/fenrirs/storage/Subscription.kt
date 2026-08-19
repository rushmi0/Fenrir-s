package org.fenrirs.storage


import io.micronaut.websocket.WebSocketSession

import org.fenrirs.storage.DatabaseFactory.cacheTask
import org.fenrirs.storage.table.SUBSCRIPTION

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere

object Subscription {

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