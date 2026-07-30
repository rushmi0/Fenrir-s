package org.fenrirs.utils

import org.fenrirs.relay.models.Event
import java.lang.management.ManagementFactory
import java.math.BigInteger
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.random.Random

object ShiftTo {

    private val HEX_CHARS = "0123456789abcdef".toCharArray()

    // MessageDigest is not thread-safe; one cached instance per thread avoids repeatedly
    // hitting the JDK's synchronized security-provider registry on every hash.
    private val sha256Digest = ThreadLocal.withInitial { MessageDigest.getInstance("SHA-256") }

    /**
     * ฟังก์ชัน randomBytes ใช้ในการสร้างอาร์เรย์ไบต์สุ่มขนาดที่กำหนด
     * @param size ขนาดของอาร์เรย์ไบต์ที่ต้องการสร้าง
     * @return อาร์เรย์ไบต์ที่สร้างขึ้น
     */
    fun randomBytes(size: Int): ByteArray = Random.nextBytes(size)

    /**
     * ฟังก์ชัน toHex ใช้ในการแปลง ByteArray เป็นสตริงที่เป็นเลขฐาน 16
     * @return สตริงที่เป็นเลขฐาน 16
     */
    fun ByteArray.toHex(): String {
        val out = CharArray(size * 2)
        for (i in indices) {
            val v = this[i].toInt() and 0xFF
            out[i * 2] = HEX_CHARS[v ushr 4]
            out[i * 2 + 1] = HEX_CHARS[v and 0x0F]
        }
        return String(out)
    }

    /**
     * ฟังก์ชัน fromHex ใช้ในการแปลงสตริงที่เป็นฐาน 16 เป็น ByteArray
     * @return อาร์เรย์ไบต์ที่ถูกแปลงจากสตริงเลขฐาน 16
     */
    fun String.fromHex(): ByteArray {
        check(length % 2 == 0) { "String length must be even" }

        val bytes = ByteArray(length / 2)
        for (i in bytes.indices) {
            val hi = Character.digit(this[i * 2], 16)
            val lo = Character.digit(this[i * 2 + 1], 16)
            if (hi < 0 || lo < 0) throw NumberFormatException("Invalid hex string: $this")
            bytes[i] = ((hi shl 4) or lo).toByte()
        }
        return bytes
    }

    /**
     * ฟังก์ชัน toSha256 ใช้ในการคำนวณ Hash SHA-256 ของ ByteArray
     * @return อาร์เรย์ไบต์
     */
    fun ByteArray.toSha256(): ByteArray = sha256Digest.get().digest(this)

    /**
     * ฟังก์ชัน toSha256 ใช้ในการคำนวณ Hash SHA-256 ของสตริง
     * @return สตริงที่เป็นเลขฐาน 16
     */
    fun String.toSha256(): String = toByteArray().toSha256().toHex()

    fun String.toBSha256(): ByteArray = toByteArray().toSha256()

    fun ByteArray.toBigInteger() = BigInteger(1, this)

    /**
     * คำนวณ NIP-01 event id: sha256(canonical-serialize([0, pubkey, created_at, kind, tags, content]))
     * เขียน canonical JSON เองแทนการใช้ Jackson reflection เพื่อลด allocation และ CPU ในเส้นทาง hot path
     * ของการตรวจสอบ Event ทุกตัวที่ Relay ได้รับ
     */
    fun generateId(event: Event): String {
        val canonical = buildString {
            append("[0,")
            appendJsonString(event.pubkey.orEmpty())
            append(',')
            append(event.created_at ?: 0L)
            append(',')
            append(event.kind ?: 0L)
            append(",[")
            event.tags?.forEachIndexed { i, tag ->
                if (i > 0) append(',')
                append('[')
                tag.forEachIndexed { j, value ->
                    if (j > 0) append(',')
                    appendJsonString(value)
                }
                append(']')
            }
            append("],")
            appendJsonString(event.content.orEmpty())
            append(']')
        }
        return canonical.toSha256()
    }

    /**
     * เขียนสตริงตามกฎ escaping ของ NIP-01: escape เฉพาะ \n \" \\ \r \t \b \f และอักขระควบคุมอื่น ๆ (< 0x20)
     * ด้วย \u00XX ส่วนอักขระ Unicode ที่เหลือ (รวมถึงที่ไม่ใช่ ASCII) จะถูกเขียนตามตัวเดิมโดยไม่ escape
     */
    private fun StringBuilder.appendJsonString(value: String) {
        append('"')
        for (c in value) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                else -> if (c.code < 0x20) {
                    append("\\u00")
                    append(HEX_CHARS[(c.code shr 4) and 0xF])
                    append(HEX_CHARS[c.code and 0xF])
                } else {
                    append(c)
                }
            }
        }
        append('"')
    }


    /**
     * ฟังก์ชันสำหรับวัดเวลาและการใช้หน่วยความจำของโค้ด
     * @param construct ชื่อของโค้ดหรือระบบที่ต้องการวัด
     * @param block โค้ดที่ต้องการวัดเวลาและการใช้หน่วยความจำ
     * @return ผลลัพธ์ของโค้ด
     */
    inline fun <T> measure(construct: String, crossinline block: () -> T): T {
        val start = System.nanoTime()
        System.gc()
        return runCatching { block() }
            .onSuccess {
                val memoryUsed = measureMemoryMultipleTimes()
                val formattedMemoryUsed = formatMemorySize(memoryUsed)
                println("Took: ${elapsedMillis(start)} ms, Memory used: $formattedMemoryUsed | $construct")
            }
            .onFailure { ex -> println("Exception occurred. $construct. Exception: ${ex.message}") }
            .getOrThrow()
    }

    /**
     * ฟังก์ชันสำหรับวัดหน่วยความจำหลายครั้งและคำนวณค่าเฉลี่ย
     * @return ค่าเฉลี่ยของหน่วยความจำที่ใช้งาน
     */
    fun measureMemoryMultipleTimes(): Long {
        // อ่านค่าหน่วยความจำหลายครั้ง และคำนวณค่าเฉลี่ย
        val measurements = List(100) {
            ManagementFactory.getMemoryMXBean().heapMemoryUsage.used
        }
        return measurements.average().toLong()
    }

    /**
     * ฟังก์ชันสำหรับคำนวณเวลาที่ใช้ในหน่วย milliseconds
     * @param startNanos เวลาเริ่มต้นในหน่วย nanoseconds
     * @return เวลาที่ใช้ในหน่วย milliseconds
     */
    fun elapsedMillis(startNanos: Long): Long = TimeUnit.MILLISECONDS.convert(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS)


    /**
     * ฟังก์ชันสำหรับแปลงขนาดหน่วยความจำเป็น KB, MB, หรือ GB
     * @param bytes ขนาดหน่วยความจำในหน่วย bytes
     * @return ขนาดหน่วยความจำที่ถูกแปลงเป็นหน่วยที่เหมาะสม
     */
    fun formatMemorySize(bytes: Long): String {
        val kilobyte = 1024L
        val megabyte = kilobyte * 1024
        val gigabyte = megabyte * 1024

        return when {
            bytes >= gigabyte -> "%.2f GB".format(bytes.toDouble() / gigabyte)
            bytes >= megabyte -> "%.2f MB".format(bytes.toDouble() / megabyte)
            bytes >= kilobyte -> "%.2f KB".format(bytes.toDouble() / kilobyte)
            else -> "$bytes bytes"
        }
    }


}