package org.fenrirs.relay.web.admin

import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.annotation.RequestAttribute
import io.micronaut.http.hateoas.JsonError
import io.micronaut.serde.annotation.Serdeable
import jakarta.inject.Inject

import org.fenrirs.relay.web.AdminAuthFilter
import org.fenrirs.storage.statement.StoredServiceImpl

@Serdeable
data class AccountStatsResponse(
    val pubkey: String,
    val totalEvents: Long,
    val oldestEventAt: Long?,
    val kindCounts: List<KindCountDto>,
    val dailyCounts: List<DailyCountDto>
)

/**
 * Per-account event-creation history for the Accounts page's "Activity" tab - [KindCountDto]/
 * [DailyCountDto] are the same DTOs the relay-wide Dashboard uses (see StatsService), just scoped
 * to one author's own events instead of every event on the relay.
 */
@Controller("/inter/api/v1/admin/accounts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.ALL)
class AccountStatsController @Inject constructor(
    private val sqlExec: StoredServiceImpl,
) {

    @Get("/{pubkey}/stats")
    suspend fun stats(
        @RequestAttribute(AdminAuthFilter.ROLE_ATTRIBUTE) role: String,
        pubkey: String,
        @QueryValue(defaultValue = "365") days: Int,
    ): HttpResponse<*> {
        if (!RoleGuard.canAccessConsole(role)) return RoleGuard.forbidden("general users cannot access the Admin Console")

        val normalized = pubkey.trim().lowercase()
        if (normalized.length != 64 || normalized.any { it !in HEX_CHARS }) {
            return HttpResponse.badRequest(JsonError("invalid: pubkey must be a 64-character hex string"))
        }

        val stats = sqlExec.eventStatsForAuthor(normalized, days.coerceIn(1, 400))
        return HttpResponse.ok(
            AccountStatsResponse(
                pubkey = normalized,
                totalEvents = stats.totalEvents,
                oldestEventAt = stats.oldestEventAt,
                kindCounts = stats.kindCounts.map { KindCountDto(it.kind, it.count) },
                dailyCounts = stats.dailyCounts.map { DailyCountDto(it.dayStart, it.count) }
            )
        )
    }

    companion object {
        private const val HEX_CHARS = "0123456789abcdef"
    }
}
