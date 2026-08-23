package org.fenrirs.relay.core.policy

import org.fenrirs.storage.NostrRelayConfig
import org.fenrirs.storage.service.PermissionRole
import org.fenrirs.storage.service.WhitelistStatus
import org.fenrirs.storage.statement.WhitelistAccountStoreImpl

/**
 * One-time migration off the legacy CSV whitelist (`SecurityPolicy.authWhitelistPubkeys`, still
 * read via [NostrRelayConfig.AUTH_WHITELIST_PUBKEYS]) onto [org.fenrirs.storage.table.WHITELIST_ACCOUNT].
 * Idempotent "insert only what's missing" - same pattern as [PermissionSeeder.seed] - so it's safe
 * to call on every boot: every pubkey already in [org.fenrirs.storage.table.WHITELIST_ACCOUNT] is
 * left untouched (an Admin's later role/status change always wins), only pubkeys that exist in the
 * old CSV but have never been migrated get a row, seeded as (GENERAL, ACTIVE, addedAt = now).
 */
object WhitelistAccountSeeder {

    fun seedFromLegacyCsv(config: NostrRelayConfig) {
        val existing = WhitelistAccountStoreImpl.all().map { it.pubkey }.toSet()
        config.AUTH_WHITELIST_PUBKEYS
            .filter { it !in existing }
            .forEach { pubkey -> WhitelistAccountStoreImpl.upsert(pubkey, PermissionRole.GENERAL, WhitelistStatus.ACTIVE) }
    }
}
