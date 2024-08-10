package org.fenrirs.utils


import org.fenrirs.utils.ShiftTo.fromHex
import org.fenrirs.utils.ShiftTo.toBigInteger
import java.math.BigInteger

object Schnorr {

    /**
     * ฟังก์ชัน verify ใช้ในการตรวจสอบความถูกต้องของลายเซ็น Schnorr
     * @param data ข้อมูลที่ต้องการตรวจสอบ
     * @param publicKey คีย์สาธารณะที่ใช้ในการตรวจสอบ
     * @param signature ลายเซ็น Schnorr ที่ต้องการตรวจสอบ
     * @return ผลลัพธ์การตรวจสอบเป็น Boolean (true ถ้าลายเซ็นถูกต้อง, false ถ้าลายเซ็นไม่ถูกต้อง)
     */
    /*
    fun verify(
        data: String,
        publicKey: String,
        signature: String
    ): Boolean {
        return Secp256k1.verifySchnorr(signature.fromHex(), data.fromHex(), publicKey.fromHex())
    }
     */

    fun verify(data: String, publicKey: String, signature: String): Boolean {
        return try {
            verifySignature(data.fromHex(), publicKey.fromHex(), signature.fromHex())
        } catch (e: Exception) {
            false
        }
    }

    private fun verifySignature(msg: ByteArray, pubkey: ByteArray, sig: ByteArray): Boolean {
        if (msg.size != 32) {
            throw Exception("The message must be a 32-byte array.")
        }
        if (pubkey.size != 32) {
            throw Exception("The public key must be a 32-byte array.")
        }
        if (sig.size != 64) {
            throw Exception("The signature must be a 64-byte array.")
        }
        val P: Point = Point.liftX(pubkey) ?: return false
        val r: BigInteger = sig.copyOfRange(0, 32).toBigInteger()
        val s: BigInteger = sig.copyOfRange(32, 64).toBigInteger()
        if (r >= Point.p || s >= Point.n) {
            return false
        }
        val len = 32 + pubkey.size + msg.size
        val buf = ByteArray(len)
        System.arraycopy(sig, 0, buf, 0, 32)
        System.arraycopy(pubkey, 0, buf, 32, pubkey.size)
        System.arraycopy(msg, 0, buf, 32 + pubkey.size, msg.size)
        val e: BigInteger = Point.taggedHash("BIP0340/challenge", buf).toBigInteger().mod(Point.n)
        val R: Point? = Point.add(
            Point.mul(Point.G, s), Point.mul(P, Point.n.subtract(e))
        )
        return R != null && R.hasEvenY() && R.x == r
    }

}
