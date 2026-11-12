package org.asyncttwiz.appui

import kotlinx.coroutines.CoroutineScope

// The JVM's threads, locks and atomics the app's controllers use, under the JVM's own names (docs/specs/web-client.md §1
// item 2). Android: typealiases of the java.util.concurrent types and the very functions the code called before, so it
// runs exactly as it did. The browser has one thread; its pieces come with the web client (§11 steps 3 and 4).

/** Work to run (Android: java.lang.Runnable). */
expect fun interface Runnable {
    fun run()
}

/** Runs work it is given (Android: java.util.concurrent.Executor). */
expect fun interface Executor {
    fun execute(command: Runnable)
}

/** An [Executor] that can be shut down (Android: java.util.concurrent.ExecutorService). */
expect interface ExecutorService : Executor {
    fun shutdown()
    fun shutdownNow(): MutableList<Runnable>
    fun isShutdown(): Boolean
}

/** Android: java.util.concurrent.atomic.AtomicBoolean. */
expect class AtomicBoolean {
    constructor()
    constructor(initialValue: Boolean)
    fun get(): Boolean
    fun set(newValue: Boolean)
    fun getAndSet(newValue: Boolean): Boolean
    fun compareAndSet(expectedValue: Boolean, newValue: Boolean): Boolean
}

/** Android: java.util.concurrent.atomic.AtomicInteger. */
expect class AtomicInteger {
    constructor()
    constructor(initialValue: Int)
    fun get(): Int
    fun set(newValue: Int)
    fun incrementAndGet(): Int
    fun decrementAndGet(): Int
    fun getAndIncrement(): Int
    fun getAndDecrement(): Int
    fun addAndGet(delta: Int): Int
}

/** Android: java.util.concurrent.atomic.AtomicLong. */
expect class AtomicLong {
    constructor()
    constructor(initialValue: Long)
    fun get(): Long
    fun set(newValue: Long)
    fun incrementAndGet(): Long
}

/** Android: java.util.concurrent.atomic.AtomicReference. */
expect class AtomicReference<V> {
    constructor()
    constructor(initialValue: V)
    fun get(): V
    fun set(newValue: V)
    fun getAndSet(newValue: V): V
    fun compareAndSet(expectedValue: V, newValue: V): Boolean
}

/** A new map safe to use from several threads (Android: java.util.concurrent.ConcurrentHashMap). */
expect fun <K : Any, V : Any> concurrentMapOf(): MutableMap<K, V>

/** [key]'s value in this map (one from [concurrentMapOf]), else [defaultValue]'s, put unless another thread put one first,
 * which wins (Android: the JVM's ConcurrentMap.getOrPut). */
expect fun <K : Any, V : Any> MutableMap<K, V>.getOrPutConcurrently(key: K, defaultValue: () -> V): V

/** A new concurrent set (Android: ConcurrentHashMap.newKeySet()). */
expect fun <T> newConcurrentKeySet(): MutableSet<T>

/** Android: java.util.concurrent.CountDownLatch. */
expect class CountDownLatch(count: Int) {
    fun countDown()
    fun await()
}

/** Runs [block] holding [lock]'s monitor (Android: Kotlin's JVM synchronized). */
expect inline fun <R> synchronized(lock: Any, block: () -> R): R

/** Runs [block] to its end on this thread, blocking it (Android: kotlinx.coroutines.runBlocking). The browser's one
 * thread can't block: code that runs there uses [executeSuspending] instead (web client step 3b). */
expect fun <T> runBlocking(block: suspend CoroutineScope.() -> T): T

/**
 * Runs [task] on this executor, its suspending calls (HTTP) waiting there (Android: execute { runBlocking(task) }, the
 * worker's thread blocked while it waits, as the controllers did before). The browser: the executor's tasks run one at
 * a time in order as coroutines on the page's thread, a task that waits holding back the ones queued after it.
 */
expect fun Executor.executeSuspending(task: suspend CoroutineScope.() -> Unit)

/**
 * A new executor with one thread (Android: Executors.newSingleThreadExecutor), whose thread is named [name] (null: the
 * platform's default thread factory) and made a daemon when [daemon] (null: left as the platform makes it).
 */
expect fun newSingleThreadExecutor(name: String? = null, daemon: Boolean? = null): ExecutorService

/** The thread this runs on, to compare by identity (Android: Thread.currentThread()). */
expect fun currentThread(): Any

/** A new executor with one thread named [name] and a queue of [queueSize] that drops its oldest work when full (Android: a
 * ThreadPoolExecutor with an ArrayBlockingQueue and DiscardOldestPolicy). */
expect fun newDiscardOldestExecutor(name: String, queueSize: Int): ExecutorService

/** A new scheduler with one thread named [name] (a daemon when [daemon]; Android: a single-thread
 * ScheduledExecutorService, a task's suspending calls blocking its thread): the returned function runs a task after its
 * milliseconds and gives what cancels it (a task already running is not interrupted). Tasks run one at a time. */
expect fun newSingleThreadScheduler(name: String, daemon: Boolean): (delayMillis: Long, task: suspend () -> Unit) -> () -> Unit

/** A coroutine dispatcher for blocking I/O (Android: Dispatchers.IO). */
expect val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher

/** This executor as a coroutine dispatcher (Android: kotlinx.coroutines' ExecutorService.asCoroutineDispatcher()). */
expect fun ExecutorService.asCoroutineDispatcher(): kotlinx.coroutines.CoroutineDispatcher

/** Makes a method hold its object's (or class's) monitor while it runs (Android: kotlin.jvm.Synchronized). */
@OptIn(ExperimentalMultiplatform::class)
@OptionalExpectation
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
expect annotation class Synchronized()
