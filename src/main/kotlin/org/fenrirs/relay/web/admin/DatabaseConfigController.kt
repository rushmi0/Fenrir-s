package org.fenrirs.relay.web.admin

import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Produces
import io.micronaut.http.annotation.Put
import io.micronaut.http.annotation.RequestAttribute
import io.micronaut.serde.annotation.Serdeable

import org.fenrirs.relay.web.AdminAuthFilter
import org.fenrirs.storage.DatabaseFactory
import org.fenrirs.storage.statement.KeyValueStoreImpl

import java.sql.DriverManager
import java.sql.SQLException

@Serdeable
data class H2PoolConfig(
    val minimumIdle: Int,
    val maximumPoolSize: Int,
    val leakDetectionThreshold: Int
)

@Serdeable
data class PostgresConfig(
    val url: String,
    val name: String,
    val username: String,
    val password: String,
    val minimumIdle: Int,
    val maximumPoolSize: Int,
    val idleTimeout: Int,
    val keepaliveTime: Int,
    val maxLifetime: Int,
    val leakDetectionThreshold: Int,
    val validationTimeout: Int,
    val transactionIsolation: String
)

@Serdeable
data class DatabaseSettings(
    val activeMode: String,
    val h2: H2PoolConfig,
    val postgres: PostgresConfig
)

@Serdeable
data class DatabaseSizeInfo(
    val mode: String,
    val sizeBytes: Long
)

@Serdeable
data class ConnectionTestResult(
    val ok: Boolean,
    val message: String
)


