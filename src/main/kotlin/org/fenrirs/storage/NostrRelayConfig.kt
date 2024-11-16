package org.fenrirs.storage

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Context

import org.fenrirs.utils.Bech32
import org.fenrirs.utils.ShiftTo.toHex

import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Properties

@Bean
@Context
class NostrRelayConfig {

    private val prop: Properties = Properties()

    init {
        try {
            InputStreamReader(FileInputStream(".env"), StandardCharsets.UTF_8).use { reader ->
                prop.load(reader)
            }
        } catch (e: IOException) {
            throw RuntimeException("Failed to load .env file", e)
        }
    }


    // Database settings
    val DATABASE_NAME: String by lazy { prop.getProperty("DATABASE_NAME") ?: "" }
    val DATABASE_URL: String by lazy { prop.getProperty("DATABASE_URL") ?: "" }
    val DATABASE_USERNAME: String by lazy { prop.getProperty("DATABASE_USERNAME") ?: "" }
    val DATABASE_PASSWORD: String by lazy { prop.getProperty("DATABASE_PASSWORD") ?: "" }

    // Relay info
    val RELAY_OWNER: String by lazy {
        val relayNpub = prop.getProperty("NPUB")
        if (relayNpub?.startsWith("npub") == true) {
            Bech32.decode(relayNpub).data.toHex()
        } else relayNpub ?: ""
    }
    val RELAY_NAME: String by lazy { prop.getProperty("NAME") ?: "" }
    val RELAY_DESCRIPTION: String by lazy { prop.getProperty("DESCRIPTION") ?: "" }
    val RELAY_CONTACT: String by lazy { prop.getProperty("CONTACT") ?: "" }

    // Policy settings
    val FOLLOWS_PASS: Boolean by lazy { prop.getProperty("FOLLOWS_PASS")?.toBoolean() ?: false }
    val ALL_PASS: Boolean by lazy { prop.getProperty("ALL_PASS")?.toBoolean() ?: false }
    val PROOF_OF_WORK_ENABLED: Boolean by lazy { prop.getProperty("POW_ENABLED")?.toBoolean() ?: false }
    val PROOF_OF_WORK_DIFFICULTY: Int by lazy {
        prop.getProperty("MIN_DIFFICULTY")?.toIntOrNull() ?: 4
    }


    // Limitation settings
    val MAX_FILTERS: Int by lazy { prop.getProperty("MAX_FILTERS")?.toIntOrNull() ?: 5 }
    val MAX_LIMIT: Int by lazy { prop.getProperty("MAX_LIMIT")?.toIntOrNull() ?: 500 }
    val PAYMENT_REQ: Boolean = false
    val AUTH_REQ: Boolean = false

    // Database backup settings
    val BACKUP_ENABLED: Boolean by lazy { prop.getProperty("BACKUP_ENABLED")?.toBoolean() ?: false }
    val BACKUP_SYNC: List<String> by lazy {
        val defaultBackupSync = listOf(
            "wss://relay.notoshi.win",
            "wss://relay.siamstr.com",
            "wss://relay.damus.io",
            "wss://nostr-01.yakihonne.com",
            "wss://nos.lol",
            "wss://purplerelay.com"
        )

        // อ่านค่า SYNC และแยกออกเป็นรายการโดยใช้ `,` และลบช่องว่างรอบข้างออก
        val syncValue = prop.getProperty("SYNC")
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

        // รวมรายการค่าเริ่มต้นและค่าที่ได้จาก SYNC
        defaultBackupSync + syncValue
    }

}

