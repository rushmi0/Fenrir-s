package org.fenrirs.storage.statement

import jakarta.inject.Singleton

import org.fenrirs.storage.DatabaseFactory.configTask
import org.fenrirs.storage.service.PermissionRole
import org.fenrirs.storage.service.WhitelistAccount
import org.fenrirs.storage.service.WhitelistAccountStore
import org.fenrirs.storage.service.WhitelistStatus
import org.fenrirs.storage.table.WHITELIST_ACCOUNT

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert

@Singleton
object WhitelistAccountStoreImpl : WhitelistAccountStore {

    override fun all(): List<WhitelistAccount> = configTask {
        WHITELIST_ACCOUNT.selectAll().map { it.toWhitelistAccount() }
    }

    override fun find(pubkey: String): WhitelistAccount? = configTask {
        WHITELIST_ACCOUNT.selectAll()
            .where { WHITELIST_ACCOUNT.PUBKEY eq pubkey }
            .firstOrNull()?.toWhitelistAccount()
    }

    override fun upsert(pubkey: String, role: PermissionRole, status: WhitelistStatus): WhitelistAccount = configTask {
        val existingAddedAt = WHITELIST_ACCOUNT.selectAll()
            .where { WHITELIST_ACCOUNT.PUBKEY eq pubkey }
            .firstOrNull()?.get(WHITELIST_ACCOUNT.ADDED_AT)
        val addedAt = existingAddedAt ?: (System.currentTimeMillis() / 1000)

        WHITELIST_ACCOUNT.upsert {
            it[PUBKEY] = pubkey
            it[ROLE] = role.name
            it[STATUS] = status.name
            it[ADDED_AT] = addedAt
        }
        WhitelistAccount(pubkey, role, status, addedAt)
    }

    override fun remove(pubkey: String) {
        configTask {
            WHITELIST_ACCOUNT.deleteWhere { PUBKEY eq pubkey }
        }
    }

    private fun ResultRow.toWhitelistAccount() = WhitelistAccount(
        pubkey = this[WHITELIST_ACCOUNT.PUBKEY],
        role = PermissionRole.valueOf(this[WHITELIST_ACCOUNT.ROLE]),
        status = WhitelistStatus.valueOf(this[WHITELIST_ACCOUNT.STATUS]),
        addedAt = this[WHITELIST_ACCOUNT.ADDED_AT]
    )
}
