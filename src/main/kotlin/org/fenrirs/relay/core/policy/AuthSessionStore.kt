package org.fenrirs.relay.core.policy

import io.micronaut.websocket.WebSocketSession

interface AuthSessionStore {

    fun challengeFor(session: WebSocketSession): String

    fun authenticatedPubkeys(session: WebSocketSession): Set<String>

    /**
     * Same lookup as [authenticatedPubkeys], but by raw session id - for callers (e.g. an HTTP
     * request filter) that only have the id a client reported, not a live [WebSocketSession].
     */
    fun authenticatedPubkeys(sessionId: String): Set<String>
}