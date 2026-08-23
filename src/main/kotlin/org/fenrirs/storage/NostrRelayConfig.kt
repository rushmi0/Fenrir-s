package org.fenrirs.storage

import io.micronaut.context.annotation.Context

import org.fenrirs.relay.core.policy.PolicyConfig
import org.fenrirs.storage.statement.KeyValueStoreImpl
import org.fenrirs.utils.Bech32
import org.fenrirs.utils.ShiftTo.toHex

@Context
class NostrRelayConfig : PolicyConfig {

    val envDefaults: Map<String, String> by lazy {
        mapOf(
        "NAME" to (KeyValueStoreImpl.get("NAME") ?: ""),
        "DESCRIPTION" to (KeyValueStoreImpl.get("DESCRIPTION") ?: ""),
        "NPUB" to (KeyValueStoreImpl.get("NPUB") ?: ""),
        "CONTACT" to (KeyValueStoreImpl.get("CONTACT") ?: ""),
        "RELAY_URL" to (KeyValueStoreImpl.get("RELAY_URL") ?: ""),
        "ALL_PASS" to (KeyValueStoreImpl.get("ALL_PASS") ?: "false"),
        "FOLLOWS_PASS" to (KeyValueStoreImpl.get("FOLLOWS_PASS") ?: "false"),
        "POW_ENABLED" to (KeyValueStoreImpl.get("POW_ENABLED") ?: "false"),
        "MIN_DIFFICULTY" to (KeyValueStoreImpl.get("MIN_DIFFICULTY") ?: "4"),
        "MAX_FILTERS" to (KeyValueStoreImpl.get("MAX_FILTERS") ?: "5"),
        "MAX_LIMIT" to (KeyValueStoreImpl.get("MAX_LIMIT") ?: "500"),
        "AUTH_ENABLED" to (KeyValueStoreImpl.get("AUTH_ENABLED") ?: "false"),
        "AUTH_WHITELIST_PUBKEYS" to (KeyValueStoreImpl.get("AUTH_WHITELIST_PUBKEYS") ?: ""),
        "BACKUP_ENABLED" to (KeyValueStoreImpl.get("BACKUP_ENABLED") ?: "false"),
        "SYNC" to (KeyValueStoreImpl.get("SYNC") ?: ""),
        "DATABASE_URL" to (KeyValueStoreImpl.get("DATABASE_URL") ?: ""),
        "DATABASE_NAME" to (KeyValueStoreImpl.get("DATABASE_NAME") ?: ""),
        "DATABASE_USERNAME" to (KeyValueStoreImpl.get("DATABASE_USERNAME") ?: ""),
        "DATABASE_PASSWORD" to (KeyValueStoreImpl.get("DATABASE_PASSWORD") ?: ""),
        "PRIMARY_DATABASE_ENABLED" to (KeyValueStoreImpl.get("PRIMARY_DATABASE_ENABLED") ?: "false"),
        "DB_H2_MIN_IDLE" to (KeyValueStoreImpl.get("DB_H2_MIN_IDLE") ?: "1"),
        "DB_H2_MAX_POOL_SIZE" to (KeyValueStoreImpl.get("DB_H2_MAX_POOL_SIZE") ?: "12"),
        "DB_H2_LEAK_DETECTION_THRESHOLD" to (KeyValueStoreImpl.get("DB_H2_LEAK_DETECTION_THRESHOLD") ?: "60000"),
        "DB_PG_MIN_IDLE" to (KeyValueStoreImpl.get("DB_PG_MIN_IDLE") ?: "10"),
        "DB_PG_MAX_POOL_SIZE" to (KeyValueStoreImpl.get("DB_PG_MAX_POOL_SIZE") ?: "64"),
        "DB_PG_IDLE_TIMEOUT" to (KeyValueStoreImpl.get("DB_PG_IDLE_TIMEOUT") ?: "60000"),
        "DB_PG_KEEPALIVE_TIME" to (KeyValueStoreImpl.get("DB_PG_KEEPALIVE_TIME") ?: "600000"),
        "DB_PG_MAX_LIFETIME" to (KeyValueStoreImpl.get("DB_PG_MAX_LIFETIME") ?: "2000000"),
        "DB_PG_LEAK_DETECTION_THRESHOLD" to (KeyValueStoreImpl.get("DB_PG_LEAK_DETECTION_THRESHOLD") ?: "30000"),
        "DB_PG_VALIDATION_TIMEOUT" to (KeyValueStoreImpl.get("DB_PG_VALIDATION_TIMEOUT") ?: "3000"),
        "DB_PG_TRANSACTION_ISOLATION" to (KeyValueStoreImpl.get("DB_PG_TRANSACTION_ISOLATION") ?: "TRANSACTION_REPEATABLE_READ")
        )
    }

    val DATABASE_URL: String get() = KeyValueStoreImpl.get("DATABASE_URL") ?: "postgresql://localhost:5432"
    val DATABASE_NAME: String get() = KeyValueStoreImpl.get("DATABASE_NAME") ?: "nostr"
    val DATABASE_USERNAME: String get() = KeyValueStoreImpl.get("DATABASE_USERNAME") ?: "lnwza007"
    val DATABASE_PASSWORD: String get() = KeyValueStoreImpl.get("DATABASE_PASSWORD") ?: "Sql@min456RTYfgh"
    val PRIMARY_DATABASE_ENABLED: Boolean get() = KeyValueStoreImpl.get("PRIMARY_DATABASE_ENABLED")?.toBoolean() ?: false


