package org.fenrirs.relay

import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.context.event.StartupEvent
import jakarta.inject.Singleton
import org.fenrirs.storage.DatabaseFactory
import org.fenrirs.storage.NostrRelayConfig
import org.fenrirs.storage.statement.KeyValueStoreImpl

@Singleton
class SystemPreload(
    private val config: NostrRelayConfig
) : ApplicationEventListener<StartupEvent> {

    override fun onApplicationEvent(event: StartupEvent) {
        DatabaseFactory.ENV = config
        DatabaseFactory.initialize()

        KeyValueStoreImpl.sync(config.envDefaults)
    }

}