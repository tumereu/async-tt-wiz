package org.asyncttwiz.storage

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * One-at-a-time work as coroutines (web client step 3b): the browser's stand-in for a single-thread executor, whose
 * thread it can't have. Tasks run in the order given, each to its end, suspensions included, before the next starts, so
 * a task that waits (HTTP) holds back the ones queued after it, as a worker thread blocked in runBlocking does on Android.
 * [capacity] bounds the tasks waiting to start: when full, the oldest waiting one is dropped (a ThreadPoolExecutor's
 * DiscardOldestPolicy). A task's failure goes to [failed] and the next task still runs. For one thread only.
 */
class SerialTasks(private val scope: CoroutineScope, private val capacity: Int = Int.MAX_VALUE,
    private val failed: (Throwable) -> Unit) {
    private val waiting = ArrayDeque<suspend CoroutineScope.() -> Unit>()
    private var runner: Job? = null

    /** Whether [shutdown] or [shutdownNow] was called: later tasks are refused. */
    var isShutdown = false
        private set

    /** Queues [task]; false (and not run) after a shutdown. */
    fun submit(task: suspend CoroutineScope.() -> Unit): Boolean {
        if (isShutdown) return false
        if (waiting.size >= capacity) waiting.removeFirst()
        waiting.addLast(task)
        if (runner?.isActive != true) runner = scope.launch { drain() }
        return true
    }

    /** Runs [task] after [delayMillis], in turn with the other tasks; the returned function cancels it unless it started. */
    fun schedule(delayMillis: Long, task: suspend CoroutineScope.() -> Unit): () -> Unit {
        var cancelled = false
        val timer = scope.launch {
            delay(delayMillis)
            submit { if (!cancelled) task() }
        }
        return { cancelled = true; timer.cancel() }
    }

    private suspend fun CoroutineScope.drain() {
        while (true) {
            val task = waiting.removeFirstOrNull() ?: return
            try { task() } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: Throwable) { failed(failure) }
        }
    }

    /** Refuses new tasks; the queued ones still run (an ExecutorService's shutdown). */
    fun shutdown() { isShutdown = true }

    /** Refuses new tasks, drops the waiting ones and cancels the running one; how many were dropped. */
    fun shutdownNow(): Int {
        isShutdown = true
        val dropped = waiting.size
        waiting.clear()
        runner?.cancel()
        return dropped
    }
}
