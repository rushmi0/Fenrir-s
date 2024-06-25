package org.fenrirs.relay.core.nip13

import io.micronaut.context.annotation.Bean
import jakarta.inject.Inject
import org.fenrirs.relay.modules.Event
import java.math.BigInteger

@Bean
class ProofOfWork @Inject constructor() {


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
     * ฟังก์ชัน checkProofOfWork ใช้ในการตรวจสอบหลักฐานการทำงาน
     * @param hex ค่า Event ID เป็นรหัสฐานสิบหก
     * @param difficultyTarget ระดับความยากของ Proof of Work ที่ต้องการตรวจสอบ
     * @return true หาก Proof of Work มีความยากตามที่กำหนด, false หากไม่มีความยาก
     */
    fun checkProofOfWork(hex: String, difficultyTarget: Long): Boolean = difficulty(hex) >= difficultyTarget


    /**
     * ฟังก์ชัน isProofOfWorkEvent ใช้ในการตรวจสอบว่าเหตุการณ์มี tag "nonce" ที่ต้องการหรือไม่
     * @param event เหตุการณ์ที่ต้องการตรวจสอบ
     * @return true หากเหตุการณ์มี tag "nonce" ที่ถูกต้อง, false หากไม่มี
     */
    fun isProofOfWorkEvent(event: Event): Boolean {
        return event.tags?.find { it.isNotEmpty() && it[0] == "nonce" }?.let { nonceTag ->
            nonceTag.size == 3 && nonceTag[2].toLongOrNull() != null
        } ?: false
    }

    /**
     * ฟังก์ชันที่ใช้ในการตรวจสอบ work ของเหตุการณ์ที่รับเข้ามา
     * @param event เหตุการณ์ที่ต้องการตรวจสอบ
     * @return Pair<Boolean, String> หากคำนวณ work แล้วเป็นไปตามที่อ้างใน event จะคืนค่าเป็นจริง และสตริงว่าง
     * ถ้าคำนวณแล้ว work น้อยกว่าที่อ้าง จะคืนค่าเป็นเท็จ และสตริงค่าที่อ้าง และค่าที่คำนวณได้
     */
    fun verifyProofOfWork(event: Event): Pair<Boolean, String> {
        val nonceTag = event.tags!!.find { it.isNotEmpty() && it[0] == "nonce" }!!
        val difficultyTarget = nonceTag[2].toLong()
        val eventDifficulty = difficulty(event.id!!)
        return when {
            eventDifficulty >= difficultyTarget -> true to ""
            else -> false to "pow: difficulty $eventDifficulty is less than $difficultyTarget"
        }
    }

}
