package org.fenrirs.benchmark

import com.sun.management.ThreadMXBean
import org.fenrirs.relay.models.Event
import org.fenrirs.utils.ShiftTo
import org.fenrirs.utils.ShiftTo.fromHex
import org.fenrirs.utils.ShiftTo.toHex
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.lang.management.ManagementFactory
import java.security.MessageDigest

/**
 * Manual micro-benchmark (not a correctness test) comparing the pre-refactor implementations
 * against the new ones in ShiftTo.kt, using the JDK's per-thread allocation counter so the
 * "bytes/op" numbers are actual measurements, not estimates.
 *
 * Not wired into CI assertions on purpose: wall-clock numbers are inherently noisy on a shared
 * machine. Run directly (`./gradlew test --tests "*Nip01VerificationBenchmark*"`) and read the
 * printed table.
 */
class Nip01VerificationBenchmark {

    private object Old {
        fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

        fun String.fromHex(): ByteArray {
            check(length % 2 == 0) { "String length must be even" }
            return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }

        fun Any.toJsonString(): String = jacksonObjectMapper().writeValueAsString(this)

        fun ByteArray.toSha256(): ByteArray = MessageDigest.getInstance("SHA-256").digest(this)

        fun String.toSha256(): String = toByteArray().toSha256().toHex()

        fun generateId(event: Event): String {
            return lazy {
                arrayListOf(
                    0,
                    event.pubkey,
                    event.created_at,
                    event.kind,
                    event.tags,
                    event.content
                ).toJsonString().toSha256()
            }.value
        }
    }

    private val threadMx = ManagementFactory.getThreadMXBean() as ThreadMXBean

    private val sampleEvent = Event(
        id = "dc90c95f09947507c1044e8f48bcf6350aa6bff1507dd4acfc755b9239b5c962",
        pubkey = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d",
        created_at = 1644271588L,
        kind = 1L,
        tags = listOf(listOf("e", "a".repeat(64)), listOf("p", "b".repeat(64))),
        content = "now that https://blueskyweb.org/blog/2-7-2022-overview was announced we can stop working on nostr?",
        sig = "230e9d8f0ddaf7eb70b5f7741ccfa37e87a455c9a469282e3464e2052d3192cd63a167e196e381ef9d7e69e9ea43af2443b839974dc85d8aaab9efe1d9296524"
    )

    private data class Sample(val name: String, val nanosPerOp: Double, val bytesPerOp: Double)

    private fun allocatedBytes(): Long = threadMx.getThreadAllocatedBytes(Thread.currentThread().threadId())

    private fun <T> measure(name: String, iterations: Int, warmup: Int, block: () -> T): Sample {
        repeat(warmup) { block() }
        System.gc()
        Thread.sleep(20)

        val allocBefore = allocatedBytes()
        val start = System.nanoTime()
        repeat(iterations) { block() }
        val elapsedNanos = System.nanoTime() - start
        val allocAfter = allocatedBytes()

        return Sample(
            name = name,
            nanosPerOp = elapsedNanos.toDouble() / iterations,
            bytesPerOp = (allocAfter - allocBefore).toDouble() / iterations
        )
    }

    @Test
    fun `benchmark old vs new hot-path functions`() {
        val iterations = 20_000
        val warmup = 5_000

        val samples = listOf(
            measure("generateId - OLD (Jackson ObjectMapper + lazy{})", iterations, warmup) {
                Old.generateId(sampleEvent)
            },
            measure("generateId - NEW (hand-rolled canonical JSON + cached SHA-256)", iterations, warmup) {
                ShiftTo.generateId(sampleEvent)
            },
            measure("toHex - OLD (\"%02x\".format per byte)", iterations, warmup) {
                with(Old) { ByteArray(32) { it.toByte() }.toHex() }
            },
            measure("toHex - NEW (lookup table)", iterations, warmup) {
                ByteArray(32) { it.toByte() }.toHex()
            },
            measure("fromHex - OLD (chunked().map())", iterations, warmup) {
                with(Old) { "ab".repeat(32).fromHex() }
            },
            measure("fromHex - NEW (Character.digit loop)", iterations, warmup) {
                "ab".repeat(32).fromHex()
            }
        )

        val separator = "-".repeat(104)
        println()
        println(separator)
        println("%-65s %16s %16s".format("Benchmark ($iterations iterations, $warmup warmup)", "ns/op", "bytes/op"))
        println(separator)
        samples.forEach { println("%-65s %16.1f %16.1f".format(it.name, it.nanosPerOp, it.bytesPerOp)) }
        println(separator)
    }
}