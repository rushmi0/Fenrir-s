package org.fenrirs

import io.github.reactivecircus.cache4k.Cache
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours

class Cache4kTest {

    private val cache = Cache.Builder<Long, String>()
        .expireAfterAccess(24.hours)
        .build()

    @Test
    fun `test cache put`(): Unit = runBlocking {
        // ใส่ค่าใน cache
        setValue(1, "dog")
        setValue(2, "cat")

        // ตรวจสอบว่าได้ค่าที่ถูกต้อง
        assertEquals("dog", cache.get(1))
        assertEquals("cat", cache.get(2))
    }

    @Test
    fun `test cache get`(): Unit = runBlocking {
        // ใส่ค่าใน cache
        setValue(1, "dog")

        // ดึงค่าจาก cache และตรวจสอบ
        assertEquals("dog", getValue(1))
        assertNull(getValue(2)) // ตรวจสอบกรณีที่ key ไม่มีใน cache ว่าจะได้ null
    }

    @Test
    fun `test cache remove`(): Unit = runBlocking {
        // ใส่ค่าใน cache
        setValue(1, "dog")

        // ตรวจสอบก่อนการลบ
        assertEquals("dog", getValue(1))

        // ลบค่าออกจาก cache
        removeValue(1)

        // ตรวจสอบหลังการลบ
        assertNull(getValue(1)) // ค่าต้องเป็น null หลังจากลบ
    }

    private fun setValue(key: Long, value: String) {
        cache.put(key, value)
    }

    private fun getValue(key: Long): String? {
        return cache.get(key)
    }

    private fun removeValue(key: Long) {
        cache.invalidate(key) // ใช้ invalidate เพื่อเอาค่าที่มี key นั้นออกจาก cache
    }
}
