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
