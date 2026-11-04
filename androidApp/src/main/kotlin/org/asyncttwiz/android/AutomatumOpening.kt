package org.asyncttwiz.android

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.asyncttwiz.protocol.*

/**
 * The opening with Automatum on (I-D49, TRN-062): the game's rules ([GameRules.STANDARD]) as ATW's own events, the
 * host's setup before the engine's game begins. Both players' opening hands are drawn as the game starts
 * ([StartRules.initialize]); each then takes London mulligans (CR 103.5) or keeps, both at the same time, and the first
 * turn begins once both have kept (TRN-057).
 */
internal object AutomatumOpening {
    val rules = GameRules.STANDARD

    private fun own(board: BoardState, viewer: String, zone: PlayZone) =
        board.containers.singleOrNull { it.zone == zone && it.accountId == viewer }

    /** The mulligans [viewer] took in this opening. */
    fun taken(board: BoardState, viewer: String): Int = board.players.firstOrNull { it.accountId == viewer }?.mulligans ?: 0

    /** The cards [viewer] puts on the bottom of their library on keeping (at most their hand). */
    fun owed(board: BoardState, viewer: String): Int =
        minOf(rules.owed(taken(board, viewer)), own(board, viewer, PlayZone.HAND)?.cards?.size ?: 0)

    /**
     * [viewer]'s mulligan: the hand back into the library, shuffled with [seed], the opening hand drawn again; counted
     * ([MtgAction.SetMulligans]) unless the hand was empty (Automatum switched on before the opening hand was drawn: this
     * draws it). Null when no further mulligan may be taken.
     */
    fun mulligan(board: BoardState, viewer: String, seed: String): List<PlayAction>? {
        val taken = taken(board, viewer)
        if (!rules.canMulligan(taken)) return null
        val library = own(board, viewer, PlayZone.LIBRARY) ?: return null
        val hand = own(board, viewer, PlayZone.HAND) ?: return null
        val draw = PlayAction.Mulligan(library.id, hand.id, hand.cards.toList(), rules.openingHand, seed)
        return if (hand.cards.isEmpty()) listOf(draw) else listOf(draw, PlayAction.Mtg(MtgAction.SetMulligans(taken, taken + 1)))
    }

    /** [viewer] keeps: [bottom] (exactly the [owed] cards, from their hand) on the bottom of their library in this order,
     * then Ready (their opening status cleared). Null when [bottom] doesn't fit. */
    fun keep(board: BoardState, viewer: String, bottom: List<String>): List<PlayAction>? {
        val library = own(board, viewer, PlayZone.LIBRARY) ?: return null
        val hand = own(board, viewer, PlayZone.HAND) ?: return null
        if (bottom.size != owed(board, viewer) || bottom.distinct().size != bottom.size || !hand.cards.containsAll(bottom)) return null
        return bottom.map { PlayAction.MoveCard(it, MoveSource(hand.id), MoveDestination(library.id, MoveInsertion.BOTTOM)) } +
            PlayAction.SetStatus("")
    }

    /** The Keep / Bottom screen's title, e.g. "Mulligan · put 1 on the bottom". */
    fun title(owed: Int) = "Mulligan · put $owed on the bottom"
    fun hint(owed: Int) = if (owed == 1) "Tap the card to put on the bottom of your library, then OK."
        else "Tap the $owed cards to put on the bottom of your library, then OK."
}

/**
 * The Keep / Bottom screen (I-D48, I-D49) after Keep when cards are owed to the bottom: the viewer's hand as cards, all
 * in Keep at first; a tap moves a card to Bottom (and back); OK is enabled with exactly the owed number in Bottom, puts
 * them on the bottom of the library and sets Ready. Docked above the hand like every Automatum panel.
 */
@Composable
internal fun OpeningKeepHost(toolkit: TableToolkitController, play: TableBoard, scene: TableScene?) {
    val viewer = toolkit.viewer
    val open = toolkit.keeping && AutomatumDrags.opening(play.board, viewer)
    LaunchedEffect(open, toolkit.keeping) { if (toolkit.keeping && !open) toolkit.closeKeep() }
    if (!open) return
    val hand = play.board.containers.singleOrNull { it.zone == PlayZone.HAND && it.accountId == viewer }?.cards.orEmpty()
    val owed = AutomatumOpening.owed(play.board, viewer)
    val state = remember(hand) { TwoAreas(hand) }
    val inspector = remember(hand) { ChoiceInspector() }
    var hidden by remember { mutableStateOf(false) }
    val cards = hand.associateWith { choiceCard(play, viewer, it, it, shown = true, fallback = "Card") }
    AboveHand(handRow(scene, viewer)) {
        AutomatumDockPanel("Keep or bottom", hidden, { hidden = !hidden }, "mulligan panel", buttons = {
            ChoiceButton("Back", Icons.Outlined.Close) { toolkit.closeKeep() }
            ChoiceButton("OK", Icons.Outlined.Check, enabled = state.second.size == owed && toolkit.enabled(), primary = true) {
                toolkit.confirmKeep(state.second)
            }
        }) {
            Text(AutomatumOpening.title(owed), fontWeight = FontWeight.Bold, fontSize = 15.sp)
            ChoiceHintText(AutomatumOpening.hint(owed))
            TwoAreaChoice(state, cards, ChoiceArea("Keep", areaIcon("Keep")), ChoiceArea("Bottom", areaIcon("Bottom")), inspector,
                Modifier.fillMaxWidth())
        }
    }
    ChoiceInspection(inspector, play, viewer)
}
