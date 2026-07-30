package org.fenrirs.relay.core.nip.nip42

import jakarta.inject.Inject
import jakarta.inject.Singleton

import org.fenrirs.relay.core.nip.nip01.ValidationResult
import org.fenrirs.relay.models.Event
import org.fenrirs.storage.NostrRelayConfig

import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * ตรวจสอบ event kind:22242 ตามข้อกำหนด NIP-42 (Authentication of clients to relays)
 * การตรวจสอบ event id / signature / pubkey ตาม NIP-01 ทำแยกไว้ที่ [org.fenrirs.relay.core.nip01.VerifyEvent.verifyNip01]
 * ก่อนเรียกใช้ฟังก์ชันนี้แล้ว (ดู CommandFactory.parseAuth) ที่นี่จึงตรวจสอบเฉพาะกฎที่เป็นของ NIP-42 โดยเฉพาะ
 */
@Singleton
class VerifyAuth @Inject constructor(private val env: NostrRelayConfig) {

    /**
     * ตรวจสอบว่า event ที่ client ส่งมาเป็น auth event ที่ถูกต้องสำหรับ challenge ที่ relay ส่งไปก่อนหน้าหรือไม่
     * @param event auth event (kind 22242) ที่ผ่านการตรวจสอบ NIP-01 มาแล้ว
     * @param expectedChallenge challenge ที่ relay สร้างไว้ให้ session นี้
     */
    fun verify(event: Event, expectedChallenge: String): ValidationResult {
        if (event.kind != AUTH_KIND) {
            return ValidationResult.invalid("invalid: auth event must be kind $AUTH_KIND")
        }

        val createdAt = event.created_at
            ?: return ValidationResult.invalid("invalid: auth event missing created_at")

        val now = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())
        if (abs(now - createdAt) > TIMESTAMP_TOLERANCE_SECONDS) {
            return ValidationResult.invalid("invalid: auth event created_at is not close to the current time")
        }

        val challenge = event.tags?.firstOrNull { it.size > 1 && it[0] == "challenge" }?.get(1)
        if (challenge != expectedChallenge) {
            return ValidationResult.invalid("invalid: auth event challenge does not match")
        }

        val relayTag = event.tags.firstOrNull { it.size > 1 && it[0] == "relay" }?.get(1)
        if (!relayUrlMatches(env.RELAY_URL, relayTag)) {
            return ValidationResult.invalid("invalid: auth event relay does not match")
        }

        return ValidationResult.Valid
    }

    companion object {
        private const val AUTH_KIND = 22242L
        private val TIMESTAMP_TOLERANCE_SECONDS = TimeUnit.MINUTES.toSeconds(10)

        /**
         * เทียบแค่ domain name ของ "relay" tag กับ RELAY_URL ที่ตั้งค่าไว้ ตามที่สเปกอนุญาตให้ normalize ได้
         * ("just checking if the domain name is correct should be enough") ถ้าไม่ได้ตั้งค่า RELAY_URL ไว้จะข้ามการตรวจสอบนี้
         *
         * แยกเป็น pure function (ไม่ต้องพึ่ง NostrRelayConfig) เพื่อให้ทดสอบได้ตรง ๆ โดยไม่ต้องอ่านไฟล์ .env จริง
         */
        internal fun relayUrlMatches(configuredUrl: String, relayTag: String?): Boolean {
            if (configuredUrl.isBlank()) return true
            if (relayTag.isNullOrBlank()) return false

            val configuredHost = runCatching { URI(configuredUrl).host }.getOrNull()
            val actualHost = runCatching { URI(relayTag).host }.getOrNull()

            return configuredHost != null && configuredHost.equals(actualHost, ignoreCase = true)
        }
    }
}