package org.fenrirs.relay.core.nip.nip11

import jakarta.inject.Inject
import jakarta.inject.Singleton

import io.micronaut.http.MediaType

import io.micronaut.core.io.ResourceResolver
import io.micronaut.core.io.scan.ClassPathResourceLoader

import org.fenrirs.storage.NostrRelayConfig
import java.io.FileNotFoundException


@Singleton
class RelayInformation @Inject constructor(private val env: NostrRelayConfig) {


    fun loadRelayInfo(contentType: String): String = loadContent(contentType)

    private fun loadContent(contentType: String): String {
        return if (contentType == MediaType.APPLICATION_JSON) {
            relayInfo()
        } else {
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
          "supported_nips": [1,2,4,9,11,13,15,28,42,45,50],
          "icon": "https://i.imgur.com/dwLPgio.png",
          "software": "https://github.com/rushmi0/Fenrir-s",
          "version": "1.0.1",
          "limitation": {
             "max_filters": ${env.MAX_FILTERS},
             "max_limit": ${env.MAX_LIMIT},
             $minPowDifficultyField
             "max_message_length": 524288,
             "payment_required": ${env.PAYMENT_REQ},
             "auth_required": ${env.AUTH_ENABLED}
          }
        }
    """.trimIndent()
    }


    private fun loadFromClasspath(path: String): String {
        val resourceLoader: ClassPathResourceLoader =
            ResourceResolver().getLoader(ClassPathResourceLoader::class.java).get()
        val resource = resourceLoader.getResource("classpath:$path").orElseThrow {
            throw FileNotFoundException("File not found: $path")
        }
        return resource.openStream().bufferedReader().use { it.readText() }
    }
}
