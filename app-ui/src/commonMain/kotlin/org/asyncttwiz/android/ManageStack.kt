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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*

/** The Stack's entries in one compact, live view (STK-018, STK-019): look and hold to inspect only; the Stack changes only by
 * the rules (I-D52). While a target is picked on the Stack (STK-048), [targets] are the legal entries: they glow violet and a
 * tap picks one ([pick]); the others are dimmed. */
@Composable
fun ManageStackDialog(state: TableBoard, viewer: String, dismiss: () -> Unit, targets: List<String>? = null,
    pick: (String) -> Unit = {}, names: Map<String, String> = emptyMap()) {
    val order = CombatStackRules.stack(state.board).cards
    if (order.isEmpty()) { LaunchedEffect(Unit) { dismiss() }; return }
    InlinePopupPanel(dismiss, title = "Stack · ${order.size}", width = 384.dp, height = 520.dp, key = "manage-stack", sheet = true) {
        StackEntries(state, viewer, Modifier.fillMaxWidth().heightIn(max = 440.dp), targets, pick, names)
    }
}

/** The Stack's entries, top first, as the Stack list (STK-018) shows them: in its sheet ([ManageStackDialog]) and in the
 * desktop table's dock (WEB-017). Holding an entry inspects it; [targets] and [pick] as in [ManageStackDialog]. */
