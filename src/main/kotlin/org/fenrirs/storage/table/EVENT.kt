package org.fenrirs.storage.table

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.json.jsonb

object EVENT: Table("event") {

    val EVENT_ID = varchar("event_id", 64).uniqueIndex()
    val PUBKEY = varchar("pubkey", 64).index()
    val CREATED_AT = integer("created_at").index()
    val KIND = integer("kind").index()

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

    // Composite indexes for the two most common REQ shapes: "kind(s) by author, newest first"
    // and "kind(s) since/until, newest first" - the single-column indexes above only let H2
    // narrow down by one predicate before falling back to a scan for the rest.
    init {
        index(false, PUBKEY, CREATED_AT)
        index(false, KIND, CREATED_AT)
    }
}
