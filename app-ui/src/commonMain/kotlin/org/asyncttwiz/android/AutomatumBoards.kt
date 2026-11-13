package org.asyncttwiz.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import arcanum.api.CardDefinition
import org.asyncttwiz.automatum.CardPlays
import org.asyncttwiz.automatum.CombatShown
import org.asyncttwiz.automatum.ComputedBoard
import org.asyncttwiz.automatum.HandPlays
import org.asyncttwiz.automatum.SharedFlow
import org.asyncttwiz.protocol.SubmittedDefinition
import org.asyncttwiz.protocol.TurnPosition
import org.asyncttwiz.storage.ParsedCard
import org.asyncttwiz.appui.newSingleThreadExecutor
import kotlin.concurrent.Volatile
import org.asyncttwiz.appui.simpleClassName
import org.asyncttwiz.appui.concurrentMapOf
import org.asyncttwiz.appui.newConcurrentKeySet
import org.asyncttwiz.appui.asCoroutineDispatcher

/** The engine's view of the shown board; [ComputedBoard.NONE] without one. */
val LocalComputedBoard = staticCompositionLocalOf { ComputedBoard.NONE }

/**
 * [play]'s board as Automatum computes it for [viewer]: a board of a game on engine state comes with its computed values
 * (and, once worked out, the hand plays) from the engine thread (R3), shown at once, nothing worked out here.
 */
@Composable
fun rememberComputedBoard(play: TableBoard, viewer: String): ComputedBoard {
    val values = play.values?.takeIf { it.viewer == viewer } ?: return ComputedBoard.NONE
    SideEffect { drawnForProbe(play, viewer) }
    return values.computed
}

/** SYS-030: the table drew [play] for [viewer]: an own action's probe counts its board's computed values and hand plays
 * (once worked out) arriving. */
fun drawnForProbe(play: TableBoard, viewer: String) {
    val values = play.values?.takeIf { it.viewer == viewer } ?: return
    OwnActionProbes.computed(play.board)
    if (values.handPlays != null) OwnActionProbes.plays(play.board)
}
