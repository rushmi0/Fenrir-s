package org.fenrirs

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

data class Personal(val name: String, val age: Int)

class CaffeineCacheTest {

    @Inject
    private lateinit var cache: Cache<String, Personal>

    @BeforeEach
    fun setUp() {
        // สร้าง cache ที่เก็บข้อมูลแบบ Personal
        cache = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .maximumSize(100)
            .build()
    }

    @Test
    fun `test cache stores and retrieves object`() {
        // สร้าง object Personal
        val personalData = Personal("John Doe", 30)

        // ใส่ข้อมูลลงใน cache
        cache.put("person_1", personalData)

        // ดึงข้อมูลจาก cache
        val cachedData = cache.getIfPresent("person_1")

        // ตรวจสอบว่า object ที่ดึงออกมาตรงกับที่ใส่เข้าไป
        assertNotNull(cachedData)
        assertEquals(personalData, cachedData)
    }

    @Test
    fun `test cache invalidation`() {
        // สร้าง object Personal
        val personalData = Personal("Jane Doe", 25)

        // ใส่ข้อมูลลงใน cache
        cache.put("person_2", personalData)

        // ลบข้อมูลจาก cache
        cache.invalidate("person_2")

        // ตรวจสอบว่า object ถูกลบไปจาก cache แล้ว
        val cachedData = cache.getIfPresent("person_2")
        assertNull(cachedData)
    }

    @Test
    fun `test cache expiration`() {
        // สร้าง cache ที่หมดอายุเร็วสำหรับทดสอบ
        val quickExpireCache = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.SECONDS)
            .build<String, Personal>()

        // สร้าง object Personal
        val personalData = Personal("Alice Doe", 28)

        // ใส่ข้อมูลลงใน cache
        quickExpireCache.put("person_3", personalData)

        // รอให้ cache หมดอายุ
        Thread.sleep(2000)

        // ตรวจสอบว่า object หมดอายุแล้วถูกลบไปจาก cache
        val cachedData = quickExpireCache.getIfPresent("person_3")
        assertNull(cachedData)
    }
}
