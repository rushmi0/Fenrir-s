package org.fenrirs.relay.web.auth

import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Produces
import io.micronaut.http.hateoas.JsonError
import io.micronaut.serde.annotation.Serdeable

import kotlinx.serialization.json.Json

import org.fenrirs.relay.core.nip.nip01.VerifyEvent.verifyNip01
import org.fenrirs.relay.core.nip.nip42.VerifyAuth
import org.fenrirs.relay.core.policy.AdminSessionStore
import org.fenrirs.relay.models.Event
import org.fenrirs.relay.web.AdminAuthFilter
import org.fenrirs.storage.statement.OperatorStoreImpl

import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Serdeable
data class ChallengeResponse(val challenge: String)

@Serdeable
data class LoginResponse(val token: String, val pubkey: String, val role: String)

/**
 * Normal Nostr login for the Admin Console - client signs a kind-22242-shaped event (NIP-07 or
 * nsec, done entirely client-side) over a challenge fetched here, and POSTs it once. Verified with
 * the exact same checks the relay itself uses for NIP-42 (see [verifyNip01] and [VerifyAuth]), but
 * over plain HTTP rather than a live WebSocket - see [AdminSessionStore] for why.
 */
@Controller("/inter/api/v1/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class AuthController(
    private val challenges: LoginChallengeStore,
    private val verifyAuth: VerifyAuth,
    private val sessions: AdminSessionStore
) {

    @Get("/challenge")
    fun challenge(): ChallengeResponse = ChallengeResponse(challenges.issue())

    @Post("/login")
    fun login(@Body raw: String): HttpResponse<*> {
        val event = runCatching { Json.decodeFromString<Event>(raw) }
            .getOrElse { return HttpResponse.badRequest(JsonError("invalid: malformed event JSON")) }

        val challengeTag = event.tags?.firstOrNull { it.size > 1 && it[0] == "challenge" }?.get(1)
        if (challengeTag == null || !challenges.consume(challengeTag)) {
            return HttpResponse.badRequest(JsonError("invalid or expired challenge"))
        }

        val (idValid, idWarning) = event.verifyNip01()
        if (!idValid) return HttpResponse.badRequest(JsonError(idWarning))

        val (authValid, authWarning) = verifyAuth.verify(event, challengeTag)
        if (!authValid) return HttpResponse.badRequest(JsonError(authWarning))

        val operator = OperatorStoreImpl.find(event.pubkey!!)
        if (operator == null) {
            LOG.warn("[ADMIN-AUTH] Login rejected, unknown operator pubkey={}", event.pubkey)
            return HttpResponse.status<JsonError>(HttpStatus.FORBIDDEN)
                .body(JsonError("pubkey is not a registered admin/operator"))
        }

        val token = sessions.issue(operator.pubkey, operator.role)
        LOG.info("[ADMIN-AUTH] Login success pubkey={} role={}", operator.pubkey, operator.role)
        return HttpResponse.ok(LoginResponse(token, operator.pubkey, operator.role))
    }

    @Post("/logout")
    fun logout(@Header("Authorization") authorization: String?): HttpResponse<Unit> {
        val token = authorization?.takeIf { it.startsWith(AdminAuthFilter.BEARER_PREFIX) }
            ?.removePrefix(AdminAuthFilter.BEARER_PREFIX)
        if (token != null) sessions.revoke(token)
        return HttpResponse.noContent()
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(AuthController::class.java)
    }
}
