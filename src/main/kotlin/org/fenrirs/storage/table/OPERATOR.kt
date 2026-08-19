package org.fenrirs.storage.table

import org.jetbrains.exposed.v1.core.Table

object OPERATOR : Table("operator") {

    val PUBKEY = varchar("pubkey", 64)
    val ROLE = varchar("role", 16)
    val CREATED_AT = long("created_at")

    override val primaryKey = PrimaryKey(PUBKEY)
}
