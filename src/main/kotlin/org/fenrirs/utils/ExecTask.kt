package org.fenrirs.utils

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

import kotlinx.coroutines.*

import org.slf4j.Logger
import org.slf4j.LoggerFactory

object ExecTask {

    val execService: ExecutorService = Executors.newVirtualThreadPerTaskExecutor()

    /**
     * ฟังก์ชันสำหรับการทำงานแบบขนานด้วย Virtual Threads ผ่าน executorService
     *
     * คำเตือน: ฟังก์ชันนี้ block เธรดที่เรียกจนกว่างานจะเสร็จ ห้ามเรียกจาก
     * event-loop thread (เช่น Netty) มิฉะนั้นจะทำให้เธรดนั้นติดค้าง
     *
     * @param block โค้ดที่ต้องการให้ Virtual Threads ทำงาน
     */
    inline fun <T> runWithVirtualThreadsPerTask(crossinline block: () -> T): T {
        val future = CompletableFuture<T>()

        try {
            execService.execute {
                runCatching(block).fold(future::complete, future::completeExceptionally)
            }
        } catch (e: RejectedExecutionException) {
            LOG.error("[SYSTEM] Virtual thread executor is shut down", e)
            future.completeExceptionally(e)
        }

        return future.get()
    }

    /**
     * ฟังก์ชันนี้จะรันโค้ด suspend บน Virtual Threads Executor โดยใช้ Coroutine Dispatcher
     * พร้อมจำกัดจำนวนงานที่สามารถรันพร้อมกันตามค่า `parallelism`
     *
     * @param parallelism จำนวนสูงสุดของ Coroutine ที่สามารถรันพร้อมกันได้ (ค่าเริ่มต้นคือ 32)
     * @param block โค้ด suspend ที่จะถูกรันภายใต้ Virtual Threads Executor
     * @return ผลลัพธ์จากการทำงานของโค้ด block
     */
    suspend inline fun <T> asyncTask(parallelism: Int = 32, crossinline block: suspend () -> T): T {
        return withContext(execService.asCoroutineDispatcher().limitedParallelism(parallelism)) {
            block()
        }
    }

    val LOG: Logger = LoggerFactory.getLogger(ExecTask::class.java)
}