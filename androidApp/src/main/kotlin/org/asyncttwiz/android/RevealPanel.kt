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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.game.GameDevice

/** The test tag of the reveal panel's dismiss button (HIS-110). */
internal const val REVEAL_OK_TAG = "reveal-ok"

/** HIS-110, TRN-063 (owner, 2026-11-07): why the main button is greyed and game actions are refused while a reveal panel
 * is open, until its OK. */
internal const val REVEAL_BUSY = "Dismiss the reveal first: tap OK."

/**
 * HIS-110 (owner 2026-11-09): a reveal, the other player's or your own ([name] "You"), docked above the hand like every
 * Automatum panel (STK-044): "<name> revealed · <source>", the revealed cards as cards at the Hand card size (holding one inspects it,
 * TBL-104), the source's card at the right, the eye, and OK, which [dismiss]es it (the table then plays on). The cards
 * were revealed to the viewer, so they show face up wherever they are now. Until OK (the main button, TRN-063) game actions are
 * refused as while a choice is open ([REVEAL_BUSY]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RevealPanel(play: TableBoard, viewer: String, reveal: GameDevice.Reveal, name: String, dismiss: () -> Unit) {
    var hidden by remember(reveal) { mutableStateOf(false) }
    val inspector = remember(reveal) { ChoiceInspector() }
    val source = reveal.source?.let { choiceCard(play, viewer, "reveal-source/${it.value}", it.value, true, reveal.sourceName ?: "") }
    val sourceName = reveal.sourceName ?: source?.name?.takeIf { source.cardId != null }
    val title = "$name revealed" + (sourceName?.let { " · $it" } ?: "")
    val cards = reveal.cards.mapIndexed { i, card -> choiceCard(play, viewer, "reveal/$i/${card.value}", card.value, true, "A card") }
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
