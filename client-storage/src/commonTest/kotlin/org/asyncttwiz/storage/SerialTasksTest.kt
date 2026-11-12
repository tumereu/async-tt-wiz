package org.asyncttwiz.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

// The browser's single-thread executors as coroutines (web client step 3b): a worker's tasks keep their order, and one
// that waits holds back the rest, as a worker thread in runBlocking does on Android, without blocking the page.
class SerialTasksTest {
    @Test
    fun tasksRunOneAtATimeInOrderEvenWhileOneWaits() = runTest {
        val failures = mutableListOf<Throwable>()
        val tasks = SerialTasks(backgroundScope) { failures += it }
        val answer = CompletableDeferred<String>()
        val log = mutableListOf<String>()
        tasks.submit { log += "first starts"; log += "first got ${answer.await()}" }
        tasks.submit { log += "second" }
        runCurrent()
        // The page isn't blocked: this code runs while the first task waits, and the second hasn't started
        assertEquals(listOf("first starts"), log)
        answer.complete("answer")
        runCurrent()
        assertEquals(listOf("first starts", "first got answer", "second"), log)
        tasks.submit { log += "third" }
        runCurrent()
        assertEquals("third", log.last())
        assertTrue(failures.isEmpty())
    }

    @Test
    fun aFailingTaskIsReportedAndTheNextStillRuns() = runTest {
        val failures = mutableListOf<String>()
        val tasks = SerialTasks(backgroundScope) { failures += it.message.orEmpty() }
        val log = mutableListOf<String>()
        tasks.submit { error("broken") }
        tasks.submit { log += "after" }
        runCurrent()
        assertEquals(listOf("broken"), failures)
        assertEquals(listOf("after"), log)
    }

    @Test
    fun aFullQueueDropsItsOldestWaitingTask() = runTest {
        val tasks = SerialTasks(backgroundScope, capacity = 2) {}
        val gate = CompletableDeferred<Unit>()
        val log = mutableListOf<Int>()
        tasks.submit { gate.await(); log += 0 }
        runCurrent()
        (1..4).forEach { n -> tasks.submit { log += n } }
        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf(0, 3, 4), log)
    }

    @Test
    fun shutdownRefusesNewTasksButFinishesQueuedOnesAndShutdownNowDropsThem() = runTest {
        val tasks = SerialTasks(backgroundScope) {}
        val log = mutableListOf<String>()
        tasks.submit { log += "queued" }
        tasks.shutdown()
        assertFalse(tasks.submit { log += "refused" })
        runCurrent()
        assertEquals(listOf("queued"), log)
        val other = SerialTasks(backgroundScope) {}
        val gate = CompletableDeferred<Unit>()
        other.submit { gate.await(); log += "cancelled" }
        other.submit { log += "dropped" }
        runCurrent()
        assertEquals(1, other.shutdownNow())
        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf("queued"), log)
        assertTrue(other.isShutdown)
    }

    @Test
    fun scheduledTasksRunAfterTheirDelayUnlessCancelled() = runTest {
        val tasks = SerialTasks(backgroundScope) {}
        val log = mutableListOf<String>()
        tasks.schedule(1_000) { log += "late" }
        val cancel = tasks.schedule(500) { log += "cancelled" }
        tasks.schedule(200) { log += "early" }
        advanceTimeBy(300); runCurrent()
        assertEquals(listOf("early"), log)
        cancel()
        advanceTimeBy(1_000); runCurrent()
        assertEquals(listOf("early", "late"), log)
    }
}
