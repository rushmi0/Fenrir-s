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

fun main() {

    val difficulty: Long = 23

    val workList = listOf(
        "000000bc2766a12eb379a84e5c25ee4bbdc0ebd87f462f956524bb6028556c5d",
        "03c9747c24414630cc1c5d701f6124fa2fe6d87756491c866b3c9d1ed2fa11aa",
        "28944df34cd19d2430de501485ded951d14479e349ea4073bb75583b62bd85ee",
        "021a66a9a13ce6d28368f5112cbe9e03c557067e6ad28007c301a1d48dd2f629",
        "0084fac59f4cb056d681c22a36ba4c12804f489dd6c2a29f9c3f607d5e639c39",
        "00000000000000000000000000f9483da878eb354ce600a42b97d6f345c5e01a",
        "000a908dcc9fa70e3a5905d51f80ecf1f863d5b1344239a28435953d76319245",
        "0001d7e54efb7923f5da1b12f13144433f948e135d5d8e84b9e863c4ed5c2757",
        "0000a832c414db74a1cc66894989d2e91851ec3e718b278dd5bea076fc878134",
        "000003bdcd6ee095997816d13674fe1c64cff5e7497efb0d27d02964fa9984da",
        "000000000000003bb725c35fb13e348b0b5b27425c9fa151d681b5cad81c070d",
        "0000005b0fc51e70b66db99ba1708b1a1b008c30db35d19d35146b3e09756029",
        "000000000e9d97a1ab09fc381030b346cdd7a142ad57e6df0b46dc9bef6c7e2d"
    )

    workList.forEach { index ->
        val isValid = ProofOfWork.checkProofOfWork(index, difficulty)
        println("Hex: $index, Valid PoW: $isValid")
    }

}
