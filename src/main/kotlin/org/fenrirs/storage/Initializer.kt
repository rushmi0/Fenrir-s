package org.fenrirs.storage

import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.context.event.StartupEvent
import jakarta.inject.Singleton
import org.fenrirs.relay.services.ProfileSync

@Singleton
class Initializer(
    private val config: NostrRelayConfig,
    private val profileSync: ProfileSync
) : ApplicationEventListener<StartupEvent> {

    override fun onApplicationEvent(event: StartupEvent) {
        DatabaseFactory.ENV = config
        DatabaseFactory.initialize()
        profileSync.sync()
    }

}