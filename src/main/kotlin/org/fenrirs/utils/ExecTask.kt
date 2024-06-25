package org.fenrirs.utils

import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object ExecTask {

    val execService: ExecutorService = Executors.newVirtualThreadPerTaskExecutor()

    /**
     * ฟังก์ชันสำหรับการทำงานแบบขนานด้วย Virtual Threads ผ่าน executorService
     * @param block โค้ดที่ต้องการให้ Virtual Threads ทำงาน
     */
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

    /**
     * ฟังก์ชันสำหรับการทำงานแบบขนานด้วย Virtual Threads
     * @param block โค้ดที่ต้องการให้ Virtual Threads ทำงาน
     */
    inline fun <T> runWithVirtualThreads(crossinline block: () -> T): T {
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




    val LOG = LoggerFactory.getLogger(ExecTask::class.java)
}
