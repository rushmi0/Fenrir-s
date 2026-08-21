package org.fenrirs.relay.web.admin

import io.micronaut.websocket.WebSocketSession
import jakarta.inject.Singleton
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

/** Fans a live "active connections" count out to every open [AdminStatsSocket] session, so that
 * one number updates the instant a client connects/disconnects on the main relay Gateway instead
 * of waiting for the socket's own periodic snapshot. Deliberately separate from [ConnectionTracker]
 * (which just counts) - [org.fenrirs.relay.Gateway] already calls that on every open/close and only
 * needs one extra line to also push here. */
@Singleton
class DashboardBroadcaster {

    private val sessions = ConcurrentHashMap<String, WebSocketSession>()

    fun register(session: WebSocketSession) {
        sessions[session.id] = session
    }

    fun unregister(sessionId: String) {
        sessions.remove(sessionId)
    }

    fun broadcastConnections(count: Int) {
        if (sessions.isEmpty()) return
        val payload = Json.encodeToString(ConnectionsMessage(type = "connections", count = count))
        sessions.values.forEach { session ->
            if (session.isOpen) session.sendAsync(payload)
        }
    }
}
