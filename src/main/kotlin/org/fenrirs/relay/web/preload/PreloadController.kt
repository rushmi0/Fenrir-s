package org.fenrirs.relay.web.preload

import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.hateoas.JsonError

import jakarta.inject.Inject

import org.fenrirs.relay.core.preload.PreloadFileStore

@Controller("/inter/api/v1/preload")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.ALL)
class PreloadController @Inject constructor(private val fileStore: PreloadFileStore) {

    @Get
    fun get(): HttpResponse<*> {
        val snapshot = fileStore.readCurrent()
            ?: return HttpResponse.status<JsonError>(HttpStatus.SERVICE_UNAVAILABLE)
                .body(JsonError("preload snapshot not ready yet"))
        return HttpResponse.ok(snapshot)
    }
}
