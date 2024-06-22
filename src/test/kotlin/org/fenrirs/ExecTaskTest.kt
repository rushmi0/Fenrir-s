package org.fenrirs

import kotlinx.coroutines.*
import org.fenrirs.utils.ExecTask
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class ExecTaskTest {

    private val workload = 100_000

    @Test
    fun `test Run With Virtual Threads Per Task`() {
        val counter = AtomicInteger(0)

        assertDoesNotThrow {
           (1..workload).map { _ ->
                ExecTask.runWithVirtualThreadsPerTask {
                    counter.incrementAndGet()
                }
            }
        }

        println("Completed run Virtual Threads Per Task with $workload tasks")
    }

    @Test
    fun `test Run With Virtual Threads`() {
        val counter = AtomicInteger(0)

        assertDoesNotThrow {
            (1..workload).forEach { _ ->
                ExecTask.runWithVirtualThreads {
                    counter.incrementAndGet()
                }
            }
        }

        println("Completed run Virtual Threads with $workload tasks")
    }

    @Test
    fun `test Run With Coroutines`() = runBlocking {
        val counter = AtomicInteger(0)

        assertDoesNotThrow {
            (1..workload).forEach { _ ->
                launch {
                    counter.incrementAndGet()
                }
            }
        }

        println("Completed run with Coroutines Dispatchers IO with $workload tasks")
    }

}
