package org.fenrirs.relay.web.admin

import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Delete
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.annotation.Put
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.RequestAttribute
import io.micronaut.http.hateoas.JsonError
import io.micronaut.serde.annotation.Serdeable

import org.fenrirs.relay.core.policy.EffectiveRole
import org.fenrirs.relay.core.policy.PermissionService
import org.fenrirs.relay.core.policy.PermissionSubject
import org.fenrirs.relay.web.AdminAuthFilter
import org.fenrirs.storage.service.OverrideState
import org.fenrirs.storage.service.PermissionRole
import org.fenrirs.storage.service.WhitelistStatus
import org.fenrirs.storage.statement.AccountPermissionStoreImpl
import org.fenrirs.storage.statement.WhitelistAccountStoreImpl

@Serdeable
data class WhitelistAccountDto(
    val pubkey: String,
    val role: String,
    val status: String,
    val addedAt: Long,
    val featureAccess: Map<String, Boolean>
)

@Serdeable
data class AddWhitelistAccountRequest(val pubkey: String, val role: String, val overrides: Map<String, String>? = null)

@Serdeable
data class UpdateWhitelistAccountRequest(val role: String)

/**
 * CRUD for [org.fenrirs.storage.table.WHITELIST_ACCOUNT] - the Role Management "Accounts" tab.
 * Status is deliberately not editable here (only set at creation, always ACTIVE) - the UI has no
 * control for it yet, see the Role Management redesign plan.
 */
@Controller("/inter/api/v1/admin/permissions/whitelist")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class WhitelistAccountController(private val permissionService: PermissionService) {

    @Get
    fun list(@RequestAttribute(AdminAuthFilter.ROLE_ATTRIBUTE) role: String): HttpResponse<*> {
        if (!RoleGuard.canAccessConsole(role)) return RoleGuard.forbidden("general users cannot access the Admin Console")
        return HttpResponse.ok(WhitelistAccountStoreImpl.all().map { it.toDto() })
    }

    @Post
    fun add(
        @RequestAttribute(AdminAuthFilter.ROLE_ATTRIBUTE) role: String,
        @Body body: AddWhitelistAccountRequest
    ): HttpResponse<*> {
        if (!RoleGuard.canWrite(role)) return RoleGuard.forbidden()

        val pubkey = body.pubkey.trim()
        if (pubkey.isEmpty()) return HttpResponse.badRequest(JsonError("pubkey is required"))

        val permissionRole = parseRole(body.role)
            ?: return HttpResponse.badRequest(JsonError("invalid: role must be GENERAL or GUEST"))

        if (WhitelistAccountStoreImpl.find(pubkey) != null) {
            return HttpResponse.status<JsonError>(HttpStatus.CONFLICT).body(JsonError("pubkey is already whitelisted"))
        }

        val account = WhitelistAccountStoreImpl.upsert(pubkey, permissionRole, WhitelistStatus.ACTIVE)

        val overrides: Map<String, OverrideState?> = (body.overrides ?: emptyMap())
            .mapValues { (_, value) -> OverrideState.entries.firstOrNull { it.name == value } }
        if (overrides.isNotEmpty()) {
            AccountPermissionStoreImpl.setManyOrClear(pubkey, overrides)
        }

        return HttpResponse.status<WhitelistAccountDto>(HttpStatus.CREATED).body(account.toDto())
    }

    @Put("/{pubkey}")
    fun update(
        @RequestAttribute(AdminAuthFilter.ROLE_ATTRIBUTE) role: String,
        pubkey: String,
        @Body body: UpdateWhitelistAccountRequest
    ): HttpResponse<*> {
        if (!RoleGuard.canWrite(role)) return RoleGuard.forbidden()

        val existing = WhitelistAccountStoreImpl.find(pubkey)
            ?: return HttpResponse.notFound(JsonError("no such whitelisted account"))

        val permissionRole = parseRole(body.role)
            ?: return HttpResponse.badRequest(JsonError("invalid: role must be GENERAL or GUEST"))

        val account = WhitelistAccountStoreImpl.upsert(pubkey, permissionRole, existing.status)
        return HttpResponse.ok(account.toDto())
    }

    @Delete("/{pubkey}")
    fun remove(
        @RequestAttribute(AdminAuthFilter.ROLE_ATTRIBUTE) role: String,
        pubkey: String
    ): HttpResponse<*> {
        if (!RoleGuard.canWrite(role)) return RoleGuard.forbidden()

        if (WhitelistAccountStoreImpl.find(pubkey) == null) {
            return HttpResponse.notFound(JsonError("no such whitelisted account"))
        }
        WhitelistAccountStoreImpl.remove(pubkey)
        AccountPermissionStoreImpl.removeAllForAccount(pubkey)
        return HttpResponse.noContent<Unit>()
    }

    private fun org.fenrirs.storage.service.WhitelistAccount.toDto(): WhitelistAccountDto {
        val effectiveRole = if (role == PermissionRole.GENERAL) EffectiveRole.GENERAL else EffectiveRole.GUEST
        val subject = PermissionSubject(effectiveRole, pubkey, blocked = status == WhitelistStatus.BLOCKED)
        return WhitelistAccountDto(
            pubkey = pubkey,
            role = role.name,
            status = status.name,
            addedAt = addedAt,
            featureAccess = permissionService.effectivePermissions(subject)
        )
    }

    private fun parseRole(value: String): PermissionRole? =
        PermissionRole.entries.firstOrNull { it.name == value }
}
