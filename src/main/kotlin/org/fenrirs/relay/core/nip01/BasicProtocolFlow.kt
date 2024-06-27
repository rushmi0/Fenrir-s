package org.fenrirs.relay.core.nip01

import io.micronaut.context.annotation.Bean
import io.micronaut.websocket.WebSocketSession

import jakarta.inject.Inject
import kotlinx.coroutines.runBlocking

import org.fenrirs.relay.modules.Event
import org.fenrirs.relay.modules.FiltersX

import org.fenrirs.relay.core.nip01.response.RelayResponse
import org.fenrirs.relay.core.nip09.EventDeletion
import org.fenrirs.relay.core.nip13.ProofOfWork

import org.fenrirs.stored.RedisCacheFactory
import org.fenrirs.stored.statement.StoredServiceImpl

import org.slf4j.LoggerFactory

@Bean
class BasicProtocolFlow @Inject constructor(
    private val sqlExec: StoredServiceImpl,
    private val redis: RedisCacheFactory,
    private val nip09: EventDeletion,
    private val nip13: ProofOfWork
) {

    suspend fun onEvent(event: Event, status: Boolean, warning: String, session: WebSocketSession) = runBlocking {
        LOG.info("Received event: $event")

        if (!status) {
            RelayResponse.OK(event.id!!, false, warning).toClient(session)
            return@runBlocking
        }

        val eventId: Event? = redis.getCache(event.id!!)?.let { sqlExec.selectById(event.id) }

        when {
            eventId != null -> {
                redis.setCache(event.id, event.id, 2_000)
                LOG.info("Event with ID ${event.id} already exists in the database")
                RelayResponse.OK(event.id, false, "duplicate: already have this event").toClient(session)
            }

            nip13.isProofOfWorkEvent(event) -> handleProofOfWorkEvent(event, session)
            nip09.isDeletable(event) -> handleDeletableEvent(event, session)
            else -> handleNormalEvent(event, session)
        }
    }


    private suspend fun handleEvent(
        event: Event,
        session: WebSocketSession,
        action: suspend () -> Pair<Boolean, String>
    ) {
        try {
            val (success, message) = action.invoke()

            if (success) {
                redis.setCache(event.id!!, event.id, 2_000) // 86_400
                LOG.info("Event handled successfully")
                RelayResponse.OK(event.id, true, message).toClient(session)
            } else {
                LOG.warn("Failed to handle event: ${event.id}")
                RelayResponse.OK(event.id!!, false, message).toClient(session)
            }

        } catch (e: Exception) {
            LOG.error("Error handling event: ${event.id}", e)
            RelayResponse.NOTICE("error: ${e.message}").toClient(session)
        }
    }

    private suspend fun handleNormalEvent(event: Event, session: WebSocketSession) {
        handleEvent(event, session) {
            val status: Boolean = sqlExec.saveEvent(event)
            LOG.info("Event saved successfully: ${event.id}")
            status to (if (status) "" else "error: could not save event to the database")
        }
    }

    private suspend fun handleDeletableEvent(event: Event, session: WebSocketSession) {
        handleEvent(event, session) {
            val deletionSuccess: Boolean = nip09.deleteEvent(event)
            LOG.info("Event deleted successfully: ${event.id}")
            deletionSuccess to (if (deletionSuccess) "" else "error: could not delete event")
        }
    }

    private suspend fun handleProofOfWorkEvent(event: Event, session: WebSocketSession) {
        handleEvent(event, session) {
            val (valid, message) = nip13.verifyProofOfWork(event)
            if (valid) {
                val status: Boolean = sqlExec.saveEvent(event)
                LOG.info("Proof of Work Event saved successfully: ${event.id}")
                status to (if (status) "" else "error: could not save Proof of Work event")
            } else {
                false to message
            }
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
            for (filter in filtersX) {
                val events = sqlExec.filterList(filter)
                events.forEachIndexed { index, event ->
                    val eventIndex = "${index + 1}/${events.size}" // Index starts from 1 for readability
                    LOG.info("Relay Response event $eventIndex: $event")
                    RelayResponse.EVENT(subscriptionId, event).toClient(session)
                }
            }

            RelayResponse.EOSE(subscriptionId).toClient(session)
        } else {
            RelayResponse.NOTICE(warning).toClient(session)
        }
    }


    suspend fun onClose(subscriptionId: String, session: WebSocketSession) {
        LOG.info("close request for subscription ID: $subscriptionId")
        RelayResponse.CLOSED(subscriptionId).toClient(session)
    }

    suspend fun onUnknown(session: WebSocketSession) {
        LOG.warn("Unknown command")
        RelayResponse.NOTICE("Unknown command").toClient(session)
    }


    private val LOG = LoggerFactory.getLogger(BasicProtocolFlow::class.java)
}
