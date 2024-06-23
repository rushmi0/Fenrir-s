package org.fenrirs.relay.core.nip01

import io.micronaut.websocket.WebSocketSession
import jakarta.inject.Inject
import kotlinx.coroutines.runBlocking

import org.fenrirs.relay.modules.Event
import org.fenrirs.relay.modules.FiltersX
import org.fenrirs.relay.core.nip01.response.RelayResponse
import org.fenrirs.relay.core.nip09.EventDeletion
import org.fenrirs.relay.core.nip13.ProofOfWork
import org.fenrirs.stored.statement.StoredServiceImpl

import org.slf4j.LoggerFactory

class BasicProtocolFlow @Inject constructor(
    private val sqlExec: StoredServiceImpl,
    private val nip09: EventDeletion,
    private val nip13: ProofOfWork
) {

    suspend fun onEvent(event: Event, status: Boolean, warning: String, session: WebSocketSession) = runBlocking {
        LOG.info("Received event: $event")

        if (!status) {
            RelayResponse.OK(event.id!!, false, warning).toClient(session)
            return@runBlocking
        }

        val eventId = sqlExec.selectById(event.id!!)

        when {
            eventId != null -> {
                LOG.info("Event with ID ${event.id} already exists in the database")
                RelayResponse.OK(event.id, false, "duplicate: already have this event").toClient(session)
            }
            nip13.isProofOfWorkEvent(event) -> handleProofOfWorkEvent(event, session)
            nip09.isDeletable(event) -> handleDeletableEvent(event, session)
            else -> handleNormalEvent(event, session)
        }


    }

    private suspend fun handleNormalEvent(event: Event, session: WebSocketSession) {
        try {
            val status: Boolean = sqlExec.saveEvent(event)
            if (status) {
                LOG.info("Event saved successfully: ${event.id}")
                RelayResponse.OK(event.id!!, true, "").toClient(session)
            } else {
                LOG.warn("Failed to save event: ${event.id}")
                RelayResponse.OK(event.id!!, false, "error: could not saving event to the database").toClient(session)
            }
        } catch (e: Exception) {
            LOG.error("Error saving event: ${event.id}", e)
            RelayResponse.NOTICE( "error: ${e.message}").toClient(session)
        }
    }

    private fun handleDeletableEvent(event: Event, session: WebSocketSession) {
        try {
            val deletionSuccess = nip09.deleteEvent(event)
            if (deletionSuccess) {
                LOG.info("Event deleted successfully: ${event.id}")
                RelayResponse.OK(event.id!!, true).toClient(session)
            } else {
                LOG.warn("Failed to delete event: ${event.id}")
                RelayResponse.OK(event.id!!, false, "error: could not delete event").toClient(session)
            }
        } catch (e: Exception) {
            LOG.error("Error deleting event: ${event.id}", e)
            RelayResponse.NOTICE("error: ${e.message}").toClient(session)
        }
    }

    private suspend fun handleProofOfWorkEvent(event: Event, session: WebSocketSession) {
        try {
            val (valid, message) = nip13.verifyProofOfWork(event)
            if (valid) {
                val status: Boolean = sqlExec.saveEvent(event)
                if (status) {
                    LOG.info("Proof of Work event saved successfully: ${event.id}")
                    RelayResponse.OK(event.id!!, true, message).toClient(session)
                } else {
                    LOG.warn("Failed to save Proof of Work event: ${event.id}")
                    RelayResponse.OK(event.id!!, false, "error: could not save Proof of Work event").toClient(session)
                }
            } else {
                LOG.warn("Proof of Work validation failed for event: ${event.id}, Reason: $message")
                RelayResponse.OK(event.id!!, false, message).toClient(session)
            }
        } catch (e: Exception) {
            LOG.error("Error saving Proof of Work event: ${event.id}", e)
            RelayResponse.NOTICE("error: ${e.message}").toClient(session)
        }
    }


    suspend fun onRequest(
        subscriptionId: String,
        filtersX: List<FiltersX>,
        status: Boolean,
        warning: String,
        session: WebSocketSession
    ) {
        if (status) {
            //LOG.info("request for subscription ID: $subscriptionId with filters: $filtersX")

            for (filter in filtersX) {
                val events = sqlExec.filterList(filter)
                events.forEach { event ->
                    //LOG.info("Relay Response event: $event")
                    RelayResponse.EVENT(subscriptionId, event).toClient(session)
                }
            }

            RelayResponse.EOSE(subscriptionId).toClient(session)
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


    private val LOG = LoggerFactory.getLogger(BasicProtocolFlow::class.java)
}

