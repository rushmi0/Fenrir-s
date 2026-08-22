package org.fenrirs.relay.core.preload

import io.micronaut.serde.annotation.Serdeable

import org.fenrirs.relay.core.nip.nip11.RelayInfo
import org.fenrirs.relay.web.admin.RelayStatsResponse
import org.fenrirs.relay.web.permission.EffectivePermissionsResponse
import org.fenrirs.relay.web.system.BackupSyncInfo
import org.fenrirs.relay.web.system.SystemStatus

@Serdeable
data class PreloadAccountProfile(val pubkey: String, val createdAt: Long, val content: String)

@Serdeable
data class PreloadSnapshot(
    val generatedAt: Long,
    val system: SystemStatus,
    val relay: RelayInfo,
    val backupSync: BackupSyncInfo,
    val guestPermissions: EffectivePermissionsResponse,
    val relayStats: RelayStatsResponse,
    val accounts: List<PreloadAccountProfile>
)
