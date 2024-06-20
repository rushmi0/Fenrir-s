package org.fenrirs.relay.service.nip13

import jakarta.inject.Singleton
import java.math.BigInteger

@Singleton
object ProofOfWork {


    /**
     * ฟังก์ชัน difficulty ใช้ในการคำนวณระดับความยากของ Proof of Work จากค่าฮาชของของ Event (hex)
     * @param hex ค่า Event ID เป็นรหัสฐานสิบหก
     * @return ระดับความยากของ Proof of Work ในรูปของจำนวนเต็ม
     */
    private fun difficulty(hex: String): Long {
        val digest = BigInteger(hex, 16).toByteArray()
        return 256 - digest.size * 8 + digest[0].countLeadingZeroBits().toLong()
    }

    /**
     * ฟังก์ชัน checkProofOfWork ใช้ในการตรวจสอบว่า Proof of Work มีความยากตามที่กำหนดหรือไม่
     * @param hex ค่า Event ID เป็นรหัสฐานสิบหก
     * @param difficultyTarget ระดับความยากของ Proof of Work ที่ต้องการตรวจสอบ
     * @return true หาก Proof of Work มีความยากตามที่กำหนด, false หากไม่มีความยาก
     */
    fun checkProofOfWork(hex: String, difficultyTarget: Long): Boolean = difficulty(hex) >= difficultyTarget


}