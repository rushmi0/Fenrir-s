package org.fenrirs.relay

import io.micronaut.context.annotation.Bean
import io.micronaut.core.annotation.Introspected
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Header
import io.micronaut.runtime.http.scope.RequestScope
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.OnClose
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import io.micronaut.websocket.annotation.ServerWebSocket
import jakarta.inject.Inject
import kotlinx.coroutines.runBlocking
import org.fenrirs.relay.service.nip01.BasicProtocolFlow
import org.fenrirs.relay.service.nip01.command.CLOSE
import org.fenrirs.relay.service.nip01.command.CommandFactory.parse
import org.fenrirs.relay.service.nip01.command.EVENT
import org.fenrirs.relay.service.nip01.command.REQ
import org.fenrirs.relay.service.nip01.response.RelayResponse
import org.fenrirs.relay.service.nip11.RelayInformation
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Bean
@RequestScope
@Introspected
@ServerWebSocket("/")
class Gateway @Inject constructor(
    private val nip01: BasicProtocolFlow,
    private val nip11: RelayInformation
) {

    @OnOpen
    fun onOpen(session: WebSocketSession?, @Header(HttpHeaders.ACCEPT) accept: String?): HttpResponse<String>? {
        session?.let {
            LOG.info("${GREEN}open$RESET $session")
            return HttpResponse.ok("Session opened")
        }

        LOG.info("${YELLOW}accept: $RESET$accept ${BLUE}session: $RESET$session")
        val contentType = when {
            accept == "application/nostr+json" -> MediaType.APPLICATION_JSON
            else -> MediaType.TEXT_HTML
        }

        val data = runBlocking {
            nip11.loadRelayInfo(contentType)
        }
        return HttpResponse.ok(data).contentType(contentType)
    }


    @OnMessage
    fun onMessage(message: String, session: WebSocketSession) {
        LOG.info("message: \n$message")

        try {
            val (command, validationResult) = parse(message) //  Pair<Command?, Pair<Boolean, String>>
            val (status, warning) = validationResult

            when (command) {
                is EVENT -> nip01.onEvent(command.event, status, warning, session)
                is REQ -> nip01.onRequest(command.subscriptionId, command.filtersX, status, warning, session)
                is CLOSE -> nip01.onClose(command.subscriptionId, session)
                else -> nip01.onUnknown(session)
            }

        } catch (e: IllegalArgumentException) {
            LOG.error("Failed to handle command: ${e.message}")
            RelayResponse.NOTICE("ERROR: ${e.message}").toClient(session)
        }
    }

    @OnClose
    fun onClose(session: WebSocketSession) {
        LOG.info("${PURPLE}close: ${RESET}$session")
    }

    companion object {

        private val LOG: Logger = LoggerFactory.getLogger(Gateway::class.java)

        const val RESET = "\u001B[0m"
        const val RED = "\u001B[31m"
        const val GREEN = "\u001B[32m"
        const val YELLOW = "\u001B[33m"
        const val BLUE = "\u001B[34m"
        const val PURPLE = "\u001B[35m"
        const val CYAN = "\u001B[36m"
        const val WHITE = "\u001B[37m"
    }

}