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
     * ฟังก์ชันสำหรับการทำงานแบบขนานด้วย Coroutines ร่วมกับ Virtual Threads
     *
     * หลักการ:
     * ฟังก์ชันนี้ถูกออกแบบมาเพื่อผสานการทำงานของ Coroutines กับ Virtual Threads ซึ่งเป็นฟีเจอร์ใหม่ใน
     * JVM. Coroutines เป็นเครื่องมือที่ช่วยให้สามารถจัดการกับงานแบบขนาน (concurrent tasks)
     * ได้อย่างมีประสิทธิภาพและจัดการทรัพยากรได้ดีขึ้นใน Kotlin. Virtual Threads
     * เป็นเทคนิคที่ช่วยสร้าง Threads ขึ้นมาแบบเบา (lightweight) ซึ่งจะช่วยเพิ่มประสิทธิภาพในการจัดการ
     * กับงานที่ต้องใช้ Threads จำนวนมาก.
     *
     * โดยฟังก์ชันนี้จะรับโค้ดบล็อคที่ต้องการให้ทำงานในรูปแบบของ Coroutine และนำไปทำงานใน Virtual Threads
     * ที่มีจำนวนจำกัดตามพารามิเตอร์ `parallelism`. จากนั้นผลลัพธ์ของโค้ดบล็อคจะถูกส่งกลับมา.
     *
     * @param parallelism จำนวน Threads ที่จะถูกใช้ในการทำงานแบบขนาน (ค่าเริ่มต้นคือ 32)
     * @param block โค้ดที่ต้องการให้ Virtual Threads ทำงานในรูปแบบของ Coroutine
     * @return ผลลัพธ์จากการทำงานของโค้ดบล็อคในรูปแบบ Coroutine
     */
    suspend inline fun <T> virtualCoroutine(parallelism: Int = 32, crossinline block: suspend () -> T): T {
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
