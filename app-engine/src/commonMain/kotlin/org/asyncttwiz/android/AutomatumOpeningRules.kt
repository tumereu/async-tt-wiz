package org.asyncttwiz.android

import org.asyncttwiz.protocol.game.EnginePreferences
import arcanum.engine.Engine
import arcanum.engine.Host
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Continuation
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.setup.SetupStage
import arcanum.engine.state.GameState
import arcanum.engine.state.PlayerId
import org.asyncttwiz.protocol.*

/**
 * The opening with Automatum on (I-D49, TRN-062): the game's rules ([GameRules.STANDARD]) as ATW's own events, the
 * host's setup before the engine's game begins. Both players' opening hands are drawn as the game starts
 * ([StartRules.initialize]); each then takes London mulligans (CR 103.5) or keeps, both at the same time, and the first
 * turn begins once both have kept (TRN-057). The engine asks for the cards a mulligan puts on the bottom right after it
 * (CR 103.5: from the second mulligan on), so keeping never owes anything.
 */
object AutomatumOpening {
    val rules = GameRules.STANDARD

    /**
     * TRN-062, CR 103.5 (owner problem report 5DDSKK): how many cards [viewer] puts on the bottom of their library now,
     * right after a mulligan: the engine's bottom decision is open for them, or theirs to give ahead of the other player's
     * ([Input.SetupAnswer]); 0 otherwise. Engine thread (or any thread: [s] is never changed).
     */
    fun bottomOwed(s: GameState, viewer: String): Int {
        if (s.turn.number != 0) return 0
        val me = PlayerId(viewer)
        val pending = s.pending ?: return 0
        val stage = (pending.continuation as? Continuation.Setup)?.stage as? SetupStage.Bottom ?: return 0
        if (me !in stage.mulligans.taking || me in stage.done) return 0
        (pending.decision as? Decision.ChooseObjects)?.takeIf { Host.decider(s, it.player) == me }?.let { return it.min }
        return Engine.options(s, me, EnginePreferences.of(s)).firstNotNullOfOrNull { input ->
            ((input as? Input.SetupAnswer)?.takeIf { it.player == me }?.answer as? Answer.Objects)?.chosen?.size
        } ?: 0
    }

    /** TRN-062: the cards [play]'s viewer puts on the bottom now ([bottomOwed], worked out with the board). */
    fun bottomOwed(play: TableBoard): Int = play.values?.openingBottom ?: 0

    /** The Keep / Bottom screen's title, e.g. "Mulligan · put 1 on the bottom". */
    fun title(owed: Int) = "Mulligan · put $owed on the bottom"
    fun hint(owed: Int) = if (owed == 1) "Tap the card to put on the bottom of your library, then OK."
        else "Tap the $owed cards to put on the bottom of your library, then OK."
}
