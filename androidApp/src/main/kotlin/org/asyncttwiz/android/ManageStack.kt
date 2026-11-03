package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*

/** The Stack's entries in one compact, live view (STK-019): look and inspect only; the Stack changes only by the rules
 * (I-D52). (An Automatum target on the Stack is picked in the docked [StackTargetPicker].) */
@Composable
internal fun ManageStackDialog(state: PlayState, viewer: String, dismiss: () -> Unit) {
    val order = CombatStackRules.stack(state.board).cards
    val size = rememberCardAppearance().size(CardArea.STACK)
    if (order.isEmpty()) { LaunchedEffect(Unit) { dismiss() }; return }
    val entries = contentsEntries(state, PileContents(CombatStackRules.stack(state.board).id, "Stack", order, true), viewer)
    var inspection by remember { mutableStateOf<ManualInspection?>(null) }
    var peeking by remember { mutableStateOf(false) }
    fun inspect(id: String, hold: Boolean) {
        val card = CombatStackRules.inspection(state.board, id) ?: return
        peeking = hold
        inspection = ManualInspection(state.gameId, state.id, id,
            ManualDisclosure.permittedFaces(state, card, viewer, inspecting = true).map { it.faceId },
            snapshot = card.takeIf { CombatStackRules.liveSource(state.board, id) == null }, stackEntryId = id)
    }
    InlinePopupPanel(dismiss, title = "Stack · ${order.size}", width = 384.dp, height = 520.dp, key = "manage-stack", sheet = true) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 440.dp).ringInputTarget(draggable = true), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(entries, key = { _, entry -> entry.cardId }) { index, entry ->
                val id = entry.cardId
                val card = CombatStackRules.inspection(state.board, id)
                val projection = state.board.stackEffects.any { it.id == id }
                val controlled = CombatStackRules.controller(state.board, id) == viewer
                val owner = if (controlled) OwnTableColor else OpponentTableColor
                Column {
                    Row(Modifier.contentsCardGestures(id, PlayZone.STACK, actions = {}, double = null,
                        inspect = { inspect(id, false) }, peek = { held -> if (held) inspect(id, true) else if (peeking) inspection = null }),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val definition = state.definitions.find { it.digest == card?.definition }?.definition
                        val face = definition?.faces?.indexOfFirst { it.faceId == card?.faceId } ?: -1
                        val art = if (entry.faceVisible && definition != null && face >= 0)
                            InspectedCard.retained(requireNotNull(card).definition, definition).artwork[face] else null
                        Box(Modifier.size(32.dp * size, 44.dp * size).cardFrame(card?.token == true, owner, projection)
                            .semantics { contentDescription = if (controlled) "Your Stack entry" else "Opponent Stack entry" },
                            contentAlignment = Alignment.Center) {
                            if (art != null) CardArtwork(art, entry.name, 64, 88, Modifier.fillMaxSize())
                            else MenuIcon("Inspect")
                        }
                        Text("${if (index == 0) "Top" else "${index + 1}"} · ${entry.name}", Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                    }
                    HorizontalDivider()
                }
            }
        }
        // Attach the peek Popup to this dialog's window, not the underlying table activity.
        inspection?.let { GameplayCardInspection(state, viewer, it, { inspection = null }, peek = peeking) }
    }
}
