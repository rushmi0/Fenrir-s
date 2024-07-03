package org.fenrirs.relay.service

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

import org.fenrirs.relay.core.nip01.Transform.toEvent
import org.fenrirs.relay.core.nip01.Transform.validateElement
import org.fenrirs.relay.modules.Event
import org.fenrirs.relay.policy.EventValidateField
import org.fenrirs.relay.policy.NostrRelayConfig
import org.fenrirs.stored.statement.StoredServiceImpl
import org.fenrirs.utils.Bech32
import org.fenrirs.utils.ShiftTo.toHex
import org.fenrirs.utils.ShiftTo.toJsonEltArray

import io.micronaut.context.annotation.Bean
import jakarta.inject.Inject

import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

import okhttp3.*

@Bean
class ProfileSync @Inject constructor(
    private val sqlExec: StoredServiceImpl,
    private val config: NostrRelayConfig
) {

    private val LOG = LoggerFactory.getLogger(ProfileSync::class.java)

    private val publicKey = Bech32.decode(config.info.npub).data.toHex()
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val reqList = listOf(
        """["REQ","fffff",{"authors":["$publicKey"],"kinds":[3]}]""",
        """["REQ","fffff",{"#p":["$publicKey"]}]"""
    )

    private val sourceList: List<String> = config.database.backup.sync

    fun sync() {

        if (sourceList.isNotEmpty()) {
            sourceList.forEach { url ->
                val request: Request = Request.Builder().url(url).build()
                client.newWebSocket(request, SyncData())
            }
            //val followsList = runBlocking { getFollowsList(publicKey) }
            //LOG.info("Follows: $followsList")
        }
    }

    /*
    private suspend fun getFollowsList(publicKey: String): List<String> {
        val filter = FiltersX(authors = setOf(publicKey), kinds = setOf(3))
        val event = sqlExec.filterList(filter)[0]
        val tagsList = event.tags?.filter { it.isNotEmpty() && it[0] == "p" }?.map { it[1] } ?: emptyList()
        return tagsList.plus(publicKey)
    }
     */


    private inner class SyncData : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            reqList.forEach { webSocket.send(it) }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val data = text.toJsonEltArray()
            if (data[0].jsonPrimitive.content == "EVENT") {
                val eventJson = data[2].jsonObject
                val event: Event = eventJson.toEvent()
                val eventMap: Map<String, JsonElement> = eventJson.toMap()

                val (status, _) = validateElement(eventMap, EventValidateField.entries.toTypedArray())
                val existingEvent = sqlExec.selectById(event.id!!)
                if (status) {
                    runBlocking {
                        if (existingEvent == null) {
                            sqlExec.saveEvent(event)
                            LOG.info("Saved event: $status")
                        }
                    }
                }
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            LOG.error("WebSocket connection failure: ${t.message}", t)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            LOG.info("WebSocket connection closed: $reason")
        }

    }
}
