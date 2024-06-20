package org.fenrirs.relay.service.nip01

import io.micronaut.websocket.WebSocketSession
import jakarta.inject.Inject
import kotlinx.coroutines.runBlocking
import org.fenrirs.relay.modules.Event
import org.fenrirs.relay.modules.FiltersX
import org.fenrirs.relay.service.nip01.response.RelayResponse
import org.fenrirs.stored.statement.StoredServiceImpl
import org.slf4j.LoggerFactory


class BasicProtocolFlow @Inject constructor(
    private val service: StoredServiceImpl,
//    private val nip09: EventDeletion,
//    private val nip13: ProofOfWork
) {

    fun onEvent(event: Event, status: Boolean, warning: String, session: WebSocketSession) = runBlocking {
        LOG.info("Received event: $event")

        if (status) {
            val existingEvent: Event? = service.selectById(event.id!!)
            if (existingEvent == null) {
                // ไม่พบข้อมูลในฐานข้อมูล ดำเนินการบันทึกข้อมูล
                val result: Boolean = service.saveEvent(event)
                LOG.info("Event saved status: $result")
                RelayResponse.OK(eventId = event.id, isSuccess = result, message = warning).toClient(session)
            } else {
                // พบข้อมูลในฐานข้อมูลแล้ว ส่งข้อมูลเป็นค่าซ้ำกลับไปยัง client
                LOG.info("Event with ID ${event.id} already exists in the database.")
                RelayResponse.OK(eventId = event.id, isSuccess = false, message = "Duplicate: already have this event")
                    .toClient(session)
            }

        } else {
            RelayResponse.OK(eventId = event.id!!, isSuccess = false, message = warning).toClient(session)
        }

    }

    fun onRequest(
        subscriptionId: String,
        filtersX: List<FiltersX>,
        status: Boolean,
        warning: String,
        session: WebSocketSession
    ) = runBlocking {
        if (status) {
            //LOG.info("request for subscription ID: $subscriptionId with filters: $filtersX")

            for (filter in filtersX) {
                val events = service.filterList(filter)
                events.forEach { event ->
                    //LOG.info("Relay Response event: $event")
                    RelayResponse.EVENT(subscriptionId, event).toClient(session)
                }
            }

            RelayResponse.EOSE(subscriptionId = subscriptionId).toClient(session)
        } else {
            RelayResponse.NOTICE(warning).toClient(session)
        }
    }

    fun onClose(subscriptionId: String, session: WebSocketSession) {
        LOG.info("close request for subscription ID: $subscriptionId")
        RelayResponse.CLOSED(subscriptionId).toClient(session)
    }

    fun onUnknown(session: WebSocketSession) {
        LOG.warn("Unknown command")
        RelayResponse.NOTICE("Unknown command").toClient(session)
    }


    private fun processEvent() {

    }

    private val LOG = LoggerFactory.getLogger(BasicProtocolFlow::class.java)
}
