package org.fenrirs.stored

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

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

        val config = HikariConfig()

        // กำหนดชื่อไดรเวอร์ของฐานข้อมูล
        config.driverClassName = "org.postgresql.Driver"

        // กำหนด่าสำหรับการเชื่อมต่อกับฐานข้อมูล
        config.jdbcUrl = DATABASE_URL //"jdbc:postgresql://localhost:5432/nostr"
        config.username = DATABASE_USERNAME//"rushmi0"
        config.password = DATABASE_PASSWORD//"sql@min"

        config.minimumIdle = 3
        config.maximumPoolSize = 7

        // กำหนดให้ไม่ทำ Auto Commit โดยอัตโนมัติ
        config.isAutoCommit = false

        config.idleTimeout = 60000
        config.keepaliveTime = 60000
        config.maxLifetime = 2000000
        config.leakDetectionThreshold = 30000
        config.validationTimeout = 3000

        config.transactionIsolation = "TRANSACTION_REPEATABLE_READ"

        // ตรวจสอบความถูกต้องของค่าการกำหนดค่า
        config.validate()

        // สร้างและคืนค่าอ็อบเจกต์ HikariDataSource ที่กำหนดค่า
        return HikariDataSource(config)
    }


    fun <T> queryTask(block: () -> T): T = runWithVirtualThreadsPerTask {
        transaction {
            block()
        }
    }

}