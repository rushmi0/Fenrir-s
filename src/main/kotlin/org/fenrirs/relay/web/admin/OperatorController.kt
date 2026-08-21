package org.fenrirs.relay.web.admin

import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Delete
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
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

@Serdeable
data class AddOperatorRequest(val pubkey: String, val role: String)

/**
 * Admin/Operator management (see task's "Authentication Scope" - Owner/Admin/Operator only).
 * OPERATOR (general user) has no access; ADMIN/OWNER (admin user) can list operators, but only
 * OWNER can add or remove one, and the OWNER itself can't be removed here (no owner-transfer
 * flow in Phase 1).
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

    @Post
    fun add(
        @RequestAttribute(AdminAuthFilter.ROLE_ATTRIBUTE) role: String,
        @Body body: AddOperatorRequest
    ): HttpResponse<*> {
        if (!RoleGuard.isOwner(role)) return RoleGuard.forbidden("only the relay OWNER can manage operators")

        val pubkey = body.pubkey.trim().lowercase()
        if (pubkey.length != 64 || pubkey.any { it !in HEX_CHARS }) {
            return HttpResponse.badRequest(JsonError("invalid: pubkey must be a 64-character hex string"))
        }
        if (body.role != Role.ADMIN.name && body.role != Role.OPERATOR.name) {
            return HttpResponse.badRequest(JsonError("invalid: role must be ADMIN or OPERATOR"))
        }
        if (OperatorStoreImpl.find(pubkey) != null) {
            return HttpResponse.badRequest(JsonError("pubkey is already registered"))
        }

        OperatorStoreImpl.add(pubkey, body.role)
        return HttpResponse.ok(OperatorDto(pubkey, body.role, System.currentTimeMillis() / 1000))
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

    companion object {
        private const val HEX_CHARS = "0123456789abcdef"
    }
}
