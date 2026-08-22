package org.fenrirs.relay.web.admin

import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Delete
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.annotation.RequestAttribute
import io.micronaut.http.hateoas.JsonError
import io.micronaut.serde.annotation.Serdeable

import org.fenrirs.relay.core.policy.AdminSessionStore
import org.fenrirs.relay.web.AdminAuthFilter
import org.fenrirs.storage.statement.AccountPermissionStoreImpl
import org.fenrirs.storage.statement.OperatorStoreImpl

@Serdeable
data class OperatorDto(val pubkey: String, val role: String, val createdAt: Long)

/**
 * Relay-operator listing/removal only - see task's "Authentication Scope". There is deliberately
 * no way to *add* an operator here any more: the only ADMIN/OWNER-tier account is the relay's own
 * owner, minted once by [org.fenrirs.relay.web.setup.SetupController] during initial setup and
 * never again - "no one will be able to be an Admin/Owner except the owner running Relay." General
 * access is granted instead via the NIP-42 auth whitelist (see [org.fenrirs.relay.core.policy.PolicyConfig.isGeneralWhitelisted]
 * and the Accounts page's Role & Access panel), which needs no operator row at all.
 *
 * OPERATOR (general user) has no access to this controller; ADMIN/OWNER can list operators, but
 * only OWNER can remove one, and the OWNER itself can't be removed here (no owner-transfer flow).
 */
@Controller("/inter/api/v1/admin/operators")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class OperatorController(private val sessions: AdminSessionStore) {

    @Get
    fun list(@RequestAttribute(AdminAuthFilter.ROLE_ATTRIBUTE) role: String): HttpResponse<*> {
        if (!RoleGuard.canAccessConsole(role)) return RoleGuard.forbidden("general users cannot access the Admin Console")
        return HttpResponse.ok(OperatorStoreImpl.all().map { OperatorDto(it.pubkey, it.role, it.createdAt) })
    }

    @Delete("/{pubkey}")
    fun remove(
        @RequestAttribute(AdminAuthFilter.ROLE_ATTRIBUTE) role: String,
        pubkey: String
    ): HttpResponse<*> {
        if (!RoleGuard.isOwner(role)) return RoleGuard.forbidden("only the relay OWNER can manage operators")

        val target = OperatorStoreImpl.find(pubkey)
            ?: return HttpResponse.notFound(JsonError("no such operator"))
        if (target.role == Role.OWNER.name) {
            return HttpResponse.badRequest(JsonError("cannot remove the relay OWNER"))
        }

        OperatorStoreImpl.remove(pubkey)
        AccountPermissionStoreImpl.removeAllForAccount(pubkey)
        sessions.revokeAll(pubkey)
        return HttpResponse.noContent<Unit>()
    }
}
