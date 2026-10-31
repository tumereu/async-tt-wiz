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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*

/** Identity-bound menu lowering. Stack order is not editable. */
internal object StackMenuActions {
    fun remove(entryId: String): PlayAction = PlayAction.Stack(StackAction.RemoveEffect(entryId))
    fun mark(entryId: String, index: Int?): PlayAction = PlayAction.Stack(StackAction.MarkText(entryId, index))
    fun toggleMark(entryId: String, index: Int): PlayAction = PlayAction.Stack(StackAction.ToggleTextMark(entryId, index))
    fun copy(state: PlayState, entryId: String): PlayAction =
        PlayAction.Stack(StackAction.CopyEffect(entryId, CombatStackRules.stackDigest(state)))
    fun changeController(state: PlayState, entryId: String): PlayAction = PlayAction.Stack(StackAction.SetController(entryId,
        state.board.players.single { it.accountId != CombatStackRules.controller(state.board, entryId) }.accountId))
    /** [note] and [ping] act on the entry itself, never on its real card (STK-009). */
    fun controls(state: PlayState, entryId: String, enabled: Boolean, perform: (TableIntent) -> Unit,
        note: (() -> Unit)? = null, ping: (() -> Unit)? = null): List<MenuControl> {
        val effect = state.board.stackEffects.find { it.id == entryId }
        val movable = effect?.isCopy != true
        return listOf(
            MenuControl("Remove from Stack", enabled && effect != null) { perform(TableIntent.Submit(remove(entryId), null)) },
            MenuControl("Put to battlefield", enabled && movable) { perform(TableIntent.StackDestination(entryId, PlayZone.BATTLEFIELD)) },
            MenuControl("Put to graveyard", enabled && movable) { perform(TableIntent.StackDestination(entryId, PlayZone.GRAVEYARD)) },
            MenuControl("Copy", enabled) { perform(TableIntent.Submit(copy(state, entryId), null)) },
            MenuControl("Change controller", enabled && effect != null) { perform(TableIntent.Submit(changeController(state, entryId), null)) },
            MenuControl("Edit note", enabled && effect != null && note != null) { note?.invoke() },
            MenuControl("Ping / Unping", enabled && ping != null) { ping?.invoke() })
    }
    /** Pings the entry: an effect entry as its own occurrence, a real card on the Stack as that card. */
    fun ping(state: PlayState, entryId: String, annotations: TableMenuAnnotations): Boolean {
        val kind = if (state.board.stackEffects.any { it.id == entryId }) PingTargetKind.OCCURRENCE else PingTargetKind.CARD
        return annotations.submit(tablePingIntent(state, annotations.reference, PingTarget(kind, entryId)).actions())
    }
    fun destination(state: PlayState, entryId: String, zone: PlayZone): List<PlayAction> {
        require(entryId in CombatStackRules.stack(state.board).cards)
        require(zone in setOf(PlayZone.BATTLEFIELD, PlayZone.GRAVEYARD, PlayZone.EXILE))
        require(state.board.stackEffects.none { it.id == entryId && it.isCopy })
        val source = CombatStackRules.liveSource(state.board, entryId)
        if (state.board.stackEffects.any { it.id == entryId } && source != null &&
            state.board.containers.single { it.id == source.containerId }.zone == zone) return listOf(remove(entryId))
        if (zone == PlayZone.EXILE) {
            val card = source ?: return listOf(remove(entryId))
            val target = state.board.containers.single { it.accountId == card.owner && it.zone == PlayZone.EXILE }
            return GameplayRules.moveActions(state, PlayAction.MoveCard(card.id, MoveSource(card.containerId, card.placement),
                MoveDestination(target.id, MoveInsertion.UNORDERED),
                removeStackEntryId = entryId.takeIf { state.board.stackEffects.any { effect -> effect.id == it } }))
        }
        return StackQuickActions.actions(state, entryId, zone)
    }
}

/** Exceptional Stack edits stay in one compact, live shared-state view. */
@Composable
internal fun ManageStackDialog(state: PlayState, viewer: String, enabled: Boolean, dismiss: () -> Unit,
    perform: (TableIntent) -> Unit, editNote: ((String) -> Unit)? = null) {
    val annotations = LocalTableMenuAnnotations.current
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
    // Wide enough for one row of seven 48 dp entry buttons.
    InlinePopupPanel(dismiss, title = "Manage Stack · ${order.size}", width = 384.dp, height = 520.dp, key = "manage-stack", sheet = true) {
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
                        StackMarkChips(state.board.stackTextMarks[id].orEmpty())
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        val icons = listOf(Icons.Outlined.Close, Icons.Outlined.TableRows, Icons.Outlined.Inventory2,
                            Icons.Outlined.ContentCopy, Icons.Outlined.SwapHoriz, Icons.AutoMirrored.Outlined.StickyNote2,
                            Icons.Outlined.AdsClick)
                        StackMenuActions.controls(state, id, enabled, perform, note = editNote?.let { { it(id) } },
                            ping = annotations?.let { { StackMenuActions.ping(state, id, it) } }).forEachIndexed { action, control ->
                            FilledTonalIconButton(onClick = control.click, enabled = control.enabled,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp), modifier = Modifier.size(48.dp).ringInputTarget()) {
                                Icon(icons[action], control.name, Modifier.size(22.dp))
                            }
                        }
                    }
                    stackTextOptions(state, id, viewer).forEachIndexed { option, text ->
                        FilterChip(selected = option in state.board.stackTextMarks[id].orEmpty(),
                            onClick = { perform(TableIntent.Submit(StackMenuActions.toggleMark(id, option), null)) },
                             enabled = enabled, modifier = Modifier.ringInputTarget().tableChangeFlash(LocalTableChanges.current,
                                 TableChangeKey(id, TableChangeKind.TEXT_MARK, line = option)), leadingIcon = { StackMarkChip(option) },
                            label = { CardText(text.text, maxLines = 3) })
                    }
                    HorizontalDivider()
                }
            }
        }
        // Attach the peek Popup to this dialog's window, not the underlying table activity.
        inspection?.let { GameplayCardInspection(state, viewer, it, { inspection = null }, peek = peeking) }
    }
}
