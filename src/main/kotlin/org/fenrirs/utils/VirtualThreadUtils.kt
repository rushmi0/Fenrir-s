package org.fenrirs.utils

import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object VirtualThreadUtils {

    // สร้าง ExecutorService ในการใช้งาน Virtual Threads
    val executorService: ExecutorService = Executors.newVirtualThreadPerTaskExecutor()

    /**
     * ฟังก์ชันสำหรับการทำงานแบบขนานด้วย Virtual Threads ผ่าน executorService
     * @param construct ชื่อของโค้ดหรือระบบที่ต้องการวัด
     * @param block โค้ดที่ต้องการให้ Virtual Threads ทำงาน
     */
    inline fun <T> runWithExecutorService(construct: String, crossinline block: () -> T) {
        measure(construct) {
            executorService.execute {
                if (executorService.isShutdown) {
                    LOG.error("Virtual thread shut down")
                    return@execute
                }
                block()
            }
        }
    }


    /**
     * ฟังก์ชันสำหรับการทำงานแบบขนานด้วย Virtual Threads
     * @param construct ชื่อของโค้ดหรือระบบที่ต้องการวัด
     * @param block โค้ดที่ต้องการให้ Virtual Threads ทำงาน
     */
    inline fun <T> runWithVirtualThreads(construct: String, crossinline block: () -> T): Thread? {
        return measure(construct) {
            Thread.startVirtualThread {
                if (Thread.currentThread().isInterrupted) {
                    LOG.error("Thread is interrupted")
                    return@startVirtualThread
                }
                block()
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
        val initialMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        val start = System.currentTimeMillis()
        try {
            return block().also {
                // คำนวณขนาด heap memory หลังจากการทำงาน
                val finalMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()//ManagementFactory.getMemoryMXBean().heapMemoryUsage.used
                val memoryUsed =  initialMemory - finalMemory
                val formattedMemoryUsed = formatMemorySize(memoryUsed)
                LOG.info("Took: ${System.currentTimeMillis() - start} ms for: $construct")
                LOG.info("Memory used: $memoryUsed, $formattedMemoryUsed")
            }
        } catch(ex: Throwable) {
            LOG.error("Exception occurred. $construct. Exception: ${ex.message}")
            throw ex
        }
    }

    /**
     * ฟังก์ชันสำหรับแปลงขนาดหน่วยความจำเป็น KB, MB, หรือ GB
     * @param bytes ขนาดหน่วยความจำในหน่วย bytes
     * @return ขนาดหน่วยความจำที่ถูกแปลงเป็นหน่วยที่เหมาะสม
     */
    fun formatMemorySize(bytes: Long): String {
        val kilobyte = 1024L
        val megabyte = kilobyte * 1024
        val gigabyte = megabyte * 1024

        return when {
            bytes >= gigabyte -> "%.2f GB".format(bytes.toDouble() / gigabyte)
            bytes >= megabyte -> "%.2f MB".format(bytes.toDouble() / megabyte)
            bytes >= kilobyte -> "%.2f KB".format(bytes.toDouble() / kilobyte)
            else -> "$bytes bytes"
        }
    }



    val LOG = LoggerFactory.getLogger(VirtualThreadUtils::class.java)

}
