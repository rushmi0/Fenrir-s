package org.fenrirs.relay

import io.micronaut.context.annotation.Bean
import io.micronaut.core.annotation.Introspected

import io.micronaut.http.*
import io.micronaut.http.annotation.Header

import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.OnClose
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import io.micronaut.websocket.annotation.ServerWebSocket

import jakarta.inject.Inject
import kotlinx.coroutines.runBlocking

import org.fenrirs.relay.core.nip01.BasicProtocolFlow
import org.fenrirs.relay.core.nip01.command.CLOSE
import org.fenrirs.relay.core.nip01.command.EVENT
import org.fenrirs.relay.core.nip01.command.REQ
import org.fenrirs.relay.core.nip01.command.CommandFactory.parse
import org.fenrirs.relay.core.nip01.response.RelayResponse
import org.fenrirs.relay.core.nip11.RelayInformation

import org.fenrirs.utils.Color.BLUE
import org.fenrirs.utils.Color.GREEN
import org.fenrirs.utils.Color.PURPLE
import org.fenrirs.utils.Color.RESET
import org.fenrirs.utils.Color.YELLOW

import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Bean
@Introspected
@ServerWebSocket("/")
class Gateway @Inject constructor(
    private val nip01: BasicProtocolFlow,
    private val nip11: RelayInformation
) {

    @OnOpen
    fun onOpen(request: HttpRequest<*>, session: WebSocketSession?, @Header(HttpHeaders.ACCEPT) accept: String?): HttpResponse<String>? {
        val clientIp = request.remoteAddress?.address?.hostAddress
        LOG.info("Client IP: $clientIp")
        session?.let {
            LOG.info("${GREEN}open$RESET $session")
            return HttpResponse.ok("Session opened")
                .header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
        }

        LOG.info("${YELLOW}accept: $RESET$accept ${BLUE}session: $RESET$session")
        val contentType = when {
            accept == "application/nostr+json" -> MediaType.APPLICATION_JSON
            else -> MediaType.TEXT_HTML
        }

        val data = runBlocking {
            nip11.loadRelayInfo(contentType)
        }

        return HttpResponse.ok(data)
            .contentType(contentType)
            .header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
    }


    @OnMessage(maxPayloadLength = 524288)
    fun onMessage(message: String, session: WebSocketSession) {
        //LOG.info("message: \n$message")
        try {

            /*
           * ทำการตรวจสอบความถูกต้องของข้อมูล ที่ได้รับจากไคลเอนต์และตอบกลับอย่างเหมาะสม
           * ถ้าข้อมูลถูกต้องเป็นไปตามข้อกำหนดจะถูกแปลงข้อมูลให้อยู่ในรูปของ Kotlin Object เพื่อสามารถนำไปใช้งานต่อได้สะดวก
           * */
            val (cmd, validationResult) = parse(message) // Pair<Command?, Pair<Boolean, String>>
            val (status, warning) = validationResult

            when (cmd) {
                is EVENT -> nip01.onEvent(cmd.event, status, warning, session)
                is REQ -> nip01.onRequest(cmd.subscriptionId, cmd.filtersX, status, warning, session)
                is CLOSE -> nip01.onClose(cmd.subscriptionId, session)
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
    }

}