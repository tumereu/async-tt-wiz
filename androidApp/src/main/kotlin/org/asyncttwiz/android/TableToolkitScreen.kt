package org.asyncttwiz.android

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.ScrollState
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
    accepted: PlayState? = null, tokenCatalog: TableTokenCatalog? = null, peekScroll: ScrollState? = null, scene: TableScene? = null,
    onTimeline: (() -> Unit)? = null) {
    val names = mapOf(game.inviter.accountId to game.inviter.username, game.recipient.accountId to game.recipient.username)
    val enabled = toolkit.enabled()
    val endingColor = if (play.board.turn.accountId == toolkit.viewer) OwnTableColor else OpponentTableColor
    val context = toolkitQuickContext(play, selection, toolkit.explicitStack, toolkit.viewer)
    val bound = toolkit.capture
    val popup = toolkit.popupItem
    var projectionNote by remember(play.gameId, play.id) { mutableStateOf<ValueDraft?>(null) }
    fun editProjectionNote(id: String) {
        projectionNote = ValueDraft(ManualScope(play.gameId, play.id, id, listOf(id)),
            ManualValue(ManualValueKind.NOTE), CombatStackRules.inspection(play.board, id)?.note.orEmpty())
        toolkit.dismiss()
    }
    projectionNote?.let { draft ->
        val card = CombatStackRules.inspection(play.board, draft.scope.contextId)
        ValueEditor(draft, card?.note, enabled && draft.scope.contextId in CombatStackRules.projectionIds(play.board),
            onCancel = { projectionNote = null }, onSubmit = {
                toolkit.submit(PlayAction.SetNote(draft.scope.contextId, draft.text.trim())).also { if (it) projectionNote = null }
            }, preview = {})
    }
    CompositionLocalProvider(LocalInlinePopupAnchor provides InlinePopupAnchor(popup?.id ?: "table",
        popup?.bounds, popup?.owner == null || popup.owner == toolkit.viewer)) {
    val tools = if (bound != null) cardMenuTools(toolkit, play, bound, names, enabled, LocalTableMenuAnnotations.current) +
        cardExtraTools(toolkit, play, bound, selection, select, names, enabled) else emptyList()
    ManualActionsHost(play, toolkit.viewer, toolkit.manual, enabled, landscape, names,
        submitAll = toolkit::submitAll, completeValue = toolkit::completeValue, tools = tools,
        previewViewId = bound?.contextViewId, inspect = bound?.let { { toolkit.inspect(it.contextViewId) } },
        editProjectionNote = bound?.let { { editProjectionNote(it.contextViewId) } }) { action -> toolkit.submit(action) }

    when (val panel = toolkit.panel) {
        "manageStack" -> ManageStackDialog(play, toolkit.viewer, enabled, toolkit::dismiss,
            perform = { toolkit.handle(it) }, cardActions = { id -> toolkit.openCard(TableScope(id, listOf(id))) })
        "table" -> {
            val suggestion = accepted?.board?.resultSuggestion
            TableActionMenu(play, toolkit.viewer, context, toolkit.table, endingColor, toolkit::dismiss,
                pendingSuggestionId = suggestion?.id, mayAccept = suggestion?.author?.let { it != toolkit.viewer } == true,
                perform = toolkit::perform, names = names, onTimeline = onTimeline)
        }
        "phase" -> PhaseStepPicker(play, toolkit.viewer, endingColor, toolkit.table, toolkit::dismiss, names = names)
        "library" -> toolkit.library?.let { library -> InlinePopupPanel(toolkit::dismiss, "Library", width = 264.dp, height = 304.dp) {
            LibraryActions(library.controller, play.board, library.handIds,
                onComplete = toolkit::dismiss, onShow = toolkit::showLibraryContents)
        } }
        "occurrence" -> if (bound != null) {
            val occurrenceTools = listOf(MenuControl("Inspect") { toolkit.inspect(bound.contextViewId) },
                MenuControl("Note", enabled) { editProjectionNote(bound.contextViewId) },
                cardPingTool(toolkit, play, bound, enabled, LocalTableMenuAnnotations.current),
                MenuControl("Select / deselect") { select(bound.contextViewId) }) +
                (if (toolkit.tokens.projectionScope != null) listOf(
                    MenuControl("Link", enabled && toolkit.tokens.canLink()) { if (toolkit.tokens.linkSelected()) toolkit.dismiss() },
                    MenuControl("Links") { toolkit.tokens.links(); toolkit.dismiss() }) else emptyList()) +
                occurrenceRemovalTools(toolkit, play, bound, enabled)
            CardActionRing(play.gameId, toolkit.viewer, bound.contextViewId, bound.contextViewId,
                occurrenceTools.map { RingEntry(when (it.name) { "Inspect" -> "inspect"; "Note" -> "note"; else -> it.name }, ringShortLabel(it.name), it) },
                own = false, OpponentTableColor, toolkit::dismiss)
        }
        "stackDestination" -> if (bound != null) InlinePopupPanel(toolkit::dismiss, "Stack destination") {
             val source = play.board.cards.singleOrNull { it.id == bound.contextViewId }
            if (source != null) for (row in toolkit.stackChoices.chunked(4)) Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (choice in row) InlinePopupCell(MenuControl(choice.label, enabled) {
                    val capturedSource = bound.sources[source.id]
                    if (capturedSource != null) {
                        val placement = capturedSource.placement?.let { p -> p.pileId?.let { PlayPlacement(BoardPosition(p.column.toInt(), p.row.toInt()), it) } }
                        if (toolkit.submit(PlayAction.MoveCard(source.id, MoveSource(capturedSource.containerId, placement), choice.destination))) toolkit.dismiss()
                    }
                })
            }
        }
        else -> if (panel?.startsWith("contents/") == true) {
            val pile = toolkit.publicContents()
            if (pile == null) LaunchedEffect(panel) { toolkit.dismiss() }
            else InlinePopupPanel(toolkit::dismiss, title = "${pile.title} · ${pile.cardIds.size}${if (pile.ordered) " entries" else ""}",
                height = if (landscape) 288.dp else 560.dp, key = panel, sheet = true) {
                ContentsScreen(play, pile, toolkit.viewer, modifier = Modifier.height(if (landscape) 220.dp else 480.dp),
                    onActions = { if (selection.mode == TableMode.SELECT) select(it) else toolkit.openCard(selection.scope(it, pile.cardIds)) },
                    onInspect = toolkit::inspect,
                    onStackIntent = if (pile.ordered && selection.mode == TableMode.NORMAL) toolkit::handle else null,
                    stackWritable = enabled,
                    onRotate = if (selection.mode != TableMode.NORMAL || play.board.containers.none {
                        it.id == pile.containerId && it.zone == PlayZone.BATTLEFIELD }) null else { id ->
                        val card = CombatStackRules.liveSource(play.board, id)
                        if (card != null) {
                            toolkit.selectionState.value = selection.orientation(id)
                            toolkit.handle(TableIntent.Rotate(selection.scope(id, pile.cardIds), !card.tapped))
                        }
                    })
            }
        } else if (scene != null) CardPanelPopup(toolkit, play, scene)
    }
    }
    TokenCatalogDialog(toolkit.tokens, play.definitions, enabled = enabled,
        loading = tokenCatalog?.loading == true, catalogError = tokenCatalog?.error,
        catalogIdentity = tokenCatalog?.identity ?: listOf(toolkit.viewer, play.gameId, play.id),
        searchSource = tokenCatalog?.source, favoritesSource = tokenCatalog?.favorites)
    LinksListDialog(toolkit.tokens, play, names, enabled = enabled)
    toolkit.inspection?.let { GameplayCardInspection(play, toolkit.viewer, it, toolkit::closeInspection, names) }
    toolkit.peek?.let { GameplayCardInspection(play, toolkit.viewer, it, { toolkit.peek(null) }, names, peek = true, peekScroll = peekScroll) }
    if (toolkit.manual.scope?.usable(play) == false) LaunchedEffect(toolkit.manual.scope) { toolkit.manual.close() }
}

