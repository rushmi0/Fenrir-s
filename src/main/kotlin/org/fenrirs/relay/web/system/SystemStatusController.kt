package org.fenrirs.relay.web.system

import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.serde.annotation.Serdeable

import org.fenrirs.storage.NostrRelayConfig
import org.fenrirs.storage.statement.OperatorStoreImpl

@Serdeable
data class SystemStatus(val state: String, val relayName: String) {
    companion object {
        fun current(env: NostrRelayConfig): SystemStatus =
            SystemStatus(if (OperatorStoreImpl.isEmpty()) "INITIAL_SETUP" else "READY", env.RELAY_NAME)
    }
}

@Serdeable
data class BackupSyncInfo(val enabled: Boolean, val relays: List<String>) {
    companion object {
        fun current(env: NostrRelayConfig): BackupSyncInfo = BackupSyncInfo(env.BACKUP_ENABLED, env.BACKUP_SYNC)
    }
}

/**
 * Public, unauthenticated status endpoint - the Fenrir Client's Login Card reads this on load to
 * decide whether to render the initial Setup flow or the normal Nostr Login flow (see task's
 * "System States" section). State is derived, not stored: no operators registered yet means the
 * relay is still in INITIAL_SETUP (see [OperatorStoreImpl.isEmpty]).
 */
@Controller("/inter/api/v1/system")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.ALL)
class SystemStatusController(private val env: NostrRelayConfig) {

    @Get("/status")
    fun status(): SystemStatus = SystemStatus.current(env)

    /**
     * The relay's own [NostrRelayConfig.BACKUP_SYNC] list (public relay URLs, nothing sensitive) -
     * lets the client fall back to querying those relays directly (e.g. for the Accounts page's
     * kind-0 directory) when this relay's own database has nothing to show. `enabled` mirrors
     * [NostrRelayConfig.BACKUP_ENABLED] so a caller can skip the fallback entirely when the admin
     * has turned sync off, even though the relay list itself always has defaults.
     */
    @Get("/backup-sync")
    fun backupSync(): BackupSyncInfo = BackupSyncInfo.current(env)
}
