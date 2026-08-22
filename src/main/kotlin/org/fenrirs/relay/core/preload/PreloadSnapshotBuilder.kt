package org.fenrirs.relay.core.preload

import jakarta.inject.Inject
import jakarta.inject.Singleton

import org.fenrirs.relay.core.nip.nip11.RelayInformation
import org.fenrirs.relay.core.policy.EffectiveRole
import org.fenrirs.relay.core.policy.PermissionService
import org.fenrirs.relay.core.policy.PermissionSubject
import org.fenrirs.relay.web.admin.StatsService
import org.fenrirs.relay.web.permission.EffectivePermissionsResponse
import org.fenrirs.relay.web.system.BackupSyncInfo
import org.fenrirs.relay.web.system.SystemStatus
import org.fenrirs.storage.NostrRelayConfig
import org.fenrirs.storage.statement.StoredServiceImpl

@Singleton
class PreloadSnapshotBuilder @Inject constructor(
    private val env: NostrRelayConfig,
    private val relayInformation: RelayInformation,
    private val permissionService: PermissionService,
    private val statsService: StatsService,
    private val sqlExec: StoredServiceImpl
) {

    suspend fun build(): PreloadSnapshot {
        val guestSubject = PermissionSubject(EffectiveRole.GUEST, null)

        return PreloadSnapshot(
            generatedAt = System.currentTimeMillis() / 1000,
            system = SystemStatus.current(env),
            relay = relayInformation.buildRelayInfo(),
            backupSync = BackupSyncInfo.current(env),
            guestPermissions = EffectivePermissionsResponse(
                role = guestSubject.effectiveRole.name,
                pubkey = guestSubject.pubkey,
                permissions = permissionService.effectivePermissions(guestSubject)
            ),
            relayStats = statsService.assemble(STATS_DAYS),
            accounts = sqlExec.latestProfileEvents(ACCOUNTS_LIMIT).map {
                PreloadAccountProfile(it.pubkeyHex, it.createdAt, it.content)
            }
        )
    }

    companion object {
        private const val STATS_DAYS = 30
        private const val ACCOUNTS_LIMIT = 200
    }
}
