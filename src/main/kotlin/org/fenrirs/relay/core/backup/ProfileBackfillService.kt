package org.fenrirs.relay.core.backup

import jakarta.inject.Inject
import jakarta.inject.Singleton

import org.fenrirs.relay.core.nip.nip01.VerifyEvent.verifyNip01
import org.fenrirs.relay.core.pubsub.EventBus
import org.fenrirs.relay.models.FiltersX
import org.fenrirs.storage.NostrRelayConfig
import org.fenrirs.storage.service.SaveOutcome
import org.fenrirs.storage.statement.OperatorStoreImpl
import org.fenrirs.storage.statement.StoredServiceImpl
import org.fenrirs.storage.statement.WhitelistAccountStoreImpl

import org.slf4j.LoggerFactory

/**
 * Every account this relay already knows by pubkey - its own OWNER/ADMIN operator rows plus every
 * whitelisted (General/Guest) account - that has no kind-0 in this relay's own database yet gets
 * looked up on the configured backup-sync relays ([NostrRelayConfig.BACKUP_SYNC]) via
 * [BackupRelayClient], and any profile found there is stored exactly like a normally-published
 * event: verified with the same [VerifyEvent.verifyNip01] check every incoming EVENT goes through,
 * written via the same [StoredServiceImpl.saveIfAbsent] path, and announced on the same [EventBus].
 *
 * Called once at startup (see `SystemPreload`, launched off a background coroutine so a slow or
 * unreachable backup relay can't delay boot) and on demand (see the Role Management Accounts tab's
 * trigger, `AccountStatsController`'s `/accounts/backfill-profiles` endpoint).
 */
@Singleton
class ProfileBackfillService @Inject constructor(
    private val config: NostrRelayConfig,
    private val backupRelayClient: BackupRelayClient,
    private val sqlExec: StoredServiceImpl,
    private val eventBus: EventBus
) {

    /** @return how many profiles were actually fetched and stored. */
    suspend fun run(): Int {
        if (!config.BACKUP_ENABLED) return 0
        val relays = config.BACKUP_SYNC
        if (relays.isEmpty()) return 0

        val candidates: Set<String> =
            (OperatorStoreImpl.all().map { it.pubkey } + WhitelistAccountStoreImpl.all().map { it.pubkey }).toSet()
        if (candidates.isEmpty()) return 0

        val alreadyPresent = (
                sqlExec.filterList(
                    FiltersX {
                        kinds = setOf(0L)
                        authors = candidates
                    }
                ) ?: emptyList())
            .mapNotNull { it.pubkey }
            .toSet()
        val missing = candidates - alreadyPresent
        if (missing.isEmpty()) return 0

        LOG.info(
            "[BACKUP-SYNC] {} account(s) missing a local profile, checking {} backup relay(s)",
            missing.size,
            relays.size
        )

        val fetched = backupRelayClient.fetchKind0(relays, missing)

        // Kind-0 is replaceable - keep only the newest revision seen for each missing author,
        // across every backup relay that answered.
        val newestPerAuthor = fetched
            .filter { it.kind == 0L && it.pubkey in missing }
            .groupBy { it.pubkey }
            .mapNotNull { (_, events) -> events.maxByOrNull { event -> event.created_at ?: 0L } }

        var stored = 0
        newestPerAuthor.forEach { event ->
            val (valid, warning) = event.verifyNip01()
            if (!valid) {
                LOG.warn("[BACKUP-SYNC] discarded invalid profile pubkey={} reason={}", event.pubkey, warning)
                return@forEach
            }
            if (sqlExec.saveIfAbsent(event) == SaveOutcome.SAVED) {
                eventBus.publish(event)
                stored++
            }
        }

        LOG.info("[BACKUP-SYNC] stored {} of {} missing profile(s)", stored, missing.size)
        return stored
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(ProfileBackfillService::class.java)
    }
}
