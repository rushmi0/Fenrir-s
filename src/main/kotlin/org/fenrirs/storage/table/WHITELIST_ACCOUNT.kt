package org.fenrirs.storage.table

import org.jetbrains.exposed.v1.core.Table

object WHITELIST_ACCOUNT : Table("whitelist_account") {

    val PUBKEY = varchar("pubkey", 64)

    /** "GENERAL" or "GUEST" - see [org.fenrirs.storage.service.PermissionRole]. */
    val ROLE = varchar("role", 16)

    /** "ACTIVE", "BLOCKED", or "PENDING" - see [org.fenrirs.storage.service.WhitelistStatus]. */
    val STATUS = varchar("status", 16)
    val ADDED_AT = long("added_at")

    override val primaryKey = PrimaryKey(PUBKEY)
}
