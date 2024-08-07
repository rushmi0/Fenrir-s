package org.fenrirs.relay.core.nip11

import io.micronaut.context.annotation.Bean
import io.micronaut.http.MediaType
import jakarta.inject.Inject
import org.fenrirs.stored.RedisCacheFactory
import java.io.File
import java.nio.charset.Charset

import kotlinx.coroutines.runBlocking
import org.fenrirs.relay.policy.NostrRelayConfig
import org.fenrirs.utils.Bech32
import org.fenrirs.utils.ShiftTo.toHex
import org.slf4j.LoggerFactory

@Bean
class RelayInformation @Inject constructor(
    private val redis: RedisCacheFactory,
    private val config: NostrRelayConfig
) {


    /**
     * ฟังก์ชันสำหรับดึงข้อมูล relay information (NIP-11)
     * @param contentType: ประเภทของเนื้อหาที่ต้องการ (application/json หรือ text/html)
     * @return ข้อมูล relay information ที่ถูกดึงจาก Redis cache หรือไฟล์ระบบ
     */
    suspend fun loadRelayInfo(contentType: String): String = runBlocking {
        // ดึงข้อมูลจาก Redis cache โดยใช้ contentType เป็น key
        redis.getCache(contentType) ?: run {
            // หากไม่มีข้อมูลใน cache ให้โหลดจากไฟล์ระบบ
            val data = loadContent(contentType)
            // แคชข้อมูลที่โหลดมาใหม่ลง Redis พร้อมตั้งเวลาอายุเป็น
            redis.setCache(contentType, data, 5)
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
        val publicKey: String = if (config.info.npub.startsWith("npub")) Bech32.decode(config.info.npub).data.toHex() else config.info.npub
        val pow = config.policy.proofOfWork.difficultyMinimum
        return """
            {
              "name": "${config.info.name}",
              "icon": "https://image.nostr.build/fc4a04e980020ed876874fa0142edd9fc22774efa8fa067f96285f2f44965e38.jpg",
              "description": "${config.info.description}",
              "pubkey": "$publicKey",
              "contact": "${config.info.contact}",
              "supported_nips": [1,2,4,9,11,13,15,28,50],
              "software": "https://github.com/rushmi0/Fenrir-s",
              "version": "0.1",
              "limitation": {
                 "max_filters": 5,
                 "min_pow_difficulty": $pow,
                 "max_limit": 1000,
                 "max_message_length": 524288,
                 "payment_required": false,
              }
            }
        """.trimIndent()
    }


    /**
     * ฟังก์ชันสำหรับอ่านข้อมูลจากไฟล์
     * @param path: เส้นทางของไฟล์ที่จะอ่าน
     * @return ข้อมูลที่อ่านจากไฟล์
     */
    private fun loadFromFile(path: String): String = File(path).readText(Charset.defaultCharset())

}