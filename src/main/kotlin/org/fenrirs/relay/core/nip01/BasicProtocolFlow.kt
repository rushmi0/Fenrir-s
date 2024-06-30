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
import org.fenrirs.relay.policy.NostrRelayConfig

import org.fenrirs.stored.RedisCacheFactory
import org.fenrirs.stored.statement.StoredServiceImpl
import org.fenrirs.utils.Bech32
import org.fenrirs.utils.ShiftTo.fromHex
import org.fenrirs.utils.ShiftTo.toHex

import org.slf4j.LoggerFactory

@Bean
class BasicProtocolFlow @Inject constructor(
    private val sqlExec: StoredServiceImpl,
    private val config: NostrRelayConfig,
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

        val relayOwner = Bech32.decode(config.info.npub).data.toHex()
        val passList: List<String> = getFollowsList(relayOwner)
        val pass: Boolean = config.policy.follows.pass
        val work: Boolean = config.policy.proofOfWork.enabled


        when {
            eventId != null -> {
                redis.setCache(event.id, event.id, 86_400)
                LOG.info("Event with ID ${event.id} already exists in the database")
                RelayResponse.OK(event.id, false, "duplicate: already have this event").toClient(session)
            }

            nip13.isProofOfWorkEvent(event) -> handleProofOfWorkEvent(event, session)
            nip09.isDeletable(event) -> handleDeletableEvent(event, session)
            else -> handleNormalEvent(event, session)
        }
    }

    private fun permission(
        event: Event,
        passList: List<String>,
        relayOwner: String, pass: Boolean, work: Boolean
    ): Boolean {
        return when {
            // ทำ Proof of Work ถ้า work เป็น true และ event.pubkey ไม่อยู่ใน passList
            work && event.pubkey !in passList -> true
            // ไม่ต้องทำ Proof of Work ถ้า pass เป็น true และ event.pubkey อยู่ใน passList
            pass && event.pubkey in passList -> false
            // ทำ Proof of Work ถ้า pass เป็น false และ event.pubkey ไม่เท่ากับ relayOwner
            !pass && event.pubkey != relayOwner -> true
            else -> false // ไม่ต้องทำ Proof of Work ในกรณีอื่นๆ
        }
    }


    private suspend fun getFollowsList(publicKey: String): List<String> {
        val filter = FiltersX(authors = setOf(publicKey), kinds = setOf(3))
        val event = sqlExec.filterList(filter)[0]
        val tagsList = event.tags?.filter { it.isNotEmpty() && it[0] == "p" }?.map { it[1] } ?: emptyList()
        return tagsList.plus(publicKey)
    }


    private suspend fun handleEvent(
        event: Event,
        session: WebSocketSession,
        action: suspend () -> Pair<Boolean, String>
    ) {
        try {
            val (success, message) = action.invoke()

            if (success) {
                redis.setCache(event.id!!, event.id, 604_800) // 86_400, 604_800
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
            status to (if (status) "" else "error: could not save event to the database")
        }
    }

    private suspend fun handleDeletableEvent(event: Event, session: WebSocketSession) {
        handleEvent(event, session) {
            val (deletionSuccess, message) = nip09.deleteEvent(event)
            if (deletionSuccess) {
                val status: Boolean = sqlExec.saveEvent(event)
                status to (if (status) message else "error: could not save event to the database after deletion")
            } else {
                false to message
            }
        }
    }


    private suspend fun handleProofOfWorkEvent(event: Event, session: WebSocketSession) {
        handleEvent(event, session) {
            val (valid, message) = nip13.verifyProofOfWork(event)
            if (valid) {
                val status: Boolean = sqlExec.saveEvent(event)
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
                val events: List<Event> = sqlExec.filterList(filter)
                events.forEachIndexed { index, event ->
                    val eventIndex = "${index + 1}/${events.size}"
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
        RelayResponse.NOTICE("Unknown command").toClient(session); session.close()
    }


    private val LOG = LoggerFactory.getLogger(BasicProtocolFlow::class.java)
}
