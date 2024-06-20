package org.fenrirs.utils

import fr.acinq.secp256k1.Secp256k1
import org.fenrirs.utils.ShiftTo.fromHex
import org.fenrirs.utils.ShiftTo.toHex

object Schnorr {

    /**
     * ฟังก์ชัน verify ใช้ในการตรวจสอบความถูกต้องของลายเซ็น Schnorr
     * @param data ข้อมูลที่ต้องการตรวจสอบ
     * @param publicKey คีย์สาธารณะที่ใช้ในการตรวจสอบ
     * @param signature ลายเซ็น Schnorr ที่ต้องการตรวจสอบ
     * @return ผลลัพธ์การตรวจสอบเป็น Boolean (true ถ้าลายเซ็นถูกต้อง, false ถ้าลายเซ็นไม่ถูกต้อง)
     */
    fun verify(
        data: String,
        publicKey: String,
        signature: String
    ): Boolean {
        return Secp256k1.verifySchnorr(signature.fromHex(), data.fromHex(), publicKey.fromHex())
    }


    /**
     * ฟังก์ชัน sign ใช้ในการสร้างลายเซ็น Schnorr สำหรับข้อมูลที่กำหนด
     * @param data ข้อมูลที่ต้องการลงลายเซ็น
     * @param privateKey คีย์ส่วนตัวที่ใช้ในการลงลายเซ็น
     * @param aux ข้อมูลเสริม (ถ้ามี) ที่ใช้ในการเพิ่มความปลอดภัย
     * @return ลายเซ็น Schnorr ที่ถูกสร้างขึ้นในรูปแบบ String (hex)
     */
    fun sign(
        data: String,
        privateKey: String,
        aux: ByteArray? = null
    ): String {
        return Secp256k1.signSchnorr(data.fromHex(), privateKey.fromHex(), aux).toHex()
    }

}
