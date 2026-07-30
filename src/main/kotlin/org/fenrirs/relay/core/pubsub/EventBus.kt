package org.fenrirs.relay.core.pubsub

import jakarta.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.fenrirs.relay.models.Event
import org.slf4j.LoggerFactory


@Singleton
class EventBus {

    private val _events = MutableSharedFlow<Event>(
        replay = 0,
        extraBufferCapacity = 4096,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val events: SharedFlow<Event> = _events.asSharedFlow()

    fun publish(event: Event) {
        if (!_events.tryEmit(event)) {
            LOG.warn("[DISPATCH] Bus saturated, dropped oldest event id={}", event.id)
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(EventBus::class.java)
    }
}