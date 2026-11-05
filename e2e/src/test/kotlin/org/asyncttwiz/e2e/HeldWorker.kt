package org.asyncttwiz.e2e

import org.asyncttwiz.android.EngineWorker
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * The app's engine thread ([EngineWorker.Background]), but what it posts to the main thread for the table waits while
 * [holding]: the table still shows the board from before (a newer one is published but hasn't reached the screen),
 * as on a busy phone. [release] (main thread) shows them in order; [observe] runs after each one.
 */
internal class HeldWorker : EngineWorker {
    @Volatile var holding = false
    var observe: () -> Unit = {}
    private val held = ConcurrentLinkedQueue<() -> Unit>()

    override fun run(work: () -> Unit) = EngineWorker.Background.run(work)
    override fun <T> call(work: () -> T): T = EngineWorker.Background.call(work)
    override fun early(work: (release: (() -> Unit)?) -> Unit) = EngineWorker.Background.early(work)
    override fun main(work: () -> Unit) = EngineWorker.Background.main {
        if (holding) held.add(work) else { work(); observe() }
    }
    override fun frame(work: () -> Unit) = EngineWorker.Background.frame { main(work) }

    fun release() {
        holding = false
        while (true) { val work = held.poll() ?: break; work(); observe() }
    }
}
