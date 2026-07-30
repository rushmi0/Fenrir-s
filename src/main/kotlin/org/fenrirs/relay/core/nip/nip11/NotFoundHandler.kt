package org.fenrirs.relay.core.nip.nip11

import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import io.micronaut.http.hateoas.JsonError
import io.micronaut.http.HttpStatus
import io.micronaut.core.io.ResourceResolver

@Controller("{+path}")
class NotFoundController(private val resourceResolver: ResourceResolver) {

    @Error(status = HttpStatus.NOT_FOUND, global = true)
    fun notFound(): HttpResponse<*> {
        val resource = resourceResolver.getResource("classpath:public/index.html")
        return if (resource.isPresent) {
            HttpResponse.ok(resource.get().readBytes())
                .contentType("text/html")
        } else {
            HttpResponse.notFound(JsonError("Page Not Found"))
        }
    }
}