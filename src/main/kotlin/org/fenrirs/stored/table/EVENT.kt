package org.fenrirs.stored.table

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.json.jsonb

object EVENT: Table("event") {

    val EVENT_ID = varchar("event_id", 64).uniqueIndex()
    val PUBKEY = varchar("pubkey", 64)
    val CREATED_AT = integer("created_at")
    val KIND = integer("kind")

    val TAGS: Column<List<List<String>>> = jsonb(
        "tags",
        serialize = { Json.encodeToString(it) },
        deserialize = { Json.decodeFromString(it) }
    )

    val CONTENT = text("content")
    val SIG = varchar("sig", 128)
}

class toTsvector(
    config: Expression<*>,
    text: ExpressionWithColumnType<String>,
) : CustomFunction<String>(
    functionName = "to_tsvector",
    columnType = TextColumnType(), // It's a wrong column type, but it's no critical, unless you want to get the result of this function inside the client code
    expr = arrayOf(config, text)
)

class plainToTsquery(
    config: Expression<*>,
    text: ExpressionWithColumnType<String>,
) : CustomFunction<String>(
    functionName = "plainto_tsquery",
    columnType = TextColumnType(), // It's a wrong column type, but it's no critical, unless you want to get the result of this function inside the client code
    expr = arrayOf(config, text)
)

class MatchOp(expr1: Expression<*>, expr2: Expression<*>) : ComparisonOp(expr1, expr2, "@@")

infix fun Expression<*>.match(t: Expression<*>): Op<Boolean> = MatchOp(this, t)

