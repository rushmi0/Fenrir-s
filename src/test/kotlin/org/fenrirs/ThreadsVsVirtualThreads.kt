package org.fenrirs

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.concurrent.thread
import kotlin.random.Random
import org.fenrirs.utils.ExecTask
import org.fenrirs.utils.ShiftTo.measure

class ThreadsVsVirtualThreads {

    @Test
    fun `many Coroutines`() = measure("Coroutines") {
        runBlocking {
            (1..10_000).map {
                launch(Dispatchers.IO) {
                    val randomNumbers = List(100) { Random.nextInt(0, 1000) }
                    randomNumbers.sorted()
                }
            }
        }
        println("Coroutines: Ready to Roll")
    }

    @Test
    fun `many Threads`() {
        measure("Threads") {
            val threads = (1..10_000).map {
                thread {
                    val randomNumbers = List(100) { Random.nextInt(0, 1000) }
                    randomNumbers.sorted()
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
            val threads = (1..10_000).map {
                Thread.startVirtualThread {
                    val randomNumbers = List(100) { Random.nextInt(0, 1000) }
                    randomNumbers.sorted()
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
        measure("Virtual Threads with ExecutorService") {
            (1..10_000).map {
                ExecTask.execService.execute {
                    val randomNumbers = List(100) { Random.nextInt(0, 1000) }
                    randomNumbers.sorted()
                }
            }
            println("Virtual Threads with ExecutorService: Ready to Roll")
        }
    }

    @Test
    fun `many async Tasks with asyncTask`() = measure("Coroutine on Virtual Threads") {
        runBlocking {
            (1..10_000).map {
                ExecTask.asyncTask {
                    val randomNumbers = List(100) { Random.nextInt(0, 1000) }
                    randomNumbers.sorted()
                }
            }
        }
        println("AsyncTask: Ready to Roll")
    }

}