@Controller("/inter/api/v1/admin/database")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class DatabaseConfigController {

    @Get
    fun get(@RequestAttribute(AdminAuthFilter.ROLE_ATTRIBUTE) role: String): HttpResponse<*> {
        if (!RoleGuard.canAccessConsole(role)) return RoleGuard.forbidden("general users cannot access the Admin Console")
        return HttpResponse.ok(readSettings())
    }

    @Put
    fun put(
        @RequestAttribute(AdminAuthFilter.ROLE_ATTRIBUTE) role: String,
        @Body body: DatabaseSettings
    ): HttpResponse<*> {
        if (!RoleGuard.canWrite(role)) return RoleGuard.forbidden()

        KeyValueStoreImpl.set("PRIMARY_DATABASE_ENABLED", (body.activeMode == "POSTGRES").toString())

        KeyValueStoreImpl.set("DB_H2_MIN_IDLE", body.h2.minimumIdle.toString())
        KeyValueStoreImpl.set("DB_H2_MAX_POOL_SIZE", body.h2.maximumPoolSize.toString())
        KeyValueStoreImpl.set("DB_H2_LEAK_DETECTION_THRESHOLD", body.h2.leakDetectionThreshold.toString())

        KeyValueStoreImpl.set("DATABASE_URL", body.postgres.url)
        KeyValueStoreImpl.set("DATABASE_NAME", body.postgres.name)
        KeyValueStoreImpl.set("DATABASE_USERNAME", body.postgres.username)
        KeyValueStoreImpl.set("DATABASE_PASSWORD", body.postgres.password)
        KeyValueStoreImpl.set("DB_PG_MIN_IDLE", body.postgres.minimumIdle.toString())
        KeyValueStoreImpl.set("DB_PG_MAX_POOL_SIZE", body.postgres.maximumPoolSize.toString())
        KeyValueStoreImpl.set("DB_PG_IDLE_TIMEOUT", body.postgres.idleTimeout.toString())
        KeyValueStoreImpl.set("DB_PG_KEEPALIVE_TIME", body.postgres.keepaliveTime.toString())
        KeyValueStoreImpl.set("DB_PG_MAX_LIFETIME", body.postgres.maxLifetime.toString())
        KeyValueStoreImpl.set("DB_PG_LEAK_DETECTION_THRESHOLD", body.postgres.leakDetectionThreshold.toString())
        KeyValueStoreImpl.set("DB_PG_VALIDATION_TIMEOUT", body.postgres.validationTimeout.toString())
        KeyValueStoreImpl.set("DB_PG_TRANSACTION_ISOLATION", body.postgres.transactionIsolation)

        return HttpResponse.ok(readSettings())
    }

    @Get("/size")
    fun size(@RequestAttribute(AdminAuthFilter.ROLE_ATTRIBUTE) role: String): HttpResponse<*> {
        if (!RoleGuard.canAccessConsole(role)) return RoleGuard.forbidden("general users cannot access the Admin Console")
        return HttpResponse.ok(
            DatabaseSizeInfo(DatabaseFactory.activeDatabaseMode(), DatabaseFactory.activeDatabaseSizeBytes())
        )
    }

    @Post("/test")
    fun test(
        @RequestAttribute(AdminAuthFilter.ROLE_ATTRIBUTE) role: String,
        @Body body: PostgresConfig
    ): HttpResponse<*> {
        if (!RoleGuard.canWrite(role)) return RoleGuard.forbidden()

        val jdbcUrl = "${body.url}/${body.name}"
        return try {
            DriverManager.setLoginTimeout(TEST_TIMEOUT_SECS)
            DriverManager.getConnection(jdbcUrl, body.username, body.password).use {
                HttpResponse.ok(ConnectionTestResult(true, "connected"))
            }
        } catch (e: SQLException) {
            HttpResponse.ok(ConnectionTestResult(false, e.message ?: "connection failed"))
        }
    }

    private fun readSettings(): DatabaseSettings = DatabaseSettings(
        activeMode = if (KeyValueStoreImpl.get("PRIMARY_DATABASE_ENABLED")?.toBoolean() == true) "POSTGRES" else "H2",
        h2 = H2PoolConfig(
            minimumIdle = KeyValueStoreImpl.get("DB_H2_MIN_IDLE")?.toIntOrNull() ?: 1,
            maximumPoolSize = KeyValueStoreImpl.get("DB_H2_MAX_POOL_SIZE")?.toIntOrNull() ?: 8,
            leakDetectionThreshold = KeyValueStoreImpl.get("DB_H2_LEAK_DETECTION_THRESHOLD")?.toIntOrNull() ?: 30_000
        ),
        postgres = PostgresConfig(
            url = KeyValueStoreImpl.get("DATABASE_URL") ?: "",
            name = KeyValueStoreImpl.get("DATABASE_NAME") ?: "",
            username = KeyValueStoreImpl.get("DATABASE_USERNAME") ?: "",
            password = KeyValueStoreImpl.get("DATABASE_PASSWORD") ?: "",
            minimumIdle = KeyValueStoreImpl.get("DB_PG_MIN_IDLE")?.toIntOrNull() ?: 10,
            maximumPoolSize = KeyValueStoreImpl.get("DB_PG_MAX_POOL_SIZE")?.toIntOrNull() ?: 64,
            idleTimeout = KeyValueStoreImpl.get("DB_PG_IDLE_TIMEOUT")?.toIntOrNull() ?: 60_000,
            keepaliveTime = KeyValueStoreImpl.get("DB_PG_KEEPALIVE_TIME")?.toIntOrNull() ?: 600_000,
            maxLifetime = KeyValueStoreImpl.get("DB_PG_MAX_LIFETIME")?.toIntOrNull() ?: 2_000_000,
            leakDetectionThreshold = KeyValueStoreImpl.get("DB_PG_LEAK_DETECTION_THRESHOLD")?.toIntOrNull() ?: 30_000,
            validationTimeout = KeyValueStoreImpl.get("DB_PG_VALIDATION_TIMEOUT")?.toIntOrNull() ?: 3_000,
            transactionIsolation = KeyValueStoreImpl.get("DB_PG_TRANSACTION_ISOLATION") ?: "TRANSACTION_REPEATABLE_READ"
        )
    )

    companion object {
        private const val TEST_TIMEOUT_SECS = 5
    }
}
