package org.fenrirs.storage

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.fenrirs.storage.table.KV_STORE
import org.fenrirs.storage.table.OPERATOR

import org.fenrirs.storage.table.EVENT
import org.fenrirs.storage.table.SUBSCRIPTION
import org.fenrirs.utils.ExecTask.asyncTask
import org.jetbrains.exposed.v1.core.StdOutSqlLogger

import org.fenrirs.storage.statement.KeyValueStoreImpl

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.io.File


internal object StoragePaths {

    private val baseDir: File by lazy {
        File(System.getProperty("user.home"), ".fenrir-s/db").apply { mkdirs() }
    }

    fun resolve(dbName: String): File = File(baseDir, dbName)
}

@Singleton
object DatabaseFactory {

    @Inject
    lateinit var ENV: NostrRelayConfig

    internal lateinit var primaryDb: Database

    internal lateinit var secondaryDb: Database

    private lateinit var cacheDb: Database

    internal lateinit var configDb: Database

    @Volatile
    internal lateinit var activeDb: Database


    @JvmStatic
    fun initialize() {

        if (!::ENV.isInitialized) {
            throw IllegalStateException("ENV has not been initialized")
        }

        configDb = Database.connect(configH2Hikari())
        transaction(configDb) {
            SchemaUtils.create(KV_STORE)
            SchemaUtils.create(OPERATOR)
        }

        // ต้อง sync ค่า default จาก .env เข้า KV_STORE ที่นี่ (ก่อนสร้าง pool อื่น ๆ) เพราะ businessH2Hikari()/
        // postgresHikari() ด้านล่างอ่านค่าการตั้งค่า pool/connection จาก ENV (KV_STORE-backed) แล้ว -
        // configDb เชื่อมต่อได้เองโดยไม่ต้องพึ่งค่าเหล่านี้ จึงไม่มีปัญหา chicken-and-egg
        KeyValueStoreImpl.sync(ENV.envDefaults)

        secondaryDb = Database.connect(businessH2Hikari())
        transaction(secondaryDb) {
            SchemaUtils.create(EVENT)
        }

        cacheDb = Database.connect(cacheHikari())
        transaction(cacheDb) {
            SchemaUtils.create(SUBSCRIPTION)
        }

        if (ENV.PRIMARY_DATABASE_ENABLED) {
            primaryDb = Database.connect(postgresHikari())
            DatabaseFailover.start()
        } else {
            activeDb = secondaryDb
        }
    }

    private fun postgresHikari(): HikariDataSource {

        val config = HikariConfig().apply {

            driverClassName = "org.postgresql.Driver"

            jdbcUrl = "${ENV.DATABASE_URL}/${ENV.DATABASE_NAME}"
            username = ENV.DATABASE_USERNAME
            password = ENV.DATABASE_PASSWORD

            minimumIdle = ENV.DB_PG_MIN_IDLE
            maximumPoolSize = ENV.DB_PG_MAX_POOL_SIZE

            isAutoCommit = false

            idleTimeout = ENV.DB_PG_IDLE_TIMEOUT.toLong()
            keepaliveTime = ENV.DB_PG_KEEPALIVE_TIME.toLong()
            maxLifetime = ENV.DB_PG_MAX_LIFETIME.toLong()
            leakDetectionThreshold = ENV.DB_PG_LEAK_DETECTION_THRESHOLD.toLong()
            validationTimeout = ENV.DB_PG_VALIDATION_TIMEOUT.toLong()

            transactionIsolation = ENV.DB_PG_TRANSACTION_ISOLATION

            initializationFailTimeout = -1

            validate()
        }

        return HikariDataSource(config)
    }

    private fun businessH2Hikari(): HikariDataSource {

        val dbFile = StoragePaths.resolve("relay-biz")

        val config = HikariConfig().apply {

            driverClassName = "org.h2.Driver"

            jdbcUrl = "jdbc:h2:file:${dbFile.absolutePath};MODE=PostgreSQL;DB_CLOSE_ON_EXIT=TRUE"
            username = "sa"
            password = ""

            minimumIdle = ENV.DB_H2_MIN_IDLE
            maximumPoolSize = ENV.DB_H2_MAX_POOL_SIZE

            isAutoCommit = false

            leakDetectionThreshold = ENV.DB_H2_LEAK_DETECTION_THRESHOLD.toLong()

            validate()
        }

        return HikariDataSource(config)
    }

    private fun cacheHikari(): HikariDataSource {

        val config = HikariConfig().apply {

            driverClassName = "org.h2.Driver"

            jdbcUrl = "jdbc:h2:mem:subscription-cache;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000"
            username = "sa"
            password = ""

            minimumIdle = 1
            maximumPoolSize = 10

            isAutoCommit = false

            leakDetectionThreshold = 30_000

            validate()
        }

        return HikariDataSource(config)
    }

    private fun configH2Hikari(): HikariDataSource {

        val dbFile = StoragePaths.resolve("relay-sys")

        val config = HikariConfig().apply {

            driverClassName = "org.h2.Driver"

            jdbcUrl = "jdbc:h2:file:${dbFile.absolutePath};DB_CLOSE_ON_EXIT=TRUE"
            username = "sa"
            password = ""

            minimumIdle = 1
            maximumPoolSize = 10

            isAutoCommit = false

            leakDetectionThreshold = 30_000

            validate()
        }

        return HikariDataSource(config)
    }


    suspend fun <T> queryTask(block: () -> T): T = asyncTask {
        transaction(activeDb) {
            //addLogger(StdOutSqlLogger)
            block()
        }
    }

    suspend fun <T> cacheTask(block: () -> T): T = asyncTask {
        transaction(cacheDb) {
            //addLogger(StdOutSqlLogger)
            block()
        }
    }

    fun <T> configTask(block: () -> T): T = transaction(configDb) {
        //addLogger(StdOutSqlLogger)
        block()
    }

    /** Which engine the business (event store) database is actually running on right now -
     * reflects [DatabaseFailover]'s current state, not just what's configured. */
    fun activeDatabaseMode(): String =
        if (::primaryDb.isInitialized && activeDb === primaryDb) "POSTGRES" else "H2"

    /** Current size of the active business database - PostgreSQL via `pg_database_size`, local
     * H2 via the `relay-biz.mv.db` file size on disk. */
    fun activeDatabaseSizeBytes(): Long = if (activeDatabaseMode() == "POSTGRES") {
        transaction(primaryDb) {
            exec("SELECT pg_database_size(current_database())") { rs -> if (rs.next()) rs.getLong(1) else null }
        } ?: 0L
    } else {
        File("${StoragePaths.resolve("relay-biz").absolutePath}.mv.db").length()
    }

}