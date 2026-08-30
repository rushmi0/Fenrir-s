package org.fenrirs.relay.core.backup

import jakarta.inject.Singleton

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement

import org.fenrirs.relay.models.Event
import org.fenrirs.relay.models.FiltersX
import org.fenrirs.relay.models.toJson
import org.fenrirs.utils.ExecTask

import org.slf4j.LoggerFactory

import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * This relay acting as a Nostr *client* rather than a server - the one place in the codebase that
 * opens an outbound WebSocket to somewhere else, used only for [ProfileBackfillService]'s "fetch
 * kind-0 for these pubkeys from the backup-sync relays" need. Plain JDK `java.net.http` (same
 * outbound-client idiom [org.fenrirs.relay.web.admin.LinkPreviewController] already uses for its
 * plain-HTTP fetches) rather than a Nostr SDK - the relay only ever needs REQ/EVENT/EOSE, nothing
 * an SDK would meaningfully save over a few dozen lines by hand, and it keeps this to zero new
 * dependencies.
 *
 * Every call blocks its calling thread (`.get()`/`CountDownLatch.await()`) - fine here because
 * every call site runs on a virtual thread (see [ExecTask]), same reasoning as the rest of this
 * codebase's suspend functions riding virtual threads instead of true async I/O.
 */
@Singleton
class BackupRelayClient {

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECS))
        .build()

    /**
     * Queries every one of [relayUrls] in parallel (each on its own virtual thread) for kind-0
     * events authored by any of [pubkeys], merging whatever comes back - one relay being slow or
     * unreachable never holds up the others, it just contributes nothing. Returned events are raw,
     * unverified wire content - NIP-01 id/signature verification is the caller's job (see
     * VerifyEvent.verifyNip01), exactly like every other event this relay ever accepts.
     */
    fun fetchKind0(relayUrls: List<String>, pubkeys: Set<String>): List<Event> {
        if (relayUrls.isEmpty() || pubkeys.isEmpty()) return emptyList()

        val futures = relayUrls.map { url ->
            CompletableFuture.supplyAsync(
                {
                    runCatching { fetchFromRelay(url, pubkeys) }
                        .getOrElse { e ->
                            LOG.warn("[BACKUP-SYNC] fetch from {} failed: {}", url, e.message)
                            emptyList()
                        }
                },
                ExecTask.execService
            )
        }

        val overallTimeout = CONNECT_TIMEOUT_SECS + FETCH_TIMEOUT_SECS + 2
        return futures.flatMap { future ->
            runCatching { future.get(overallTimeout, TimeUnit.SECONDS) }.getOrElse { emptyList() }
        }
    }

    private fun fetchFromRelay(url: String, pubkeys: Set<String>): List<Event> {
        val subId = "backfill-${UUID.randomUUID().toString().take(8)}"
        val filters = FiltersX {
            kinds = setOf(0L)
            authors = pubkeys
        }
        val reqJson = "[\"REQ\",${Json.encodeToString(subId)},${filters.toJson()}]"

        val events = ConcurrentLinkedQueue<Event>()
        val done = CountDownLatch(1)
        val buffer = StringBuilder()

        val listener = object : WebSocket.Listener {
            override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*> {
                buffer.append(data)
                if (last) {
                    handleFrame(buffer.toString(), subId, events, done)
                    buffer.setLength(0)
                }
                webSocket.request(1)
                return CompletableFuture.completedFuture(null)
            }

            override fun onError(webSocket: WebSocket, error: Throwable) {
                done.countDown()
            }

            override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*> {
                done.countDown()
                return CompletableFuture.completedFuture(null)
            }
        }

        var socket: WebSocket? = null
        try {
            socket = httpClient.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECS))
                .buildAsync(URI.create(url), listener)
                .get(CONNECT_TIMEOUT_SECS, TimeUnit.SECONDS)
            socket.sendText(reqJson, true)
            done.await(FETCH_TIMEOUT_SECS, TimeUnit.SECONDS)
        } finally {
            socket?.sendClose(WebSocket.NORMAL_CLOSURE, "done")
        }
        return events.toList()
    }

    private fun handleFrame(text: String, subId: String, events: ConcurrentLinkedQueue<Event>, done: CountDownLatch) {
        val frame = runCatching { Json.parseToJsonElement(text).jsonArray }.getOrNull() ?: return
        when (frame.getOrNull(0)?.jsonPrimitive?.contentOrNull) {
            "EVENT" -> {
                if (frame.size < 3 || frame[1].jsonPrimitive.contentOrNull != subId) return
                runCatching { Json.decodeFromJsonElement<Event>(frame[2]) }.getOrNull()?.let { events += it }
            }

            "EOSE" -> if (frame.getOrNull(1)?.jsonPrimitive?.contentOrNull == subId) done.countDown()

            else -> Unit // NOTICE/CLOSED/anything else - nothing this fetch needs to react to
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(BackupRelayClient::class.java)
        private const val CONNECT_TIMEOUT_SECS = 5L
        private const val FETCH_TIMEOUT_SECS = 8L
    }
}
