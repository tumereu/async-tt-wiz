package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*

/** The table's accessibility layer: a semantics node for every field, active ping and table item, each
 * laid out on the animated frame. Inline, so it stays part of TableSurface's own recomposition scope. */
@Composable
internal inline fun TableSemanticNodes(game: GameSummary, play: PlayState, scene: TableScene, semanticItems: List<TableItem>,
    rendered: State<TableRenderFrame>, annotations: TableAnnotationGeometry, pingProjection: ActivePingProjection,
    pingReference: EventReference, activePings: List<RecordedPingChange>, runtime: TableRuntimeHooks,
    crossinline currentRuntime: () -> TableRuntimeHooks, coordinator: TableGestureCoordinator, writable: Boolean,
    toolkit: TableToolkitController?, selectionState: State<TableSelection>, crossinline preview: () -> TableGesturePreview,
    layoutState: MutableState<TableLayoutState>) {
    var layout by layoutState
    val selection by selectionState
    for (field in scene.fields.filter { it.bounds.width > 0 && it.bounds.height > 0 }) Box(Modifier.offset(field.bounds.x.dp, field.bounds.y.dp)
        .size(field.bounds.width.dp, field.bounds.height.dp).semantics {
            val player = if (field.owner == game.inviter.accountId) game.inviter else game.recipient
            contentDescription = "${player.username} Battlefield, ${play.board.containers.single { it.id == field.containerId }.cards.size} cards"
            stateDescription = if (field.holdsAttention) "${player.username}'s move" else "Waiting for move"
            if (runtime.ping != null && runtime.controlsConnected == true) customActions = annotations.locations(field).map { (target, _) ->
                val captured = tablePingIntent(play, pingReference, target, pingProjection)
                val position = requireNotNull(target.position)
                CustomAccessibilityAction("${if (captured.add) "Ping" else "Unping"} location column ${position.x}, row ${Math.floorDiv(position.y, 2)}") {
                    currentRuntime().controlsConnected == true && currentRuntime().ping?.invoke(captured) == true
                }
            }
        })
    for (ping in activePings) annotations.endpoint(ping.target)?.let { point ->
        val captured = EventPingIntent(ping.reference.reference, listOf(ping.target), false)
        Box(Modifier.tableFrameBounds {
            val p = TableAnnotationGeometry(rendered.value.scene, game.inviter.accountId).endpoint(ping.target) ?: point
            TableBounds(p.x - 10, p.y - 10, 20f, 20f)
        }.semantics {
            contentDescription = "Ping ${ping.to!!.ordinal}, ${ping.target.kind.name.lowercase()}, author ${ping.recording.author}"
            if (runtime.ping != null && runtime.controlsConnected == true) onClick("Unping") {
                currentRuntime().controlsConnected == true && currentRuntime().ping?.invoke(captured) == true
            }
        })
    }
    // Semantics-only children do not install a second pointer detector. Dock piles expose
    // the same minimum touch rectangle used by the scene's pointer hit testing.
    for (item in semanticItems) {
        if (item.kind == TableItemKind.HAND && item.cards.isNotEmpty()) continue
        key(item.id) {
            Box(Modifier.tableFrameBounds {
                rendered.value.items[item.id]?.let { current ->
                    if (current.touchBounds != null) current.touchBounds
                    else if (current.zone == PlayZone.STACK && current.kind == TableItemKind.PILE) current.stackBadge
                    else current.visible.maxByOrNull { it.width * it.height }
                }
            }.semantics {
                contentDescription = item.label
                 if (item.peekOwners.isNotEmpty()) stateDescription = "Visible card owners: " + item.peekOwners.joinToString { owner ->
                    if (owner == game.inviter.accountId) game.inviter.username else game.recipient.username
                }
                  selected = item.cardId in rendered.value.scene.highlightedSelection(preview(), selection) ||
                      toolkit?.panel?.startsWith("panelCard/") == true && item.cardId == toolkit.popupItem?.cardId
                 if (item.sourceId != null && scene.items.any { it.cardId != item.cardId && it.sourceId == item.sourceId })
                     stateDescription = "Related source and projections share this source identity; selection is independent"
                onClick("Actions") { coordinator.actions(item); true }
                val commands = mutableListOf<CustomAccessibilityAction>()
                if (item.cardId == null && !item.covered && item.cards.isNotEmpty())
                    commands += CustomAccessibilityAction("Inspect") { coordinator.inspect(item); true }
                if (item.cardId != null) {
                    commands += CustomAccessibilityAction("Inspect") { coordinator.inspect(item); true }
                     if (!runtime.reviewing) commands += CustomAccessibilityAction("Select or deselect") { coordinator.select(item); true }
                    if (writable && scene.canDrag(item)) {
                        if (item.zone == PlayZone.BATTLEFIELD && !item.projection) {
                            commands += CustomAccessibilityAction("Tap selected scope") { coordinator.rotate(item, true); true }
                            commands += CustomAccessibilityAction("Untap selected scope") { coordinator.rotate(item, false); true }
                        }
                        for ((label, destination) in libraryDestinations(play.board, sourceIds = listOf(item.cardId))) {
                            val zone = play.board.containers.single { it.id == destination.containerId }.zone
                            val bound = destination.position?.let { TableDestination.Grid.auto(destination.containerId) }
                                ?: TableDestination.Container(destination.containerId, zone, destination.insertion == MoveInsertion.BOTTOM)
                            commands += CustomAccessibilityAction(label) { coordinator.accessibleMove(item, bound); true }
                        }
                    }
                }
                scene.viewports.firstOrNull { it.id == item.viewport && it.maxScroll > 0 }?.let { port ->
                    commands += CustomAccessibilityAction("Scroll toward start") { layout = layout.copy(scroll = layout.scroll + (port.id to (port.scroll - port.bounds.width / 2).coerceAtLeast(0f))); true }
                    commands += CustomAccessibilityAction("Scroll toward end") { layout = layout.copy(scroll = layout.scroll + (port.id to (port.scroll + port.bounds.width / 2).coerceAtMost(port.maxScroll))); true }
                }
                  val ping = tablePingTarget(play, item)?.let { tablePingIntent(play, pingReference, it) }
                  if (ping != null && runtime.ping != null && runtime.controlsConnected == true)
                      commands += CustomAccessibilityAction(if (ping.add) "Ping this target" else "Unping this target") {
                          currentRuntime().controlsConnected == true && currentRuntime().ping?.invoke(ping) == true
                      }
                 customActions = commands
            })
        }
    }
}
