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
data class SecurityPolicy(
    val allPass: Boolean,
    val followsPass: Boolean,
    val powEnabled: Boolean,
    val minDifficulty: Int,
    val authEnabled: Boolean,
    /** Comma-separated hex/npub pubkeys - same format [org.fenrirs.storage.NostrRelayConfig] parses. */
    val authWhitelistPubkeys: String
)

/**
 * Read/write for the "Security policy" group (see task's "Configuration" section) - the same
 * settings [org.fenrirs.relay.core.policy.PolicyConfig] reads for the relay's NIP-42/pass-list/PoW
 * rules, via the same [KeyValueStoreImpl] config store. OPERATOR role is read-only.
 */
@Controller("/inter/api/v1/admin/policy")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class SecurityPolicyController {

    @Get
    fun get(): SecurityPolicy = read()

    @Put
    fun put(
        @RequestAttribute(AdminAuthFilter.ROLE_ATTRIBUTE) role: String,
        @Body body: SecurityPolicy
    ): HttpResponse<*> {
        if (!RoleGuard.canWrite(role)) return RoleGuard.forbidden()

        KeyValueStoreImpl.set("ALL_PASS", body.allPass.toString())
        KeyValueStoreImpl.set("FOLLOWS_PASS", body.followsPass.toString())
        KeyValueStoreImpl.set("POW_ENABLED", body.powEnabled.toString())
        KeyValueStoreImpl.set("MIN_DIFFICULTY", body.minDifficulty.toString())
        KeyValueStoreImpl.set("AUTH_ENABLED", body.authEnabled.toString())
        KeyValueStoreImpl.set("AUTH_WHITELIST_PUBKEYS", body.authWhitelistPubkeys)
        return HttpResponse.ok(read())
    }

    private fun read(): SecurityPolicy = SecurityPolicy(
        allPass = KeyValueStoreImpl.get("ALL_PASS")?.toBoolean() ?: false,
        followsPass = KeyValueStoreImpl.get("FOLLOWS_PASS")?.toBoolean() ?: false,
        powEnabled = KeyValueStoreImpl.get("POW_ENABLED")?.toBoolean() ?: false,
        minDifficulty = KeyValueStoreImpl.get("MIN_DIFFICULTY")?.toIntOrNull() ?: 4,
        authEnabled = KeyValueStoreImpl.get("AUTH_ENABLED")?.toBoolean() ?: false,
        authWhitelistPubkeys = KeyValueStoreImpl.get("AUTH_WHITELIST_PUBKEYS") ?: ""
    )
}