internal fun toolkitQuickContext(play: PlayState, selection: TableSelection, explicit: ExplicitStackTarget?, viewer: String? = null): QuickContext = QuickContext(
    selectMode = selection.mode == TableMode.SELECT,
    selectedSourceIds = selection.ids.mapNotNull { CombatStackRules.liveSource(play.board, it)?.id }.distinct(),
    combatAreaId = play.board.combat?.id,
    stackTarget = explicit?.takeIf { it.id in CombatStackRules.stack(play.board).cards }, viewer = viewer)

@Composable
private fun ToolkitDialog(dismiss: () -> Unit, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    TableMenuPanel(dismiss, title = title, content = content)
}

/** All tools retain the captured sources and the existing gameplay routes. */
private fun cardMenuTools(toolkit: TableToolkitController, play: PlayState, bound: ToolkitCapture,
    names: Map<String, String>, enabled: Boolean, annotations: TableMenuAnnotations?): List<MenuControl> {
    val card = play.board.cards.singleOrNull { it.id == toolkit.manual.scope?.contextId } ?: return emptyList()
    val battlefield = play.board.containers.any { it.id == card.containerId && it.zone == PlayZone.BATTLEFIELD }
    return listOf(
        MenuControl("Clone", enabled && toolkit.tokens.scope != null) {
            toolkit.batch { toolkit.tokens.clone() }; if (toolkit.tokens.error == null) toolkit.dismiss()
        },
        MenuControl("Link", enabled && toolkit.tokens.canLink()) { if (toolkit.tokens.linkSelected()) toolkit.dismiss() },
        cardPingTool(toolkit, play, bound, enabled, annotations),
        MenuControl("Declare without tapping", enabled && battlefield) { if (toolkit.declare(bound.sourceIds, false)) toolkit.dismiss() },
        MenuControl("Declare + tap", enabled && battlefield) { if (toolkit.declare(bound.sourceIds, true)) toolkit.dismiss() },
        MenuControl("Unlink", enabled) { toolkit.tokens.links(); toolkit.manual.close() }
    )
}

