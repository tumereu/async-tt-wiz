package org.asyncttwiz.android

import org.asyncttwiz.protocol.game.EnginePreferences
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
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

/** TRN-062 (owner problem report 5DDSKK): why Mulligan and Keep are greyed while the bottom after a mulligan is open. */
fun bottomFirst(owed: Int) = if (owed == 1) "Put the card on the bottom first." else "Put the $owed cards on the bottom first."

/**
 * The Keep / Bottom screen (I-D48, I-D49, STK-043) right after a mulligan that puts cards on the bottom (TRN-062, owner
 * problem report 5DDSKK): the viewer's hand as cards, all in Keep at first; a tap moves a card to Bottom (and back); OK is
 * enabled with exactly the owed number in Bottom and puts them on the bottom of the library. Mulligan and Keep are greyed
 * until then. Docked above the hand like every Automatum panel.
 */
@Composable
fun OpeningKeepHost(toolkit: TableToolkitController, play: TableBoard, scene: TableScene?) {
    val viewer = toolkit.viewer
    val owed = if (AutomatumDrags.opening(play.board, viewer)) AutomatumOpening.bottomOwed(play) else 0
    if (owed <= 0) return
    val hand = play.board.containers.singleOrNull { it.zone == PlayZone.HAND && it.accountId == viewer }?.cards.orEmpty()
    val state = remember(hand) { TwoAreas(hand) }
    val inspector = remember(hand) { ChoiceInspector() }
    var hidden by remember { mutableStateOf(false) }
    val cards = hand.associateWith { choiceCard(play, viewer, it, it, shown = true, fallback = "Card") }
    AboveHand(handRow(scene, viewer)) {
        // TRN-063 (owner playtest 2026-11-09): its OK is the main button
        val ready = state.second.size == owed && toolkit.enabled()
        val onMain = MainButtonAction(MainAction("OK", Icons.Outlined.Check, refusal = AutomatumOpening.hint(owed).takeIf { !ready },
            tooltip = AutomatumOpening.title(owed)) {
            AppLog.info("game", "Keep or bottom: OK on the main button with ${state.second.size} of $owed in Bottom")
            toolkit.confirmBottom(state.second)
        })
        AutomatumDockPanel("Keep or bottom", hidden, { hidden = !hidden }, "mulligan panel", buttons = {
            if (!onMain) ChoiceButton("OK", Icons.Outlined.Check, enabled = ready, primary = true) { toolkit.confirmBottom(state.second) }
        }) {
            Text(AutomatumOpening.title(owed), fontWeight = FontWeight.Bold, fontSize = 15.sp)
            ChoiceHintText(AutomatumOpening.hint(owed))
            TwoAreaChoice(state, cards, ChoiceArea("Keep", areaIcon("Keep")), ChoiceArea("Bottom", areaIcon("Bottom")), inspector,
                Modifier.fillMaxWidth())
        }
    }
    ChoiceInspection(inspector, play, viewer)
}
