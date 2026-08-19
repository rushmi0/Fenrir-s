package org.fenrirs.relay.web.system

import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.serde.annotation.Serdeable

import org.fenrirs.storage.NostrRelayConfig
import org.fenrirs.storage.statement.OperatorStoreImpl

@Serdeable
data class SystemStatus(val state: String, val relayName: String)

/**
 * Public, unauthenticated status endpoint - the Fenrir Client's Login Card reads this on load to
 * decide whether to render the initial Setup flow or the normal Nostr Login flow (see task's
 * "System States" section). State is derived, not stored: no operators registered yet means the
 * relay is still in INITIAL_SETUP (see [OperatorStoreImpl.isEmpty]).
 */
@Controller("/inter/api/v1/system")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.ALL)
class SystemStatusController(private val env: NostrRelayConfig) {

    @Get("/status")
    fun status(): SystemStatus {
        val state = if (OperatorStoreImpl.isEmpty()) "INITIAL_SETUP" else "READY"
        return SystemStatus(state, env.RELAY_NAME)
    }
}
