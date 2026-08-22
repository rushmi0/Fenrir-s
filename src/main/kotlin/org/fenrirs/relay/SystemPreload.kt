package org.fenrirs.relay

import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.context.event.StartupEvent
import jakarta.inject.Singleton
import org.fenrirs.relay.core.preload.PreloadScheduler
import org.fenrirs.relay.web.setup.SetupTokenIssuer
import org.fenrirs.storage.DatabaseFactory
import org.fenrirs.storage.NostrRelayConfig
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Singleton
class SystemPreload(
    private val config: NostrRelayConfig,
    private val preloadScheduler: PreloadScheduler
) : ApplicationEventListener<StartupEvent> {

    override fun onApplicationEvent(event: StartupEvent) {
        DatabaseFactory.ENV = config
        // KeyValueStoreImpl.sync(config.envDefaults) now runs inside initialize() itself, right
        // after configDb connects and before the business/primary pools are built - those pools
        // read connection/pool settings from the KV store too, so the seed has to happen first.
        DatabaseFactory.initialize()

        SetupTokenIssuer.issueIfNeeded()
        preloadScheduler.refresh()
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(SystemPreload::class.java)
    }

}