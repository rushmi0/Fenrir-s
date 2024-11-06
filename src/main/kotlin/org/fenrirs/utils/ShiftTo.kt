package org.fenrirs.utils

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.serialization.json.*
import org.fenrirs.relay.policy.Event
import org.slf4j.LoggerFactory
import java.lang.management.ManagementFactory
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
        val start = System.nanoTime()
        System.gc()
        try {
            return block().also {
                val memoryUsed = measureMemoryMultipleTimes()
                val formattedMemoryUsed = formatMemorySize(memoryUsed)
                println("Took: ${elapsedMillis(start)} ms, Memory used: $formattedMemoryUsed | $construct")
            }
        } catch (ex: Throwable) {
            println("Exception occurred. $construct. Exception: ${ex.message}")
            throw ex
        }
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


    /*
    fun renderTable(data: List<Pair<String, String>>): String {
        val colWidth1 = data.maxOf { it.first.length } + 2
        val colWidth2 = data.maxOf { it.second.length } + 2

        val separator = "${Color.GREEN}+${"─".repeat(colWidth1)}+${"─".repeat(colWidth2)}+${Color.RESET}"
        val table = StringBuilder().apply {
            appendLine(separator)
            data.forEachIndexed { index, (label, value) ->
                appendLine(
                    "${Color.GREEN}│${Color.RESET} ${label.padEnd(colWidth1 - 2)} ${Color.GREEN}│${Color.RESET} ${value.padEnd(colWidth2 - 2)} ${Color.GREEN}│${Color.RESET}"
                )
                if (index < data.size - 1) appendLine(separator) else append(separator)
            }
        }
        return table.toString()
    }
     */


    val LOG = LoggerFactory.getLogger(ShiftTo::class.java)

}