@Composable
fun StackEntries(state: TableBoard, viewer: String, modifier: Modifier, targets: List<String>? = null,
    pick: (String) -> Unit = {}, names: Map<String, String> = emptyMap(),
    /** WEB-017: a card without its picture yet names itself small inside its frame (the desktop dock's list). */
    nameInCard: Boolean = false) {
    val order = CombatStackRules.stack(state.board).cards
    val size = rememberCardAppearance().size(CardArea.STACK)
    val entries = contentsEntries(state, PileContents(CombatStackRules.stack(state.board).id, "Stack", order, true), viewer)
    fun place(index: Int) = if (index == 0) "Top" else "${index + 1}"
    // STK-018 (owner playtest 2026-11-07): what was chosen for each entry, and which entries target which
    // (owner 2026-11-07) each entry's text names its targets in place; the details list only the targets it doesn't name
    val texts = remember(IdentityKey(state.board), viewer, names) { order.associateWith { StackAbilities.text(state, it, viewer, names) } }
    val details = remember(texts) { order.associateWith { StackAbilities.details(state, it, viewer, names, texts[it]) } }
    val targetedBy = remember(IdentityKey(state.board)) { stackTargetedBy(state, order) }
    var inspection by remember { mutableStateOf<ManualInspection?>(null) }
    fun inspect(id: String) {
        val card = CombatStackRules.inspection(state.board, id) ?: return
        inspection = ManualInspection(state.gameId, state.playId, id,
            ManualDisclosure.permittedFaces(state.board, state.definitions, card, viewer, inspecting = true).map { it.faceId },
            snapshot = card.takeIf { CombatStackRules.liveSource(state.board, id) == null }, stackEntryId = id)
    }
    Box(modifier) {
        LazyColumn(Modifier.fillMaxWidth().ringInputTarget(draggable = true), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(entries, key = { _, entry -> entry.cardId }) { index, entry ->
                val id = entry.cardId
                val card = CombatStackRules.inspection(state.board, id)
                val projection = state.board.stackEffects.any { it.id == id }
                val controlled = CombatStackRules.controller(state.board, id) == viewer
                val owner = if (controlled) OwnTableColor else OpponentTableColor
                val legal = targets != null && id in targets
                val shape = RoundedCornerShape(8.dp)
                // STK-058: an entry another entry targets is softly highlighted in the targeting entry's controller's colour
                val targeter = targetedBy[id]?.firstOrNull()?.let { if (CombatStackRules.controller(state.board, it) == viewer) OwnTableColor else OpponentTableColor }
                Column(Modifier.alpha(if (targets != null && !legal) .4f else 1f)) {
                    Row(Modifier.then(if (legal) Modifier.background(AutomatumViolet.copy(alpha = .1f), shape)
                            .border(2.dp, AutomatumViolet, shape).padding(4.dp)
                            .semantics { stateDescription = "Legal target" }
                        else if (targeter != null) Modifier.background(targeter.copy(alpha = .08f), shape)
                            .border(1.5.dp, targeter.copy(alpha = .7f), shape).padding(4.dp) else Modifier)
                        .contentsCardGestures(id, PlayZone.STACK, actions = { if (legal) pick(id) }, double = null,
                        peek = { held -> if (held) inspect(id) else inspection = null })
                        // WEB-024: on the desktop table the finger key over an entry inspects it (the table's Inspect)
                        .fingerTarget(id),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val definition = state.definitions.find { it.digest == card?.definition }?.definition
                        val face = definition?.faces?.indexOfFirst { it.faceId == card?.faceId } ?: -1
                        val art = if (entry.faceVisible && definition != null && face >= 0)
                            InspectedCard.retained(definition).artwork[face] else null
                        val ability = entry.ability
                        val described = Modifier.semantics { contentDescription = if (controlled) "Your Stack entry" else "Opponent Stack entry" }
                        // STK-046: an ability is its token (the source's art and the marker), never the source card
                        if (ability != null) AbilityTokenFace(ability, art, controlled,
                            Modifier.size(32.dp * size, 44.dp * size).then(described))
                        else Box(Modifier.size(32.dp * size, 44.dp * size).cardFrame(card?.token == true, owner, projection)
                            .then(described), contentAlignment = Alignment.Center) {
                            val small: (@Composable () -> Unit)? = if (!nameInCard) null else { {
                                Text(entry.name, Modifier.padding(2.dp), fontSize = 9.sp, lineHeight = 10.sp, maxLines = 3,
                                    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            } }
                            if (art != null) CardArtwork(art, entry.name, 64, 88, Modifier.fillMaxSize().then(if (nameInCard) Modifier.clip(RoundedCornerShape(4.dp)) else Modifier),
                                placeholder = small)
                            else MenuIcon("Inspect")
                        }
                        Column(Modifier.weight(1f)) {
                            Text("${place(index)} · ${entry.name}",
                                style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                            // STK-018 (owner playtest 2026-11-07): an ability's kind, its text (a spell's own text), its
                            // targets named in it, then what was chosen for the entry (modes, X, targets the text doesn't
                            // name, as far as the viewer may see them), and the entries targeting it
                            if (ability != null) Text(ability.kind, style = MaterialTheme.typography.labelMedium, color = AutomatumViolet)
                            val text = texts[id]
                            if (text != null) CardText(text.pieces.tinted(viewer), style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            else if (ability != null && ability.text.isNotEmpty()) CardText(ability.text,
                                style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            if (entry.faceVisible) for (line in details[id].orEmpty()) CardText(line, style = MaterialTheme.typography.bodySmall,
                                color = MenuInk, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            targetedBy[id]?.let { by -> Text("Targeted by ${by.joinToString(", ") { place(order.indexOf(it)) }}",
                                style = MaterialTheme.typography.labelMedium, color = targeter ?: MenuInk) }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
        // Attach the peek Popup to this dialog's window, not the underlying table activity.
        inspection?.let { GameplayCardInspection(state, viewer, it, { inspection = null }, names) }
    }
}

/** STK-018, STK-058: for each of the Stack entries [order] (top first) that another entry targets, the entries targeting
 * it, top first. */
fun stackTargetedBy(state: TableBoard, order: List<String>): Map<String, List<String>> {
    val effects = state.board.stackEffects.associateBy { it.id }
    val byItem = order.mapNotNull { id -> effects[id]?.item?.id?.let { it to id } }.toMap()
    return order.flatMap { id ->
        effects[id]?.item?.targets?.values.orEmpty().flatten().mapNotNull { ref -> byItem[ref]?.takeIf { it != id } }.map { it to id }
    }.groupBy({ it.first }, { it.second })
}
