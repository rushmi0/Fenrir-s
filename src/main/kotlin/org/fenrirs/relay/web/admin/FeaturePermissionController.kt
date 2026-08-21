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

import org.fenrirs.relay.core.policy.FeatureRegistry
import org.fenrirs.relay.web.AdminAuthFilter
import org.fenrirs.storage.service.PermissionRole
import org.fenrirs.storage.statement.RolePermissionStoreImpl

@Serdeable
data class FeatureDto(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val alwaysAdminOnly: Boolean
)

@Serdeable
data class RolePermissionsResponse(val role: String, val permissions: Map<String, Boolean>)

@Serdeable
data class UpdateRolePermissionsRequest(val permissions: Map<String, Boolean>)


@Controller("/inter/api/v1/admin/permissions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class FeaturePermissionController {

    @Get("/features")
    fun listFeatures(@RequestAttribute(AdminAuthFilter.ROLE_ATTRIBUTE) role: String): HttpResponse<*> {
        if (!RoleGuard.canAccessConsole(role)) return RoleGuard.forbidden("general users cannot access the Admin Console")
        return HttpResponse.ok(FeatureRegistry.all().map {
            FeatureDto(it.id, it.name, it.description, it.category.name, it.alwaysAdminOnly)
        })
    }

    @Get("/roles/{targetRole}")
    fun getRolePermissions(
        @RequestAttribute(AdminAuthFilter.ROLE_ATTRIBUTE) role: String,
        targetRole: String
    ): HttpResponse<*> {
        if (!RoleGuard.canAccessConsole(role)) return RoleGuard.forbidden("general users cannot access the Admin Console")
        val permissionRole = parseRole(targetRole)
            ?: return HttpResponse.badRequest(JsonError("invalid: role must be GENERAL or GUEST"))
        return HttpResponse.ok(RolePermissionsResponse(permissionRole.name, readRole(permissionRole)))
    }

    @Put("/roles/{targetRole}")
    fun putRolePermissions(
        @RequestAttribute(AdminAuthFilter.ROLE_ATTRIBUTE) role: String,
        targetRole: String,
        @Body body: UpdateRolePermissionsRequest
    ): HttpResponse<*> {
        if (!RoleGuard.canWrite(role)) return RoleGuard.forbidden()
        val permissionRole = parseRole(targetRole)
            ?: return HttpResponse.badRequest(JsonError("invalid: role must be GENERAL or GUEST"))

        val known = body.permissions.filterKeys { FeatureRegistry.find(it) != null }
        RolePermissionStoreImpl.setMany(permissionRole, known)
        return HttpResponse.ok(RolePermissionsResponse(permissionRole.name, readRole(permissionRole)))
    }

    private fun readRole(role: PermissionRole): Map<String, Boolean> =
        FeatureRegistry.all().associate { feature ->
            val allowed = RolePermissionStoreImpl.get(role, feature.id)
                ?: (if (role == PermissionRole.GENERAL) feature.defaultGeneralAllowed else feature.defaultGuestAllowed)
            feature.id to allowed
        }

    private fun parseRole(value: String): PermissionRole? =
        PermissionRole.entries.firstOrNull { it.name == value }
}
