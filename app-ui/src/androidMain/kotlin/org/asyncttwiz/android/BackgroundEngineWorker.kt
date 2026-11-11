package org.asyncttwiz.android

import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import org.asyncttwiz.appui.isMainThread
import org.asyncttwiz.appui.postToMain
import org.asyncttwiz.appui.postFrameCallback

/** The app's one engine thread (made on first use), results posted to the main looper. */
actual object BackgroundEngineWorker : EngineWorker {
    @Volatile private var thread: Thread? = null
    private val executor by lazy {
        Executors.newSingleThreadExecutor { work -> Thread(work, "atw-engine").apply { isDaemon = true; thread = this } }
    }

    actual override fun run(work: () -> Unit) {
        executor.execute {
            // A failing step never stops the thread: the next event or action carries on (a device that can't follow
            // asks to load the game again)
            try { work() } catch (failure: Throwable) { AppLog.error("engine", "Engine work failed", failure) }
        }
    }

    actual override fun <T> call(work: () -> T): T {
        if (Thread.currentThread() === thread) return work()
        try { return executor.submit<T> { work() }.get() }
        catch (failure: ExecutionException) { throw failure.cause ?: failure }
    }

    actual override fun main(work: () -> Unit) {
        if (isMainThread()) work() else postToMain(work)
    }

    /** The next frame's callback posts [work], which runs once that frame's main-thread work (the recomposition and
     * the drawing it brings) is done. */
    actual override fun frame(work: () -> Unit) = main {
        postFrameCallback { postToMain(work) }
    }

    actual override fun early(work: (release: (() -> Unit)?) -> Unit) {
        if (Thread.currentThread() === thread) { work(null); return }
        val gate = java.util.concurrent.CountDownLatch(1)
        executor.execute {
            try { work { gate.countDown() } } catch (failure: Throwable) { AppLog.error("engine", "Engine work failed", failure) }
            finally { gate.countDown() }
        }
        gate.await()
    }
}
