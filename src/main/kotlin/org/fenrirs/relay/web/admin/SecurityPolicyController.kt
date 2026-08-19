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
 * rules, via the same [KeyValueStoreImpl] config store. OPERATOR (general user) has no access.
 */
@Controller("/inter/api/v1/admin/policy")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class SecurityPolicyController {

    @Get
    fun get(@RequestAttribute(AdminAuthFilter.ROLE_ATTRIBUTE) role: String): HttpResponse<*> {
        if (!RoleGuard.canAccessConsole(role)) return RoleGuard.forbidden("general users cannot access the Admin Console")
        return HttpResponse.ok(read())
    }

    @Put
    fun put(
        @RequestAttribute(AdminAuthFilter.ROLE_ATTRIBUTE) role: String,
        @Body body: SecurityPolicy
    ): HttpResponse<*> {
        if (!RoleGuard.canWrite(role)) return RoleGuard.forbidden()

        // Auth Enabled ("require NIP-42 auth before publishing") and All Pass ("accept events
        // from any pubkey with no restriction") are mutually exclusive - enforced here too (not
        // just in the admin UI) so a direct API write can't leave both true at once. Auth Enabled
        // wins if a single request asks for both, since it's the more restrictive setting.
        val authEnabled = body.authEnabled
        val allPass = body.allPass && !authEnabled

        KeyValueStoreImpl.set("ALL_PASS", allPass.toString())
        KeyValueStoreImpl.set("FOLLOWS_PASS", body.followsPass.toString())
        KeyValueStoreImpl.set("POW_ENABLED", body.powEnabled.toString())
        KeyValueStoreImpl.set("MIN_DIFFICULTY", body.minDifficulty.toString())
        KeyValueStoreImpl.set("AUTH_ENABLED", authEnabled.toString())
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
