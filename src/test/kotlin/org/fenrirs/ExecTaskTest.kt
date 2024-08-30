package org.fenrirs

import kotlinx.coroutines.runBlocking
import org.fenrirs.utils.ExecTask.parallelIO
import org.fenrirs.utils.ExecTask.runWithVirtualThreads
import org.fenrirs.utils.ExecTask.runWithVirtualThreadsPerTask
import org.fenrirs.utils.ExecTask.virtualCoroutine
import org.fenrirs.utils.ShiftTo.measure
import org.junit.jupiter.api.Test
import kotlin.random.Random

class ExecTaskTest {

    @Test
    fun testPerformance() = runBlocking {
        val tasks = listOf(1_000, 10_000, 100_000)

        tasks.forEach { taskCount ->
            println("Running tests with $taskCount tasks")

            // ทดสอบ runWithVirtualThreadsPerTask
            measure("runWithVirtualThreadsPerTask") {
                repeat(taskCount) {
                    runWithVirtualThreadsPerTask {
                        dummyTask()
                    }
                }
            }

            // ทดสอบ runWithVirtualThreads
            measure("runWithVirtualThreads") {
                repeat(taskCount) {
                    runWithVirtualThreads {
                        dummyTask()
                    }
                }
            }

            // ทดสอบ virtualCoroutine
            measure("virtualCoroutine") {
                runBlocking {
                    repeat(taskCount) {
                        virtualCoroutine {
                            dummyTask()
                        }
                    }
                }
            }

            // ทดสอบ parallelIO
            measure("parallelIO") {
                runBlocking {
                    repeat(taskCount) {
                        parallelIO {
                            dummyTask()
                        }
                    }
                }
            }

            println("Finished tests with $taskCount tasks\n")
        }
    }

    // ฟังก์ชันจำลองสำหรับทดสอบ
    private fun dummyTask() {
        // ทำงานบางอย่างที่ใช้เวลาและหน่วยความจำ
        val data = List(1000) { Random.nextInt() }
        data.sorted()
    }
}
