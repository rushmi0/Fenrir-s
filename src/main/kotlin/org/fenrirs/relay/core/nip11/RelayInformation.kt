package org.fenrirs.relay.core.nip11

import jakarta.inject.Inject

import io.micronaut.http.MediaType
import io.micronaut.context.annotation.Bean

import kotlinx.coroutines.runBlocking
import org.fenrirs.stored.Environment

import java.io.File
import java.nio.charset.Charset

@Bean
class RelayInformation @Inject constructor(private val env: Environment) {


    /**
     * ฟังก์ชันสำหรับดึงข้อมูล relay information (NIP-11)
     * @param contentType: ประเภทของเนื้อหาที่ต้องการ (application/json หรือ text/html)
     * @return ข้อมูล relay information ที่ถูกดึงจาก Redis cache หรือไฟล์ระบบ
     */
    fun loadRelayInfo(contentType: String): String = runBlocking {
        loadContent(contentType)
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
        return """
            {
              "name": "${env.RELAY_NAME}",
              "description": "${env.RELAY_DESCRIPTION}",
              "pubkey": "${env.RELAY_OWNER}",
              "contact": "${env.RELAY_CONTACT}",
              "supported_nips": [1,2,4,9,11,13,15,28,50],
              "software": "https://github.com/rushmi0/Fenrir-s",
              "version": "1.0",
              "limitation": {
                 "max_filters": ${env.MAX_FILTERS},
                 "min_pow_difficulty": ${if (env.PROOF_OF_WORK_ENABLED) env.PROOF_OF_WORK_DIFFICULTY else 0},
                 "max_limit": ${env.MAX_LIMIT},
                 "max_message_length": 524288,
                 "payment_required": ${env.PAYMENT_REQ},
                 "auth_required": ${env.AUTH_REQ},
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
