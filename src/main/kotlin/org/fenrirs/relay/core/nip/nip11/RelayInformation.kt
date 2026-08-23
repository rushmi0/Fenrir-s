package org.fenrirs.relay.core.nip.nip11

import com.fasterxml.jackson.annotation.JsonProperty

import jakarta.inject.Inject
import jakarta.inject.Singleton

import io.micronaut.core.io.ResourceResolver
import io.micronaut.core.io.scan.ClassPathResourceLoader
import io.micronaut.http.MediaType
import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.annotation.Serdeable

import java.io.FileNotFoundException
import org.fenrirs.storage.NostrRelayConfig

@Serdeable
data class RelayLimitation(

    @JsonProperty("max_filters")
    val maxFilters: Int,

    @JsonProperty("max_limit")
    val maxLimit: Int,

    @JsonProperty("min_pow_difficulty")
    val minPowDifficulty: Int?,

    @JsonProperty("max_message_length")
    val maxMessageLength: Int,

    @JsonProperty("payment_required")
    val paymentRequired: Boolean,

    @JsonProperty("auth_required")
    val authRequired: Boolean

)

@Serdeable
data class RelayInfo(
    val name: String,
    val description: String,
    val pubkey: String,
    val contact: String,
    @JsonProperty("supported_nips") val supportedNips: List<Int>,
    val icon: String,
    val software: String,
    val version: String,
    val limitation: RelayLimitation
) {
    companion object {
        val SUPPORTED_NIPS = listOf(1, 2, 4, 9, 11, 13, 15, 28, 42, 45, 50)
    }
}

@Singleton
class RelayInformation @Inject constructor(
    private val cfg: NostrRelayConfig,
    private val objectMapper: ObjectMapper
) {

    fun loadRelayInfo(contentType: String): String = loadContent(contentType)

    fun buildRelayInfo(): RelayInfo = RelayInfo(
        name = cfg.RELAY_NAME,
        description = cfg.RELAY_DESCRIPTION,
        pubkey = cfg.RELAY_OWNER,
        contact = cfg.RELAY_CONTACT,
        supportedNips = RelayInfo.SUPPORTED_NIPS,
        icon = "https://files.catbox.moe/h1g3ww.png",
        software = "https://github.com/rushmi0/Fenrir-s",
        version = "2.0",
        limitation = RelayLimitation(
            maxFilters = cfg.MAX_FILTERS,
            maxLimit = cfg.MAX_LIMIT,
            minPowDifficulty = if (cfg.PROOF_OF_WORK_ENABLED) cfg.PROOF_OF_WORK_DIFFICULTY else 0,
            maxMessageLength = 524288,
            paymentRequired = cfg.PAYMENT_REQ,
            authRequired = cfg.AUTH_ENABLED
        )
    )

    private fun loadContent(contentType: String): String {
        return if (contentType == MediaType.APPLICATION_JSON) {
            objectMapper.writeValueAsString(buildRelayInfo())
        } else {
            renderIndexView("public/index.html")
        }
    }

    private fun renderIndexView(path: String): String {
        val resourceLoader: ClassPathResourceLoader =
            ResourceResolver().getLoader(ClassPathResourceLoader::class.java).get()
        val resource = resourceLoader.getResource("classpath:$path").orElseThrow {
            throw FileNotFoundException("File not found: $path")
        }
        return resource.openStream().bufferedReader().use { it.readText() }
    }
}
