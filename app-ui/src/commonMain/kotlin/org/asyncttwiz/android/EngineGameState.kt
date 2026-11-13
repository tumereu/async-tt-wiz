package org.asyncttwiz.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Web client step 4c: a value the engine side gives a composable through a callback, as snapshot state ([value] once
 * [arrived]): on Android it arrives before the call that asks returns, so the composition that asked reads it at once and
 * nothing flickers; in the browser it arrives when the engine side answers, and what reads it recomposes then.
 */
class AnsweredValue<T> {
    var arrived by androidx.compose.runtime.mutableStateOf(false)
        private set
    var value by androidx.compose.runtime.mutableStateOf<T?>(null)
        private set

    /** The callback: the answer arrived. */
    fun set(answer: T) { value = answer; arrived = true }
}

/**
 * What the table reads of an [EngineGame] as snapshot state (main thread; web client step 4c: the engine game itself is
 * app-engine's, without Compose): the last published board's version, whether older history is on its way, and the
 * timeline's events and texts. Made on first use and kept with the game ([EngineGame.ui]).
 */
class EngineGameState(game: EngineGame) : EngineGame.Observer {
    /** [EngineGame.Shown.version] of the last board published: a dialog reading it follows every board published
     * (HIS-111: older history coming in) without its parent recomposing. */
    var shownVersion by mutableLongStateOf(game.version)
        private set
    /** Whether a page of older history is on its way (the strip's edge shows it). */
    var historyLoading by mutableStateOf(game.historyLoadingNow)
        private set
    /** The timeline's events and texts (HIS-001–HIS-005 on engine events). */
    val timeline = EngineTimeline()

    override fun shown(version: Long) { shownVersion = version }
    override fun historyLoading(loading: Boolean) { historyLoading = loading }
}

/** [game]'s snapshot state for the table, made on first use (main thread). */
val EngineGame.tableState: EngineGameState
    get() = ui as? EngineGameState ?: EngineGameState(this).also { ui = it; observer = it }

/** [EngineGameState.shownVersion]. */
val EngineGame.shownVersion: Long get() = tableState.shownVersion

/** [EngineGameState.historyLoading]. */
val EngineGame.historyLoading: Boolean get() = tableState.historyLoading

/** [EngineGameState.timeline]. */
val EngineGame.timeline: EngineTimeline get() = tableState.timeline
