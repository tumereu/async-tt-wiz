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
 * HIS-233 (owner 2026-11-19, N18): the reveal panel's title for cards exiled face up from a library by [name]: "Bob exiled
 * Plains from their library", "You exiled 4 cards from Bob's library"; [whose] as [pileTitle]'s.
 */
fun libraryExileTitle(name: String, cards: List<String>, whose: String): String =
    "$name exiled ${cards.singleOrNull() ?: "${cards.size} cards"} from $whose library"

/**
 * HIS-252, STK-181 (owner 2026-11-25): the whole text of what made [reveal], shown under the reveal panel's title as a choice shows its
 * source's: an ability's whole text (a keyword's trigger as its keyword's printed line; public, also when its source can't be
 * seen), a spell's whole printed text, and without a Stack item (a cost) the lines of the source's printed text that say what
 * happened ([AskedPart.sentence]'s words), else its whole text. None for a spell or source the viewer may not see, or without a
 * source.
 */
fun revealText(play: TableBoard, reveal: GameDevice.Reveal, sourceShown: Boolean): String? {
    val item = reveal.item
    val rules = reveal.source?.let { rulesTextOf(play, it.value) }
    if (item != null && (item.kind is arcanum.engine.state.StackItemKind.Activated || item.kind is arcanum.engine.state.StackItemKind.Triggered))
        return org.asyncttwiz.automatum.StackAbilityText.of(item, rules)
    if (!sourceShown) return null
    if (item != null) return spellText(rules)
    val lines = rules?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }?.takeIf { it.isNotEmpty() } ?: return null
    return lines.filter { revealWords(reveal).containsMatchIn(it) }.ifEmpty { lines }.joinToString("\n")
}

/** STK-230 (owner 2026-11-25): the words of the sentence that did what [reveal] shows: revealed, exiled from a library, or moved out of
 * a graveyard or exile into a hand or library (a sentence naming both). */
fun revealWords(reveal: GameDevice.Reveal): Regex {
    val left = reveal.left
    return when {
        left != null -> AskedPart.leaving(if (left.from == arcanum.api.Zone.Exile) "exiled?" else "graveyards?",
            if (left.to == arcanum.api.Zone.Hand) "hands?" else "library|libraries")
        reveal.exiled != null -> AskedPart.EXILE
        else -> AskedPart.REVEAL
    }
}

/** STK-230: what [revealWords] look for, in words for the log. */
private fun revealWhat(reveal: GameDevice.Reveal): String = when {
    reveal.left != null -> "where the cards came from and went"
    reveal.exiled != null -> "\"exile\""
    else -> "\"reveal\""
}

/**
 * HIS-110 (owner 2026-11-09): a reveal, the other player's or your own ([name] "You"), docked above the hand like every
 * Automatum panel (STK-044): "<name> revealed · <source>", the revealed cards as cards at the Hand card size (holding one inspects it,
 * TBL-104), the source's card at the right, the eye, and OK, which [dismiss]es it (the table then plays on). The cards
 * were revealed to the viewer, so they show face up wherever they are now. Until OK (the main button, TRN-063) game actions are
 * refused as while a choice is open ([REVEAL_BUSY]). HIS-114: cards that left a public pile for a hidden zone show in it too,
 * titled by [pileTitle], [ownerName] naming the pile's owner where that is neither the viewer nor the mover. HIS-233: cards
 * exiled face up from a library show in it too, titled by [libraryExileTitle].
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
        ?: reveal.exiled?.let { exiled ->
            val whose = when {
                exiled.owner.value == viewer -> "your"
                exiled.owner == reveal.by -> "their"
                else -> "$ownerName's"
            }
            libraryExileTitle(name, cards.map { it.name }, whose)
        }
        ?: ("$name revealed" + (sourceName?.let { " · $it" } ?: ""))
    // HIS-252, STK-230 (owner 2026-11-25): the source's whole text under the title, the sentence that did it highlighted when exactly one
    // sentence of it says so
    val text = revealText(play, reveal, source?.shown == true)
    val asked = text?.let { AskedPart.sentence(it, revealWords(reveal), revealWhat(reveal)) }
    // HIS-110 (problem 2026-11-12, the wrong player named): what the panel says, with who revealed by the event
    LaunchedEffect(reveal) {
        AppLog.info("game", "Reveal panel in ${reveal.event}: \"$title\" (by ${reveal.by.value.take(8)}, viewer ${viewer.take(8)}" +
            (reveal.left?.let { ", pile owner ${it.owner.value.take(8)}" } ?: "") +
            (reveal.exiled?.let { ", exiled from ${it.owner.value.take(8)}'s library" } ?: "") + ")" +
            ", made by ${reveal.item?.let { "Stack item ${it.id} (${it.kind::class.simpleName})" } ?: "no Stack item"}" +
            ", text ${text?.let { "\"${it.take(160)}\"" } ?: "none"}, " +
            (if (asked != null && text != null) "highlighted \"${asked.of(text).take(120)}\" (${asked.why})"
                else if (text != null) "nothing highlighted: no sentence or several say ${revealWhat(reveal)}, or it is the whole text" else "nothing highlighted"))
    }
    CompositionLocalProvider(LocalChoiceFrame provides ChoiceFrame(viewer)) {
        // TRN-063 (owner playtest 2026-11-09): its OK is the main button (its own button where there is none)
        val onMain = MainButtonAction(MainAction("OK", Icons.Outlined.Check, tooltip = "Dismiss the reveal") { dismiss() })
        AutomatumDockPanel("Reveal", hidden, { hidden = !hidden }, "reveal panel", buttons = {
            if (!onMain) ChoiceButton("OK", Icons.Outlined.Check, Modifier.testTag(REVEAL_OK_TAG), primary = true, onClick = dismiss)
        }) {
            ChoiceHead(title, source, inspector) {
                text?.let { AskedText(it, asked, "Ability") }
            }
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
