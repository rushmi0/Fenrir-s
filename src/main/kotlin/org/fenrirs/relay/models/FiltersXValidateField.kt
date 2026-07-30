package org.fenrirs.relay.models

import java.util.ArrayList

// หมายเหตุ: field แบบ "#<tag>" (เช่น "#e", "#p", หรือ tag อื่นใดตาม NIP-12) ไม่ได้ประกาศไว้ที่นี่
// เพราะ tag filter เป็นชื่อ field แบบไดนามิก (client กำหนดเองได้ทุกชื่อ) จึงตรวจสอบแยกใน
// VerificationFactory โดยอนุญาตให้ field ใดก็ตามที่ขึ้นต้นด้วย "#" ผ่านการตรวจสอบชื่อ/ชนิดข้อมูล
// แบบเดียวกับ ArrayList แทนที่จะประกาศเป็น enum entry ตายตัวทีละอัน
enum class FiltersXValidateField(
    override val fieldName: String,
    override val fieldType: Class<*>,
    override val fieldCollectionType: Class<*>? = null
) : NostrField {
    IDS("ids", ArrayList::class.java),
    AUTHORS("authors", ArrayList::class.java),
    KINDS("kinds", ArrayList::class.java),
    SINCE("since", Long::class.java),
    UNTIL("until", Long::class.java),
    LIMIT("limit", Long::class.java),
    SEARCH("search", String::class.java)
}