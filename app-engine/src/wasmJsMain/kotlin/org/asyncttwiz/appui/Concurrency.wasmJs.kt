package org.asyncttwiz.appui

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.asyncttwiz.storage.SerialTasks

// The browser runs the page on one thread (web client step 3b): the atomics and maps are plain values, locks run their
// block, and executors are queues of coroutines on the page's event loop ([PageExecutor]); nothing blocks. The engine's
// own thread becomes a Web Worker in step 4.

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

/** The browser's one thread can't block: code that runs there uses [executeSuspending] (web client step 3b). */
actual fun <T> runBlocking(block: suspend CoroutineScope.() -> T): T =
    throw UnsupportedOperationException("The browser can't block its thread: use executeSuspending")

/** Where the page's background work runs: its one thread's event loop. A failure that escapes reaches [uncaught]. */
val pageScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default +
    CoroutineExceptionHandler { _, failure -> uncaught("coroutine", failure) })

/** What the page does with a failure nothing caught (the crash reports install it: web client step 3b). */
var uncaughtFailureHandler: (where: String, failure: Throwable) -> Unit = { where, failure ->
    println("Uncaught failure in $where: ${failure.stackTraceToString()}")
}

fun uncaught(where: String, failure: Throwable) = try { uncaughtFailureHandler(where, failure) } catch (_: Throwable) {}

/** A single-thread executor in the browser: its tasks run one at a time in order on the page's thread ([SerialTasks]). */
class PageExecutor(private val name: String, queueSize: Int = Int.MAX_VALUE) : ExecutorService {
    private val tasks = SerialTasks(pageScope, queueSize) { uncaught(name, it) }
    override fun execute(command: Runnable) { submit { command.run() } }
    fun submit(task: suspend CoroutineScope.() -> Unit) {
        // A shut down Java executor refuses work by throwing; the browser's drops it, as nothing here catches that.
        if (!tasks.submit(task)) println("Executor $name is shut down: a task was dropped")
    }
    override fun shutdown() = tasks.shutdown()
    override fun shutdownNow(): MutableList<Runnable> { tasks.shutdownNow(); return mutableListOf() }
    override fun isShutdown(): Boolean = tasks.isShutdown
}

actual fun Executor.executeSuspending(task: suspend CoroutineScope.() -> Unit) {
    if (this is PageExecutor) submit(task) else execute { pageScope.launch(start = CoroutineStart.UNDISPATCHED, block = task) }
}

actual fun newSingleThreadExecutor(name: String?, daemon: Boolean?): ExecutorService = PageExecutor(name ?: "executor")

private val pageThread = Any()

actual fun currentThread(): Any = pageThread

actual fun newDiscardOldestExecutor(name: String, queueSize: Int): ExecutorService = PageExecutor(name, queueSize)

actual fun newSingleThreadScheduler(name: String, daemon: Boolean): (delayMillis: Long, task: suspend () -> Unit) -> () -> Unit {
    val tasks = SerialTasks(pageScope) { uncaught(name, it) }
    return { delay, task -> tasks.schedule(delay) { task() } }
}

actual val ioDispatcher: CoroutineDispatcher get() = Dispatchers.Default

/** The executor's tasks as a dispatcher: each dispatched block is one of its tasks. */
actual fun ExecutorService.asCoroutineDispatcher(): CoroutineDispatcher = object : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: kotlinx.coroutines.Runnable) = execute { block.run() }
}
