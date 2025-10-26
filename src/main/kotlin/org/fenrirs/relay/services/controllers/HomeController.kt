package org.fenrirs.relay.services.controllers

import io.micronaut.http.annotation.*
import io.micronaut.views.View

@Controller("/app")
class HomeController {

    @Get("/")
    @View("App")
    fun index(): Map<String, Any> = mapOf("user" to "Fenrir")
}

