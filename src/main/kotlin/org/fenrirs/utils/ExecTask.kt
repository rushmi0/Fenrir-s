package org.fenrirs.utils

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.*

import org.slf4j.LoggerFactory

@OptIn(ExperimentalCoroutinesApi::class)
object ExecTask {

    val execService: ExecutorService = Executors.newVirtualThreadPerTaskExecutor()

    inline fun <T> runWithVirtualThreadsPerTask(crossinline block: () -> T): T {
        val future = CompletableFuture<T>()

        execService.execute {
            try {
                if (execService.isShutdown) {
                    LOG.error("Virtual thread shut down")
                    future.completeExceptionally(IllegalStateException("Virtual thread shut down"))
                    return@execute
                }
                val result = block()
                future.complete(result)
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }

        return future.get()
    }

    inline fun <T : Any> runWithVirtualThreads(crossinline block: () -> T): T {
        val future = CompletableFuture<T>()

        // Thread.startVirtualThread
        val runnable = Runnable {
            try {
                if (Thread.currentThread().isInterrupted) {
                    LOG.error("Thread is interrupted")
                    future.completeExceptionally(InterruptedException("Thread is interrupted"))
                    return@Runnable
                }
                val result = block()
                future.complete(result)
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }

        Thread.startVirtualThread(runnable)

        return future.get()
    }


    suspend inline fun <T> asyncTask(parallelism: Int = 100_000, crossinline block: suspend () -> T): T {
        return withContext(execService.asCoroutineDispatcher().limitedParallelism(parallelism)) {
            block()
        }
    }


    suspend fun <T> parallelIO(parallelism: Int = 10000, block: suspend CoroutineScope.() -> T): T {
        return withContext(Dispatchers.IO.limitedParallelism(parallelism)) {
            block.invoke(this)
        }
    }

    val LOG = LoggerFactory.getLogger(ExecTask::class.java)
}