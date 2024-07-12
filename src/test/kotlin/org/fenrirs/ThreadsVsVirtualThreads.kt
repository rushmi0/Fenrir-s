package org.fenrirs

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Test
import java.lang.Thread.sleep
import kotlinx.coroutines.runBlocking

import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import org.fenrirs.utils.ExecTask
import org.fenrirs.utils.ShiftTo.measure

class ThreadsVsVirtualThreads {

    @Test
    fun `many Coroutines`() = measure("Coroutines") {
        runBlocking {
            (1..100_000).map {
                launch(Dispatchers.IO) {
                    counter.incrementAndGet()
                    delay(2000)
                }
            }
        }
        println("Coroutines: Ready to Roll")
    }

    @Test
    fun `many Threads`() {
        measure("Threads") {
            val threads = (1..100_000).map {
                thread {
                    counter.incrementAndGet()
                    sleep(2000)
                }
            }
            println("Threads: Ready to Roll")
            threads.forEach {
                it.join()
            }
        }
    }

    @Test
    fun `many virtual Threads`() {
        measure("VirtualThreads") {
            val threads = (1..100_000).map {
                Thread.startVirtualThread {
                    counter.incrementAndGet()
                    sleep(2000)
                }
            }
            println("Virtual Threads: Ready to Roll")
            threads.forEach {
                it.join()
            }
        }
    }

    @Test
    fun `many virtual Threads with executorService`() {
        measure("VirtualThreads with ExecutorService") {
            val futures = (1..100_000).map {
                ExecTask.execService.execute {
                    counter.incrementAndGet()
                    Thread.sleep(2000)
                }
            }
            println(futures.joinToString("\n"))
            println("Virtual Threads with ExecutorService: Ready to Roll")
        }
    }

    companion object {
        val counter = AtomicLong(0)
    }
}
