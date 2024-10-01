package org.fenrirs.relay.model

import org.fenrirs.relay.core.nip01.Transform.toEvent
import org.fenrirs.relay.core.nip01.Transform.validateElement
import org.fenrirs.relay.policy.EventValidateField
import org.fenrirs.storage.Environment

import org.fenrirs.utils.Bech32
import org.fenrirs.utils.ShiftTo.toHex
import org.fenrirs.utils.ShiftTo.toJsonEltArray

import org.fenrirs.relay.policy.Event
import org.fenrirs.storage.statement.StoredServiceImpl

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*

import io.micronaut.context.annotation.Bean

import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import jakarta.inject.Inject
import okhttp3.*

@Bean
class ProfileSync @Inject constructor(
    private val sqlExec: StoredServiceImpl,
    private val env: Environment,
) {

    private val LOG = LoggerFactory.getLogger(ProfileSync::class.java)

    private val publicKey: String? by lazy {
        if (env.RELAY_OWNER.startsWith("npub")) Bech32.decode(env.RELAY_OWNER).data.toHex() else env.RELAY_OWNER
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val reqList = listOf("""["REQ","fffff",{"authors":["$publicKey"],"kinds":[3]}]""")
    private val sourceList: List<String> = env.BACKUP_SYNC
    private val syncPass = env.BACKUP_ENABLED

    fun sync() {
        if (sourceList.isNotEmpty() && syncPass) {
            sourceList.forEach { url ->
                val request: Request = Request.Builder().url(url).build()
                client.newWebSocket(request, SyncData())
            }
        }
    }

    private inner class SyncData : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            publicKey?.let { key ->
                reqList.forEach { webSocket.send(it.replace("\$publicKey", key)) }
            } ?: LOG.warn("No public key available, skipping sync request")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val data = text.toJsonEltArray()
            if (data[0].jsonPrimitive.content == "EVENT") {
                runBlocking { receivedEvent(data[2].jsonObject) }
            }
        }

        private suspend fun receivedEvent(eventJson: JsonObject) {
            val event: Event = eventJson.toEvent()
            val eventMap: Map<String, JsonElement> = eventJson.toMap()

            val (status, _) = validateElement(eventMap, EventValidateField.entries.toTypedArray())
            if (status) {
                handleEvent(event) {
                    val eventId: Event? = runBlocking { sqlExec.selectById(event.id!!) }

                    if (eventId == null) {
                        LOG.info("Event ID: $eventId");
                        sqlExec.saveEvent(event)
                    } else {
                        false
                    }
                }
            }
        }

        private suspend fun handleEvent(event: Event, action: suspend () -> Boolean) {
            try {
                val success = action.invoke()
                if (success) {
                    LOG.info("Event handled successfully: ${event.id}")
                } else {
                    LOG.info("Event handled pass")
                }
            } catch (e: Exception) {
                LOG.error("Error handling event: ${event.id}", e)
            }
        }


        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            LOG.info("WebSocket connection closed: $reason")
        }
    }

}
