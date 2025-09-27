package org.fenrirs.storage

import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.context.event.StartupEvent
import jakarta.inject.Singleton
// import org.slf4j.LoggerFactory

@Singleton
class DatabaseInitializer(
    private val config: NostrRelayConfig
) : ApplicationEventListener<StartupEvent> {

    override fun onApplicationEvent(event: StartupEvent) {
        DatabaseFactory.ENV = config
        DatabaseFactory.initialize()
    }

/*
    companion object {
        val LOG = LoggerFactory.getLogger(DatabaseInitializer::class.java)
    }
*/

}