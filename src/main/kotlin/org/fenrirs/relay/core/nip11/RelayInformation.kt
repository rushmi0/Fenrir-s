package org.fenrirs.relay.core.nip11

import jakarta.inject.Inject
import jakarta.inject.Singleton

import io.micronaut.http.MediaType
import io.micronaut.context.annotation.Bean

import io.micronaut.core.io.ResourceResolver
import io.micronaut.core.io.scan.ClassPathResourceLoader

import org.fenrirs.storage.NostrRelayConfig
import java.io.FileNotFoundException


@Bean
@Singleton
class RelayInformation @Inject constructor(private val env: NostrRelayConfig) {


    /**
     * ฟังก์ชันสำหรับดึงข้อมูล relay information (NIP-11)
     * @param contentType: ประเภทของเนื้อหาที่ต้องการ (application/json หรือ text/html)
     * @return ข้อมูล relay information ที่ถูกดึงจาก Redis cache หรือไฟล์ระบบ
     */
    fun loadRelayInfo(contentType: String): String = loadContent(contentType)

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
            loadFromClasspath("public/index.html")
        }
    }

    private fun relayInfo(): String {
        val minPowDifficultyField = if (env.PROOF_OF_WORK_ENABLED) {
            "\"min_pow_difficulty\": ${env.PROOF_OF_WORK_DIFFICULTY},"
        } else {
            ""
        }

        return """
        {
          "name": "${env.RELAY_NAME}",
          "description": "${env.RELAY_DESCRIPTION}",
          "pubkey": "${env.RELAY_OWNER}",
          "contact": "${env.RELAY_CONTACT}",
          "supported_nips": [1,2,4,9,11,13,15,28,45,50],
          "icon": "https://i.imgur.com/dwLPgio.png",
          "software": "https://github.com/rushmi0/Fenrir-s",
          "version": "1.0.1",
          "limitation": {
             "max_filters": ${env.MAX_FILTERS},
             "max_limit": ${env.MAX_LIMIT},
             $minPowDifficultyField
             "max_message_length": 524288,
             "payment_required": ${env.PAYMENT_REQ},
             "auth_required": ${env.AUTH_REQ}
          }
        }
    """.trimIndent()
    }


    /**
     * ฟังก์ชันสำหรับอ่านไฟล์จาก classpath
     * @param path เส้นทางของไฟล์ใน classpath
     * @return ข้อมูลที่อ่านจากไฟล์
     */
    private fun loadFromClasspath(path: String): String {
        val resourceLoader: ClassPathResourceLoader =
            ResourceResolver().getLoader(ClassPathResourceLoader::class.java).get()
        val resource = resourceLoader.getResource("classpath:$path").orElseThrow {
            throw FileNotFoundException("File not found: $path")
        }
        return resource.openStream().bufferedReader().use { it.readText() }
    }
}
