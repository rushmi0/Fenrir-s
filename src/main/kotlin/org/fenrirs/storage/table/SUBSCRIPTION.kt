package org.fenrirs.storage.table

import kotlinx.serialization.json.Json

import org.fenrirs.relay.models.FiltersX

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.json.jsonb

object SUBSCRIPTION : Table("subscription") {

    val SESSION_ID = varchar("session_id", 64)
    val SUBSCRIPTION_ID = varchar("subscription_id", 64)

    val FILTERS: Column<List<FiltersX>> = jsonb(
        "filters",
        serialize = { Json.encodeToString(it) },
        deserialize = { Json.decodeFromString(it) }
    )

    val UPDATED_AT = long("updated_at")

    override val primaryKey = PrimaryKey(SESSION_ID, SUBSCRIPTION_ID)
}