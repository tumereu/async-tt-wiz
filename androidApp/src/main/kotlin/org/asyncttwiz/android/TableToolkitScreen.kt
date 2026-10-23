package org.asyncttwiz.android

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    val tools = if (bound != null) cardMenuTools(toolkit, play, bound, names, enabled, LocalTableMenuAnnotations.current) else emptyList()
    ManualActionsHost(play, toolkit.viewer, toolkit.manual, enabled, landscape, names, extras = {
        if (bound != null) {
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                ManualActionSquare("Select / deselect", "", true, selected = bound.contextViewId in selection.ids,
                    onClick = { select(bound.contextViewId) })
                if (selection.mode == TableMode.SELECT) for (tapped in listOf(true, false))
                    ManualActionSquare(if (tapped) "Tap selected scope" else "Untap selected scope", "", enabled,
                        onClick = { if (toolkit.handle(TableIntent.Rotate(TableScope(bound.contextViewId, bound.viewIds), tapped, bound.sources))) toolkit.dismiss() })
                if (toolkit.tokens.scope != null) ManualActionSquare("Clone", "", enabled && bound.sourceIds.isNotEmpty(), onClick = {
                    toolkit.batch { toolkit.tokens.clone() }; if (toolkit.tokens.error == null) toolkit.dismiss()
                })
                if (toolkit.tokens.scope != null && bound.sourceIds.any { id -> play.board.cards.any { it.id == id && it.token } })
                    ManualActionSquare("Remove token", "", enabled, onClick = {
                        toolkit.batch { toolkit.tokens.removeTokens() }; if (toolkit.tokens.error == null) toolkit.dismiss()
                    })
                if (toolkit.tokens.projectionScope != null && bound.sourceIds.isNotEmpty())
                    ManualActionSquare("Source actions", "", true, onClick = { toolkit.openCard(TableScope(bound.sourceIds.first(), bound.sourceIds)) })
                val realEndpoints = toolkit.tokens.scope != null || toolkit.tokens.projectionScope != null
                ManualActionSquare("Link", "", enabled && realEndpoints, onClick = { toolkit.tokens.beginLink(); toolkit.manual.close() })
                ManualActionSquare("Links", "", realEndpoints, onClick = { toolkit.tokens.links(); toolkit.manual.close() })
            }
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                val battlefield = bound.sourceIds.any { id -> play.board.cards.any { card -> card.id == id &&
                    play.board.containers.any { it.id == card.containerId && it.zone == PlayZone.BATTLEFIELD } } }
                ManualActionSquare("Declare without tapping", "", enabled && battlefield, onClick = { if (toolkit.declare(bound.sourceIds, false)) toolkit.dismiss() })
                ManualActionSquare("Declare + tap", "", enabled && battlefield, onClick = { if (toolkit.declare(bound.sourceIds, true)) toolkit.dismiss() })
                val effect = bound.sourceIds.any { id -> play.board.cards.any { card -> card.id == id &&
                    play.board.containers.any { it.id == card.containerId && it.zone in setOf(PlayZone.BATTLEFIELD, PlayZone.GRAVEYARD, PlayZone.EXILE) } } }
                ManualActionSquare("Add Stack effect", "", enabled && effect, onClick = { if (toolkit.addEffects(bound.sourceIds)) toolkit.dismiss() })
                OccurrenceRemoval(toolkit, play, bound, enabled)
            }
            BasicMoveControls(play, names, enabled, TableScope(bound.sourceIds.first(), bound.sourceIds),
                bound.sources, toolkit::handle, toolkit::dismiss)
            if (play.board.combat != null) CombatDestinationActions(
                CombatStackPresentation.combat(play.board, 48f, 68f).destinations.map { it.label to it.destination }, enabled) {
                if (toolkit.place(bound, it)) toolkit.dismiss()
            }
        }
    }, submitAll = toolkit::submitAll, completeValue = toolkit::completeValue, tools = tools) { action -> toolkit.submit(action) }

    when (val panel = toolkit.panel) {
        "table" -> {
            val suggestion = accepted?.board?.resultSuggestion
            TableActionMenu(play, toolkit.viewer, context, toolkit.table, endingColor, toolkit::dismiss,
                pendingSuggestionId = suggestion?.id, mayAccept = suggestion?.author?.let { it != toolkit.viewer } == true,
                perform = toolkit::perform, blankToken = {
                    toolkit.tokens.catalog(); toolkit.tokens.create(TokenCatalog.blank); !toolkit.tokens.showingCatalog
                })
        }
        "phase" -> PhaseStepPicker(play, toolkit.viewer, endingColor, toolkit.table, toolkit::dismiss)
        "library" -> toolkit.library?.let { library -> ToolkitDialog(toolkit::dismiss, "Library") {
            LibraryActions(library.controller, play.board, library.handIds, initialTool = library.tool,
                names = names, onComplete = toolkit::dismiss)
            play.board.contentsDisplays.singleOrNull { it.id == toolkit.libraryDisplayId }?.let { display ->
                ContentsScreen(play, display, toolkit.viewer, library.controller, toolkit::inspect,
                    Modifier.height(300.dp), onActions = { toolkit.openCard(selection.scope(it, display.cardIds)) }, names = names,
                    onComplete = toolkit::dismiss)
            }
        } }
        "occurrence" -> if (bound != null) ToolkitDialog(toolkit::dismiss) {
            Text("Retained source · read-only")
            ManualActionSquare("Inspect", "", true, onClick = { toolkit.inspect(bound.contextViewId) })
            ManualActionSquare("Select / deselect", "", true, onClick = { select(bound.contextViewId) })
            if (toolkit.tokens.projectionScope != null) {
                ManualActionSquare("Link", "", enabled, onClick = { toolkit.tokens.beginLink(); toolkit.dismiss() })
                ManualActionSquare("Links", "", true, onClick = { toolkit.tokens.links(); toolkit.dismiss() })
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
        else -> if (panel?.startsWith("contents/") == true) {
            val pile = toolkit.publicContents()
            if (pile == null) LaunchedEffect(panel) { toolkit.dismiss() }
            else TableMenuPanel(toolkit::dismiss, title = "${pile.title} · ${pile.cardIds.size}${if (pile.ordered) " entries" else ""}") {
                ContentsScreen(play, pile, toolkit.viewer, modifier = Modifier.height(if (landscape) 220.dp else 480.dp),
                    onActions = { if (selection.mode == TableMode.SELECT) select(it) else toolkit.openCard(selection.scope(it, pile.cardIds)) },
                    onInspect = toolkit::inspect,
                    onRotate = if (selection.mode != TableMode.NORMAL) null else { id ->
                        val card = CombatStackRules.liveSource(play.board, id)
                        if (card != null) {
                            toolkit.selectionState.value = selection.orientation(id)
                            toolkit.handle(TableIntent.Rotate(selection.scope(id, pile.cardIds), !card.tapped))
                        }
                    })
            }
        } else if (panel?.startsWith("display/") == true) {
            val display = play.board.contentsDisplays.singleOrNull { it.id == panel.substringAfter('/') }
            if (display == null) LaunchedEffect(panel) { toolkit.dismiss() }
            else {
                val container = play.board.containers.single { it.id == display.containerId }
                val hand = play.board.containers.first { it.zone == PlayZone.HAND && it.accountId == (container.accountId ?: toolkit.viewer) }
                val controller = remember(toolkit, display.id) { toolkit.libraryController(container.id, hand.id) }
                if (controller == null) LaunchedEffect(display.id) { toolkit.dismiss() }
                else TableMenuPanel(toolkit::dismiss, title = if (display.reveal) "Reveal" else "View") {
                    ContentsScreen(play, display, toolkit.viewer, controller, toolkit::inspect,
                        Modifier.height(480.dp), onActions = { toolkit.openCard(selection.scope(it, display.cardIds)) }, names = names,
                        onComplete = toolkit::dismiss)
                }
            }
        }
    }
    TokenCatalogDialog(toolkit.tokens, play.definitions, enabled = enabled,
        loading = tokenCatalog?.loading == true, catalogError = tokenCatalog?.error,
        catalogIdentity = tokenCatalog?.identity ?: listOf(toolkit.viewer, play.gameId, play.id),
        searchSource = tokenCatalog?.source)
    LinkEndpointPicker(toolkit.tokens, play, names, enabled = enabled, onPick = { toolkit.batch { toolkit.tokens.pick(it) } })
    LinksListDialog(toolkit.tokens, play, names, enabled = enabled)
    toolkit.inspection?.let { GameplayCardInspection(play, toolkit.viewer, it, toolkit::closeInspection, names) }
    if (toolkit.manual.scope?.usable(play) == false) LaunchedEffect(toolkit.manual.scope) { toolkit.manual.close() }
}

internal fun toolkitQuickContext(play: PlayState, selection: TableSelection, explicit: ExplicitStackTarget?): QuickContext = QuickContext(
    selectMode = selection.mode == TableMode.SELECT,
    selectedSourceIds = selection.ids.mapNotNull { CombatStackRules.liveSource(play.board, it)?.id }.distinct(),
    combatAreaId = play.board.combat?.id,
    stackTarget = explicit?.takeIf { it.id in CombatStackRules.stack(play.board).cards })

@Composable
private fun ToolkitDialog(dismiss: () -> Unit, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    TableMenuPanel(dismiss, title = title, content = content)
}

/** The dense first page uses the same bound move sources as the full destination area. */
private fun cardMenuTools(toolkit: TableToolkitController, play: PlayState, bound: ToolkitCapture,
    names: Map<String, String>, enabled: Boolean, annotations: TableMenuAnnotations?): List<MenuControl> {
    val card = play.board.cards.singleOrNull { it.id == toolkit.manual.scope?.contextId } ?: return emptyList()
    val moves = listOf(PlayZone.LIBRARY to false, PlayZone.LIBRARY to true, PlayZone.GRAVEYARD to false, PlayZone.EXILE to false)
        .mapNotNull { (zone, bottom) ->
            val target = play.board.containers.singleOrNull { it.accountId == card.owner && it.zone == zone } ?: return@mapNotNull null
            val name = when (zone) {
                PlayZone.LIBRARY -> if (bottom) "Library bottom" else "Library top"
                PlayZone.GRAVEYARD -> "Graveyard"
                else -> "Exile"
            } + " · ${names[card.owner] ?: "Player"}"
            MenuControl(name, enabled) {
                if (toolkit.handle(TableIntent.Move(TableScope(bound.sourceIds.first(), bound.sourceIds),
                        TableDestination.Container(target.id, zone, bottom), bound.sources))) toolkit.dismiss()
            }
        }
    val battlefield = play.board.containers.any { it.id == card.containerId && it.zone == PlayZone.BATTLEFIELD }
    val effect = play.board.containers.any { it.id == card.containerId && it.zone in setOf(PlayZone.BATTLEFIELD, PlayZone.GRAVEYARD, PlayZone.EXILE) }
    return moves + listOf(
        MenuControl("Clone", enabled && toolkit.tokens.scope != null) {
            toolkit.batch { toolkit.tokens.clone() }; if (toolkit.tokens.error == null) toolkit.dismiss()
        },
        MenuControl("Link", enabled) { toolkit.tokens.beginLink(); toolkit.manual.close() },
        MenuControl("Ping / Unping", enabled && annotations != null) {
            if (annotations != null) {
                val intents = bound.viewIds.map { id -> tablePingIntent(play, annotations.reference,
                    PingTarget(if (play.board.cards.any { it.id == id }) PingTargetKind.CARD else PingTargetKind.OCCURRENCE, id)) }
                var sent = false
                intents.forEach { sent = annotations.ping(it) || sent }
                if (sent) toolkit.dismiss()
            }
        },
        MenuControl("Add Stack effect", enabled && effect) { if (toolkit.addEffects(bound.sourceIds)) toolkit.dismiss() },
        MenuControl("Declare without tapping", enabled && battlefield) { if (toolkit.declare(bound.sourceIds, false)) toolkit.dismiss() },
        MenuControl("Declare + tap", enabled && battlefield) { if (toolkit.declare(bound.sourceIds, true)) toolkit.dismiss() },
        MenuControl("Unlink", enabled) { toolkit.tokens.links(); toolkit.manual.close() }
    )
}

@Composable
private fun OccurrenceRemoval(toolkit: TableToolkitController, play: PlayState, bound: ToolkitCapture, enabled: Boolean) {
    val effects = bound.viewIds.filter { id -> play.board.stackEffects.any { it.id == id } }
    val combat = bound.viewIds.filter { id -> CombatStackRules.occurrences(play.board.combat).any { it.id == id } }
    if (effects.isNotEmpty()) {
        ManualActionSquare("Remove Stack effect", "", enabled,
            onClick = { if (toolkit.removeOccurrences(bound.copy(viewIds = effects))) toolkit.dismiss() })
        if (effects.size == 1) {
            val id = effects.single()
            val order = CombatStackRules.stack(play.board).cards
            val at = order.indexOf(id)
            val expected = IdentityGap(order.getOrNull(at - 1), order.getOrNull(at + 1))
            val remaining = order.filter { it != id }
            ManualActionSquare("Move effect to top", "", enabled, onClick = {
                if (toolkit.reorderEffect(id, expected, IdentityGap(beforeId = remaining.firstOrNull()))) toolkit.dismiss()
            })
            ManualActionSquare("Move effect to bottom", "", enabled, onClick = {
                if (toolkit.reorderEffect(id, expected, IdentityGap(afterId = remaining.lastOrNull()))) toolkit.dismiss()
            })
        }
    }
    if (combat.isNotEmpty()) ManualActionSquare("Remove from combat", "", enabled,
        onClick = { if (toolkit.removeOccurrences(bound.copy(viewIds = combat))) toolkit.dismiss() })
}
