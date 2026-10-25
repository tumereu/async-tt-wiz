package org.asyncttwiz.android

import java.util.concurrent.Executor

/** At most one running and one pending review per consumer. Superseded work is never executed. */
internal class LatestReview(private val executor: Executor) {
    private val pending = linkedMapOf<String, () -> Unit>()
    private var scheduled = false

    @Synchronized fun submit(key: String, action: () -> Unit) {
        pending[key] = action
        if (scheduled) return
        scheduled = true
        executor.execute(::drain)
    }

    @Synchronized fun cancel(key: String) { pending.remove(key) }
    @Synchronized fun clear() { pending.clear() }

    private fun drain() {
        val action = synchronized(this) { pending.keys.firstOrNull()?.let { pending.remove(it) } }
        try { action?.invoke() } finally {
            synchronized(this) {
                scheduled = false
                if (pending.isNotEmpty()) {
                    scheduled = true
                    executor.execute(::drain)
                }
            }
        }
    }
}
