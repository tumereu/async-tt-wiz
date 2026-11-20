package org.asyncttwiz.android

import org.asyncttwiz.appui.postFrameCallback
import org.asyncttwiz.appui.postToMain

/**
 * The engine on the page's one thread, in order, where the engine's Web Worker doesn't take it (a game opened from the
 * lobby still loads its engine state on the page).
 */
actual object BackgroundEngineWorker : EngineWorker {
    // Inline, like EngineWorker.Direct, so calls keep their order; results still reach the main thread's queue
    actual override fun run(work: () -> Unit) = work()
    actual override fun <T> call(work: () -> T, done: (T) -> Unit) = done(work())
    actual override fun main(work: () -> Unit) = postToMain(work)
    actual override fun frame(work: () -> Unit) = postFrameCallback(work)
    actual override fun early(work: (release: (() -> Unit)?) -> Unit, released: () -> Unit) { work(null); released() }
}
