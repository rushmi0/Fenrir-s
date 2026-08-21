package org.fenrirs.storage.service

/** Explicit per-account override states - INHERIT is deliberately excluded, represented by row absence instead. */
enum class OverrideState { ALLOW, DENY }

data class AccountPermissionOverride(
    val pubkey: String,
    val featureId: String,
    val state: OverrideState,
    val updatedAt: Long
)

interface AccountPermissionStore {

    /** null = no override row - the caller should fall back to the role default (INHERIT). */
    fun get(pubkey: String, featureId: String): OverrideState?

    /** Only the explicit overrides for this account - features with no row are omitted (they inherit). */
    fun allForAccount(pubkey: String): Map<String, OverrideState>

    /** state = null clears the override (back to INHERIT, row deleted); non-null upserts it. */
    fun setOrClear(pubkey: String, featureId: String, state: OverrideState?)

    fun setManyOrClear(pubkey: String, values: Map<String, OverrideState?>)

    /** Deletes every override row for this account - called when the operator itself is removed. */
    fun removeAllForAccount(pubkey: String)
}
