package org.fenrirs.relay.core.nip11

import io.micronaut.context.annotation.Value
import io.micronaut.http.MediaType
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fenrirs.stored.RedisCacheFactory
import java.io.File
import java.nio.charset.Charset
import jakarta.inject.Singleton
import org.fenrirs.utils.Bech32
import org.fenrirs.utils.ShiftTo.toHex
import org.slf4j.LoggerFactory

@Singleton
class RelayInformation @Inject constructor(
    private val redis: RedisCacheFactory,

    @Value("\${nostr.relay.info.name}")
    private val name: String,

    @Value("\${nostr.relay.info.description}")
    private val description: String,

    @Value("\${nostr.relay.info.npub}")
    private val npub: String,

    @Value("\${nostr.relay.info.contact}")
    private val contact: String
) {

    /**
     * ฟังก์ชันสำหรับดึงข้อมูล relay information (NIP-11)
     * @param contentType: ประเภทของเนื้อหาที่ต้องการ (application/json หรือ text/html)
     * @return ข้อมูล relay information ที่ถูกดึงจาก Redis cache หรือไฟล์ระบบ
     */
    suspend fun loadRelayInfo(contentType: String): String = withContext(Dispatchers.IO) {
        // ดึงข้อมูลจาก Redis cache โดยใช้ contentType เป็น key
        redis.getCache(contentType) { it } ?: run {
            // หากไม่มีข้อมูลใน cache ให้โหลดจากไฟล์ระบบ
            val data = loadContent(contentType)
            // แคชข้อมูลที่โหลดมาใหม่ลง Redis พร้อมตั้งเวลาอายุเป็น 200 วินาที
            redis.setCache(contentType, data, 2) { it }
            data
        }
    }

    /**
     * ฟังก์ชันสำหรับโหลดเนื้อหาจากไฟล์ตามประเภทของ contentType
     * @param contentType: ประเภทของเนื้อหาที่ต้องการ
     * @return ข้อมูลที่โหลดจากไฟล์
     */
    private fun loadContent(contentType: String): String {
        return if (contentType == MediaType.APPLICATION_JSON) {
            // ถ้า contentType เป็น application/json ให้โหลดไฟล์ JSON
            relayInfo()
        } else {
            // ถ้า contentType เป็น text/html ให้โหลดไฟล์ HTML
            loadFromFile("src/main/resources/public/index.html")
        }
    }


    private fun relayInfo(): String {
        val publicKey = Bech32.decode(npub).data.toHex()
        return """
            {
              "name": "$name",
              "description": "$description",
              "pubkey": "$publicKey",
              "contact": "$contact",
              "supported_nips": [1,2,4,9,11,12,13,15,16,20,28,50],
              "software": "https://github.com/rushmi0/lnwza007.git",
              "version": "0.1"
            }
        """.trimIndent()
    }


    /**
     * ฟังก์ชันสำหรับอ่านข้อมูลจากไฟล์
     * @param path: เส้นทางของไฟล์ที่จะอ่าน
     * @return ข้อมูลที่อ่านจากไฟล์
     */
    private fun loadFromFile(path: String): String = File(path).readText(Charset.defaultCharset())


    private val LOG = LoggerFactory.getLogger(RelayInformation::class.java)
}