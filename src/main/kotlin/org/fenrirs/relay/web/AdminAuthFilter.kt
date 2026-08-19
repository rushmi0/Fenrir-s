package org.fenrirs.relay.web

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ServerFilter
import io.micronaut.http.hateoas.JsonError

import org.fenrirs.relay.core.policy.AdminSessionStore

/**
 * Auth gate for the Admin Console API, i.e. anything under [ADMIN_API_PATTERN].
 *
 * Deliberately separate from [AuthFilter]: that one resolves a pubkey already authenticated over
 * the relay's NIP-42 WebSocket session; this one resolves an HTTP-only bearer token minted by
 * [org.fenrirs.relay.web.auth.AuthController] (or [org.fenrirs.relay.web.setup.SetupController])
 * after verifying a signed login event - no live WebSocket required. This filter only handles
 * *authentication* (who is this token for, and what role do they hold) - per-endpoint
 * *authorization* (can this role perform this action) is left to [RoleGuard], read by each
 * controller from the request attributes this filter sets.
 */
@ServerFilter(AdminAuthFilter.ADMIN_API_PATTERN)
class AdminAuthFilter(private val sessions: AdminSessionStore) {

    @RequestFilter
    fun filter(request: HttpRequest<*>): HttpResponse<*>? {
        val header = request.headers.get(AUTHORIZATION_HEADER)
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return HttpResponse.unauthorized<JsonError>()
                .body(JsonError("missing or malformed $AUTHORIZATION_HEADER header - expected 'Bearer <token>'"))
        }

        val token = header.removePrefix(BEARER_PREFIX)
        val session = sessions.resolve(token)
            ?: return HttpResponse.unauthorized<JsonError>()
                .body(JsonError("session is invalid or has expired - please log in again"))

        request.setAttribute(PUBKEY_ATTRIBUTE, session.pubkey)
        request.setAttribute(ROLE_ATTRIBUTE, session.role)
        return null
    }

    companion object {
        const val ADMIN_API_PATTERN = "/inter/api/v1/admin/**"
        const val AUTHORIZATION_HEADER = "Authorization"
        const val BEARER_PREFIX = "Bearer "
        const val PUBKEY_ATTRIBUTE = "admin.pubkey"
        const val ROLE_ATTRIBUTE = "admin.role"
    }
}
