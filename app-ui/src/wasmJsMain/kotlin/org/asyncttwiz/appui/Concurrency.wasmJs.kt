package org.asyncttwiz.appui

import kotlinx.coroutines.CoroutineScope

// The browser runs the page on one thread: the atomics and maps are plain values; executors and blocking come with the
// web client (docs/specs/web-client.md §11 steps 3 and 4).

actual fun interface Runnable {
    actual fun run()
}

actual fun interface Executor {
    actual fun execute(command: Runnable)
}

actual interface ExecutorService : Executor {
    actual fun shutdown()
    actual fun shutdownNow(): MutableList<Runnable>
    actual fun isShutdown(): Boolean
}

actual class AtomicBoolean actual constructor(initialValue: Boolean) {
    private var value = initialValue
    actual constructor() : this(false)
    actual fun get(): Boolean = value
    actual fun set(newValue: Boolean) { value = newValue }
    actual fun getAndSet(newValue: Boolean): Boolean = value.also { value = newValue }
    actual fun compareAndSet(expectedValue: Boolean, newValue: Boolean): Boolean =
        (value == expectedValue).also { if (it) value = newValue }
}

actual class AtomicInteger actual constructor(initialValue: Int) {
    private var value = initialValue
    actual constructor() : this(0)
    actual fun get(): Int = value
    actual fun set(newValue: Int) { value = newValue }
    actual fun incrementAndGet(): Int = ++value
    actual fun decrementAndGet(): Int = --value
    actual fun getAndIncrement(): Int = value++
    actual fun getAndDecrement(): Int = value--
    actual fun addAndGet(delta: Int): Int { value += delta; return value }
}

actual class AtomicLong actual constructor(initialValue: Long) {
    private var value = initialValue
    actual constructor() : this(0L)
    actual fun get(): Long = value
    actual fun set(newValue: Long) { value = newValue }
    actual fun incrementAndGet(): Long = ++value
}

actual class AtomicReference<V> private constructor(private var value: V, @Suppress("UNUSED_PARAMETER") unit: Unit) {
    actual constructor(initialValue: V) : this(initialValue, Unit)
    @Suppress("UNCHECKED_CAST")
    actual constructor() : this(null as V, Unit)
    actual fun get(): V = value
    actual fun set(newValue: V) { value = newValue }
    actual fun getAndSet(newValue: V): V = value.also { value = newValue }
    actual fun compareAndSet(expectedValue: V, newValue: V): Boolean =
        (value === expectedValue).also { if (it) value = newValue }
}

actual fun <K : Any, V : Any> concurrentMapOf(): MutableMap<K, V> = LinkedHashMap()

actual fun <K : Any, V : Any> MutableMap<K, V>.getOrPutConcurrently(key: K, defaultValue: () -> V): V = getOrPut(key, defaultValue)

actual fun <T> newConcurrentKeySet(): MutableSet<T> = LinkedHashSet()

actual class CountDownLatch actual constructor(count: Int) {
    private var count = count
    actual fun countDown() { if (count > 0) count-- }
    actual fun await() { check(count == 0) { "The browser's one thread can't wait" } }
}

@Suppress("NOTHING_TO_INLINE")
actual inline fun <R> synchronized(lock: Any, block: () -> R): R = block()

actual fun <T> runBlocking(block: suspend CoroutineScope.() -> T): T =
    throw NotImplementedError("web client step 3/4: the browser can't block its thread")

actual fun newSingleThreadExecutor(name: String?, daemon: Boolean?): ExecutorService =
    throw NotImplementedError("web client step 3/4")

private val pageThread = Any()

actual fun currentThread(): Any = pageThread

actual fun newDiscardOldestExecutor(name: String, queueSize: Int): ExecutorService =
    throw NotImplementedError("web client step 3/4")

actual fun newSingleThreadScheduler(name: String, daemon: Boolean): (delayMillis: Long, task: () -> Unit) -> () -> Unit =
    throw NotImplementedError("web client step 3/4")

actual val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher get() = kotlinx.coroutines.Dispatchers.Default

actual fun ExecutorService.asCoroutineDispatcher(): kotlinx.coroutines.CoroutineDispatcher =
    throw NotImplementedError("web client step 3/4")
