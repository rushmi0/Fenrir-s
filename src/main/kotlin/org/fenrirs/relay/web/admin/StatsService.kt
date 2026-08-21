package org.fenrirs.relay.web.admin

import io.micronaut.serde.annotation.Serdeable
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.serialization.Serializable

import org.fenrirs.relay.core.RelayClock
import org.fenrirs.relay.core.pubsub.ConnectionTracker
import org.fenrirs.storage.DatabaseFactory
import org.fenrirs.storage.statement.OperatorStoreImpl
import org.fenrirs.storage.statement.StoredServiceImpl

/** `@Serdeable` (Micronaut, for the REST response body) and `@Serializable` (kotlinx, for the
 * live WebSocket push in [AdminStatsSocket]) both decorate these DTOs - one snapshot shape,
 * carried over either transport. */
@Serdeable
@Serializable
data class KindCountDto(val kind: Int, val count: Long)

@Serdeable
@Serializable
data class DailyCountDto(val dayStart: Long, val count: Long)

@Serdeable
@Serializable
data class RelayStatsResponse(
    val totalEvents: Long,
    val totalAuthors: Long,
    val totalOperators: Int,
    val oldestEventAt: Long?,
    val kindCounts: List<KindCountDto>,
    val dailyCounts: List<DailyCountDto>,
    val databaseMode: String,
    val databaseSizeBytes: Long,
    val activeConnections: Int,
    val supportedNips: List<Int>,
    val uptimeSeconds: Long
)

/** Assembles the admin Dashboard's stats snapshot - shared by [StatsController] (one-shot REST
 * fetch) and [AdminStatsSocket] (the live channel's initial push and periodic resync), so the two
 * transports can never drift into computing this differently. */
@Singleton
class StatsService @Inject constructor(
    private val sqlExec: StoredServiceImpl,
    private val connections: ConnectionTracker,
) {

    suspend fun assemble(days: Int): RelayStatsResponse {
        val stats = sqlExec.eventStats(days.coerceIn(1, 90))

        return RelayStatsResponse(
            totalEvents = stats.totalEvents,
            totalAuthors = stats.totalAuthors,
            totalOperators = OperatorStoreImpl.all().size,
            oldestEventAt = stats.oldestEventAt,
            kindCounts = stats.kindCounts.map { KindCountDto(it.kind, it.count) },
            dailyCounts = stats.dailyCounts.map { DailyCountDto(it.dayStart, it.count) },
            databaseMode = DatabaseFactory.activeDatabaseMode(),
            databaseSizeBytes = DatabaseFactory.activeDatabaseSizeBytes(),
            activeConnections = connections.current(),
            supportedNips = SUPPORTED_NIPS,
            uptimeSeconds = System.currentTimeMillis() / 1000 - RelayClock.startedAt
        )
    }

    companion object {
        // Keep in sync with RelayInformation.relayInfo()'s NIP-11 "supported_nips" list.
        private val SUPPORTED_NIPS = listOf(1, 2, 4, 9, 11, 13, 15, 28, 42, 45, 50)
    }
}
