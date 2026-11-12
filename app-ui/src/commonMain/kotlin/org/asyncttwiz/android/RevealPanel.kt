package org.asyncttwiz.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.game.GameDevice

/** The test tag of the reveal panel's dismiss button (HIS-110). */
const val REVEAL_OK_TAG = "reveal-ok"

/** HIS-110, TRN-063 (owner, 2026-11-07): why the main button is greyed and game actions are refused while a reveal panel
 * is open, until its OK. */
const val REVEAL_BUSY = "Dismiss the reveal first: tap OK."

/**
 * HIS-114 (owner playtest 2026-11-09): the reveal panel's title for cards that left a public pile for a hidden zone, moved
 * by [name]: "Bob returned Storm Spark from their graveyard to their hand", "… put 2 cards from exile into their library";
 * [whose]: whose pile and zone they are, "your" for the viewer's, "their" for the mover's own, else the owner's name
 * with "'s" ("Carol's", with three or more players), so the title names whose cards they were.
 */
fun pileTitle(name: String, cards: List<String>, left: GameDevice.LeftPile, whose: String): String {
    val their = whose
    val what = cards.singleOrNull() ?: "${cards.size} cards"
    val pile = if (left.from == arcanum.api.Zone.Exile) "exile" else "$their graveyard"
    return if (left.to == arcanum.api.Zone.Hand) "$name returned $what from $pile to $their hand"
        else "$name put $what from $pile into $their library"
}

/**
 * HIS-110 (owner 2026-11-09): a reveal, the other player's or your own ([name] "You"), docked above the hand like every
 * Automatum panel (STK-044): "<name> revealed · <source>", the revealed cards as cards at the Hand card size (holding one inspects it,
 * TBL-104), the source's card at the right, the eye, and OK, which [dismiss]es it (the table then plays on). The cards
 * were revealed to the viewer, so they show face up wherever they are now. Until OK (the main button, TRN-063) game actions are
 * refused as while a choice is open ([REVEAL_BUSY]). HIS-114: cards that left a public pile for a hidden zone show in it too,
 * titled by [pileTitle], [ownerName] naming the pile's owner where that is neither the viewer nor the mover.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RevealPanel(play: TableBoard, viewer: String, reveal: GameDevice.Reveal, name: String, ownerName: String, dismiss: () -> Unit) {
    var hidden by remember(reveal) { mutableStateOf(false) }
    val inspector = remember(reveal) { ChoiceInspector() }
    val source = reveal.source?.let { choiceCard(play, viewer, "reveal-source/${it.value}", it.value, true, reveal.sourceName ?: "") }
    val sourceName = reveal.sourceName ?: source?.name?.takeIf { source.cardId != null }
    val cards = reveal.cards.mapIndexed { i, card -> choiceCard(play, viewer, "reveal/$i/${card.value}", card.value, true, "A card") }
    val title = reveal.left?.let { left ->
        val whose = when {
            left.owner.value == viewer -> "your"
            left.owner == reveal.by -> "their"
            else -> "$ownerName's"
        }
        pileTitle(name, cards.map { it.name }, left, whose)
    }
        ?: ("$name revealed" + (sourceName?.let { " · $it" } ?: ""))
    // HIS-110 (problem 2026-11-12, the wrong player named): what the panel says, with who revealed by the event
    LaunchedEffect(reveal) {
        AppLog.info("game", "Reveal panel in ${reveal.event}: \"$title\" (by ${reveal.by.value.take(8)}, viewer ${viewer.take(8)}" +
            (reveal.left?.let { ", pile owner ${it.owner.value.take(8)}" } ?: "") + ")")
    }
    CompositionLocalProvider(LocalChoiceFrame provides ChoiceFrame(viewer)) {
        // TRN-063 (owner playtest 2026-11-09): its OK is the main button (its own button where there is none)
        val onMain = MainButtonAction(MainAction("OK", Icons.Outlined.Check, tooltip = "Dismiss the reveal") { dismiss() })
        AutomatumDockPanel("Reveal", hidden, { hidden = !hidden }, "reveal panel", buttons = {
            if (!onMain) ChoiceButton("OK", Icons.Outlined.Check, Modifier.testTag(REVEAL_OK_TAG), primary = true, onClick = dismiss)
        }) {
            ChoiceHead(title, source, inspector)
            FlowRow(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // HIS-110 (owner, 2026-11-07): the revealed cards at the hand's card size, wrapping (the panel scrolls)
                for (card in cards) ChoiceCardFace(card, Modifier.choiceCardGestures(card.key, tap = {},
                    hold = { inspector.hold(card, it) }, moved = inspector::moved), area = CardArea.HAND)
            }
            ChoiceInspection(inspector, play, viewer)
        }
    }
}
