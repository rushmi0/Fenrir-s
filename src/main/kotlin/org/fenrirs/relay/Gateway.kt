package org.fenrirs.relay


import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.OnClose
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import io.micronaut.websocket.annotation.ServerWebSocket

import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import org.fenrirs.relay.core.nip.nip01.command.AUTH
import org.fenrirs.relay.core.nip.nip01.command.EVENT
import org.fenrirs.relay.core.nip.nip01.command.CLOSE
import org.fenrirs.relay.core.nip.nip01.command.COUNT
import org.fenrirs.relay.core.nip.nip01.command.REQ
import org.fenrirs.relay.core.nip.nip01.command.CommandFactory.parse
import org.fenrirs.relay.core.nip.nip01.response.RelayResponse
import org.fenrirs.relay.core.nip.nip01.BasicProtocolFlow
import org.fenrirs.relay.core.policy.PolicyConfig
import org.fenrirs.relay.core.pubsub.SubscriptionRegistry
import org.fenrirs.storage.Authentication

import org.slf4j.Logger
import org.slf4j.LoggerFactory


@ServerWebSocket("/")
class Gateway @Inject constructor(
    private val service: BasicProtocolFlow,
    private val registry: SubscriptionRegistry,
    private val authentication: Authentication,
    private val config: PolicyConfig,
) {

    @OnOpen
    fun onOpen(session: WebSocketSession) {
        LOG.info("[CONN] Opened session={}", session.id)
        if (config.AUTH_ENABLED) {
            RelayResponse.AUTH(authentication.challengeFor(session)).toClient(session)
        }
    }


    @OnMessage(maxPayloadLength = 524288)
    suspend fun onMessage(session: WebSocketSession, message: String) {
        // Micronaut invokes this suspend function on the Netty event-loop thread that owns the
        // channel. Everything below - JSON parse, policy checks, the H2 query, and (for REQ) the
        // per-event JSON re-encode + send loop - is CPU/blocking work that has no business running
        // on that thread, since it would stall I/O framing for every other connection sharing the
        // same event-loop thread. withContext(Dispatchers.IO) hops off before doing any of it; the
        // suspend fun still doesn't return until the work (and the resulting sends) complete, so
        // per-session message ordering is preserved.
        withContext(Dispatchers.IO) {
            runCatching {
                val (cmd, validationResult) = parse(message) // Pair<Command?, Pair<Boolean, String>>
                val (status, warning) = validationResult

                when (cmd) {
                    is EVENT -> service.onEvent(cmd.event, status, warning, session)
                    is REQ -> service.onRequest(cmd.subscriptionId, cmd.filtersX, status, warning, session)
                    is COUNT -> service.onCount(cmd.subscriptionId, cmd.filtersX, status, warning, session)
                    is CLOSE -> service.onClose(cmd.subscriptionId, session)
                    is AUTH -> service.onAuth(cmd.event, status, warning, session)
                    else -> service.onUnknown(session)
                }
            }.onFailure { e ->
                when (e) {
                    is IllegalArgumentException -> {
                        LOG.warn("[COMMAND] Rejected session={} reason={}", session.id, e.message)
                        RelayResponse.NOTICE("ERROR: ${e.message}").toClient(session)
                    }
                    is NullPointerException -> Unit
                    else -> throw e
                }
            }
        }
    }

    @OnClose
    fun onClose(session: WebSocketSession) {
        LOG.info("[CONN] Closed session={}", session.id)
        registry.unregisterSession(session.id)
        authentication.clearSession(session)
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(Gateway::class.java)
    }

}