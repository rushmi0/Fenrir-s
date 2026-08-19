package org.fenrirs.relay.web.admin

import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.annotation.RequestAttribute
import io.micronaut.serde.annotation.Serdeable

import org.fenrirs.relay.web.AdminAuthFilter

@Serdeable
data class AdminIdentity(val pubkey: String, val role: String)

/** Lets the console shell rehydrate who's logged in from just the bearer token after a reload. */
@Controller("/inter/api/v1/admin/me")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.ALL)
class AdminMeController {

    @Get
    fun me(
        @RequestAttribute(AdminAuthFilter.PUBKEY_ATTRIBUTE) pubkey: String,
        @RequestAttribute(AdminAuthFilter.ROLE_ATTRIBUTE) role: String
    ): AdminIdentity = AdminIdentity(pubkey, role)
}
