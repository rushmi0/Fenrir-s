package org.fenrirs.relay


import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.OnClose
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import io.micronaut.websocket.annotation.ServerWebSocket

import jakarta.inject.Inject

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