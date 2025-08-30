package org.fenrirs.storage

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.micronaut.context.annotation.Bean

import jakarta.inject.Inject

import org.fenrirs.storage.table.EVENT
import org.fenrirs.utils.ExecTask.asyncTask

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger

@Bean
object DatabaseFactory {

    @Inject
    lateinit var ENV: NostrRelayConfig

    @JvmStatic
    fun initialize() {

        if (!::ENV.isInitialized) {
            throw IllegalStateException("ENV has not been initialized")
        }

        val directory = File("src/main/resources/db/migration")
        val files = directory.listFiles()

        Database.connect(hikariConfig())
        transaction {
            addLogger(StdOutSqlLogger)
            SchemaUtils.create(EVENT)
            files?.forEach {
                //exec(it.readText())
            }
        }
    }

    private fun hikariConfig(): HikariDataSource {

        val config = HikariConfig().apply {

            driverClassName = "org.postgresql.Driver"

            // กำหนด่าสำหรับการเชื่อมต่อกับฐานข้อมูล
            jdbcUrl = "${ENV.DATABASE_URL}/${ENV.DATABASE_NAME}"
            username = ENV.DATABASE_USERNAME
            password = ENV.DATABASE_PASSWORD

            minimumIdle = 10
            maximumPoolSize = 64

            isAutoCommit = false

            idleTimeout = 60_000
            keepaliveTime = 600_000
            maxLifetime = 2_000_000
            leakDetectionThreshold = 30_000
            validationTimeout = 3_000

            transactionIsolation = "TRANSACTION_REPEATABLE_READ"

            validate()
        }

        // สร้างและคืนค่าอ็อบเจกต์ HikariDataSource ที่กำหนดค่า
        return HikariDataSource(config)
    }


    suspend fun <T> queryTask(block: () -> T): T = asyncTask {
        transaction {
            addLogger(StdOutSqlLogger)
            block()
        }
    }

}