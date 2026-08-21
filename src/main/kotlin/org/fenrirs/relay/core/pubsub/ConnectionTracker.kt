package org.fenrirs.relay.core.pubsub

import jakarta.inject.Singleton
import java.util.concurrent.atomic.AtomicInteger

/** Live WebSocket connection count, for the admin Dashboard's "active connections" stat. */
@Singleton
class ConnectionTracker {

    private val count = AtomicInteger(0)

    fun opened(): Int = count.incrementAndGet()

    fun closed(): Int = count.decrementAndGet()

    fun current(): Int = count.get()
}
