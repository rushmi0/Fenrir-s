package org.fenrirs

import io.github.reactivecircus.cache4k.Cache
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours

class Cache4kTest {

    @Test
    fun `test cache put and get`(): Unit = runBlocking {
        val cache = Cache.Builder<Long, String>()
            .expireAfterAccess(24.hours)
            .build()

        // ใส่ค่าใน cache
        cache.put(1, "dog")
        cache.put(2, "cat")

        // ดึงค่าจาก cache และตรวจสอบว่าได้ค่าที่ถูกต้อง
        assertEquals("dog", cache.get(1))
        assertEquals("cat", cache.get(2))

        // ตรวจสอบกรณีที่ key ไม่มีใน cache ว่าจะได้ null
        assertNull(cache.get(3))
    }
}
