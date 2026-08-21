package org.fenrirs.storage.table

import org.jetbrains.exposed.v1.core.Table

object ROLE_PERMISSION : Table("role_permission") {

    /** "GENERAL" or "GUEST" only - ADMIN is never persisted, it's always unconditionally allowed. */
    val ROLE = varchar("role", 16)
    val FEATURE_ID = varchar("feature_id", 64)
    val ALLOWED = bool("allowed")

    override val primaryKey = PrimaryKey(ROLE, FEATURE_ID)
}
