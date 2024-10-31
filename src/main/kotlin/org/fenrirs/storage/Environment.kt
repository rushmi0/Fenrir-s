package org.fenrirs.storage

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Context
import jakarta.inject.Singleton
import org.fenrirs.utils.Bech32
import org.fenrirs.utils.ShiftTo.toHex
import java.io.FileInputStream
import java.io.IOException
import java.util.Properties

@Bean
@Context
@Singleton
class Environment {

    private val properties: Properties = Properties()

    init {
        try {
            FileInputStream(".env").use { input ->
                properties.load(input)
            }
        } catch (e: IOException) {
            throw RuntimeException("Failed to load .env file", e)
        }
    }

    // Database settings
    val DATABASE_NAME: String by lazy { properties.getProperty("DATABASE_NAME") ?: "" }
    val DATABASE_URL: String by lazy { properties.getProperty("DATABASE_URL") ?: "" }
    val DATABASE_USERNAME: String by lazy { properties.getProperty("DATABASE_USERNAME") ?: "" }
    val DATABASE_PASSWORD: String by lazy { properties.getProperty("DATABASE_PASSWORD") ?: "" }

    // Relay info
    val RELAY_OWNER: String by lazy {
        val relayNpub = properties.getProperty("RELAY_NPUB")
        if (relayNpub?.startsWith("npub") == true) {
            Bech32.decode(relayNpub).data.toHex()
        } else relayNpub ?: ""
    }
    val RELAY_NAME: String by lazy { properties.getProperty("RELAY_NAME") ?: "" }
    val RELAY_DESCRIPTION: String by lazy { properties.getProperty("RELAY_DESCRIPTION") ?: "" }
    val RELAY_CONTACT: String by lazy { properties.getProperty("RELAY_CONTACT") ?: "" }

    // Policy settings
    val FOLLOWS_PASS: Boolean by lazy { properties.getProperty("FOLLOWS_PASS")?.toBoolean() ?: false }
    val ALL_PASS: Boolean by lazy { properties.getProperty("ALL_PASS")?.toBoolean() ?: false }
    val PROOF_OF_WORK_ENABLED: Boolean by lazy { properties.getProperty("PROOF_OF_WORK_ENABLED")?.toBoolean() ?: false }
    val PROOF_OF_WORK_DIFFICULTY: Int by lazy { properties.getProperty("PROOF_OF_WORK_DIFFICULTY")?.toInt() ?: 0 }

    // Limitation settings
    val MAX_FILTERS: Int by lazy { properties.getProperty("MAX_FILTERS")?.toIntOrNull() ?: 5 }
    val MAX_LIMIT: Int by lazy { properties.getProperty("MAX_LIMIT")?.toIntOrNull() ?: 150 }
    val PAYMENT_REQ: Boolean = false
    val AUTH_REQ: Boolean = false

    // Database backup settings
    val BACKUP_ENABLED: Boolean by lazy { properties.getProperty("BACKUP_ENABLED")?.toBoolean() ?: false }
    val BACKUP_SYNC: List<String> = listOf(
        "wss://relay.notoshi.win",
        "wss://relay.siamstr.com",
        "wss://relay.damus.io",
        "wss://nostr-01.yakihonne.com",
        "wss://nos.lol",
        "wss://purplerelay.com"
    )
}
