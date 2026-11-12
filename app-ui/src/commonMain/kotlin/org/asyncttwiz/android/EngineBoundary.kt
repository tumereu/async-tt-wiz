package org.asyncttwiz.android

import org.asyncttwiz.automatum.boundary.EngineChannel
import org.asyncttwiz.automatum.boundary.EngineLog
import org.asyncttwiz.automatum.boundary.InProcessEngineChannel

/**
 * The app's side of the engine boundary (docs/specs/web-client.md §1 item 3; [org.asyncttwiz.automatum.boundary]): the
 * planning on a board of a game on engine state ([EngineBoardValues]: the hand plays' trial casts, the card menus' plays)
 * goes to it as requests and comes back as answers. Android answers them in-process on its engine thread on the board it
 * holds ([inProcess], nothing serialized); the browser's Web Worker answers the same requests sent as JSON strings
 * ([remote]: [org.asyncttwiz.automatum.boundary.MessageEngineChannel]).
 */
object EngineBoundary {
    /** The boundary's log lines in the app log (problem reports carry it). */
    val log = EngineLog { warn, text -> if (warn) AppLog.warn("engine", text) else AppLog.info("engine", text) }

    /** In-process, on the engine thread: answers at once on the board it is given; work of [SLOW_MS] or more is logged. */
    val inProcess = InProcessEngineChannel(log, SLOW_MS)

    /** In-process work this long or longer is logged (a phone's planning takes well under it). */
    const val SLOW_MS = 500

    /**
     * The browser: the engine's Web Worker, installed when the page starts ([useRemote]). A board's plays are then asked
     * of it ([EngineBoardValues.askPlays]) and arrive later on the page's thread. Null on Android (planned in-process on
     * the engine thread) and once the worker has failed ([lost]).
     */
    var remote: EngineChannel? = null
        private set

    /** The board of each game that waits for answers from [remote] (the page's thread): woken if it is lost, or when a
     * newer board of its game asks (the worker's answers about an older board are dropped as stale), so nothing waits for
     * good and nothing is kept. */
    private val waits = LinkedHashMap<String, () -> Unit>()

    /** Installs the web page's worker channel [channel]. */
    fun useRemote(channel: EngineChannel) {
        remote = channel
        log.log(false, "Engine boundary: planning goes to the engine's Web Worker")
    }

    /** The worker failed: planning goes back in-process on the page, and every wait for its answers ends. */
    fun lost(why: String) {
        if (remote == null) return
        remote = null
        log.log(true, "Engine boundary: the worker is lost ($why): planning in-process on the page from now on")
        val woken = waits.values.toList()
        waits.clear()
        woken.forEach { it() }
    }

    /** A board of [game] waits for answers: [wake] runs if [remote] is lost, or another board of [game] waits, before
     * [stopWaiting] removes it. */
    internal fun waiting(game: String, wake: () -> Unit) {
        val before = waits.put(game, wake)
        if (before != null && before !== wake) before()
    }

    internal fun stopWaiting(game: String, wake: () -> Unit) { if (waits[game] === wake) waits.remove(game) }

    /** Tests: back to no worker. */
    fun reset() { remote = null; waits.clear() }
}
