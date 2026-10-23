package org.asyncttwiz.android

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.asyncttwiz.protocol.*

/** The production overlay tree, mounted once alongside the Canvas and using its captured IDs. */
@Composable
internal fun TableToolkitScreen(toolkit: TableToolkitController, play: PlayState, game: GameSummary,
    landscape: Boolean, selection: TableSelection, select: (String) -> Unit,
    accepted: PlayState? = null, tokenCatalog: TableTokenCatalog? = null) {
    val names = mapOf(game.inviter.accountId to game.inviter.username, game.recipient.accountId to game.recipient.username)
    val enabled = toolkit.enabled()
    val endingColor = if (play.board.turn.accountId == toolkit.viewer) OwnTableColor else OpponentTableColor
    val context = toolkitQuickContext(play, selection, toolkit.explicitStack)
    val bound = toolkit.capture
    ManualActionsHost(play, toolkit.viewer, toolkit.manual, enabled, landscape, names, extras = {
        if (bound != null) {
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                ActionSquare("Select / deselect", "☑", true, selected = bound.contextViewId in selection.ids,
                    onClick = { select(bound.contextViewId) })
                if (selection.mode == TableMode.SELECT) for (tapped in listOf(true, false))
                    ActionSquare(if (tapped) "Tap selected scope" else "Untap selected scope", if (tapped) "↷" else "↑", enabled,
                        onClick = { toolkit.handle(TableIntent.Rotate(TableScope(bound.contextViewId, bound.viewIds), tapped, bound.sources)) })
                if (toolkit.tokens.scope != null) ActionSquare("Clone", "⧉", enabled && bound.sourceIds.isNotEmpty(), onClick = { toolkit.batch { toolkit.tokens.clone() } })
                if (toolkit.tokens.scope != null && bound.sourceIds.any { id -> play.board.cards.any { it.id == id && it.token } })
                    ActionSquare("Remove token", "⌫", enabled, onClick = { toolkit.batch { toolkit.tokens.removeTokens() } })
                if (toolkit.tokens.projectionScope != null && bound.sourceIds.isNotEmpty())
                    ActionSquare("Source actions", "↗", true, onClick = { toolkit.openCard(TableScope(bound.sourceIds.first(), bound.sourceIds)) })
                val realEndpoints = toolkit.tokens.scope != null || toolkit.tokens.projectionScope != null
                ActionSquare("Link", "∞", enabled && realEndpoints, onClick = { toolkit.tokens.beginLink(); toolkit.manual.close() })
                ActionSquare("Links", "⌁", realEndpoints, onClick = { toolkit.tokens.links(); toolkit.manual.close() })
            }
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                val battlefield = bound.sourceIds.any { id -> play.board.cards.any { card -> card.id == id &&
                    play.board.containers.any { it.id == card.containerId && it.zone == PlayZone.BATTLEFIELD } } }
                ActionSquare("Declare without tapping", "⚔", enabled && battlefield, onClick = { toolkit.declare(bound.sourceIds, false) })
                ActionSquare("Declare + tap", "⚔↷", enabled && battlefield, onClick = { toolkit.declare(bound.sourceIds, true) })
                val effect = bound.sourceIds.any { id -> play.board.cards.any { card -> card.id == id &&
                    play.board.containers.any { it.id == card.containerId && it.zone in setOf(PlayZone.BATTLEFIELD, PlayZone.GRAVEYARD, PlayZone.EXILE) } } }
                ActionSquare("Add Stack effect", "▱+", enabled && effect, onClick = { toolkit.addEffects(bound.sourceIds) })
                OccurrenceRemoval(toolkit, play, bound, enabled)
            }
            BasicMoveControls(play, names, enabled, TableScope(bound.sourceIds.first(), bound.sourceIds),
                bound.sources, toolkit::handle, toolkit::dismiss)
            if (play.board.combat != null) CombatDestinationActions(
                CombatStackPresentation.combat(play.board, 48f, 68f).destinations.map { it.label to it.destination }, enabled) {
                toolkit.place(bound, it)
            }
        }
    }, submitAll = toolkit::submitAll, completeValue = toolkit::completeValue) { action -> toolkit.submit(action) }

    when (val panel = toolkit.panel) {
        "table" -> {
            val suggestion = accepted?.board?.resultSuggestion
            TableActionMenu(play, toolkit.viewer, context, toolkit.table, endingColor, toolkit::dismiss,
                pendingSuggestionId = suggestion?.id, mayAccept = suggestion?.author?.let { it != toolkit.viewer } == true,
                perform = toolkit::perform)
        }
        "phase" -> PhaseStepPicker(play, toolkit.viewer, endingColor, toolkit.table, toolkit::dismiss)
        "library" -> toolkit.library?.let { library -> ToolkitDialog(toolkit::dismiss) {
            LibraryActions(library.controller, play.board, library.handIds, initialTool = library.tool)
            play.board.contentsDisplays.singleOrNull { it.id == toolkit.libraryDisplayId }?.let { display ->
                ContentsScreen(play, display, toolkit.viewer, library.controller, toolkit::inspect,
                    Modifier.height(300.dp), onActions = { toolkit.openCard(selection.scope(it, display.cardIds)) })
            }
        } }
        "stack" -> Dialog(onDismissRequest = toolkit::dismiss) { Surface {
            Column(Modifier.padding(12.dp).heightIn(max = 480.dp)) {
                Text("Stack · ${CombatStackRules.stack(play.board).cards.size} entries")
                StackContents(play, onEntry = { target -> toolkit.openCard(selection.scope(target.viewId,
                    CombatStackRules.stack(play.board).cards)) }, modifier = Modifier.weight(1f, fill = false))
            }
        } }
        "occurrence" -> if (bound != null) ToolkitDialog(toolkit::dismiss) {
            Text("Retained source · read-only")
            ActionSquare("Inspect", "⌕", true, onClick = { toolkit.inspect(bound.contextViewId) })
            ActionSquare("Select / deselect", "☑", true, onClick = { select(bound.contextViewId) })
            if (toolkit.tokens.projectionScope != null) {
                ActionSquare("Link", "∞", enabled, onClick = { toolkit.tokens.beginLink(); toolkit.dismiss() })
                ActionSquare("Links", "⌁", true, onClick = { toolkit.tokens.links(); toolkit.dismiss() })
            }
            OccurrenceRemoval(toolkit, play, bound, enabled)
        }
        "stackDestination" -> if (bound != null) ToolkitDialog(toolkit::dismiss) {
            val source = play.board.cards.singleOrNull { it.id == bound.contextViewId }
            if (source != null) for (choice in toolkit.stackChoices) {
                TextButton(enabled = enabled, onClick = {
                    val capturedSource = bound.sources[source.id]
                    if (capturedSource != null) {
                        val placement = capturedSource.placement?.let { p -> p.pileId?.let { PlayPlacement(BoardPosition(p.column.toInt(), p.row.toInt()), it) } }
                        if (toolkit.submit(PlayAction.MoveCard(source.id, MoveSource(capturedSource.containerId, placement), choice.destination))) toolkit.dismiss()
                    }
                }) { Text("Move to ${names[choice.label]} ${choice.zone.name.lowercase()}") }
            }
        }
        else -> if (panel?.startsWith("container/") == true) {
            val container = play.board.containers.singleOrNull { it.id == panel.substringAfter('/') }
            if (container != null) ToolkitDialog(toolkit::dismiss) {
                Text("${container.zone.name.lowercase()} · ${container.cards.size}")
                for (id in container.cards.sorted()) {
                    val card = play.board.cards.singleOrNull { it.id == id } ?: continue
                    val title = if (card.covered) "Covered card" else play.definitions.singleOrNull { it.digest == card.definition }
                        ?.definition?.faces?.singleOrNull { it.faceId == card.faceId }?.name ?: "Card unavailable"
                    TextButton(onClick = { toolkit.openCard(selection.scope(id, container.cards)) }) { Text(title) }
                }
            }
        } else if (panel?.startsWith("display/") == true) {
            val display = play.board.contentsDisplays.singleOrNull { it.id == panel.substringAfter('/') }
            if (display == null) LaunchedEffect(panel) { toolkit.dismiss() }
            else {
                val container = play.board.containers.single { it.id == display.containerId }
                val hand = play.board.containers.first { it.zone == PlayZone.HAND && it.accountId == (container.accountId ?: toolkit.viewer) }
                val controller = remember(toolkit, display.id) { toolkit.libraryController(container.id, hand.id) }
                Dialog(onDismissRequest = toolkit::dismiss) { Surface(Modifier.fillMaxHeight(.9f)) {
                    ContentsScreen(play, display, toolkit.viewer, controller, toolkit::inspect,
                        onActions = { toolkit.openCard(selection.scope(it, display.cardIds)) })
                } }
            }
        }
    }
    TokenCatalogDialog(toolkit.tokens, play.definitions, enabled = enabled,
        loading = tokenCatalog?.loading == true, catalogError = tokenCatalog?.error,
        catalogIdentity = tokenCatalog?.identity ?: listOf(toolkit.viewer, play.gameId, play.id),
        searchSource = tokenCatalog?.source)
    LinkEndpointPicker(toolkit.tokens, play, names, enabled = enabled, onPick = { toolkit.batch { toolkit.tokens.pick(it) } })
    LinksListDialog(toolkit.tokens, play, names, enabled = enabled)
    toolkit.inspection?.let { GameplayCardInspection(play, toolkit.viewer, it, toolkit::closeInspection) }
    if (toolkit.manual.scope?.usable(play) == false) LaunchedEffect(toolkit.manual.scope) { toolkit.manual.close() }
}

