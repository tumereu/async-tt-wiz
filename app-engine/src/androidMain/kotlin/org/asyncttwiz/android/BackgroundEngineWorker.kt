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

    /** The main thread waits for the engine thread here, as it always has (SYS-025): [done] runs before this returns, in
     * the same frame, so the press's result, its note and its board need no frame of their own. */
    actual override fun <T> call(work: () -> T, done: (T) -> Unit) {
        if (Thread.currentThread() === thread) { done(work()); return }
        val result = try { executor.submit<T> { work() }.get() }
        catch (failure: ExecutionException) { throw failure.cause ?: failure }
        done(result)
    }

    actual override fun main(work: () -> Unit) {
        if (isMainThread()) work() else postToMain(work)
    }

    /** The next frame's callback posts [work], which runs once that frame's main-thread work (the recomposition and
     * the drawing it brings) is done. */
    actual override fun frame(work: () -> Unit) = main {
        postFrameCallback { postToMain(work) }
    }

    /** The main thread waits only until the action's release (or its end), then runs [released] before returning. */
    actual override fun early(work: (release: (() -> Unit)?) -> Unit, released: () -> Unit) {
        if (Thread.currentThread() === thread) { work(null); released(); return }
        val gate = java.util.concurrent.CountDownLatch(1)
        executor.execute {
            try { work { gate.countDown() } } catch (failure: Throwable) { AppLog.error("engine", "Engine work failed", failure) }
            finally { gate.countDown() }
        }
        gate.await()
        released()
    }
}
