package org.fenrirs.relay.core.policy

data class AdminSession(val pubkey: String, val role: String, val expiresAt: Long)

/**
 * Bearer-token session store สำหรับ Admin Console (HTTP-only, ไม่ผูกกับ WebSocketSession ใด ๆ)
 * แยกต่างหากจาก [AuthSessionStore] โดยตั้งใจ - นั่นคือ session ของการยืนยันตัวตน NIP-42 บน WebSocket
 * ที่ใช้เผยแพร่/อ่าน event ของ relay เอง ส่วนนี้คือ session ของผู้ดูแลระบบที่ล็อกอินเข้า Admin Console
 * ผ่านการเซ็นชื่อ event รูปแบบเดียวกับ NIP-42 (kind 22242) แต่ส่งผ่าน HTTP ครั้งเดียวตอนล็อกอิน
 * (ดู AuthController) แล้วถือ token นี้ไว้ใช้กับทุก request ถัดไป โดยไม่ต้องเปิด WebSocket ค้างไว้
 */
interface AdminSessionStore {

    /** ออก session token ใหม่ให้ pubkey/role นี้ */
    fun issue(pubkey: String, role: String): String

    /** คืน session ถ้า token ยังใช้ได้ (มีอยู่จริงและยังไม่หมดอายุ) มิฉะนั้นคืน null */
    fun resolve(token: String): AdminSession?

    fun revoke(token: String)

    /** เพิกถอนทุก session ที่ออกให้ pubkey นี้ - ใช้ตอนลบ operator ออกจากระบบ */
    fun revokeAll(pubkey: String)
}
