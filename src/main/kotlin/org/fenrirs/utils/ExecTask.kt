package org.fenrirs.utils

import kotlinx.coroutines.runBlocking
import org.fenrirs.utils.ShiftTo.formatMemorySize
import org.slf4j.LoggerFactory
import java.lang.management.ManagementFactory
import java.lang.management.MemoryMXBean
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

object ExecTask {

    // สร้าง ExecutorService ในการใช้งาน Virtual Threads
    val execService: ExecutorService = Executors.newVirtualThreadPerTaskExecutor()

    /**
     * ฟังก์ชันสำหรับการทำงานแบบขนานด้วย Virtual Threads ผ่าน executorService
     * @param construct ชื่อของโค้ดหรือระบบที่ต้องการวัด
     * @param block โค้ดที่ต้องการให้ Virtual Threads ทำงาน
     */
    inline fun <T> runWithVirtualThreadsPerTask(construct: String = "", crossinline block: suspend () -> T): T {
        return runBlocking {
            suspendCoroutine { continuation ->
                execService.execute {
                    try {
                        if (execService.isShutdown) {
                            LOG.error("Virtual thread shut down")
                            continuation.resumeWithException(IllegalStateException("Virtual thread shut down"))
                            return@execute
                        }
                        // ต้องเรียกใช้งาน block() ภายใน coroutine scope ที่ถูกต้อง
                        continuation.resume(runBlocking { block() })
                    } catch (e: Exception) {
                        // กรณีเกิด Exception ให้ส่ง exception กลับไปยัง coroutine caller
                        continuation.resumeWithException(e)
                    }
                }
            }
        }
    }

    /**
     * ฟังก์ชันสำหรับการทำงานแบบขนานด้วย Virtual Threads
     * @param construct ชื่อของโค้ดหรือระบบที่ต้องการวัด
     * @param block โค้ดที่ต้องการให้ Virtual Threads ทำงาน
     */
    inline fun <T> runWithVirtualThreads(construct: String = "", crossinline block: suspend () -> T): T {
        return runBlocking {
            suspendCoroutine { continuation ->
                Thread.startVirtualThread {
                    try {
                        if (Thread.currentThread().isInterrupted) {
                            LOG.error("Thread is interrupted")
                            continuation.resumeWithException(InterruptedException("Thread is interrupted"))
                            return@startVirtualThread
                        }
                        // ต้องเรียกใช้งาน block() ภายใน coroutine scope ที่ถูกต้อง
                        continuation.resume(runBlocking { block() })
                    } catch (e: Exception) {
                        // กรณีเกิด Exception ให้ส่ง exception กลับไปยัง coroutine caller
                        continuation.resumeWithException(e)
                    }
                }
            }
        }
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
        val start = System.currentTimeMillis()
        try {
            return block().also {
                // ขนาดหน่วยความจำที่ใช้งานหลังจากการทำงาน
                val finalMemory = memoryMXBean.heapMemoryUsage.used
                val memoryUsed = finalMemory - initialMemory // คำนวณความแตกต่างของหน่วยความจำ
                val formattedMemoryUsed = formatMemorySize(memoryUsed)
                LOG.info("Took: ${System.currentTimeMillis() - start} ms for: $construct")
                LOG.info("Memory used: $memoryUsed bytes ($formattedMemoryUsed)")
            }
        } catch(ex: Throwable) {
            LOG.error("Exception occurred. $construct. Exception: ${ex.message}")
            throw ex
        }
    }

    val LOG = LoggerFactory.getLogger(ExecTask::class.java)
}
