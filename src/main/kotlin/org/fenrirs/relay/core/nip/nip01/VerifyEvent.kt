package org.fenrirs.relay.core.nip.nip01

import org.fenrirs.relay.models.Event
import org.fenrirs.utils.Schnorr
import org.fenrirs.utils.ShiftTo.generateId

object VerifyEvent {

    /**
     * ตรวจสอบ Event ตามข้อกำหนด NIP-01 ทั้งหมด โดยเรียงลำดับจากการตรวจสอบที่ราคาถูกที่สุดไปแพงที่สุด
     * เพื่อปฏิเสธ Event ที่ไม่ถูกต้องให้เร็วและถูกที่สุดโดยไม่ต้องคำนวณ event id หรือ signature ที่มีราคาแพง
     * เมื่อไม่จำเป็น (pubkey ผิดความยาว -> ไม่ต้องคำนวณ id หรือ signature เลย)
     *
     * ไม่ log ที่นี่ - รายละเอียดเหตุผลที่ปฏิเสธถูกส่งกลับผ่าน [ValidationResult] และ log ครั้งเดียวที่ชั้น
     * protocol (BasicProtocolFlow) ซึ่งรู้บริบทของคำสั่ง (EVENT/AUTH) และ session ด้วย
     */
    fun Event.verifyNip01(): ValidationResult {
        val pubkeyResult = isEventPublicKeyValid()
        if (!pubkeyResult.isValid) {
            return pubkeyResult
        }

        val idResult = isValidEventId()
        if (!idResult.isValid) {
            return idResult
        }

        return isValidSignature()
    }

    fun Event.isValidEventId(): ValidationResult {
        val actualId = generateId(this)
        return if (id == actualId) {
            ValidationResult.Valid
        } else {
            ValidationResult.invalid("invalid: bad event id, actual $actualId")
        }
    }

    /**
     * ตรวจสอบลายเซ็น Schnorr โดยอ้างอิง event id ที่ผ่านการตรวจสอบแล้วจาก [verifyNip01] โดยตรง
     * (ไม่ต้องเรียก isValidEventId() / generateId() ซ้ำอีกครั้งเหมือนโค้ดเดิม)
     */
    fun Event.isValidSignature(): ValidationResult {
        val sig = this.sig!!
        return if (sig.length == 128 && Schnorr.verify(this.id!!, this.pubkey!!, sig)) {
            ValidationResult.Valid
        } else {
            ValidationResult.invalid("invalid: bad signature")
        }
    }

    fun Event.isEventPublicKeyValid(): ValidationResult =
        if (pubkey?.length == 64) {
            ValidationResult.Valid
        } else {
            ValidationResult.invalid("invalid: bad public key length, expected 64 characters")
        }

}