private fun cardPingTool(toolkit: TableToolkitController, play: PlayState, bound: ToolkitCapture,
    enabled: Boolean, annotations: TableMenuAnnotations?) = MenuControl("Ping / Unping", enabled && annotations != null) {
    if (annotations != null) {
        val intents = bound.viewIds.map { id -> tablePingIntent(play, annotations.reference,
            PingTarget(if (play.board.cards.any { it.id == id }) PingTargetKind.CARD else PingTargetKind.OCCURRENCE, id)) }
        var sent = false
        intents.forEach { sent = annotations.ping(it) || sent }
        if (sent) toolkit.dismiss()
    }
}

private fun occurrenceRemovalTools(toolkit: TableToolkitController, play: PlayState, bound: ToolkitCapture, enabled: Boolean): List<MenuControl> = buildList {
    val effects = bound.viewIds.filter { id -> play.board.stackEffects.any { it.id == id } }
    val combat = bound.viewIds.filter { id -> CombatStackRules.occurrences(play.board.combat).any { it.id == id } }
    if (effects.isNotEmpty()) {
        add(MenuControl("Remove Stack effect", enabled) { if (toolkit.removeOccurrences(bound.copy(viewIds = effects))) toolkit.dismiss() })
    }
    if (combat.isNotEmpty()) {
        add(MenuControl("Undeclare", enabled) { if (toolkit.removeOccurrences(bound.copy(viewIds = combat))) toolkit.dismiss() })
        add(MenuControl("Graveyard", enabled) {
            toolkit.batch { combat.forEach { toolkit.submit(CombatStackController.graveyard(play.board, it)) } }
            toolkit.dismiss()
        })
    }
}

private fun cardExtraTools(toolkit: TableToolkitController, play: PlayState, bound: ToolkitCapture,
    selection: TableSelection, select: (String) -> Unit, names: Map<String, String>, enabled: Boolean): List<MenuControl> = buildList {
    add(MenuControl("Select / deselect", selected = bound.contextViewId in selection.ids) { select(bound.contextViewId) })
    if (selection.mode == TableMode.SELECT && play.board.cards.any { card -> card.id == bound.contextViewId &&
            play.board.containers.any { it.id == card.containerId && it.zone == PlayZone.BATTLEFIELD } }) for (tapped in listOf(true, false))
        add(MenuControl(if (tapped) "Tap selected scope" else "Untap selected scope", enabled) {
            if (toolkit.handle(TableIntent.Rotate(TableScope(bound.contextViewId, bound.viewIds), tapped, bound.sources))) toolkit.dismiss()
        })
    if (toolkit.tokens.scope != null && bound.sourceIds.any { id -> play.board.cards.any { it.id == id && it.token } })
        add(MenuControl("Remove token", enabled) { toolkit.batch { toolkit.tokens.removeTokens() }; if (toolkit.tokens.error == null) toolkit.dismiss() })
    if (toolkit.tokens.projectionScope != null && bound.sourceIds.isNotEmpty())
        add(MenuControl("Source actions") { toolkit.openCard(TableScope(bound.sourceIds.first(), bound.sourceIds)) })
    addAll(occurrenceRemovalTools(toolkit, play, bound, enabled))
    if (bound.sourceIds.isNotEmpty()) addAll(basicMoveTools(play, names, enabled, TableScope(bound.sourceIds.first(), bound.sourceIds),
        bound.sources, toolkit::handle, toolkit::dismiss))
    if (play.board.combat != null) for (destination in CombatStackPresentation.combat(play.board, 48f, 68f).destinations)
        add(MenuControl(destination.label, enabled) { if (toolkit.place(bound, destination.destination)) toolkit.dismiss() })
}
