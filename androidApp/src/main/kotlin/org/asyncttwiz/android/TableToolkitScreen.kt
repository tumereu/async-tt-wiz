package org.asyncttwiz.android

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.asyncttwiz.protocol.*

/** The production overlay tree, mounted once alongside the Canvas and using its captured IDs. */
@Composable
internal fun TableToolkitScreen(toolkit: TableToolkitController, play: PlayState, game: GameSummary,
    landscape: Boolean, selection: TableSelection,
    accepted: PlayState? = null, tokenCatalog: TableTokenCatalog? = null, peekScroll: ScrollState? = null, scene: TableScene? = null,
    onTimeline: (() -> Unit)? = null, selectionEnabled: Boolean = true) {
    val names = mapOf(game.inviter.accountId to game.inviter.username, game.recipient.accountId to game.recipient.username)
    val enabled = toolkit.enabled()
    val endingColor = if (play.board.turn.accountId == toolkit.viewer) OwnTableColor else OpponentTableColor
    val context = toolkitQuickContext(play, selection, toolkit.explicitStack, toolkit.viewer)
    val bound = toolkit.capture
    val popup = toolkit.popupItem
    fun editProjectionNote(id: String) {
        toolkit.manual.projectionNote(play, id, toolkit.viewer, bound?.viewIds ?: listOf(id))
    }
    CompositionLocalProvider(LocalInlinePopupAnchor provides InlinePopupAnchor(popup?.id ?: "table",
        popup?.bounds, popup?.owner == null || popup.owner == toolkit.viewer)) {
    val annotations = LocalTableMenuAnnotations.current
    val tools = if (bound == null) emptyList() else cardContextTools(toolkit, play, bound, selection, names, enabled, annotations)
    ManualActionsHost(play, toolkit.viewer, toolkit.manual, enabled, landscape, names,
        submitAll = toolkit::submitAll, completeValue = toolkit::completeValue, tools = tools,
        previewViewId = bound?.contextViewId, inspect = bound?.let { { toolkit.inspect(it.contextViewId) } },
        editProjectionNote = bound?.let { { editProjectionNote(it.contextViewId) } }) { action -> toolkit.submit(action) }

    when (val panel = toolkit.panel) {
        "attackTarget" -> if (bound != null) InlinePopupPanel(toolkit::dismiss, "Attack →") {
            // Only a target shared by every selected attacker counts as current (null is the player).
            val targets = CombatStackController.attackerPairs(play.board, bound.viewIds).map { it.attackTarget }.distinct()
            val defender = listOf(game.inviter, game.recipient).find { it.accountId == CombatStackRules.defendingPlayer(play.board) }
            for ((id, label) in attackTargetChoices(play, names, toolkit.viewer)) {
                val current = targets.size == 1 && targets.single() == id
                AttackTargetRow(play, id, label, defender, current = current, enabled = enabled && !current) {
                if (toolkit.setAttackTarget(bound, id)) toolkit.dismiss()
            } }
        }
        "manageStack" -> ManageStackDialog(play, toolkit.viewer, enabled, toolkit::dismiss,
            perform = { toolkit.handle(it) })
        "table" -> {
            val suggestion = accepted?.board?.resultSuggestion
            TableActionMenu(play, toolkit.viewer, context, toolkit.table, endingColor, toolkit::dismiss,
                pendingSuggestionId = suggestion?.id, mayAccept = suggestion?.author?.let { it != toolkit.viewer } == true,
                perform = toolkit::perform, names = names, onTimeline = onTimeline)
        }
        "phase" -> PhaseStepPicker(play, toolkit.viewer, endingColor, toolkit.table, toolkit::dismiss, names = names)
        "mulligan" -> toolkit.library?.let { library ->
            val owner = play.board.containers.single { it.id == library.controller.libraryId }.accountId.orEmpty()
            MulliganDialog(library.controller, library.handIds, owner, toolkit::dismiss, toolkit::dismiss)
        }
        "library" -> toolkit.library?.let { library -> InlinePopupPanel(toolkit::dismiss, "Library", width = 264.dp, height = 304.dp) {
            LibraryActions(library.controller, play.board, library.handIds,
                onComplete = toolkit::dismiss, onShow = toolkit::showLibraryContents)
        } }
        "temp" -> toolkit.library?.let { library -> TempPiles.refs(play)[library.controller.libraryId]?.let { ref ->
            InlinePopupPanel(toolkit::dismiss, "Temp ${ref.number}", width = 264.dp, height = 288.dp,
                key = "temp/${library.controller.libraryId}") {
                val tempId = library.controller.libraryId
                TempPileActions(library.controller, play.board, ref, onShow = toolkit::showLibraryContents,
                    toLibrary = { bottom -> toolkit.tempToLibrary(tempId, bottom) },
                    toZone = { zone -> toolkit.tempToZone(tempId, zone) },
                    shuffleIntoLibrary = { toolkit.tempShuffleIntoLibrary(tempId) },
                    toPile = { number -> toolkit.tempToPile(tempId, number) },
                    topToPile = { number -> toolkit.tempTopToPile(tempId, number) },
                    // Turning this very pile off removes it from the table, so its menu closes too.
                    close = { toolkit.closeTempPile(ref.accountId, ref.number) },
                    onComplete = toolkit::dismiss)
            }
        } }
        "occurrence" -> if (bound != null && toolkit.manual.editor == null) {
            // The source left play: the same combat/effect layout, with source-dependent cells disabled.
            val layout = if (play.board.stackEffects.any { it.id == bound.contextViewId }) CardMenuLayout.STACK_EFFECT else CardMenuLayout.COMBAT
            val occurrenceTools = listOf(MenuControl("Inspect") { toolkit.inspect(bound.contextViewId) },
                MenuControl("Edit card note", enabled) { editProjectionNote(bound.contextViewId) }) +
                projectionMenuTools(toolkit, play, bound, names, enabled, annotations)
            CardActionRing(play.gameId, toolkit.viewer, bound.contextViewId, bound.contextViewId,
                occurrenceTools.map { RingEntry(when (it.name) { "Inspect" -> "inspect"; "Edit card note" -> "note"; else -> it.name },
                    if (it.name == "Edit card note") "Note" else ringShortLabel(it.name), it) },
                own = false, OpponentTableColor, toolkit::dismiss, layout)
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
        else -> if (panel?.startsWith("pile/") == true) {
            val pile = play.board.containers.singleOrNull { it.id == panel.substringAfter('/') && it.zone in PublicPiles.zones }
            if (pile == null) LaunchedEffect(panel) { toolkit.dismiss() }
            else InlinePopupPanel(toolkit::dismiss, moveZoneLabel(pile.zone), width = 152.dp, height = 104.dp, key = panel) {
                PublicPileActions(toolkit, play, pile)
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

/** The tools of the captured context's layout (cells CardActions builds itself are not included). */
internal fun cardContextTools(toolkit: TableToolkitController, play: PlayState, bound: ToolkitCapture, selection: TableSelection,
    names: Map<String, String>, enabled: Boolean, annotations: TableMenuAnnotations?): List<MenuControl> =
    when (CardRingPages.context(play.board, bound.contextViewId)) {
        CardMenuLayout.HAND -> handMenuTools(toolkit, play, bound, selection, names, enabled)
        CardMenuLayout.COMBAT, CardMenuLayout.STACK_EFFECT -> projectionMenuTools(toolkit, play, bound, names, enabled, annotations)
        else -> cardMenuTools(toolkit, play, bound, names, enabled, annotations) + cardExtraTools(toolkit, play, bound, names, enabled)
    }

/** All tools retain the captured sources and the existing gameplay routes. */
private fun cardMenuTools(toolkit: TableToolkitController, play: PlayState, bound: ToolkitCapture,
    names: Map<String, String>, enabled: Boolean, annotations: TableMenuAnnotations?): List<MenuControl> {
    val card = play.board.cards.singleOrNull { it.id == toolkit.manual.scope?.contextId } ?: return emptyList()
    val battlefield = play.board.containers.any { it.id == card.containerId && it.zone == PlayZone.BATTLEFIELD }
    return listOf(
        MenuControl("Clone", enabled && bound.sourceIds.isNotEmpty()) {
            if (toolkit.tokens.scope != null) {
                toolkit.batch { toolkit.tokens.clone() }; if (toolkit.tokens.error == null) toolkit.dismiss()
            } else { toolkit.submitAll(bound.sourceIds.map { PlayAction.CloneToken(it) }); toolkit.dismiss() }
        },
        MenuControl("Link", enabled && toolkit.tokens.canLink()) { if (toolkit.tokens.linkSelected()) toolkit.dismiss() },
        cardPingTool(toolkit, play, bound, enabled, annotations),
        MenuControl("Declare without tapping", enabled && battlefield) { if (toolkit.declare(bound.sourceIds, false)) toolkit.dismiss() },
        MenuControl("Stack", enabled && bound.sourceIds.any { id -> play.board.cards.any {
            it.id == id && CombatStackRules.canProject(play.board, it) } }) {
            if (toolkit.addEffects(bound.sourceIds)) toolkit.dismiss()
        },
        MenuControl("Unlink", enabled) { toolkit.tokens.links(); toolkit.manual.close() }
    )
}

internal fun cardPingTool(toolkit: TableToolkitController, play: PlayState, bound: ToolkitCapture,
    enabled: Boolean, annotations: TableMenuAnnotations?) = MenuControl("Ping / Unping", enabled && annotations != null) {
    if (annotations != null) {
        val intents = bound.viewIds.map { id -> tablePingIntent(play, annotations.reference,
            PingTarget(if (play.board.cards.any { it.id == id }) PingTargetKind.CARD else PingTargetKind.OCCURRENCE, id)) }
        if (annotations.submit(intents.flatMap { it.actions() })) toolkit.dismiss()
    }
}

/** Combat copies and Stack effect markers: only projection controls, never card modifications.
 * Every cell of both layouts is returned; cells needing a live source are disabled once it left play. */
internal fun projectionMenuTools(toolkit: TableToolkitController, play: PlayState, bound: ToolkitCapture,
    names: Map<String, String>, enabled: Boolean, annotations: TableMenuAnnotations?): List<MenuControl> {
    val effects = bound.viewIds.filter { id -> play.board.stackEffects.any { it.id == id } }
    val combat = bound.viewIds.filter { id -> CombatStackRules.occurrences(play.board.combat).any { it.id == id } }
    val live = combat.filter { CombatStackRules.liveSource(play.board, it) != null }
    val sources = live.mapNotNull { CombatStackRules.liveSource(play.board, it)?.id }.distinct()
    val moves = if (sources.isEmpty()) emptyList() else basicMoveTools(play, names, enabled, TableScope(sources.first(), sources),
        bound.sources.filterKeys { it in sources }, toolkit::handle, toolkit::dismiss)
    fun move(name: String) = moves.firstOrNull { it.name == name } ?: MenuControl(name, false) {}
    val linking = toolkit.tokens.projectionScope != null
    return listOf(
        cardPingTool(toolkit, play, bound, enabled, annotations),
        MenuControl("Attack →", enabled && CombatStackController.attackerPairs(play.board, bound.viewIds).isNotEmpty()) {
            toolkit.openAttackTargets()
        },
        MenuControl("Remove from combat", enabled && combat.isNotEmpty()) {
            if (toolkit.removeOccurrences(bound.copy(viewIds = combat))) toolkit.dismiss()
        },
        MenuControl("Graveyard", enabled && live.isNotEmpty()) {
            toolkit.batch { live.forEach { toolkit.submit(CombatStackController.graveyard(play.board, it)) } }
            toolkit.dismiss()
        },
        move("Exile"), move("Hand"),
        MenuControl("Remove Stack effect", enabled && effects.isNotEmpty()) {
            if (toolkit.removeOccurrences(bound.copy(viewIds = effects))) toolkit.dismiss()
        },
        MenuControl("Link", enabled && linking && toolkit.tokens.canLink()) { if (toolkit.tokens.linkSelected()) toolkit.dismiss() },
        MenuControl("Links", linking) { toolkit.tokens.links(); toolkit.dismiss() }) +
        combatDamageMenuTools(toolkit, play, bound, enabled)
}

/** Battlefield/Stack and panel cards. Every cell is supplied, disabled when it does not apply. */
private fun cardExtraTools(toolkit: TableToolkitController, play: PlayState, bound: ToolkitCapture,
    names: Map<String, String>, enabled: Boolean): List<MenuControl> = buildList {
    val tokens = bound.sourceIds.filter { id -> play.board.cards.any { it.id == id && it.token } }
    fun removeTokens() {
        if (toolkit.tokens.scope != null) {
            toolkit.batch { toolkit.tokens.removeTokens() }; if (toolkit.tokens.error == null) toolkit.dismiss()
        } else {
            toolkit.submitAll(tokens.map { PlayAction.RemoveToken(it) })
            toolkit.dismiss()
        }
    }
    add(MenuControl("Remove token", enabled && tokens.isNotEmpty()) { removeTokens() })
    // A declared attacker retargets its attacker copies, exactly like the copy's own "Attack →".
    val attackers = play.board.combat?.pairs.orEmpty().flatMap { it.attackers }.filter { it.sourceId in bound.sourceIds }.map { it.id }
    add(MenuControl("Attack →", enabled && attackers.isNotEmpty()) { toolkit.openAttackTargets(attackers) })
    addAll(combatDamageMenuTools(toolkit, play, bound, enabled))
    if (bound.sourceIds.isNotEmpty()) addAll(basicMoveTools(play, names, enabled, TableScope(bound.sourceIds.first(), bound.sourceIds),
        bound.sources, toolkit::handle, toolkit::dismiss))
    // Moving a token into a library destroys it.
    if (play.board.cards.any { it.id == bound.sourceIds.firstOrNull() && it.token })
        for (name in listOf("Library top", "Library bottom", "Shuffle into library")) add(MenuControl(name, enabled && tokens.isNotEmpty()) { removeTokens() })
    addAll(tempPileTools(toolkit, play, bound.sourceIds, enabled))
}

/** The same targets, in the same order, as the combat area's lanes. */
internal fun attackTargetChoices(play: PlayState, names: Map<String, String>, viewer: String): List<Pair<String?, String>> =
    listOf(null to (names[CombatStackRules.defendingPlayer(play.board)] ?: "Player")) +
        CombatStackPresentation.attackTargets(play.board, play.definitions, viewer).map { id ->
            val card = play.board.cards.single { it.id == id }
            id to (play.definitions.find { it.digest == card.definition }?.definition?.faces
                ?.find { it.faceId == card.faceId }?.name ?: "Card")
        }

/** Target picture (player avatar or card art) beside its full name; the current target is marked and inert. */
@Composable
private fun AttackTargetRow(play: PlayState, cardId: String?, label: String, defender: AccountIdentity?,
    current: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val ink = MenuInk.copy(alpha = if (enabled || current) 1f else .38f)
    // The same opaque, bordered, shadowed cell as every other floating popup control; the gaps stay outside.
    RingPress(MenuControl("Attack $label${if (current) ", current target" else ""}", enabled, selected = current, click = onClick),
        Modifier.fillMaxWidth().heightIn(min = 44.dp), LocalInlinePopupColor.current) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        val card = cardId?.let { id -> play.board.cards.find { it.id == id } }
        if (card == null) {
            if (defender != null) Avatar(defender, size = 32.dp)
        } else {
            val definition = play.definitions.find { it.digest == card.definition }?.definition
            val face = definition?.faces?.find { it.faceId == card.faceId }
            val art = face?.tableArtwork(definition.faces.indexOf(face), card.definition)?.forTable()
            CardArtwork(art, "", 192, 140, Modifier.size(32.dp, 32.dp).clip(MaterialTheme.shapes.extraSmall), crop = true)
        }
        Text(label, Modifier.weight(1f).padding(start = 8.dp), color = ink, fontSize = 13.sp, lineHeight = 15.sp,
            fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (current) Icon(menuIcon("Accept"), null, Modifier.size(18.dp), tint = OwnTableColor)
    }
    }
}