internal fun toolkitQuickContext(play: PlayState, selection: TableSelection, explicit: ExplicitStackTarget?): QuickContext = QuickContext(
    selectMode = selection.mode == TableMode.SELECT,
    selectedSourceIds = selection.ids.mapNotNull { CombatStackRules.liveSource(play.board, it)?.id }.distinct(),
    combatAreaId = play.board.combat?.id,
    stackTarget = explicit?.takeIf { it.id in CombatStackRules.stack(play.board).cards })

@Composable
private fun ToolkitDialog(dismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = dismiss) { Surface {
        Column(Modifier.padding(12.dp).heightIn(max = 520.dp).verticalScroll(rememberScrollState()), content = content)
    } }
}

@Composable
private fun OccurrenceRemoval(toolkit: TableToolkitController, play: PlayState, bound: ToolkitCapture, enabled: Boolean) {
    val effects = bound.viewIds.filter { id -> play.board.stackEffects.any { it.id == id } }
    val combat = bound.viewIds.filter { id -> CombatStackRules.occurrences(play.board.combat).any { it.id == id } }
    if (effects.isNotEmpty()) {
        ActionSquare("Remove Stack effect", "▱−", enabled,
            onClick = { toolkit.removeOccurrences(bound.copy(viewIds = effects)) })
        if (effects.size == 1) {
            val id = effects.single()
            val order = CombatStackRules.stack(play.board).cards
            val at = order.indexOf(id)
            val expected = IdentityGap(order.getOrNull(at - 1), order.getOrNull(at + 1))
            val remaining = order.filter { it != id }
            ActionSquare("Move effect to top", "⇑", enabled, onClick = {
                toolkit.reorderEffect(id, expected, IdentityGap(beforeId = remaining.firstOrNull()))
            })
            ActionSquare("Move effect to bottom", "⇓", enabled, onClick = {
                toolkit.reorderEffect(id, expected, IdentityGap(afterId = remaining.lastOrNull()))
            })
        }
    }
    if (combat.isNotEmpty()) ActionSquare("Remove from combat", "⊖", enabled,
        onClick = { toolkit.removeOccurrences(bound.copy(viewIds = combat)) })
}
