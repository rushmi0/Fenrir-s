package org.fenrirs.storage

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.fenrirs.storage.table.KV_STORE
import org.fenrirs.storage.table.OPERATOR
import org.fenrirs.storage.table.ROLE_PERMISSION
import org.fenrirs.storage.table.ACCOUNT_PERMISSION_OVERRIDE
import org.fenrirs.storage.table.WHITELIST_ACCOUNT

import org.fenrirs.storage.table.EVENT
import org.fenrirs.storage.table.EVENT_TAGS
import org.fenrirs.utils.ExecTask.asyncTask
import org.jetbrains.exposed.v1.core.StdOutSqlLogger

import org.fenrirs.storage.statement.KeyValueStoreImpl
import org.fenrirs.relay.core.policy.PermissionSeeder
import org.fenrirs.relay.core.policy.WhitelistAccountSeeder

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.io.File
import java.sql.SQLException


internal object StoragePaths {

    private val baseDir: File by lazy {
        File(System.getProperty("user.home"), ".fenrir-s/db").apply { mkdirs() }
    }

    fun resolve(dbName: String): File = File(baseDir, dbName)
}

@Singleton
object DatabaseFactory {

    @Inject
    lateinit var CFG: NostrRelayConfig

    internal lateinit var primaryDb: Database

    internal lateinit var secondaryDb: Database

    internal lateinit var configDb: Database

    @Volatile
    internal lateinit var activeDb: Database


    @JvmStatic
    fun initialize() {

        if (!::CFG.isInitialized) {
            throw IllegalStateException("CFG has not been initialized")
        }

        configDb = Database.connect(configH2Hikari())
        transaction(configDb) {
            SchemaUtils.create(KV_STORE)
            SchemaUtils.create(OPERATOR)
            SchemaUtils.create(ROLE_PERMISSION)
            SchemaUtils.create(ACCOUNT_PERMISSION_OVERRIDE)
            SchemaUtils.create(WHITELIST_ACCOUNT)
        }

        KeyValueStoreImpl.sync(CFG.envDefaults)
        PermissionSeeder.seed()
        WhitelistAccountSeeder.seedFromLegacyCsv(CFG)

        secondaryDb = Database.connect(businessH2Hikari())
        transaction(secondaryDb) {
            SchemaUtils.create(EVENT)
            SchemaUtils.create(EVENT_TAGS)
        }

        if (CFG.PRIMARY_DATABASE_ENABLED) {
            primaryDb = Database.connect(postgresHikari())
            DatabaseFailover.start()
        } else {
            activeDb = secondaryDb
        }
    }

    private fun postgresHikari(): HikariDataSource {

        val config = HikariConfig().apply {

            driverClassName = "org.postgresql.Driver"

            jdbcUrl = "${CFG.DATABASE_URL}/${CFG.DATABASE_NAME}"
            username = CFG.DATABASE_USERNAME
            password = CFG.DATABASE_PASSWORD

            minimumIdle = CFG.DB_PG_MIN_IDLE
            maximumPoolSize = CFG.DB_PG_MAX_POOL_SIZE

            isAutoCommit = false

            idleTimeout = CFG.DB_PG_IDLE_TIMEOUT.toLong()
            keepaliveTime = CFG.DB_PG_KEEPALIVE_TIME.toLong()
            maxLifetime = CFG.DB_PG_MAX_LIFETIME.toLong()
            leakDetectionThreshold = CFG.DB_PG_LEAK_DETECTION_THRESHOLD.toLong()
            validationTimeout = CFG.DB_PG_VALIDATION_TIMEOUT.toLong()

            transactionIsolation = CFG.DB_PG_TRANSACTION_ISOLATION

            initializationFailTimeout = -1

            validate()
        }

        return HikariDataSource(config)
    }

