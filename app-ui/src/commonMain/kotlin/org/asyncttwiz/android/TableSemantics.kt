package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*

/** The table's accessibility layer: a semantics node for every field and table item, each laid out on the animated
 * frame. Inline, so it stays part of TableSurface's own recomposition scope. */
@Composable
inline fun TableSemanticNodes(game: GameSummary, play: TableBoard, scene: TableScene, semanticItems: List<TableItem>,
    rendered: State<TableRenderFrame>, runtime: TableRuntimeHooks,
    crossinline currentRuntime: () -> TableRuntimeHooks, coordinator: TableGestureCoordinator, writable: Boolean,
    toolkit: TableToolkitController?, crossinline preview: () -> TableGesturePreview,
    layoutState: MutableState<TableLayoutState>, groupTaps: Map<String, GroupTap> = emptyMap(),
    /** The battlefields as laid out now (with the camera): where their nodes are, read in layout (web frame rate, 2026-11-15:
     * a pan or zoom step recomposes nothing). */
    noinline fieldsNow: () -> List<TableField> = { scene.fields }) {
    for (field in scene.fields.filter { it.bounds.width > 0 && it.bounds.height > 0 }) Box(Modifier.tableFrameBounds {
            fieldsNow().firstOrNull { it.owner == field.owner && it.containerId == field.containerId }?.bounds
        }.semantics {
            val player = if (field.owner == game.inviter.accountId) game.inviter else game.recipient
            contentDescription = "${player.username} Battlefield, ${play.board.containers.firstOrNull { it.id == field.containerId }?.cards?.size ?: 0} cards"
            stateDescription = if (field.holdsAttention) "${player.username}'s move" else "Waiting for move"
        })
    // Semantics-only children do not install a second pointer detector. Dock piles expose
    // the same minimum touch rectangle used by the scene's pointer hit testing.
    // TBL-117, STK-047, ZON-079: what the table draws on a card and doesn't say, for accessibility services and tests: the
    // targets' violet frame and the picked look as drawn, a closed group's as a whole ([TableGlows])
    val drawn = TableGlows.of(scene.battlefieldGroups, groupTaps, emptyMap(),
        toolkit?.cast?.glow(play).orEmpty() + toolkit?.flows?.glow(play).orEmpty(),
        picked = toolkit?.cast?.pickedGlow(play).orEmpty() + toolkit?.flows?.pickedGlow(play).orEmpty())
    val targets = drawn.targets
    // STK-047 (owner, 2026-11-12): the piles glowing because they hold a choice ([PileChoiceGlow])
    val glowingPiles = toolkit?.cast?.glow(play).orEmpty() + toolkit?.flows?.glow(play).orEmpty()
    val picks = drawn.picked
    val outlined = toolkit?.hand?.sources(play).orEmpty() + toolkit?.paySources().orEmpty()
    // Owner notes 2026-11-14: a closed group a tap spreads open to pick from glows as a pile holding choices
    val spreading = scene.battlefieldGroups.filter { it.key in drawn.spread }.flatMapTo(HashSet()) { it.members }
    val marks = { card: String -> setOfNotNull("target".takeIf { card in targets }, "picked".takeIf { card in picks }, "source".takeIf { card in outlined },
        "choices inside".takeIf { card in spreading }) }
    // Web frame rate (2026-11-15): each item's node is its own skippable composable with what it says worked out here, so a new
    // board recomposes only the nodes of what it changed (and only their semantics are worked out again)
    val bySource = scene.items.groupBy { it.sourceId }
    for (item in semanticItems) {
        if (item.kind == TableItemKind.HAND && item.cards.isNotEmpty()) continue
        val pileGlow = item.kind == TableItemKind.PILE && item.zone != PlayZone.STACK && item.id in glowingPiles
        val related = item.sourceId != null && bySource[item.sourceId].orEmpty().any { it.cardId != item.cardId }
        val facts = TableNodeFacts(item,
            marks = if (pileGlow) setOf("choices inside") else item.cardId?.let { card -> marks(card).takeIf { it.isNotEmpty() } },
            state = if (related) "Related source and projections share this source identity; selection is independent"
                else if (item.peekOwners.isNotEmpty()) "Visible card owners: " + item.peekOwners.joinToString { owner ->
                    if (owner == game.inviter.accountId) game.inviter.username else game.recipient.username
                } else null,
            port = scene.viewports.firstOrNull { it.id == item.viewport && it.maxScroll > 0 })
        key(item.id) { TableItemNode(facts, rendered, coordinator, layoutState) }
    }
}

/** What a table item's semantics node says ([TableItemNode]): equal for an item a new board left as it was. */
@Immutable
data class TableNodeFacts(val item: TableItem, val marks: Set<String>?, val state: String?, val port: TableViewport?)

/** A table item's semantics node, laid out on the animated frame ([rendered]); skipped while its [facts] stay the same. */
@Composable
fun TableItemNode(facts: TableNodeFacts, rendered: State<TableRenderFrame>, coordinator: TableGestureCoordinator,
    layoutState: MutableState<TableLayoutState>) {
    val item = facts.item
    var layout by layoutState
    Box(Modifier.tableFrameBounds {
        rendered.value.items[item.id]?.let { current ->
            if (current.touchBounds != null) current.touchBounds
            else if (current.zone == PlayZone.STACK && current.kind == TableItemKind.PILE) current.stackBadge
            else current.visible.maxByOrNull { it.width * it.height }
        }
    }.semantics {
        contentDescription = item.label
        facts.marks?.let { this[TableMarks] = it }
        facts.state?.let { stateDescription = it }
        onClick("Actions") { coordinator.actions(item); true }
        val commands = mutableListOf<CustomAccessibilityAction>()
        facts.port?.let { port ->
            commands += CustomAccessibilityAction("Scroll toward start") { layout = layout.copy(scroll = layout.scroll + (port.id to (port.scroll - port.bounds.width / 2).coerceAtLeast(0f))); true }
            commands += CustomAccessibilityAction("Scroll toward end") { layout = layout.copy(scroll = layout.scroll + (port.id to (port.scroll + port.bounds.width / 2).coerceAtMost(port.maxScroll))); true }
        }
        customActions = commands
    })
}

/** The marks the table draws on a card: "target" a violet glow, "picked" picked in a choice of several, "source" Autotap's amber
 * outline; on a pile, or on the members of a closed group a tap spreads open to pick from, "choices inside" its pulsing glow
 * (STK-047). */
val TableMarks = androidx.compose.ui.semantics.SemanticsPropertyKey<Set<String>>("TableMarks")
