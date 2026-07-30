package org.fenrirs.storage

import io.micronaut.context.annotation.Context

import org.fenrirs.relay.core.policy.PolicyConfig
import org.fenrirs.storage.statement.KeyValueStoreImpl
import org.fenrirs.utils.Bech32
import org.fenrirs.utils.ShiftTo.toHex

import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Properties

@Context
class NostrRelayConfig : PolicyConfig {

    private val prop: Properties = Properties()

    init {
        runCatching {
            InputStreamReader(FileInputStream(".env"), StandardCharsets.UTF_8).use { reader ->
                prop.load(reader)
            }
        }.getOrElse { e -> throw RuntimeException("Failed to load .env file", e) }
    }


    /** ค่า default ทั้งหมดที่อ่านจาก .env ใช้ sync เข้า [DatabaseFactory] ตอน startup - ดู [org.fenrirs.relay.SystemPreload] */
    val envDefaults: Map<String, String> = mapOf(
        "NAME" to (prop.getProperty("NAME") ?: ""),
        "DESCRIPTION" to (prop.getProperty("DESCRIPTION") ?: ""),
        "NPUB" to (prop.getProperty("NPUB") ?: ""),
        "CONTACT" to (prop.getProperty("CONTACT") ?: ""),
        "RELAY_URL" to (prop.getProperty("RELAY_URL") ?: ""),
        "ALL_PASS" to (prop.getProperty("ALL_PASS") ?: "false"),
        "FOLLOWS_PASS" to (prop.getProperty("FOLLOWS_PASS") ?: "false"),
        "POW_ENABLED" to (prop.getProperty("POW_ENABLED") ?: "false"),
        "MIN_DIFFICULTY" to (prop.getProperty("MIN_DIFFICULTY") ?: "4"),
        "MAX_FILTERS" to (prop.getProperty("MAX_FILTERS") ?: "5"),
        "MAX_LIMIT" to (prop.getProperty("MAX_LIMIT") ?: "500"),
        "AUTH_ENABLED" to (prop.getProperty("AUTH_ENABLED") ?: "false"),
        "AUTH_WHITELIST_PUBKEYS" to (prop.getProperty("AUTH_WHITELIST_PUBKEYS") ?: ""),
        "BACKUP_ENABLED" to (prop.getProperty("BACKUP_ENABLED") ?: "false"),
        "SYNC" to (prop.getProperty("SYNC") ?: "")
    )


    // Database settings - ค่าเชื่อมต่อฐานข้อมูลต้องอ่านจาก .env เท่านั้น เพราะต้องใช้ก่อนที่ฐานข้อมูลใด ๆ จะพร้อมใช้งาน
    val DATABASE_NAME: String by lazy { prop.getProperty("DATABASE_NAME") ?: "" }
    val DATABASE_URL: String by lazy { prop.getProperty("DATABASE_URL") ?: "" }
    val DATABASE_USERNAME: String by lazy { prop.getProperty("DATABASE_USERNAME") ?: "" }
    val DATABASE_PASSWORD: String by lazy { prop.getProperty("DATABASE_PASSWORD") ?: "" }
    val PRIMARY_DATABASE_ENABLED: Boolean by lazy { prop.getProperty("PRIMARY_DATABASE_ENABLED")?.toBoolean() ?: false }

    // Relay info
    override val RELAY_OWNER: String by lazy {
        val relayNpub = KeyValueStoreImpl.get("NPUB")
        if (relayNpub?.startsWith("npub") == true) {
            Bech32.decode(relayNpub).data.toHex()
        } else relayNpub ?: ""
    }
    val RELAY_NAME: String by lazy { KeyValueStoreImpl.get("NAME") ?: "" }
    val RELAY_DESCRIPTION: String by lazy { KeyValueStoreImpl.get("DESCRIPTION") ?: "" }
    val RELAY_CONTACT: String by lazy { KeyValueStoreImpl.get("CONTACT") ?: "" }
    // ที่อยู่ของ relay นี้เอง (เช่น wss://relay.example.com/) ใช้ตรวจสอบ "relay" tag ใน NIP-42 AUTH event
    val RELAY_URL: String by lazy { KeyValueStoreImpl.get("RELAY_URL") ?: "" }

    // Policy settings
    override val FOLLOWS_PASS: Boolean by lazy { KeyValueStoreImpl.get("FOLLOWS_PASS")?.toBoolean() ?: false }
    override val ALL_PASS: Boolean by lazy { KeyValueStoreImpl.get("ALL_PASS")?.toBoolean() ?: false }
    override val PROOF_OF_WORK_ENABLED: Boolean by lazy { KeyValueStoreImpl.get("POW_ENABLED")?.toBoolean() ?: false }
    val PROOF_OF_WORK_DIFFICULTY: Int by lazy {
        KeyValueStoreImpl.get("MIN_DIFFICULTY")?.toIntOrNull() ?: 4
    }


    // Limitation settings
    val MAX_FILTERS: Int by lazy { KeyValueStoreImpl.get("MAX_FILTERS")?.toIntOrNull() ?: 5 }
    val MAX_LIMIT: Int by lazy { KeyValueStoreImpl.get("MAX_LIMIT")?.toIntOrNull() ?: 500 }
    val PAYMENT_REQ: Boolean = false

    // ต้องยืนยันตัวตนตาม NIP-42 ก่อนถึงจะ REQ/COUNT/EVENT ได้หรือไม่ - บังคับใช้จริงใน AuthenticationRule (policy package)
    override val AUTH_ENABLED: Boolean by lazy { KeyValueStoreImpl.get("AUTH_ENABLED")?.toBoolean() ?: false }


    override val AUTH_WHITELIST_PUBKEYS: Set<String> by lazy {
        KeyValueStoreImpl.get("AUTH_WHITELIST_PUBKEYS")
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.map { normalizePubkey(it) }
            ?.toSet()
            ?: emptySet()
    }

    // Database backup settings
    val BACKUP_ENABLED: Boolean by lazy { KeyValueStoreImpl.get("BACKUP_ENABLED")?.toBoolean() ?: false }
    val BACKUP_SYNC: List<String> by lazy {
        val defaultBackupSync = listOf(
            "wss://relay.notoshi.win",
            "wss://relay.siamstr.com",
            "wss://relay.damus.io",
            "wss://nostr-01.yakihonne.com",
            "wss://nos.lol",
            "wss://purplerelay.com"
        )

        val syncValue = KeyValueStoreImpl.get("SYNC")
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
        defaultBackupSync + syncValue
    }

    companion object {
        private fun normalizePubkey(pubkey: String): String =
            if (pubkey.startsWith("npub")) Bech32.decode(pubkey).data.toHex() else pubkey
    }

}

