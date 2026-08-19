package org.fenrirs.relay.web.setup

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

import kotlinx.serialization.json.Json

import org.fenrirs.relay.core.nip.nip01.VerifyEvent.verifyNip01
import org.fenrirs.relay.core.nip.nip42.VerifyAuth
import org.fenrirs.relay.core.policy.AdminSessionStore
import org.fenrirs.relay.models.Event
import org.fenrirs.relay.web.auth.ChallengeResponse
import org.fenrirs.relay.web.auth.LoginChallengeStore
import org.fenrirs.relay.web.auth.LoginResponse
import org.fenrirs.storage.statement.KeyValueStoreImpl
import org.fenrirs.storage.statement.OperatorStoreImpl

import org.slf4j.Logger
import org.slf4j.LoggerFactory

private const val SETUP_TOKEN_HEADER = "X-Setup-Token"

/**
 * Initial-setup flow (see task's "Initial Setup" section): only reachable while the relay has no
 * operators registered ([OperatorStoreImpl.isEmpty]), and only with the one-time token
 * [SetupTokenIssuer] printed to the server console at boot. Verifies the signed login event with
 * the same checks as [org.fenrirs.relay.web.auth.AuthController.login], then mints the first
 * OWNER operator and hands back a ready-to-use admin session so the wizard can go straight to the
 * console without a second login round-trip.
 */
@Controller("/inter/api/v1/setup")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class SetupController(
    private val challenges: LoginChallengeStore,
    private val verifyAuth: VerifyAuth,
    private val sessions: AdminSessionStore
) {

    @Get("/challenge")
    fun challenge(@Header(SETUP_TOKEN_HEADER) setupToken: String?): HttpResponse<*> {
        guardInitialSetup()?.let { return it }
        if (setupToken == null || !SetupTokenIssuer.isValid(setupToken)) {
            return HttpResponse.status<JsonError>(HttpStatus.FORBIDDEN)
                .body(JsonError("invalid or expired setup token"))
        }
        return HttpResponse.ok(ChallengeResponse(challenges.issue()))
    }

    @Post("/complete")
    fun complete(@Header(SETUP_TOKEN_HEADER) setupToken: String?, @Body raw: String): HttpResponse<*> {
        guardInitialSetup()?.let { return it }

        if (setupToken == null || !SetupTokenIssuer.isValid(setupToken)) {
            return HttpResponse.status<JsonError>(HttpStatus.FORBIDDEN)
                .body(JsonError("invalid or expired setup token"))
        }

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

        // Re-check right before writing - guards a race between two concurrent setup attempts.
        if (!OperatorStoreImpl.isEmpty()) {
            return HttpResponse.status<JsonError>(HttpStatus.CONFLICT).body(JsonError("setup already completed"))
        }

        val ownerPubkey = event.pubkey!!
        OperatorStoreImpl.add(ownerPubkey, "OWNER")
        KeyValueStoreImpl.set("NPUB", ownerPubkey)
        SetupTokenIssuer.invalidate()

        val token = sessions.issue(ownerPubkey, "OWNER")
        LOG.info("[SETUP] Initial setup completed, owner pubkey={}", ownerPubkey)
        return HttpResponse.ok(LoginResponse(token, ownerPubkey, "OWNER"))
    }

    private fun guardInitialSetup(): HttpResponse<*>? =
        if (!OperatorStoreImpl.isEmpty()) {
            HttpResponse.status<JsonError>(HttpStatus.CONFLICT).body(JsonError("setup already completed"))
        } else null

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(SetupController::class.java)
    }
}
