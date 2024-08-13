package org.fenrirs.stored

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

import org.fenrirs.stored.table.EVENT
import org.fenrirs.utils.ExecTask.runWithVirtualThreadsPerTask

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction


object DatabaseFactory {

    lateinit var ENV: Environment

    @JvmStatic
    fun initialize() {

        if (!::ENV.isInitialized) {
            throw IllegalStateException("ENV has not been initialized")
        }

        Database.connect(hikariConfig())
        transaction {
            SchemaUtils.create(EVENT)
        }
    }

    private fun hikariConfig(): HikariDataSource {

        val config = HikariConfig().apply {
            // กำหนดชื่อไดรเวอร์ของฐานข้อมูล
            driverClassName = "org.postgresql.Driver"

            // กำหนด่าสำหรับการเชื่อมต่อกับฐานข้อมูล
            jdbcUrl = "${ENV.DATABASE_URL}/${ENV.DATABASE_NAME}"
            username = ENV.DATABASE_USERNAME
            password = ENV.DATABASE_PASSWORD

            minimumIdle = 2
            maximumPoolSize = 10

            // กำหนดให้ไม่ทำ Auto Commit โดยอัตโนมัติ
            isAutoCommit = false

            idleTimeout = 60000
            keepaliveTime = 60000
            maxLifetime = 2000000
            leakDetectionThreshold = 30000
            validationTimeout = 3000

            transactionIsolation = "TRANSACTION_REPEATABLE_READ"

            validate()
        }

        // สร้างและคืนค่าอ็อบเจกต์ HikariDataSource ที่กำหนดค่า
        return HikariDataSource(config)
    }


    fun <T> queryTask(block: () -> T): T = runWithVirtualThreadsPerTask {
        transaction {
            block()
        }
    }

}