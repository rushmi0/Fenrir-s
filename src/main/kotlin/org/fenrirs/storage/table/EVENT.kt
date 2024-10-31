package org.fenrirs.storage.table

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
