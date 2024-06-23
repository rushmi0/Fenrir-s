package org.fenrirs.utils


import org.fenrirs.utils.ShiftTo.formatMemorySize
import org.slf4j.LoggerFactory
import java.lang.management.ManagementFactory
import java.lang.management.MemoryMXBean
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


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

        Thread.startVirtualThread {
            try {
                if (Thread.currentThread().isInterrupted) {
                    LOG.error("Thread is interrupted")
                    future.completeExceptionally(InterruptedException("Thread is interrupted"))
                    return@startVirtualThread
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
     * ฟังก์ชันสำหรับวัดเวลาและการใช้หน่วยความจำของโค้ด
     * @param construct ชื่อของโค้ดหรือระบบที่ต้องการวัด
     * @param block โค้ดที่ต้องการวัดเวลาและการใช้หน่วยความจำ
     * @return ผลลัพธ์ของโค้ด
     */
    inline fun <T> measure(construct: String, crossinline block: () -> T): T {
        // ขนาดหน่วยความจำที่ใช้งานก่อนการทำงาน
        val memoryMXBean: MemoryMXBean = ManagementFactory.getMemoryMXBean()
        val initialMemory = memoryMXBean.heapMemoryUsage.used
        val start = System.nanoTime()
        try {
            return block().also {
                // ขนาดหน่วยความจำที่ใช้งานหลังจากการทำงาน
                val finalMemory = memoryMXBean.heapMemoryUsage.used
                val memoryUsed = finalMemory - initialMemory // คำนวณความแตกต่างของหน่วยความจำ
                val formattedMemoryUsed = formatMemorySize(memoryUsed)
                LOG.info("Took: ${elapsedMillis(start)} ms for: $construct")
                LOG.info("Memory used: $memoryUsed bytes ($formattedMemoryUsed)")
            }
        } catch (ex: Throwable) {
            LOG.error("Exception occurred. $construct. Exception: ${ex.message}")
            throw ex
        }
    }

    fun elapsedMillis(startNanos: Long): Long {
        return TimeUnit.MILLISECONDS.convert(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS)
    }


    val LOG = LoggerFactory.getLogger(ExecTask::class.java)
}
