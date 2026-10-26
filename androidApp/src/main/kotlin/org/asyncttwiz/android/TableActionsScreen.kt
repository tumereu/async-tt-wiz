package org.asyncttwiz.android

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*
import java.util.UUID

/** Basic controls on the real route. Unsupported View/Shuffle/toolkit controls are not fake buttons. */
@Composable
internal fun TableActionsScreen(game: GameSummary, play: PlayState, viewer: String,
    captured: TableIntent.Actions, item: TableItem, selection: TableSelection, enabled: Boolean,
    landscape: Boolean, dismiss: () -> Unit, inspect: (String) -> Unit, select: (String) -> Unit,
    open: (String) -> Unit, request: (TableIntent) -> Boolean, submit: (PlayAction, String?) -> Boolean) {
    val names = mapOf(game.inviter.accountId to game.inviter.username, game.recipient.accountId to game.recipient.username)
    if (item.kind in listOf(TableItemKind.LIFE, TableItemKind.COUNTER)) {
        val manual = remember(play.id, captured) { ManualActions().apply {
            player(play, requireNotNull(item.owner), counters = item.kind == TableItemKind.COUNTER)
        } }
        if (manual.scope == null) { SideEffect(dismiss); return }
        CompositionLocalProvider(LocalInlinePopupAnchor provides InlinePopupAnchor(item.id, item.bounds, item.owner == viewer)) {
            ManualActionsHost(play, viewer, manual, enabled, landscape, names) { action -> submit(action, UUID.randomUUID().toString()) }
        }
        return
    }
    val context = captured.scope?.contextId
    val card = play.board.cards.singleOrNull { it.id == context }
    val container = play.board.containers.singleOrNull { it.id == item.id }
    // Browser card entries are real IDs, even when not independently drawn on the Canvas.
    if (context != null && card == null) { SideEffect(dismiss); return }
    if (card != null) {
        val manual = remember(play.id, captured) { ManualActions().apply { cards(play, card.id, requireNotNull(captured.scope).cardIds) } }
        if (manual.scope == null) { SideEffect(dismiss); return }
        val rotate = if (selection.mode == TableMode.SELECT && item.zone == PlayZone.BATTLEFIELD && !item.projection) listOf(true, false).map { tapped ->
            MenuControl(if (tapped) "Tap selected scope" else "Untap selected scope", enabled) {
                if (request(TableIntent.Rotate(requireNotNull(captured.scope), tapped))) dismiss()
            }
        } else emptyList()
        val tools = listOf(MenuControl("Select / deselect", selected = card.id in selection.ids) { select(card.id) }) + rotate +
            basicMoveTools(play, names, enabled, requireNotNull(captured.scope), captured.sources, request, dismiss)
        ManualActionsHost(play, viewer, manual, enabled, landscape, names, tools = tools, inspect = { inspect(card.id) }) {
            submit(it, UUID.randomUUID().toString())
        }
        return
    }
    CompositionLocalProvider(LocalInlinePopupAnchor provides InlinePopupAnchor(item.id, item.bounds, item.owner == viewer)) {
    InlinePopupPanel(dismiss, title = "Contents", height = 480.dp, sheet = true) {
                if (container != null) {
                    Text(item.label)
                    if (container.zone == PlayZone.LIBRARY) {
                        // Full-library View is logged and not implemented by the pinned processor.
                        // A top-card move exposes no identity/order through this Actions panel.
                        val top = captured.topId
                        if (top == null) Text("Empty Library") else {
                            val scope = remember(captured) { TableScope(top, listOf(top)) }
                            val sources = captured.sources.filterKeys { it == top }
                            val hand = play.board.containers.single { it.zone == PlayZone.HAND && it.accountId == container.accountId }
                            ManualActionSquare("Draw one", "", enabled, onClick = {
                                if (request(TableIntent.Move(scope, TableDestination.Container(hand.id, hand.zone), sources))) dismiss()
                            })
                            BasicMoveControls(play, names, enabled, scope, sources, request, dismiss, topCard = true)
                        }
                    } else {
                        if (container.cards.isEmpty()) Text("Empty")
                        // Covered entries stay covered. Library is deliberately excluded from this local list.
                        for (id in container.cards.sorted()) {
                            val entry = play.board.cards.single { it.id == id }
                            val title = if (entry.covered) "Covered card" else play.definitions.singleOrNull { it.digest == entry.definition }
                                ?.definition?.faces?.singleOrNull { it.faceId == entry.faceId }?.name ?: "Card unavailable"
                            TextButton(onClick = { open(id) }, modifier = Modifier.ringInputTarget()) { Text(title) }
                        }
                    }
                }
                if (!enabled) Text("Read-only")
    }
    }
}

