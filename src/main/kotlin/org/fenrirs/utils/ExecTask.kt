package org.fenrirs.utils

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

import io.micronaut.context.annotation.Factory
import kotlinx.coroutines.*

import org.slf4j.LoggerFactory

@Factory
@OptIn(ExperimentalCoroutinesApi::class)
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


    /**
     * ฟังก์ชันนี้จะรับโค้ดบล็อคที่ต้องการให้ทำงานในรูปแบบของ Coroutine และนำไปทำงานใน Virtual Threads
     * ที่มีจำนวนจำกัดตามพารามิเตอร์ `parallelism`. จากนั้นผลลัพธ์ของโค้ดบล็อคจะถูกส่งกลับมา.
     *
     * @param parallelism จำนวน Threads ที่จะถูกใช้ในการทำงานแบบขนาน (ค่าเริ่มต้นคือ 32)
     * @param block โค้ดที่ต้องการให้ Virtual Threads ทำงานในรูปแบบของ Coroutine
     * @return ผลลัพธ์จากการทำงานของโค้ดบล็อคในรูปแบบ Coroutine
     */
    suspend inline fun <T> asyncTask(parallelism: Int = 32, crossinline block: suspend () -> T): T {
        return withContext(execService.asCoroutineDispatcher().limitedParallelism(parallelism)) {
            block()
        }
    }


    suspend fun <T> parallelIO(parallelism: Int = 32, block: suspend CoroutineScope.() -> T): T {
        return withContext(Dispatchers.IO.limitedParallelism(parallelism)) {
            block.invoke(this)
        }
    }

    val LOG = LoggerFactory.getLogger(ExecTask::class.java)
}