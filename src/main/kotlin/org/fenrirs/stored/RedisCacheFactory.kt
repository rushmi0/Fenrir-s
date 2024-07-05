package org.fenrirs.stored

import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import io.micronaut.context.annotation.Bean
import jakarta.inject.Inject
import kotlinx.coroutines.runBlocking

import org.fenrirs.utils.ExecTask.parallelDefault
import org.slf4j.LoggerFactory

@Bean
class RedisCacheFactory @Inject constructor(
    private val redisClient: RedisClient = RedisClient.create("redis://relay-cache:6379"),
    private val connection: StatefulRedisConnection<String, String> = redisClient.connect(),
    private val redisCommands: RedisCommands<String, String> = connection.sync()
) {

    private val LOG = LoggerFactory.getLogger(RedisCacheFactory::class.java)

    /**
     * ฟังก์ชันสำหรับ cache ข้อมูลลงใน Redis
     * @param key: คีย์ที่ใช้เก็บข้อมูลใน Redis
     * @param value: ข้อมูลที่จะถูกเก็บ
     * @param expirySeconds: เวลาที่ข้อมูลจะหมดอายุในหน่วยวินาที
     * @param serializer: Lambda expression สำหรับแปลงข้อมูลจาก T เป็น String
     * @return ค่า String ที่เก็บใน Redis หรือ null หากเกิดข้อผิดพลาด
     */
    private suspend fun <T> setCacheData(
        key: String,
        value: T,
        expirySeconds: Long,
        serializer: (T) -> String
    ): String? = parallelDefault(10_000) {
        // แปลงข้อมูลจาก T เป็น String โดยใช้ serializer ที่ส่งเข้ามา
        val serializedValue = serializer.invoke(value)
        // เก็บข้อมูลใน Redis พร้อมกำหนดเวลาหมดอายุ
        val result = redisCommands.setex(key, expirySeconds, serializedValue)

        return@parallelDefault result
    }

    /**
     * ฟังก์ชันสำหรับดึงข้อมูลจาก Redis
     * @param key: คีย์ที่ใช้ค้นหาข้อมูลใน Redis
     * @param deserializer: Lambda expression สำหรับแปลงข้อมูลจาก String เป็น T
     * @return ข้อมูลที่ถูกดึงออกมาในรูปแบบของ T หรือ null หากไม่มีข้อมูลในคีย์นั้น
     */
    private suspend fun <T> getCacheData(
        key: String,
        deserializer: (String) -> T
    ): T? = parallelDefault(10_000) {
        // ดึงข้อมูลจาก Redis โดยใช้คีย์
        val serializedValue = redisCommands[key]
        return@parallelDefault serializedValue?.let { deserializer.invoke(it) }
    }

    suspend fun getCache(key: String): String? {
        return runBlocking { getCacheData(key) { it } }
    }

    fun setCache(key: String, value: String, expirySeconds: Long): String? {
        return runBlocking { setCacheData(key, value, expirySeconds) { it } }
    }



}