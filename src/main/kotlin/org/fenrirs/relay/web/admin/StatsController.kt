package org.fenrirs.relay.web.admin

import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.annotation.RequestAttribute
import jakarta.inject.Inject

import org.fenrirs.relay.web.AdminAuthFilter

/** Backs the admin Dashboard's initial/cached load - event/kind distribution, daily activity, and
 * relay vitals. See [AdminStatsSocket] for the live-updating counterpart this hands off to. */
@Controller("/inter/api/v1/admin/stats")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.ALL)
class StatsController @Inject constructor(
    private val statsService: StatsService,
) {

    @Get
    suspend fun get(
        @RequestAttribute(AdminAuthFilter.ROLE_ATTRIBUTE) role: String,
        @QueryValue(defaultValue = "30") days: Int,
    ): HttpResponse<*> {
        if (!RoleGuard.canAccessConsole(role)) return RoleGuard.forbidden("general users cannot access the Admin Console")
        return HttpResponse.ok(statsService.assemble(days))
    }
}
