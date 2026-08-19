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

            minimumIdle = 10
            maximumPoolSize = 64

            isAutoCommit = false

            idleTimeout = 60_000
            keepaliveTime = 600_000
            maxLifetime = 2_000_000
            leakDetectionThreshold = 30_000
            validationTimeout = 3_000

            transactionIsolation = "TRANSACTION_REPEATABLE_READ"

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

            minimumIdle = 2
            maximumPoolSize = 20

            isAutoCommit = false

            leakDetectionThreshold = 30_000

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


}