@Composable
internal fun BasicMoveControls(play: PlayState, names: Map<String, String>, enabled: Boolean, scope: TableScope,
    sources: Map<String, TableMoveSource>, request: (TableIntent) -> Boolean, dismiss: () -> Unit, topCard: Boolean = false) {
    val controls = basicMoveTools(play, names, enabled, scope, sources, request, dismiss, topCard)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (control in controls.take(6)) control.Square()
    }
    for (control in controls.drop(6)) control.Square()
}

internal fun basicMoveTools(play: PlayState, names: Map<String, String>, enabled: Boolean, scope: TableScope,
    sources: Map<String, TableMoveSource>, request: (TableIntent) -> Boolean, dismiss: () -> Unit, topCard: Boolean = false): List<MenuControl> = buildList {
    val context = CombatStackRules.liveSource(play.board, scope.contextId) ?: return@buildList
    val owner = context.owner
    val currentZone = play.board.containers.single { it.id == context.containerId }.zone
    for (zone in listOf(PlayZone.HAND, PlayZone.BATTLEFIELD, PlayZone.LIBRARY, PlayZone.GRAVEYARD, PlayZone.EXILE)) {
        if (context.token && zone in setOf(PlayZone.HAND, PlayZone.LIBRARY)) continue
        val target = play.board.containers.single { it.accountId == owner && it.zone == zone }
        val choices = if (zone == PlayZone.LIBRARY) listOf(false, true) else listOf(false)
        for (bottom in choices) {
            val label = moveZoneLabel(zone, bottom)
            // The destination cell is captured by this rendered control, not recomputed in a later callback.
            val destination = if (zone == PlayZone.BATTLEFIELD) {
                val occupied = play.board.cards.filter { it.containerId == target.id }.mapNotNull { it.placement?.position }.toSet()
                val column = generateSequence(0) { it + 1 }.first { BoardPosition(it, 0) !in occupied }
                TableDestination.Grid(target.id, column, 0, ownerOnly = true)
            } else TableDestination.Container(target.id, zone, bottom)
            add(MenuControl(label, enabled && currentZone != zone) { if (request(TableIntent.Move(scope, destination, sources))) dismiss() })
        }
    }
    if (!context.token) add(MenuControl("Shuffle into library", enabled && currentZone != PlayZone.LIBRARY) {
        val invocation = java.util.UUID.randomUUID().toString()
        val library = play.board.containers.single { it.accountId == owner && it.zone == PlayZone.LIBRARY }
        val libraries = scope.cardIds.mapNotNull { id -> CombatStackRules.liveSource(play.board, id)?.owner }
            .distinct().map { account -> play.board.containers.single { it.accountId == account && it.zone == PlayZone.LIBRARY } }
        val intents = listOf(TableIntent.Move(scope, TableDestination.Container(library.id, PlayZone.LIBRARY), sources, invocation)) +
            libraries.map { target -> TableIntent.Submit(PlayAction.ShuffleLibrary(target.id,
                RandomV1.encodeSeed(ByteArray(32).also { java.security.SecureRandom().nextBytes(it) })), invocation) }
        if (request(TableIntent.Batch(intents, invocation))) dismiss()
    })
    val stack = play.board.containers.single { it.zone == PlayZone.STACK }
    if (scope.cardIds.all { id -> CombatStackRules.liveSource(play.board, id)?.let {
            CombatStackRules.canProject(play.board, it) } == true }) add(MenuControl("Move to Stack", enabled) {
        if (request(TableIntent.Move(scope, TableDestination.Container(stack.id, stack.zone), sources))) dismiss()
    })
}