    val DB_H2_MIN_IDLE: Int get() = KeyValueStoreImpl.get("DB_H2_MIN_IDLE")?.toIntOrNull() ?: 1
    val DB_H2_MAX_POOL_SIZE: Int get() = KeyValueStoreImpl.get("DB_H2_MAX_POOL_SIZE")?.toIntOrNull() ?: 12
    val DB_H2_LEAK_DETECTION_THRESHOLD: Int
        get() = KeyValueStoreImpl.get("DB_H2_LEAK_DETECTION_THRESHOLD")?.toIntOrNull() ?: 160_000

    // PostgreSQL (primary) pool settings
    val DB_PG_MIN_IDLE: Int get() = KeyValueStoreImpl.get("DB_PG_MIN_IDLE")?.toIntOrNull() ?: 10
    val DB_PG_MAX_POOL_SIZE: Int get() = KeyValueStoreImpl.get("DB_PG_MAX_POOL_SIZE")?.toIntOrNull() ?: 64
    val DB_PG_IDLE_TIMEOUT: Int get() = KeyValueStoreImpl.get("DB_PG_IDLE_TIMEOUT")?.toIntOrNull() ?: 60_000
    val DB_PG_KEEPALIVE_TIME: Int get() = KeyValueStoreImpl.get("DB_PG_KEEPALIVE_TIME")?.toIntOrNull() ?: 600_000
    val DB_PG_MAX_LIFETIME: Int get() = KeyValueStoreImpl.get("DB_PG_MAX_LIFETIME")?.toIntOrNull() ?: 2_000_000
    val DB_PG_LEAK_DETECTION_THRESHOLD: Int
        get() = KeyValueStoreImpl.get("DB_PG_LEAK_DETECTION_THRESHOLD")?.toIntOrNull() ?: 30_000
    val DB_PG_VALIDATION_TIMEOUT: Int get() = KeyValueStoreImpl.get("DB_PG_VALIDATION_TIMEOUT")?.toIntOrNull() ?: 3_000
    val DB_PG_TRANSACTION_ISOLATION: String
        get() = KeyValueStoreImpl.get("DB_PG_TRANSACTION_ISOLATION") ?: "TRANSACTION_REPEATABLE_READ"


    override val RELAY_OWNER: String
        get() {
            val relayNpub = KeyValueStoreImpl.get("NPUB")
            return if (relayNpub?.startsWith("npub") == true) {
                Bech32.decode(relayNpub).data.toHex()
            } else relayNpub ?: ""
        }
    val RELAY_NAME: String get() = KeyValueStoreImpl.get("NAME") ?: ""
    val RELAY_DESCRIPTION: String get() = KeyValueStoreImpl.get("DESCRIPTION") ?: ""
    val RELAY_CONTACT: String get() = KeyValueStoreImpl.get("CONTACT") ?: ""
    val RELAY_URL: String get() = KeyValueStoreImpl.get("RELAY_URL") ?: ""

    // Policy settings
    override val FOLLOWS_PASS: Boolean get() = KeyValueStoreImpl.get("FOLLOWS_PASS")?.toBoolean() ?: false
    override val ALL_PASS: Boolean get() = KeyValueStoreImpl.get("ALL_PASS")?.toBoolean() ?: false
    override val PROOF_OF_WORK_ENABLED: Boolean get() = KeyValueStoreImpl.get("POW_ENABLED")?.toBoolean() ?: false
    val PROOF_OF_WORK_DIFFICULTY: Int
        get() = KeyValueStoreImpl.get("MIN_DIFFICULTY")?.toIntOrNull() ?: 4


    // Limitation settings
    val MAX_FILTERS: Int get() = KeyValueStoreImpl.get("MAX_FILTERS")?.toIntOrNull() ?: 5
    val MAX_LIMIT: Int get() = KeyValueStoreImpl.get("MAX_LIMIT")?.toIntOrNull() ?: 500
    val PAYMENT_REQ: Boolean = false

    override val AUTH_ENABLED: Boolean get() = KeyValueStoreImpl.get("AUTH_ENABLED")?.toBoolean() ?: false


    override val AUTH_WHITELIST_PUBKEYS: Set<String>
        get() = KeyValueStoreImpl.get("AUTH_WHITELIST_PUBKEYS")
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.map { normalizePubkey(it) }
            ?.toSet()
            ?: emptySet()

    // Database backup settings
    val BACKUP_ENABLED: Boolean get() = KeyValueStoreImpl.get("BACKUP_ENABLED")?.toBoolean() ?: false
    val BACKUP_SYNC: List<String>
        get() {
            val defaultBackupSync = listOf(
                "wss://relay.notoshi.win",
                "wss://relay.damus.io",
                "wss://nostr-01.yakihonne.com",
                "wss://relay.snort.social",
                "wss://yabu.me",
                "wss://relay.nostr.wirednet.jp",
                "wss://nos.lol",
                "wss://frens.nostr1.com",
                "wss://purplerelay.com"
            )

            val syncValue = KeyValueStoreImpl.get("SYNC")
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList()
            return (defaultBackupSync + syncValue).distinct()
        }

    companion object {
        private fun normalizePubkey(pubkey: String): String =
            if (pubkey.startsWith("npub")) Bech32.decode(pubkey).data.toHex() else pubkey
    }

}

