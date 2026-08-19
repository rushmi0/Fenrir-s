package org.fenrirs.relay.web.admin

import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.annotation.Put
import io.micronaut.http.annotation.RequestAttribute
import io.micronaut.serde.annotation.Serdeable

import org.fenrirs.relay.web.AdminAuthFilter
import org.fenrirs.storage.statement.KeyValueStoreImpl

@Serdeable
data class RelayConfig(
    val name: String,
    val description: String,
    val npub: String,
    val contact: String,
    val relayUrl: String
)

@Serdeable
data class SystemConfig(
    val maxFilters: Int,
    val maxLimit: Int,
    val backupEnabled: Boolean,
    val sync: String
)

/**
 * Read/write for the "Relay configuration" and "System settings" groups (see task's
 * "Configuration" section) - both are thin wrappers over [KeyValueStoreImpl], the same config
 * store [org.fenrirs.storage.NostrRelayConfig] reads from. OPERATOR role is read-only; ADMIN/OWNER
 * can write (see [RoleGuard]).
 */
@Controller("/inter/api/v1/admin/config")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class ConfigController {

    @Get("/relay")
    fun getRelay(): RelayConfig = readRelay()

    @Put("/relay")
    fun putRelay(
        @RequestAttribute(AdminAuthFilter.ROLE_ATTRIBUTE) role: String,
        @Body body: RelayConfig
    ): HttpResponse<*> {
        if (!RoleGuard.canWrite(role)) return RoleGuard.forbidden()

        KeyValueStoreImpl.set("NAME", body.name)
        KeyValueStoreImpl.set("DESCRIPTION", body.description)
        KeyValueStoreImpl.set("NPUB", body.npub)
        KeyValueStoreImpl.set("CONTACT", body.contact)
        KeyValueStoreImpl.set("RELAY_URL", body.relayUrl)
        return HttpResponse.ok(readRelay())
    }

    @Get("/system")
    fun getSystem(): SystemConfig = readSystem()

    @Put("/system")
    fun putSystem(
        @RequestAttribute(AdminAuthFilter.ROLE_ATTRIBUTE) role: String,
        @Body body: SystemConfig
    ): HttpResponse<*> {
        if (!RoleGuard.canWrite(role)) return RoleGuard.forbidden()

        KeyValueStoreImpl.set("MAX_FILTERS", body.maxFilters.toString())
        KeyValueStoreImpl.set("MAX_LIMIT", body.maxLimit.toString())
        KeyValueStoreImpl.set("BACKUP_ENABLED", body.backupEnabled.toString())
        KeyValueStoreImpl.set("SYNC", body.sync)
        return HttpResponse.ok(readSystem())
    }

    private fun readRelay(): RelayConfig = RelayConfig(
        name = KeyValueStoreImpl.get("NAME") ?: "",
        description = KeyValueStoreImpl.get("DESCRIPTION") ?: "",
        npub = KeyValueStoreImpl.get("NPUB") ?: "",
        contact = KeyValueStoreImpl.get("CONTACT") ?: "",
        relayUrl = KeyValueStoreImpl.get("RELAY_URL") ?: ""
    )

    private fun readSystem(): SystemConfig = SystemConfig(
        maxFilters = KeyValueStoreImpl.get("MAX_FILTERS")?.toIntOrNull() ?: 5,
        maxLimit = KeyValueStoreImpl.get("MAX_LIMIT")?.toIntOrNull() ?: 500,
        backupEnabled = KeyValueStoreImpl.get("BACKUP_ENABLED")?.toBoolean() ?: false,
        sync = KeyValueStoreImpl.get("SYNC") ?: ""
    )
}
