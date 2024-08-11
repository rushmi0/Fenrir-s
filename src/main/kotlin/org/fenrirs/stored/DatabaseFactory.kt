package org.fenrirs.stored

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.fenrirs.stored.Environment.DATABASE_NAME

import org.fenrirs.stored.Environment.DATABASE_PASSWORD
import org.fenrirs.stored.Environment.DATABASE_URL
import org.fenrirs.stored.Environment.DATABASE_USERNAME

import org.fenrirs.stored.table.EVENT
import org.fenrirs.utils.ExecTask.runWithVirtualThreadsPerTask

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction


object DatabaseFactory {

    @JvmStatic
    fun initialize() {
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
            jdbcUrl = "$DATABASE_URL/$DATABASE_NAME"
            username = DATABASE_USERNAME
            password = DATABASE_PASSWORD

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