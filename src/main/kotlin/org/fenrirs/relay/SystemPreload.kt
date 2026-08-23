package org.fenrirs.relay

import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.context.event.StartupEvent
import jakarta.inject.Singleton

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

import org.fenrirs.relay.core.backup.ProfileBackfillService
import org.fenrirs.relay.core.preload.PreloadScheduler
import org.fenrirs.relay.web.setup.SetupTokenIssuer
import org.fenrirs.storage.DatabaseFactory
import org.fenrirs.storage.NostrRelayConfig
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Singleton
class SystemPreload(
    private val config: NostrRelayConfig,
    private val preloadScheduler: PreloadScheduler,
    private val profileBackfillService: ProfileBackfillService
) : ApplicationEventListener<StartupEvent> {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onApplicationEvent(event: StartupEvent) {
        DatabaseFactory.CFG = config
        DatabaseFactory.initialize()

        SetupTokenIssuer.issueIfNeeded()
        preloadScheduler.refresh()

        // Off the startup path on purpose - this reaches out to external relays over the network,
        // and a slow or unreachable one must never delay the relay's own boot.
        scope.launch {
            runCatching { profileBackfillService.run() }
                .onFailure { LOG.error("[BACKUP-SYNC] startup profile backfill failed", it) }
        }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(SystemPreload::class.java)
    }

}