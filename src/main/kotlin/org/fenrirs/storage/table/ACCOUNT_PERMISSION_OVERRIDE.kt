package org.fenrirs.storage.table

import org.jetbrains.exposed.v1.core.Table

object ACCOUNT_PERMISSION_OVERRIDE : Table("account_permission_override") {

    val PUBKEY = varchar("pubkey", 64)
    val FEATURE_ID = varchar("feature_id", 64)

    /** "ALLOW" or "DENY" only - INHERIT is never persisted, an absent row means inherit. */
    val STATE = varchar("state", 8)
    val UPDATED_AT = long("updated_at")

    override val primaryKey = PrimaryKey(PUBKEY, FEATURE_ID)
}
