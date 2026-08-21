package org.fenrirs.relay.web.permission

import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.serde.annotation.Serdeable

import org.fenrirs.relay.core.policy.AdminSessionStore
import org.fenrirs.relay.core.policy.PermissionService

@Serdeable
data class EffectivePermissionsResponse(
    val role: String,
    val pubkey: String?,
    val permissions: Map<String, Boolean>
)

/**
 * Deliberately lives outside the Admin Console's path prefix so [org.fenrirs.relay.web.AdminAuthFilter]'s
 * unconditional-401-on-missing-token behavior never applies here - this is the one endpoint that
 * must answer for Admin, General, and Guest (no token at all). A missing/invalid/expired token
 * resolves to Guest rather than a 401. The frontend uses this purely for nav/route/UI behavior -
 * every genuine enforcement point re-checks via the same [PermissionService] independently.
 */
@Controller("/inter/api/v1/permissions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.ALL)
class PermissionMeController(
    private val sessions: AdminSessionStore,
    private val permissionService: PermissionService
) {

    @Get("/me")
    fun me(request: HttpRequest<*>): EffectivePermissionsResponse {
        val header = request.headers.get(AUTHORIZATION_HEADER)
        val token = header?.takeIf { it.startsWith(BEARER_PREFIX) }?.removePrefix(BEARER_PREFIX)
        val session = token?.let { sessions.resolve(it) }

        val subject = permissionService.resolveSubject(session?.role, session?.pubkey)
        return EffectivePermissionsResponse(
            role = subject.effectiveRole.name,
            pubkey = subject.pubkey,
            permissions = permissionService.effectivePermissions(subject)
        )
    }

    companion object {
        private const val AUTHORIZATION_HEADER = "Authorization"
        private const val BEARER_PREFIX = "Bearer "
    }
}
