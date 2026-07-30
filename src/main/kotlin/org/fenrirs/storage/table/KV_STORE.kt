package org.fenrirs.storage.table

import org.jetbrains.exposed.v1.core.Table

object KV_STORE : Table("kv_store") {

    val KEY = varchar("key", 64)
    val VALUE = text("value")

    override val primaryKey = PrimaryKey(KEY)
}