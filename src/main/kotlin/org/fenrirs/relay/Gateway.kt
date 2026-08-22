package org.fenrirs.relay


import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.OnClose
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import io.micronaut.websocket.annotation.ServerWebSocket

import jakarta.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

import org.fenrirs.relay.core.nip.nip01.command.AUTH
import org.fenrirs.relay.core.nip.nip01.command.EVENT
import org.fenrirs.relay.core.nip.nip01.command.CLOSE
import org.fenrirs.relay.core.nip.nip01.command.COUNT
import org.fenrirs.relay.core.nip.nip01.command.REQ
import org.fenrirs.relay.core.nip.nip01.command.CommandFactory.parse
import org.fenrirs.relay.core.nip.nip01.response.RelayResponse
import org.fenrirs.relay.core.nip.nip01.BasicProtocolFlow
import org.fenrirs.relay.core.policy.PolicyConfig
import org.fenrirs.relay.core.pubsub.ConnectionTracker
import org.fenrirs.relay.core.pubsub.SubscriptionRegistry
import org.fenrirs.relay.web.admin.DashboardBroadcaster
import org.fenrirs.storage.Authentication

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.milliseconds


@ServerWebSocket("/")
class Gateway @Inject constructor(
    private val service: BasicProtocolFlow,
    private val registry: SubscriptionRegistry,
    private val authentication: Authentication,
    private val config: PolicyConfig,
    private val connections: ConnectionTracker,
    private val dashboardBroadcaster: DashboardBroadcaster,
) {

    @OnOpen
    fun onOpen(session: WebSocketSession) {
        LOG.info("[CONN] Opened session={}", session.id)
        dashboardBroadcaster.broadcastConnections(connections.opened())
        if (config.AUTH_ENABLED) {
            RelayResponse.AUTH(authentication.challengeFor(session)).toClient(session)
        }
        startKeepalive(session)
    }


    @OnMessage(maxPayloadLength = 524288)
    suspend fun onMessage(session: WebSocketSession, message: String) {
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
        dashboardBroadcaster.broadcastConnections(connections.closed())
        registry.unregisterSession(session.id)
        authentication.clearSession(session)
        keepaliveJobs.remove(session.id)?.cancel()
    }


    private fun startKeepalive(session: WebSocketSession) {
        keepaliveJobs[session.id] = keepaliveScope.launch {
            while (isActive && session.isOpen) {
                delay(KEEPALIVE_INTERVAL_MS.milliseconds)
                if (!session.isOpen) break
                runCatching { session.sendPingAsync(EMPTY_PING_PAYLOAD) }
            }
        }
    }

    private val keepaliveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val keepaliveJobs = ConcurrentHashMap<String, Job>()

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(Gateway::class.java)
        private const val KEEPALIVE_INTERVAL_MS = 10_000L
        private val EMPTY_PING_PAYLOAD = ByteArray(0)
    }

}