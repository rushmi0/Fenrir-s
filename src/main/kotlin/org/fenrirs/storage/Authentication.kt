package org.fenrirs.storage

import io.micronaut.websocket.WebSocketSession
import jakarta.inject.Singleton

import org.fenrirs.relay.core.policy.AuthSessionStore
import org.fenrirs.utils.ShiftTo.randomBytes
import org.fenrirs.utils.ShiftTo.toHex

import java.util.concurrent.ConcurrentHashMap


@Singleton
class Authentication : AuthSessionStore {

    private class SessionAuth {
        val challenge: String = randomBytes(16).toHex()
        val pubkeys: MutableSet<String> = ConcurrentHashMap.newKeySet()
    }

    private val sessions = ConcurrentHashMap<String, SessionAuth>()

    /**
     * คืน challenge ของ session นี้ ถ้ายังไม่เคยมีจะสร้างแบบสุ่มให้ใหม่
     * (เรียกตอน session เปิดเพื่อส่งให้ client ครั้งแรก และเรียกซ้ำตอนตรวจสอบ AUTH event ที่ client ส่งกลับมา)
     */
    override fun challengeFor(session: WebSocketSession): String =
        sessions.computeIfAbsent(session.id) { SessionAuth() }.challenge

    /**
     * บันทึกว่า pubkey นี้ผ่านการยืนยันตัวตนแล้วสำหรับ session นี้
     * รองรับหลาย pubkey ต่อหนึ่ง session ตามที่ NIP-42 อนุญาต ("Clients MAY provide signed events from multiple pubkeys")
     */
    fun markAuthenticated(session: WebSocketSession, pubkey: String) {
        sessions.computeIfAbsent(session.id) { SessionAuth() }.pubkeys.add(pubkey)
    }

    /**
     * pubkey ทั้งหมดที่ session นี้ยืนยันตัวตนสำเร็จแล้ว - ใช้โดย [org.fenrirs.relay.core.policy.PolicyController]
     * เพื่อ resolve [org.fenrirs.relay.core.policy.RuleContext] ของ session นี้
     */
    override fun authenticatedPubkeys(session: WebSocketSession): Set<String> =
        authenticatedPubkeys(session.id)

    override fun authenticatedPubkeys(sessionId: String): Set<String> =
        sessions[sessionId]?.pubkeys?.toSet() ?: emptySet()

    /**
     * session นี้ยืนยันตัวตนแล้วหรือยัง (มีอย่างน้อยหนึ่ง pubkey ที่ auth ผ่าน)
     */
    fun isAuthenticated(session: WebSocketSession): Boolean =
        sessions[session.id]?.pubkeys?.isNotEmpty() == true

    fun clearSession(session: WebSocketSession) {
        sessions.remove(session.id)
    }
}