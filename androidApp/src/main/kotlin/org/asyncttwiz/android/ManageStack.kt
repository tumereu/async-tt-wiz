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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*

/** The Stack's entries in one compact, live view (STK-018, STK-019): look and hold to inspect only; the Stack changes only by
 * the rules (I-D52). While a target is picked on the Stack (STK-048), [targets] are the legal entries: they glow violet and a
 * tap picks one ([pick]); the others are dimmed. */
@Composable
internal fun ManageStackDialog(state: TableBoard, viewer: String, dismiss: () -> Unit, targets: List<String>? = null,
    pick: (String) -> Unit = {}) {
    val order = CombatStackRules.stack(state.board).cards
    val size = rememberCardAppearance().size(CardArea.STACK)
    if (order.isEmpty()) { LaunchedEffect(Unit) { dismiss() }; return }
    val entries = contentsEntries(state, PileContents(CombatStackRules.stack(state.board).id, "Stack", order, true), viewer)
    var inspection by remember { mutableStateOf<ManualInspection?>(null) }
    fun inspect(id: String) {
        val card = CombatStackRules.inspection(state.board, id) ?: return
        inspection = ManualInspection(state.gameId, state.playId, id,
            ManualDisclosure.permittedFaces(state.board, state.definitions, card, viewer, inspecting = true).map { it.faceId },
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
                val legal = targets != null && id in targets
                val shape = RoundedCornerShape(8.dp)
                Column(Modifier.alpha(if (targets != null && !legal) .4f else 1f)) {
                    Row(Modifier.then(if (legal) Modifier.background(AutomatumViolet.copy(alpha = .1f), shape)
                            .border(2.dp, AutomatumViolet, shape).padding(4.dp)
                            .semantics { stateDescription = "Legal target" } else Modifier)
                        .contentsCardGestures(id, PlayZone.STACK, actions = { if (legal) pick(id) }, double = null,
                        peek = { held -> if (held) inspect(id) else inspection = null }),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val definition = state.definitions.find { it.digest == card?.definition }?.definition
                        val face = definition?.faces?.indexOfFirst { it.faceId == card?.faceId } ?: -1
                        val art = if (entry.faceVisible && definition != null && face >= 0)
                            InspectedCard.retained(requireNotNull(card).definition, definition).artwork[face] else null
                        val ability = entry.ability
                        val described = Modifier.semantics { contentDescription = if (controlled) "Your Stack entry" else "Opponent Stack entry" }
                        // STK-046: an ability is its token (the source's art and the marker), never the source card
                        if (ability != null) AbilityTokenFace(ability, art, controlled,
                            Modifier.size(32.dp * size, 44.dp * size).then(described))
                        else Box(Modifier.size(32.dp * size, 44.dp * size).cardFrame(card?.token == true, owner, projection)
                            .then(described), contentAlignment = Alignment.Center) {
                            if (art != null) CardArtwork(art, entry.name, 64, 88, Modifier.fillMaxSize())
                            else MenuIcon("Inspect")
                        }
                        Column(Modifier.weight(1f)) {
                            Text("${if (index == 0) "Top" else "${index + 1}"} · ${entry.name}",
                                style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                            if (ability != null && ability.text.isNotEmpty()) Text(ability.text,
                                style = MaterialTheme.typography.bodySmall, maxLines = 3)
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
        // Attach the peek Popup to this dialog's window, not the underlying table activity.
        inspection?.let { GameplayCardInspection(state, viewer, it, { inspection = null }) }
    }
}
