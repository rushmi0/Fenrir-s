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

import org.fenrirs.relay.core.nip01.command.CLOSE
import org.fenrirs.relay.core.nip01.command.EVENT
import org.fenrirs.relay.core.nip01.command.REQ
import org.fenrirs.relay.core.nip01.command.CommandFactory.parse
import org.fenrirs.relay.core.nip01.response.RelayResponse
import org.fenrirs.relay.core.nip01.BasicProtocolFlow
import org.fenrirs.relay.core.nip11.RelayInformation
import org.fenrirs.relay.policy.FiltersX

import org.fenrirs.utils.Color.BLUE
import org.fenrirs.utils.Color.CYAN
import org.fenrirs.utils.Color.GREEN
import org.fenrirs.utils.Color.PURPLE
import org.fenrirs.utils.Color.RED
import org.fenrirs.utils.Color.RESET
import org.fenrirs.utils.Color.YELLOW

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

@Bean
@Introspected
@ServerWebSocket("/")
class Gateway @Inject constructor(
    private val nip01: BasicProtocolFlow,
    private val nip11: RelayInformation
) {

    // ใช้ ConcurrentHashMap เพื่อเก็บข้อมูล session และ subscriptionIds
    private val subscriptions = ConcurrentHashMap<WebSocketSession, MutableSet<String>>()


    @OnOpen
    fun onOpen(session: WebSocketSession?, @Header(HttpHeaders.ACCEPT) accept: String?): HttpResponse<String>? {
        session?.let {
            subscriptions[session] = mutableSetOf()
            LOG.info("${GREEN}open ${YELLOW}$session $RESET")
            return@let HttpResponse.ok("Session opened")
                .header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
        }

        LOG.info("${YELLOW}accept: $RESET$accept ${BLUE}$session $RESET")
        val contentType = when {
            accept == "application/nostr+json" -> MediaType.APPLICATION_JSON
            else -> MediaType.TEXT_HTML
        }

        return HttpResponse.ok(nip11.loadRelayInfo(contentType))
            .contentType(contentType)
            .header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
    }


    @OnMessage(maxPayloadLength = 524288)
    suspend fun onMessage(message: String, session: WebSocketSession) {
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
                is REQ -> handleRequest(cmd.subscriptionId, cmd.filtersX, status, warning, session)
                is CLOSE -> handleClose(cmd.subscriptionId, session)
                else -> nip01.onUnknown(session)
            }

        } catch (e: IllegalArgumentException) {
            LOG.error("${RED}Failed ${RESET}to handle command: ${e.message}")
            RelayResponse.NOTICE("ERROR: ${e.message}").toClient(session)
        }
    }

    @OnClose
    fun onClose(session: WebSocketSession) {
        subscriptions.remove(session)
        LOG.info("${PURPLE}close: ${CYAN}$session")
    }


    private fun handleRequest(
        subscriptionId: String,
        filtersX: List<FiltersX>,
        status: Boolean,
        warning: String,
        session: WebSocketSession
    ) {
        // Check if the subscriptionId already exists in another session
        if (subscriptions.values.any { it.contains(subscriptionId) }) {
            RelayResponse.CLOSED(subscriptionId = subscriptionId, message = "duplicate: $subscriptionId already opened")
                .toClient(session)
        } else {
            subscriptions[session]?.add(subscriptionId)
            nip01.onRequest(subscriptionId, filtersX, status, warning, session)
            subscriptions[session]?.remove(subscriptionId)
        }
    }


    private fun handleClose(subscriptionId: String, session: WebSocketSession) {
        subscriptions[session]?.remove(subscriptionId)
        if (subscriptions[session]?.isEmpty() == true) {
            subscriptions.remove(session)
        }
        LOG.info("Subscription ${PURPLE}closed: ${RESET}$subscriptionId from ${CYAN}$session ${RESET}")
    }


    // private fun isDuplicateSubscription(subscriptionId: String): Boolean = subscriptions.values.flatten().contains(subscriptionId)


    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(Gateway::class.java)
    }

}