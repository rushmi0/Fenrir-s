package org.fenrirs.relay.web.admin

import io.micronaut.websocket.CloseReason
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
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

import org.fenrirs.relay.core.policy.AdminSessionStore
import org.fenrirs.relay.core.pubsub.EventBus

import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

/**
 * Live push channel for the admin Dashboard - real-time counterpart to [StatsController]'s
 * one-shot REST fetch. On connect it sends one full [SnapshotMessage], then keeps the client
 * current two ways: a batched [EventsDeltaMessage] roughly once a second for any events saved
 * meanwhile (via [EventBus], the same bus the relay's own live REQ subscriptions consume), and a
 * full resync [SnapshotMessage] every [SNAPSHOT_INTERVAL_MS] to correct anything a delta can't
 * express (author count, database size, uptime, ...). [DashboardBroadcaster] separately pushes
 * [ConnectionsMessage] the instant the main Gateway's connection count changes.
 *
 * Deliberately its own path *outside* the admin API tree ([org.fenrirs.relay.web.AdminAuthFilter]
 * matches everything under `/inter/api/v1/admin/`): that filter is behind
 * [org.fenrirs.relay.web.AdminAuthFilter], which unconditionally requires an `Authorization`
 * header - but the browser WebSocket API can't set one on the handshake request. So this path
 * carries the bearer token as a `?token=` query parameter instead and authenticates it manually
 * in [onOpen], the same threshold [RoleGuard.canAccessConsole] enforces on the REST endpoint.
 */
@ServerWebSocket("/inter/api/v1/dashboard/live")
class AdminStatsSocket @Inject constructor(
    private val sessions: AdminSessionStore,
    private val statsService: StatsService,
    private val eventBus: EventBus,
    private val broadcaster: DashboardBroadcaster,
) {

    private val socketScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val jobs = ConcurrentHashMap<String, Job>()

    @OnOpen
    fun onOpen(session: WebSocketSession) {
        val token = session.requestParameters.get("token")
        val resolved = token?.let { sessions.resolve(it) }
        if (resolved == null || !RoleGuard.canAccessConsole(resolved.role)) {
            LOG.warn("[DASHBOARD-WS] Rejected unauthorized connection session={}", session.id)
            session.close(CloseReason.POLICY_VIOLATION)
            return
        }

        broadcaster.register(session)
        jobs[session.id] = socketScope.launch {
            launch { pushSnapshotsPeriodically(session) }
            launch { forwardEventDeltas(session) }
        }
        LOG.info("[DASHBOARD-WS] Connected session={} pubkey={}", session.id, resolved.pubkey)
    }

    private suspend fun pushSnapshotsPeriodically(session: WebSocketSession) {
        while (session.isOpen) {
            runCatching { statsService.assemble(SNAPSHOT_DAYS) }
                .onSuccess { session.sendAsync(Json.encodeToString(SnapshotMessage(type = "snapshot", data = it))) }
                .onFailure { e -> LOG.error("[DASHBOARD-WS] Failed to assemble snapshot", e) }
            delay(SNAPSHOT_INTERVAL_MS.milliseconds)
        }
    }

    /** Buffers per-kind counts off [EventBus] and flushes them as one batched message every
     * [DELTA_FLUSH_MS] instead of one WS frame per event - keeps a busy relay from flooding an
     * open dashboard tab with a message per save. */
    private suspend fun forwardEventDeltas(session: WebSocketSession) {
        val buffer = ConcurrentHashMap<Int, Long>()
        val collectJob = socketScope.launch {
            eventBus.events.collect { event ->
                val kind = event.kind?.toInt() ?: return@collect
                buffer.merge(kind, 1L, Long::plus)
            }
        }
        try {
            while (session.isOpen) {
                delay(DELTA_FLUSH_MS)
                if (buffer.isEmpty()) continue
                val kinds = buffer.map { (kind, count) -> KindCountDto(kind, count) }
                buffer.clear()
                session.sendAsync(Json.encodeToString(EventsDeltaMessage(type = "events", kinds = kinds)))
            }
        } finally {
            collectJob.cancel()
        }
    }

    // Push-only channel - the client never sends anything, but Micronaut requires every
    // @ServerWebSocket bean to declare an @OnMessage handler regardless.
    @OnMessage
    fun onMessage(session: WebSocketSession, message: String) = Unit

    @OnClose
    fun onClose(session: WebSocketSession) {
        broadcaster.unregister(session.id)
        jobs.remove(session.id)?.cancel()
        LOG.info("[DASHBOARD-WS] Disconnected session={}", session.id)
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(AdminStatsSocket::class.java)
        private const val SNAPSHOT_INTERVAL_MS = 5_000L
        private const val DELTA_FLUSH_MS = 1_000L

        // Fixed at the widest day-range preset the Dashboard UI offers - the client slices this
        // down to 7d/14d locally instead of reconnecting or re-requesting per filter.
        private const val SNAPSHOT_DAYS = 30
    }
}