    private fun businessH2Hikari(): HikariDataSource {

        val dbFile = StoragePaths.resolve("relay-biz")

        val config = HikariConfig().apply {

            driverClassName = "org.h2.Driver"

            jdbcUrl = "jdbc:h2:file:${dbFile.absolutePath};MODE=PostgreSQL;DB_CLOSE_ON_EXIT=TRUE;DB_CLOSE_DELAY=-1"
            username = "sa"
            password = ""

            minimumIdle = CFG.DB_H2_MIN_IDLE
            maximumPoolSize = CFG.DB_H2_MAX_POOL_SIZE

            isAutoCommit = false

            leakDetectionThreshold = CFG.DB_H2_LEAK_DETECTION_THRESHOLD.toLong()

            validate()
        }

        return HikariDataSource(config)
    }

    private fun configH2Hikari(): HikariDataSource {

        val dbFile = StoragePaths.resolve("relay-sys")

        val config = HikariConfig().apply {

            driverClassName = "org.h2.Driver"

            jdbcUrl = "jdbc:h2:file:${dbFile.absolutePath};DB_CLOSE_ON_EXIT=TRUE;DB_CLOSE_DELAY=-1"
            username = "sa"
            password = ""

            minimumIdle = 1
            maximumPoolSize = 4

            isAutoCommit = false

            leakDetectionThreshold = 30_000

            validate()
        }

        return HikariDataSource(config)
    }


    suspend fun <T> queryTask(block: () -> T): T = asyncTask {
        val db = activeDb
        try {
            transaction(db) {
                //addLogger(StdOutSqlLogger)
                block()
            }
        } catch (e: Throwable) {
            if (!isDatabaseClosedError(e) || db !== secondaryDb) throw e
            LOG.warn("[DATABASE] business database connection was closed unexpectedly - reconnecting and retrying")
            transaction(reconnectSecondaryDb(db)) { block() }
        }
    }

    fun <T> configTask(block: () -> T): T {
        val db = configDb
        return try {
            transaction(db) {
                //addLogger(StdOutSqlLogger)
                block()
            }
        } catch (e: Throwable) {
            if (!isDatabaseClosedError(e)) throw e
            LOG.warn("[DATABASE] config database connection was closed unexpectedly - reconnecting and retrying")
            transaction(reconnectConfigDb(db)) { block() }
        }
    }

    /**
     * Rebuilds [secondaryDb] (and repoints [activeDb] at it, if that's what was active) with a
     * fresh Hikari pool - the escape hatch for H2 error code 90098 ("the database has been
     * closed"), which [queryTask]/[configTask] catch and retry through exactly once. Guarded so
     * that under a burst of concurrent failures only the first caller actually reconnects; every
     * other caller just observes [secondaryDb] already replaced (`staleDb` no longer matches) and
     * reuses that.
     */
    @Synchronized
    private fun reconnectSecondaryDb(staleDb: Database): Database {
        if (secondaryDb !== staleDb) return secondaryDb
        val fresh = Database.connect(businessH2Hikari())
        val wasActive = ::activeDb.isInitialized && activeDb === staleDb
        secondaryDb = fresh
        if (wasActive) activeDb = fresh
        return fresh
    }

    /** Same idea as [reconnectSecondaryDb], for [configDb]. */
    @Synchronized
    private fun reconnectConfigDb(staleDb: Database): Database {
        if (configDb !== staleDb) return configDb
        configDb = Database.connect(configH2Hikari())
        return configDb
    }

    /**
     * True when [e] (or anything in its cause chain) is H2 error code 90098, "the database has
     * been closed" - an embedded H2 file database can hit this even with `DB_CLOSE_DELAY=-1` set
     * (only guaranteed to rule out closing on last-connection-out, not every path that can shut
     * the engine down) - checked via the standard `java.sql.SQLException.errorCode` rather than
     * H2's own exception type, since the H2 driver is only a runtime dependency of this project
     * (not visible at compile time - see build.gradle.kts).
     */
    private fun isDatabaseClosedError(e: Throwable): Boolean {
        var cause: Throwable? = e
        while (cause != null) {
            if (cause is SQLException && cause.errorCode == H2_DATABASE_CLOSED_ERROR_CODE) return true
            cause = cause.cause
        }
        return false
    }

    private const val H2_DATABASE_CLOSED_ERROR_CODE = 90098

    private val LOG = LoggerFactory.getLogger(DatabaseFactory::class.java)

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