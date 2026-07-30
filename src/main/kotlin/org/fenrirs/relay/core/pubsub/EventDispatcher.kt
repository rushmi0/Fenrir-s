package org.fenrirs.relay.core.pubsub

import jakarta.annotation.PostConstruct
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory


@Singleton
class EventDispatcher @Inject constructor(
    private val eventBus: EventBus,
    private val registry: SubscriptionRegistry
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @PostConstruct
    fun start() {
        scope.launch {
            eventBus.events.collect { savedEvent ->
                registry.candidatesFor(savedEvent).forEach { entry ->
                    if (entry.matches(savedEvent)) {
                        entry.channel.trySend(savedEvent)
                    }
                }
            }
        }
        LOG.info("[DISPATCH] Started")
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(EventDispatcher::class.java)
    }
}