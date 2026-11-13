package org.asyncttwiz.automatum.boundary

/**
 * Web client step 4c part 3: the page's ledger of one game's requests to its device on the engine side
 * ([DeviceMessages]): each request numbered ([next]), its callback kept until its answer ([done], [failed]). The engine
 * side answers in the order it was asked (its one thread, in order), so an answer that skips older calls is logged; the
 * skipped ones stay waiting. Once the game closes on the page ([close]), every waiting call gets its fallback result
 * ([ask]'s `failed`) and later answers are dropped, logged. Not thread-safe: the page's thread only.
 */
class DeviceCalls(private val log: EngineLog) {
    private class Call(val what: String, val failed: () -> DeviceResult, val done: (DeviceResult) -> Unit)

    private var last = 0L
    private val waiting = LinkedHashMap<Long, Call>()
    private var closed: String? = null

    /** Calls asked and not answered yet. */
    val awaited: Int get() = waiting.size

    /** The next request number. */
    fun next(): Long = ++last

    /** Request [id] ([what]: its name for the log) waits for its answer: [done] gets it, or [failed]'s result when it fails or
     * the game closes first. */
    fun ask(id: Long, what: String, failed: () -> DeviceResult, done: (DeviceResult) -> Unit) {
        closed?.let { why ->
            log.log(true, "Device boundary: $what #$id asked after the game closed ($why)")
            done(failed()); return
        }
        waiting[id] = Call(what, failed, done)
    }

    /** Request [id]'s answer: its callback runs with [result]. False when nothing waited for it (closed, or unknown). */
    fun done(id: Long, result: DeviceResult): Boolean {
        val call = take(id, "answered") ?: return false
        call.done(result)
        return true
    }

    /** Request [id] failed on the engine side ([problem]): its callback gets its fallback result. */
    fun failed(id: Long, problem: String): Boolean {
        val call = take(id, "failed") ?: return false
        log.log(true, "Device boundary: ${call.what} #$id failed on the engine side: $problem")
        call.done(call.failed())
        return true
    }

    private fun take(id: Long, how: String): Call? {
        val call = waiting.remove(id)
        if (call == null) {
            log.log(closed == null, "Device boundary: request #$id $how" +
                (closed?.let { ", after the game closed ($it): dropped" } ?: ", but nothing waits for it"))
            return null
        }
        val skipped = waiting.keys.filter { it < id }
        if (skipped.isNotEmpty()) log.log(true, "Device boundary: ${call.what} #$id $how before older requests ${skipped.joinToString()}")
        return call
    }

    /** The game closed on the page ([why]): every waiting call gets its fallback result, later answers are dropped. */
    fun close(why: String) {
        if (closed != null) return
        closed = why
        val left = waiting.values.toList()
        waiting.clear()
        if (left.isNotEmpty()) log.log(false, "Device boundary: game closed ($why) with ${left.size} requests waiting: " +
            left.joinToString { it.what })
        left.forEach { it.done(it.failed()) }
    }
}
