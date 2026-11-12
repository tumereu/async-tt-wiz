package org.asyncttwiz.appui

import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher as jvmAsCoroutineDispatcher

actual typealias Runnable = java.lang.Runnable
actual typealias Executor = java.util.concurrent.Executor
actual typealias ExecutorService = java.util.concurrent.ExecutorService
actual typealias AtomicBoolean = java.util.concurrent.atomic.AtomicBoolean
actual typealias AtomicInteger = java.util.concurrent.atomic.AtomicInteger
actual typealias AtomicLong = java.util.concurrent.atomic.AtomicLong
actual typealias AtomicReference<V> = java.util.concurrent.atomic.AtomicReference<V>
actual typealias CountDownLatch = java.util.concurrent.CountDownLatch

actual fun <K : Any, V : Any> concurrentMapOf(): MutableMap<K, V> = java.util.concurrent.ConcurrentHashMap()

actual fun <K : Any, V : Any> MutableMap<K, V>.getOrPutConcurrently(key: K, defaultValue: () -> V): V =
    (this as java.util.concurrent.ConcurrentMap<K, V>).getOrPut(key, defaultValue)

actual fun <T> newConcurrentKeySet(): MutableSet<T> = java.util.concurrent.ConcurrentHashMap.newKeySet()

@Suppress("NOTHING_TO_INLINE")
actual inline fun <R> synchronized(lock: Any, block: () -> R): R = kotlin.synchronized(lock, block)

actual fun <T> runBlocking(block: suspend CoroutineScope.() -> T): T = kotlinx.coroutines.runBlocking(block = block)

actual fun Executor.executeSuspending(task: suspend CoroutineScope.() -> Unit) =
    execute { kotlinx.coroutines.runBlocking(block = task) }

actual fun newSingleThreadExecutor(name: String?, daemon: Boolean?): ExecutorService = when {
    name == null -> Executors.newSingleThreadExecutor()
    daemon == null -> Executors.newSingleThreadExecutor { Thread(it, name) }
    else -> Executors.newSingleThreadExecutor { Thread(it, name).apply { isDaemon = daemon } }
}

actual fun currentThread(): Any = Thread.currentThread()

actual fun newDiscardOldestExecutor(name: String, queueSize: Int): ExecutorService =
    java.util.concurrent.ThreadPoolExecutor(1, 1, 0, java.util.concurrent.TimeUnit.SECONDS,
        java.util.concurrent.ArrayBlockingQueue<Runnable>(queueSize), { Thread(it, name) },
        java.util.concurrent.ThreadPoolExecutor.DiscardOldestPolicy())

actual fun newSingleThreadScheduler(name: String, daemon: Boolean): (delayMillis: Long, task: suspend () -> Unit) -> () -> Unit =
    Executors.newSingleThreadScheduledExecutor { Thread(it, name).apply { isDaemon = daemon } }.let { marks ->
        { delay, task ->
            val scheduled = marks.schedule(Runnable { kotlinx.coroutines.runBlocking { task() } }, delay,
                java.util.concurrent.TimeUnit.MILLISECONDS)
            val cancel: () -> Unit = { scheduled.cancel(false) }
            cancel
        }
    }

actual val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher get() = kotlinx.coroutines.Dispatchers.IO

actual fun ExecutorService.asCoroutineDispatcher(): kotlinx.coroutines.CoroutineDispatcher = jvmAsCoroutineDispatcher()

actual typealias Synchronized = kotlin.jvm.Synchronized
