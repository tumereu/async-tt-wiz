package org.asyncttwiz.android

import org.asyncttwiz.automatum.boundary.EngineLog
import org.asyncttwiz.automatum.boundary.InProcessEngineChannel

/**
 * The app's side of the engine boundary (docs/specs/web-client.md §1 item 3; [org.asyncttwiz.automatum.boundary]): the
 * planning on a board of a game on engine state ([EngineBoardValues]: the hand plays' trial casts, the card menus' plays)
 * goes to it as requests and comes back as answers. Android answers them in-process on its engine thread on the board it
 * holds ([inProcess], nothing serialized); the browser's Web Worker answers the same requests sent as JSON strings
 * ([org.asyncttwiz.automatum.boundary.MessageEngineChannel]).
 */
object EngineBoundary {
    /** The boundary's log lines in the app log (problem reports carry it). */
    val log = EngineLog { warn, text -> if (warn) AppLog.warn("engine", text) else AppLog.info("engine", text) }

    /** In-process, on the engine thread: answers at once on the board it is given; work of [SLOW_MS] or more is logged. */
    val inProcess = InProcessEngineChannel(log, SLOW_MS)

    /** In-process work this long or longer is logged (a phone's planning takes well under it). */
    const val SLOW_MS = 500
}
