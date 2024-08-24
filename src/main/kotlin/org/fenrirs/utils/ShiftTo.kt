package org.fenrirs.utils

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.serialization.KSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlinx.serialization.serializer
import org.fenrirs.relay.policy.Event
import org.slf4j.LoggerFactory
import java.lang.management.ManagementFactory
import java.lang.management.MemoryMXBean
import java.math.BigInteger
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.random.Random

object ShiftTo {

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
    fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    /**
     * ฟังก์ชัน fromHex ใช้ในการแปลงสตริงที่เป็นฐาน 16 เป็น ByteArray
     * @return อาร์เรย์ไบต์ที่ถูกแปลงจากสตริงเลขฐาน 16
     */
    fun String.fromHex(): ByteArray {
        check(length % 2 == 0) { "String length must be even" }

        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    /**
     * ฟังก์ชัน toSha256 ใช้ในการคำนวณ Hash SHA-256 ของ ByteArray
     * @return อาร์เรย์ไบต์
     */
    fun ByteArray.toSha256(): ByteArray = MessageDigest.getInstance("SHA-256").digest(this)

    /**
     * ฟังก์ชัน toSha256 ใช้ในการคำนวณ Hash SHA-256 ของสตริง
     * @return สตริงที่เป็นเลขฐาน 16
     */
    fun String.toSha256(): String = toByteArray().toSha256().toHex()

    fun String.toBSha256(): ByteArray = toByteArray().toSha256()

    fun ByteArray.toBigInteger() = BigInteger(1, this)

    fun generateId(event: Event): String {
        return lazy {
            arrayListOf(
                0,
                event.pubkey,
                event.created_at,
                event.kind,
                event.tags,
                event.content
            ).toJsonString().toSha256()
        }.value
    }


    /**
     * ฟังก์ชัน toJsonString ใช้ในการแปลงข้อมูลใดๆเป็นสตริง JSON
     * @return สตริง JSON ที่เป็นผลลัพธ์จากการแปลง Object
     */
    fun Any.toJsonString(): String = jacksonObjectMapper().writeValueAsString(this)

    /**
     * ฟังก์ชัน toJsonElementMap ใช้ในการแปลงสตริง JSON เป็น Map ของ JsonElement
     * @return Map ของ JsonElement ที่เป็นผลลัพธ์จากการแปลงสตริง JSON
     */
    fun String.toJsonEltMap(): Map<String, JsonElement> {
        val json = Json { isLenient = true }
        return json.parseToJsonElement(this).jsonObject
    }

    fun String.toJsonEltArray(): JsonArray {
        val json = Json { ignoreUnknownKeys = true }
        return json.parseToJsonElement(this).jsonArray
    }


    /**
     * ฟังก์ชันสำหรับวัดเวลาและการใช้หน่วยความจำของโค้ด
     * @param construct ชื่อของโค้ดหรือระบบที่ต้องการวัด
     * @param block โค้ดที่ต้องการวัดเวลาและการใช้หน่วยความจำ
     * @return ผลลัพธ์ของโค้ด
     */
    inline fun <T> measure(construct: String, crossinline block: () -> T): T {
        // ขนาดหน่วยความจำที่ใช้งานก่อนการทำงาน
        val memoryMXBean: MemoryMXBean = ManagementFactory.getMemoryMXBean()
        val initialMemory = memoryMXBean.heapMemoryUsage.used
        val start = System.nanoTime()
        try {
            return block().also {
                // ขนาดหน่วยความจำที่ใช้งานหลังจากการทำงาน
                val finalMemory = memoryMXBean.heapMemoryUsage.used
                val memoryUsed = finalMemory - initialMemory // คำนวณความแตกต่างของหน่วยความจำ
                val formattedMemoryUsed = formatMemorySize(memoryUsed)
                LOG.info("Took: ${elapsedMillis(start)} ms for: $construct")
                LOG.info("Memory used: $memoryUsed bytes ($formattedMemoryUsed)")
            }
        } catch (ex: Throwable) {
            LOG.error("Exception occurred. $construct. Exception: ${ex.message}")
            throw ex
        }
    }

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

    val LOG = LoggerFactory.getLogger(ShiftTo::class.java)

}