package org.fenrirs.storage.service

/** ACTIVE = in effect; BLOCKED = denied even Guest-tier defaults; PENDING = staged, not yet in effect. */
enum class WhitelistStatus { ACTIVE, BLOCKED, PENDING }

data class WhitelistAccount(
    val pubkey: String,
    val role: PermissionRole,
    val status: WhitelistStatus,
    val addedAt: Long
)

interface WhitelistAccountStore {

    fun all(): List<WhitelistAccount>

    fun find(pubkey: String): WhitelistAccount?

    /** Inserts a new row (addedAt = now) or updates role/status on an existing one (addedAt preserved). */
    fun upsert(pubkey: String, role: PermissionRole, status: WhitelistStatus): WhitelistAccount

    fun remove(pubkey: String)
}
