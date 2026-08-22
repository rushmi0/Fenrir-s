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
import io.micronaut.http.hateoas.JsonError
import io.micronaut.serde.annotation.Serdeable

import org.fenrirs.relay.core.policy.EffectiveRole
import org.fenrirs.relay.core.policy.FeatureRegistry
import org.fenrirs.relay.core.policy.PermissionService
import org.fenrirs.relay.web.AdminAuthFilter
import org.fenrirs.storage.service.OverrideState
import org.fenrirs.storage.service.PermissionRole
import org.fenrirs.storage.statement.AccountPermissionStoreImpl
import org.fenrirs.storage.statement.RolePermissionStoreImpl

@Serdeable
data class AccountOverrideRowDto(
    val featureId: String,
    val roleDefault: Boolean,
    val override: String,
    val effective: Boolean
)

@Serdeable
data class AccountPermissionsResponse(val pubkey: String, val overrides: List<AccountOverrideRowDto>)

@Serdeable
data class UpdateAccountPermissionsRequest(val overrides: Map<String, String>)


@Controller("/inter/api/v1/admin/permissions/accounts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class AccountPermissionController(private val permissionService: PermissionService) {

    @Get("/{pubkey}")
    fun get(
        @RequestAttribute(AdminAuthFilter.ROLE_ATTRIBUTE) role: String,
        pubkey: String
    ): HttpResponse<*> {
        if (!RoleGuard.canAccessConsole(role)) return RoleGuard.forbidden("general users cannot access the Admin Console")

        val subject = permissionService.resolveByPubkey(pubkey)
        if (subject.effectiveRole != EffectiveRole.GENERAL) {
            return HttpResponse.notFound(JsonError("pubkey is not currently General-tier (not on the auth whitelist)"))
        }

        val overrides = AccountPermissionStoreImpl.allForAccount(pubkey)

        val rows = FeatureRegistry.all().map { feature ->
            val overrideState = overrides[feature.id]
            val roleDefault = RolePermissionStoreImpl.get(PermissionRole.GENERAL, feature.id)
                ?: feature.defaultGeneralAllowed
            AccountOverrideRowDto(
                featureId = feature.id,
                roleDefault = roleDefault,
                override = overrideState?.name ?: "INHERIT",
                effective = permissionService.canAccess(subject, feature.id)
            )
        }
        return HttpResponse.ok(AccountPermissionsResponse(pubkey, rows))
    }

    @Put("/{pubkey}")
    fun put(
        @RequestAttribute(AdminAuthFilter.ROLE_ATTRIBUTE) role: String,
        pubkey: String,
        @Body body: UpdateAccountPermissionsRequest
    ): HttpResponse<*> {
        if (!RoleGuard.canWrite(role)) return RoleGuard.forbidden()

        if (permissionService.resolveByPubkey(pubkey).effectiveRole != EffectiveRole.GENERAL) {
            return HttpResponse.notFound(JsonError("pubkey is not currently General-tier (not on the auth whitelist)"))
        }

        val updates: Map<String, OverrideState?> = body.overrides
            .filterKeys { FeatureRegistry.find(it) != null }
            .mapValues { (_, value) -> OverrideState.entries.firstOrNull { it.name == value } }

        AccountPermissionStoreImpl.setManyOrClear(pubkey, updates)
        return get(role, pubkey)
    }
}
