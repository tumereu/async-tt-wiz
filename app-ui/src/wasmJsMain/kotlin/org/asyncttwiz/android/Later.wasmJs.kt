package org.asyncttwiz.android

import org.asyncttwiz.appui.postFrameCallback
import org.asyncttwiz.appui.postToMain

/**
 * The engine on the page's one thread, in order, until the web table moves it into a Web Worker (docs/specs/web-client.md
 * §11 step 4). The web shell (step 3) shows no table, but a game opened from the lobby still loads its engine state.
 */
actual object BackgroundEngineWorker : EngineWorker {
    // Inline, like EngineWorker.Direct, so calls keep their order; results still reach the main thread's queue
    actual override fun run(work: () -> Unit) = work()
    actual override fun <T> call(work: () -> T): T = work()
    actual override fun main(work: () -> Unit) = postToMain(work)
    actual override fun frame(work: () -> Unit) = postFrameCallback(work)
    actual override fun early(work: (release: (() -> Unit)?) -> Unit) = work(null)
}
