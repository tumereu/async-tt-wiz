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
        val manual = remember(play.id, captured) { ManualActions().apply { player(play, requireNotNull(item.owner)) } }
        if (manual.scope == null) { SideEffect(dismiss); return }
        ManualActionsHost(play, viewer, manual, enabled, landscape, names) { action -> submit(action, UUID.randomUUID().toString()) }
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
        val rotate = if (selection.mode == TableMode.SELECT && item.zone != PlayZone.STACK) listOf(true, false).map { tapped ->
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
    TableMenuPanel(dismiss, title = "Contents") {
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
                            TextButton(onClick = { open(id) }) { Text(title) }
                        }
                    }
                }
                if (!enabled) Text("Read-only")
    }
}

@Composable
internal fun BasicMoveControls(play: PlayState, names: Map<String, String>, enabled: Boolean, scope: TableScope,
    sources: Map<String, TableMoveSource>, request: (TableIntent) -> Boolean, dismiss: () -> Unit, topCard: Boolean = false) {
    val controls = remember(scope, sources, enabled) { basicMoveTools(play, names, enabled, scope, sources, request, dismiss, topCard) }
    for ((index, player) in play.board.players.withIndex()) {
        Text(names[player.accountId].orEmpty(), style = MaterialTheme.typography.labelSmall)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (control in controls.drop(index * 6).take(6)) control.Square()
        }
    }
    for (control in controls.drop(play.board.players.size * 6)) control.Square()
}

internal fun basicMoveTools(play: PlayState, names: Map<String, String>, enabled: Boolean, scope: TableScope,
    sources: Map<String, TableMoveSource>, request: (TableIntent) -> Boolean, dismiss: () -> Unit, topCard: Boolean = false): List<MenuControl> = buildList {
    for (player in play.board.players) {
        for (zone in listOf(PlayZone.HAND, PlayZone.BATTLEFIELD, PlayZone.LIBRARY, PlayZone.GRAVEYARD, PlayZone.EXILE)) {
            val target = play.board.containers.single { it.accountId == player.accountId && it.zone == zone }
            val choices = if (zone == PlayZone.LIBRARY) listOf(false, true) else listOf(false)
            for (bottom in choices) {
                val label = "Move ${if (topCard) "top card " else ""}to ${names[player.accountId]} ${zone.name.lowercase()}" +
                    if (zone == PlayZone.LIBRARY) if (bottom) " bottom" else " top" else ""
                // The destination cell is captured by this rendered control, not recomputed in a later callback.
                val destination = if (zone == PlayZone.BATTLEFIELD) {
                    val occupied = play.board.cards.filter { it.containerId == target.id }.mapNotNull { it.placement?.position }.toSet()
                    val column = generateSequence(0) { it + 1 }.first { BoardPosition(it, 0) !in occupied }
                    TableDestination.Grid(target.id, column, 0)
                } else TableDestination.Container(target.id, zone, bottom)
                add(MenuControl(label, enabled) { if (request(TableIntent.Move(scope, destination, sources))) dismiss() })
            }
        }
    }
    val stack = play.board.containers.single { it.zone == PlayZone.STACK }
    if (scope.cardIds.all { id -> CombatStackRules.liveSource(play.board, id)?.let {
            CombatStackRules.canProject(play.board, it) } == true }) add(MenuControl("Move to Stack", enabled) {
        if (request(TableIntent.Move(scope, TableDestination.Container(stack.id, stack.zone), sources))) dismiss()
    })
    // Snapshot the same real-card destination used by dragging; sync must not replace an open choice.
    val piles = basicPileDestinations(play, scope)
    for (destination in piles) {
        val source = requireNotNull(destination.source)
        val position = requireNotNull(source.placement)
        val owner = play.board.containers.singleOrNull { it.id == source.containerId }?.accountId
        add(MenuControl("Move to ${names[owner] ?: "Player"} pile (${position.column.toInt()}, ${position.row.toInt()})", enabled) {
            if (request(TableIntent.Move(scope, destination, sources))) dismiss()
        })
    }
}

internal fun basicPileDestinations(play: PlayState, scope: TableScope): List<TableDestination.CardPile> =
    play.board.cards.filter { it.placement != null && it.id !in scope.cardIds }
        .distinctBy { it.containerId to it.placement }.filter { card ->
            val members = play.board.cards.filter { it.containerId == card.containerId && it.placement == card.placement }.map { it.id }
            (members + scope.cardIds).distinct().size <= 3
        }.map { card -> TableDestination.CardPile(card.id, BasicTableController.sources(play, listOf(card.id)).getValue(card.id)) }
