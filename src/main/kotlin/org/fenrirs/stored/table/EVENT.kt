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

    /**
     * คอลัมน์สำหรับเก็บ "tags" ซึ่งเป็นข้อมูล JSON ที่เป็นลิสต์ซ้อนลิสต์
     * - ใช้ฟังก์ชัน `jsonb` เพื่อจัดการข้อมูล JSON
     * - ฟังก์ชัน `serialize` ใช้เพื่อแปลงข้อมูลจาก JSON เป็น JSON String
     * - ฟังก์ชัน `deserialize` ใช้เพื่อแปลง JSON String กลับเป็น JSON
     */
    val TAGS: Column<List<List<String>>> = jsonb(
        "tags",
        serialize = { Json.encodeToString(it) },
        deserialize = { Json.decodeFromString(it) }
    )

    val CONTENT = text("content")
    val SIG = varchar("sig", 128)
}

/**
 * คลาสที่ใช้ในการสร้างฟังก์ชัน `to_tsvector` ของ PostgreSQL
 * - ฟังก์ชันนี้ใช้เพื่อแปลงข้อความเป็น `tsvector` สำหรับการค้นหาข้อความ
 * - `config` คือค่าการตั้งค่า
 * - `text` คือข้อความที่ต้องการแปลง
 */
class toTsvector(
    config: Expression<*>,
    text: ExpressionWithColumnType<String>,
) : CustomFunction<String>(
    functionName = "to_tsvector",
    columnType = TextColumnType(),
    expr = arrayOf(config, text)
)

/**
 * คลาสที่ใช้ในการสร้างฟังก์ชัน `plainto_tsquery` ของ PostgreSQL
 * - ฟังก์ชันนี้ใช้เพื่อแปลงข้อความเป็น `tsquery` สำหรับการค้นหาข้อความ
 * - `config` คือค่าการตั้งค่า
 * - `text` คือข้อความที่ต้องการแปลง
 */
class plainToTsquery(
    config: Expression<*>,
    text: ExpressionWithColumnType<String>,
) : CustomFunction<String>(
    functionName = "plainto_tsquery",
    columnType = TextColumnType(),
    expr = arrayOf(config, text)
)

/**
 * คลาสที่ใช้สำหรับการเปรียบเทียบ `tsvector` กับ `tsquery` โดยใช้ตัวดำเนินการ `@@`
 * - ตัวดำเนินการนี้ใช้ในการค้นหาข้อความใน PostgreSQL
 */
class MatchOp(expr1: Expression<*>, expr2: Expression<*>) : ComparisonOp(expr1, expr2, "@@")

/**
 * ฟังก์ชันสำหรับสร้างการเปรียบเทียบ `tsvector` กับ `tsquery`
 * - ใช้ตัวดำเนินการ `@@` สำหรับการค้นหาข้อความ
 */
infix fun Expression<*>.match(t: Expression<*>): Op<Boolean> = MatchOp(this, t)