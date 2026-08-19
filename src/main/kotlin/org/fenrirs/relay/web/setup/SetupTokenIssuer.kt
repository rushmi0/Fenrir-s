package org.fenrirs.relay.web.setup

import org.fenrirs.storage.statement.KeyValueStoreImpl
import org.fenrirs.storage.statement.OperatorStoreImpl
import org.fenrirs.utils.ShiftTo.randomBytes
import org.fenrirs.utils.ShiftTo.toHex
import org.fenrirs.utils.ShiftTo.toSha256

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.concurrent.TimeUnit

/**
 * ออก one-time setup token ตอน startup เมื่อยังไม่มี operator ใด ๆ ในระบบ (สถานะ INITIAL_SETUP)
 * เก็บเฉพาะ SHA-256 hash + เวลาหมดอายุไว้ใน [KeyValueStoreImpl] - ตัว token จริงพิมพ์ออก console
 * ครั้งเดียวตอนสร้างเท่านั้น ไม่ persist และไม่ log ที่อื่นอีกเลย (ดู SetupController สำหรับการตรวจสอบ)
 *
 * เรียกจาก [org.fenrirs.relay.SystemPreload] หลังจาก DatabaseFactory.initialize() เสร็จเท่านั้น
 * (ไม่ใช้ ApplicationEventListener<StartupEvent> ของตัวเอง เพื่อไม่ต้องพึ่งลำดับการทำงานของ listener หลายตัว)
 */
object SetupTokenIssuer {

    const val TOKEN_HASH_KEY = "__setup_token_hash"
    const val TOKEN_EXPIRES_AT_KEY = "__setup_token_expires_at"

    private const val TOKEN_TTL_SECONDS = 30L * 60

    private val LOG: Logger = LoggerFactory.getLogger(SetupTokenIssuer::class.java)

    fun issueIfNeeded() {
        if (!OperatorStoreImpl.isEmpty()) return

        val token = randomBytes(32).toHex()
        val expiresAt = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()) + TOKEN_TTL_SECONDS

        KeyValueStoreImpl.set(TOKEN_HASH_KEY, token.toSha256())
        KeyValueStoreImpl.set(TOKEN_EXPIRES_AT_KEY, expiresAt.toString())

        LOG.warn(
            "[SETUP] No admin/operator configured yet. One-time setup token (valid {} minutes): {}",
            TimeUnit.SECONDS.toMinutes(TOKEN_TTL_SECONDS), token
        )
    }

    /**
     * ล้าง setup token ทันทีที่ setup สำเร็จ (หรือหมดอายุ) ป้องกันการใช้ซ้ำ
     */
    fun invalidate() {
        KeyValueStoreImpl.set(TOKEN_HASH_KEY, "")
        KeyValueStoreImpl.set(TOKEN_EXPIRES_AT_KEY, "0")
    }

    /**
     * ตรวจสอบ setup token ที่ client ส่งมา - ต้องตรงกับ hash ที่เก็บไว้และยังไม่หมดอายุ
     */
    fun isValid(candidate: String): Boolean {
        if (candidate.isBlank()) return false

        val storedHash = KeyValueStoreImpl.get(TOKEN_HASH_KEY)
        if (storedHash.isNullOrBlank()) return false

        val expiresAt = KeyValueStoreImpl.get(TOKEN_EXPIRES_AT_KEY)?.toLongOrNull() ?: return false
        val now = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())
        if (now > expiresAt) return false

        return candidate.toSha256() == storedHash
    }
}
