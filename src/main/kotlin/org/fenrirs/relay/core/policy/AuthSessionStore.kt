package org.fenrirs.relay.core.policy

import io.micronaut.websocket.WebSocketSession

interface AuthSessionStore {

    fun challengeFor(session: WebSocketSession): String

    fun authenticatedPubkeys(session: WebSocketSession): Set<String>
}