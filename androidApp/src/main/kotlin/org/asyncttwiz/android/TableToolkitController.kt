package org.asyncttwiz.android

import androidx.compose.runtime.*
import org.asyncttwiz.protocol.*
import java.util.UUID
import androidx.lifecycle.ViewModel

data class ToolkitLibrary(val controller: LibraryController, val handIds: List<String>, val tool: LibraryTool)
data class ToolkitCapture(val viewIds: List<String>, val contextViewId: String,
    val sourceIds: List<String>, val sources: Map<String, TableMoveSource>, val areaId: String?)

/** Personal routes/captured input only. All mutations enter the one existing gameplay session.
 * No acknowledgement gate, board store, command envelope factory or second request queue lives here. */
class TableToolkitController(val viewer: String, private val state: () -> PlayState?,
    private val connected: () -> Boolean, private val gameplay: (PlayAction, String?) -> Boolean,
    private val ending: (TableActionIntent.Ending) -> Boolean = { false },
    private val acceptedInspection: ((PlayAction.InspectFace, String, () -> Boolean, (InspectionEvent) -> Unit) -> InspectionSubscription?)? = null,
    private val waitingForConnection: () -> Boolean = { false }) : TableActionRoutes {
    private val identity = state()?.let { TableActionScope(it.gameId, it.id) }
    fun current(): PlayState? = state()?.takeIf { TableActionScope(it.gameId, it.id) == identity }
    fun enabled() = connected() && current() != null
    val manual = ManualActions()
    val layoutState = mutableStateOf(TableLayoutState())
    val selectionState = mutableStateOf(TableSelection())
    val tokens = TokenLinkController(::current, { action -> submit(action) }, submitInvocation = { actions ->
        batch {
            var allQueued = true
            actions.forEach { if (!submit(it)) allQueued = false }
            allQueued
        }
    })
    val table = TableActionController(::current, ::enabled, { submit(it) }, this)
    private val basic = BasicTableController(::current, ::enabled, ::enqueue)
    private var invocation: String? = null
    private var lastInvocation: String? = null
    var panel by mutableStateOf<String?>(null); private set
    var popupItem by mutableStateOf<TableItem?>(null); private set
    var library by mutableStateOf<ToolkitLibrary?>(null); private set
    var capture by mutableStateOf<ToolkitCapture?>(null); private set
    var inspection by mutableStateOf<ManualInspection?>(null); private set
    var peek by mutableStateOf<ManualInspection?>(null); private set
    fun peek(id: String?) {
        if (id == null) { peek = null; return }
        val play = current() ?: return
        val card = CombatStackRules.inspection(play.board, id) ?: return
        peek = ManualInspection(play.gameId, play.id, id,
            ManualDisclosure.permittedFaces(play, card, viewer, inspecting = true).map { it.faceId },
            stackEntryId = id.takeIf { it in CombatStackRules.stack(play.board).cards })
    }
    var error by mutableStateOf<String?>(null); private set
    fun dismissError() { error = null }
    var explicitStack by mutableStateOf<ExplicitStackTarget?>(null); private set
    var stackChoices by mutableStateOf<List<StackMoveChoice>>(emptyList()); private set
    var stackBubbleEntry by mutableStateOf<String?>(null); private set
    private var tapReturnPanel: String? = null
    fun dismissTapUi() {
        val previous = tapReturnPanel
        tapReturnPanel = null
        dismiss(); showStackBubbles(null)
        panel = previous
    }
    private data class PendingStackBubble(val nodeId: String, val before: Set<String>, val sourceId: String, val effect: Boolean)
    private var pendingStackBubble: PendingStackBubble? = null
    private data class PendingStackAdvance(val nodeId: String, val entryId: String)
    private var pendingStackAdvance: PendingStackAdvance? = null
    fun showStackBubbles(id: String?) { pendingStackBubble = null; pendingStackAdvance = null; stackBubbleEntry = id }
    /** Follow the session's prediction as soon as it is published, without waiting for acceptance.
     * Real card IDs and request-derived effect IDs are identical in prediction and accepted state. */
    fun refreshStackBubbles() {
        val play = current() ?: return
        val stack = play.board.containers.singleOrNull { it.zone == PlayZone.STACK }?.cards.orEmpty()
        pendingStackBubble?.let { pending ->
            val added = stack.firstOrNull { id -> id !in pending.before &&
                if (pending.effect) play.board.stackEffects.any { it.id == id && it.sourceId == pending.sourceId }
                else id == pending.sourceId }
            if (added != null) stackBubbleEntry = stack.firstOrNull()
            if (added != null || play.currentNodeId != pending.nodeId) pendingStackBubble = null
        }
        pendingStackAdvance?.let { pending ->
            if (pending.entryId !in stack) stackBubbleEntry = stack.firstOrNull()
            if (pending.entryId !in stack || play.currentNodeId != pending.nodeId) pendingStackAdvance = null
        }
        if (stackBubbleEntry != stack.firstOrNull()) stackBubbleEntry = null
    }
    private var inspectionSubscription: InspectionSubscription? = null
    private var inspectionLifetime: String? = null
    private var reviewContext: String? = null
    fun reviewContext(context: String) {
        if (reviewContext != null && reviewContext != context) { closeInspection(); dismiss(); tokens.close(); showStackBubbles(null) }
        reviewContext = context
    }

    fun <T> batch(block: () -> T): T {
        val previous = invocation
        if (previous == null) invocation = UUID.randomUUID().toString()
        return try { block() } finally { invocation = previous }
    }
    fun submitAll(actions: List<PlayAction>) { batch { actions.forEach { submit(it) } } }
    fun completeValue(): Boolean = batch {
        val play = current()?.takeIf { enabled() } ?: return@batch false
        manual.submitValue(play, viewer) { submit(it) }
    }
    private fun enqueue(action: PlayAction, invocationId: String?): Boolean {
        if (lastInvocation != invocationId) { lastInvocation = invocationId; error = null }
        val play = current()
        val source = when (action) {
            is PlayAction.MoveCard -> action.cardId.takeIf { id -> action.removeStackEntryId == null && play?.board?.containers?.any {
                it.id == action.destination.containerId && it.zone == PlayZone.STACK && id !in it.cards } == true }
            is PlayAction.Stack -> (action.action as? StackAction.AddEffect)?.sourceId
            else -> null
        }
        val pending = if (source != null && play != null) PendingStackBubble(play.currentNodeId,
            CombatStackRules.stack(play.board).cards.toSet(), source, action is PlayAction.Stack) else null
        return gameplay(action, invocationId).also { sent ->
            if (!sent) error = if (waitingForConnection()) null else "One or more actions could not be queued. Already queued actions are retained."
            else {
                if (pending != null) pendingStackBubble = pending
                refreshStackBubbles()
            }
        }
    }

    /** Convenience defaults are explicit per-card requests with the SAME invocation as the move. */
    fun submit(action: PlayAction, invocationId: String? = invocation): Boolean {
        val play = current()?.takeIf { enabled() } ?: return false
        if (action is PlayAction.MoveCard && action.removeStackEntryId == null && play.board.containers.any {
                it.id == action.destination.containerId && it.zone == PlayZone.STACK }) {
            return enqueue(BasicTableController.projection(play, action) ?: return false,
                invocationId ?: UUID.randomUUID().toString())
        }
        if (action is PlayAction.InspectFace && acceptedInspection != null) return loggedInspection(action)
        val group = invocationId ?: UUID.randomUUID().toString()
        val actions = if (action is PlayAction.MoveCard) GameplayRules.moveActions(play,
            action.copy(destination = ownerMoveDestination(play.board, action.cardId, action.destination))) else listOf(action)
        if (!enqueue(actions.first(), group)) return false
        actions.drop(1).forEach { if (enabled()) enqueue(it, group) }
        // This is ordinary provisional disclosure, NOT an accepted result inferred from Boolean receipt.
        if (action is PlayAction.InspectFace) {
            manual.closeInspection()
            val card = current()?.board?.cards?.singleOrNull { it.id == action.cardId && it.containerId == action.containerId }
            if (card != null) inspection = ManualInspection(play.gameId, play.id, card.id,
                listOf(action.faceId), explicit = true, provisional = true)
        }
        return true
    }

    fun dismiss() { panel = null; library = null; manual.close(); capture = null; tapReturnPanel = null; popupItem = null }
    internal val hasBackOverlay get() = inspection != null || peek != null || manual.inspection != null ||
        manual.editor != null || manual.scope != null || panel != null || stackBubbleEntry != null ||
        tokens.showingCatalog || tokens.showingLinks || tokens.pickingLink
    internal fun dismissBackOverlay() {
        when {
            inspection != null || manual.inspection != null -> closeInspection()
            peek != null -> peek(null)
            manual.editor != null -> manual.cancelEditor()
            tokens.showingCatalog || tokens.showingLinks || tokens.pickingLink -> tokens.close()
            manual.scope != null || panel != null -> dismissTapUi()
            stackBubbleEntry != null -> showStackBubbles(null)
        }
    }
    fun closeInspection() { inspectionLifetime = null; inspectionSubscription?.close(); inspectionSubscription = null; inspection = null; manual.closeInspection() }
    private fun loggedInspection(action: PlayAction.InspectFace): Boolean {
        val play = current() ?: return false
        closeInspection()
        val lifetime = UUID.randomUUID().toString()
        inspectionLifetime = lifetime
        inspection = ManualInspection(play.gameId, play.id, action.cardId, listOf(action.faceId), explicit = true, provisional = true)
        val subscription = acceptedInspection?.invoke(action, lifetime, {
            inspectionLifetime == lifetime && inspection?.cardId == action.cardId &&
                current()?.board?.cards?.any { it.id == action.cardId && it.containerId == action.containerId } == true
        }) { event ->
            when (event) {
                is InspectionEvent.Accepted -> {
                    manual.inspected(event.state, viewer, event.disclosure)
                    inspection = manual.inspection
                    manual.closeInspection()
                }
                is InspectionEvent.NotAccepted -> { closeInspection(); error = "Inspection was not accepted." }
            }
        }
        inspectionSubscription = subscription
        if (subscription == null) { closeInspection(); error = if (waitingForConnection()) null else "Inspection is unavailable." }
        return subscription != null
    }
    fun tableMenu() { dismiss(); panel = "table" }
    fun phasePicker() { dismiss(); panel = "phase" }
    fun stackContents() { dismiss(); tokens.close(); showStackBubbles(null); panel = "manageStack" }

    /** Open information is a personal route, not a shared View/Reveal reference display. */
    fun openPublicPile(id: String) {
        val container = current()?.board?.containers?.singleOrNull { it.id == id &&
            it.zone in setOf(PlayZone.GRAVEYARD, PlayZone.EXILE, PlayZone.STACK) } ?: return
        if (container.zone == PlayZone.STACK) { stackContents(); return }
        dismiss(); showStackBubbles(null)
        layoutState.value = layoutState.value.copy(localPileId = container.id.takeUnless { it == layoutState.value.localPileId })
    }
    fun closeLocalPile() { layoutState.value = layoutState.value.copy(localPileId = null); dismiss() }
    fun publicContents(): PileContents? {
        val id = panel?.takeIf { it.startsWith("contents/") }?.substringAfter('/')
            ?: layoutState.value.localPileId?.takeIf { panel == null } ?: return null
        val play = current() ?: return null
        val container = play.board.containers.singleOrNull { it.id == id } ?: return null
        val stack = container.zone == PlayZone.STACK
        return PileContents(container.id, container.zone.name.lowercase().replaceFirstChar { it.uppercase() },
            if (stack) CombatStackRules.stack(play.board).cards else container.cards, ordered = stack)
    }

    fun bind(scope: TableScope, sources: Map<String, TableMoveSource> = emptyMap()): ToolkitCapture? {
        val play = current() ?: return null
        val views = scope.cardIds.distinct()
        fun source(id: String): String? = sources[id]?.sourceId ?: CombatStackRules.liveSource(play.board, id)?.id
        val ids = views.mapNotNull(::source).distinct()
        val capturedSources = ids.mapNotNull { id ->
            val view = views.firstOrNull { source(it) == id }
            (sources[view] ?: BasicTableController.sources(play, listOf(id))[id])?.let { id to it }
        }.toMap()
        return ToolkitCapture(views, scope.contextId, ids, capturedSources, play.board.combat?.id)
    }

    fun openCard(scope: TableScope, sources: Map<String, TableMoveSource> = emptyMap()) {
        val play = current() ?: return
        val bound = bind(scope, sources) ?: return
        val previous = panel?.takeIf { it.startsWith("contents/") }
        dismiss(); tokens.close(); capture = bound; showStackBubbles(null)
        tapReturnPanel = previous
        explicitStack = if (scope.contextId in CombatStackRules.stack(play.board).cards) {
            val context = StackContexts.context(play, scope.contextId)
            when (context.kind) {
                StackContextKind.EFFECT -> ExplicitStackTarget(scope.contextId, StackTargetKind.EFFECT)
                StackContextKind.SPELL -> ExplicitStackTarget(scope.contextId, StackTargetKind.INSTANT_OR_SORCERY)
                StackContextKind.PERMANENT -> ExplicitStackTarget(scope.contextId, StackTargetKind.PERMANENT)
                else -> null
            }
        } else null
        val source = CombatStackRules.liveSource(play.board, scope.contextId)?.id
        if (source != null) {
            manual.cards(play, source, bound.sourceIds)
        } else panel = "occurrence"
        val occurrence = ProjectionEndpoint(bound.contextViewId)
        if (CombatStackRules.exists(play.board, occurrence)) {
            tokens.openOccurrences(occurrence, bound.viewIds.map(::ProjectionEndpoint).filter { CombatStackRules.exists(play.board, it) })
        } else if (source != null) tokens.open(source, bound.sourceIds)
    }

    fun open(captured: TableIntent.Actions, item: TableItem, selection: TableSelection) {
        val play = current() ?: return
        if (item.viewport.startsWith("cardPanel/")) {
            val display = (play.board.contentsDisplays + listOfNotNull(localPanelDisplay(play, viewer, layoutState.value)))
                .singleOrNull { it.id == item.viewport.substringAfter('/') } ?: return
            if (display.author != viewer || !enabled()) return
            dismiss(); popupItem = item; panel = "panelCard/${display.id}/${item.cardId}"
            return
        }
        if (tokens.pickingLink) {
            val occurrence = item.cardId?.let(::ProjectionEndpoint)?.takeIf { CombatStackRules.exists(play.board, it) }
            if (occurrence != null) { tokens.pick(occurrence); return }
            val endpoint = item.cardId?.let { id ->
                if (play.board.cards.any { it.id == id }) LinkEndpoint(LinkEndpointKind.CARD, id) else null
            } ?: item.owner?.takeIf { item.kind in listOf(TableItemKind.LIFE, TableItemKind.COUNTER) }
                ?.let { LinkEndpoint(LinkEndpointKind.PLAYER, it) }
            if (endpoint != null) batch { tokens.pick(endpoint) }
            return
        }
        when {
            item.zone == PlayZone.STACK -> stackContents()
            captured.scope != null -> openCard(captured.scope, captured.sources)
            item.kind in listOf(TableItemKind.LIFE, TableItemKind.COUNTER) -> {
                dismiss(); manual.player(play, requireNotNull(item.owner))
            }
            item.zone == PlayZone.LIBRARY -> openLibrary(item.id)
            else -> openPublicPile(item.id)
        }
        popupItem = item
    }

    fun openLibrary(id: String, tool: LibraryTool = LibraryTool.CONTENTS, handIds: List<String>? = null) {
        val play = current() ?: return
        val source = play.board.containers.singleOrNull { it.id == id && it.zone == PlayZone.LIBRARY } ?: return
        if (source.accountId != viewer) return
        val hand = play.board.containers.single { it.zone == PlayZone.HAND && it.accountId == source.accountId }
        dismiss()
        val controller = libraryController(source.id, hand.id) ?: return
        library = ToolkitLibrary(controller, handIds?.toList() ?: hand.cards.toList(), tool)
        panel = "library"
    }
    /** Shared panels render directly from the board, including after reconnect and during review. */
    fun showLibraryContents(selection: ContentsSelection, count: Int, reveal: Boolean) {
        val controller = library?.controller ?: return
        val result = controller.show(selection, count, reveal)
        if (result.refused != 0 || result.submitted == 0) return
        dismiss()
    }
    fun closeCardPanel(display: ContentsDisplay) {
        if (display.author != viewer || !enabled()) return
        if (!display.search && display.cardIds.isNotEmpty()) { dismiss(); panel = "panelRest/${display.id}" }
        else resolveCardPanel(display)
    }
    fun resolveCardPanel(display: ContentsDisplay, rest: ContentsRest = ContentsRest.TOP) {
        val play = current() ?: return
        val hand = play.board.containers.single { it.zone == PlayZone.HAND && it.accountId == display.author }
        val result = libraryController(display.containerId, hand.id)?.dismiss(display.id, rest)
        if (result?.refused == 0) dismiss()
    }
    fun cardPanelAction(display: ContentsDisplay, id: String, zone: PlayZone, reveal: Boolean = false,
        bottom: Boolean = false, tapped: Boolean = false) {
        if (display.author != viewer || id !in display.cardIds || !enabled()) return
        // Disclosure and onward movement deliberately have different invocations and history groups.
        if (reveal && !submit(PlayAction.RevealContentsCard(display.id, id), UUID.randomUUID().toString())) return
        val play = current() ?: return
        val destination = libraryDestinations(play.board, sourceIds = listOf(id)).map { it.second }.firstOrNull { d ->
            play.board.containers.any { it.id == d.containerId && it.zone == zone } &&
                (zone != PlayZone.LIBRARY || d.insertion == if (bottom) MoveInsertion.BOTTOM else MoveInsertion.TOP)
        } ?: return
        val card = play.board.cards.single { it.id == id }
        if (submit(PlayAction.MoveCard(id, MoveSource(card.containerId, card.placement), destination), UUID.randomUUID().toString())) {
            if (tapped) submit(PlayAction.SetTapped(id, true))
            dismiss()
        }
    }
    fun localPanelAction(id: String, zone: PlayZone, bottom: Boolean = false, tapped: Boolean = false) {
        val play = current()?.takeIf { enabled() } ?: return
        val card = play.board.cards.singleOrNull { it.id == id && it.containerId == layoutState.value.localPileId } ?: return
        val destination = libraryDestinations(play.board, sourceIds = listOf(id)).map { it.second }.firstOrNull { d ->
            play.board.containers.any { it.id == d.containerId && it.zone == zone } &&
                (zone != PlayZone.LIBRARY || d.insertion == if (bottom) MoveInsertion.BOTTOM else MoveInsertion.TOP)
        } ?: return
        if (submit(PlayAction.MoveCard(id, MoveSource(card.containerId, card.placement), destination))) {
            if (tapped) submit(PlayAction.SetTapped(id, true))
            dismiss()
        }
    }
    fun libraryController(source: String, hand: String): LibraryController? {
        val playId = current()?.id ?: return null
        return LibraryController(playId, source, hand, ::current, ::enabled, { submit(it) },
        request = { action, group -> submit(action, group) }, moveRequest = { intent ->
            var submitted = 0
            val adapter = BasicTableController(::current, ::enabled) { action, group ->
                enqueue(action, group).also { if (it && (action is PlayAction.MoveCard ||
                    (action as? PlayAction.Stack)?.action is StackAction.AddEffect)) submitted++ }
            }
            adapter.handle(intent)
            LibrarySubmission(submitted, intent.scope.cardIds.size - submitted)
        })
    }

    fun inspect(viewId: String) {
        val play = current() ?: return
        val card = CombatStackRules.inspection(play.board, viewId) ?: return
        val live = CombatStackRules.liveSource(play.board, viewId)
        val faces = ManualDisclosure.permittedFaces(play, card, viewer, inspecting = true)
        closeInspection()
        inspection = ManualInspection(play.gameId, play.id, viewId, faces.map { it.faceId }, snapshot = if (live == null) card else null,
            stackEntryId = viewId.takeIf { it in CombatStackRules.stack(play.board).cards })
    }
    fun handle(intent: TableIntent): Boolean = batch {
        when (intent) {
            is TableIntent.StackBubbles -> { showStackBubbles(intent.entryId); true }
            is TableIntent.StackDestination, is TableIntent.StackBubbleRemove -> {
                val play = current()?.takeIf { enabled() } ?: return@batch false
                val actions = when (intent) {
                    is TableIntent.StackDestination -> runCatching { StackMenuActions.destination(play, intent.entryId, intent.zone) }.getOrNull()
                    is TableIntent.StackBubbleRemove -> listOf(StackMenuActions.remove(intent.entryId))
                    else -> null
                } ?: return@batch false
                val advance = stackBubbleAdvanceEntry(intent, play.board)?.let { PendingStackAdvance(play.currentNodeId, it) }
                showStackBubbles(null)
                var sent = false
                // The shared lowering already includes move-entry defaults; do not expand the move twice.
                for (action in actions) { if (!enqueue(action, invocation)) return@batch sent; sent = true }
                if (sent) { pendingStackAdvance = advance; refreshStackBubbles() }
                sent
            }
            is TableIntent.Move -> {
                val board = current()?.board ?: return@batch false
                if (intent.scope.cardIds.any { id -> board.cards.none { it.id == id } && board.stackEffects.none { it.id == id } &&
                    CombatStackRules.occurrences(board.combat).none { it.id == id } }) return@batch false
                if (intent.scope.cardIds.any { it in CombatStackRules.stack(board).cards })
                    return@batch basic.handle(intent.copy(invocationId = intent.invocationId ?: invocation))
                val bound = bind(intent.scope, intent.sources) ?: return@batch false
                if (intent.destination is TableDestination.Combat) place(bound, intent.destination.destination)
                else if (bound.sourceIds.isEmpty()) false else basic.handle(intent.copy(
                    scope = TableScope(bound.contextViewId.takeIf { it in bound.sourceIds }
                        ?: intent.sources[bound.contextViewId]?.sourceId ?: bound.sourceIds.first(), bound.sourceIds), sources = bound.sources))
            }
            is TableIntent.Rotate -> {
                val stackIds = current()?.board?.let { CombatStackRules.stack(it).cards }.orEmpty()
                if (intent.scope.contextId in stackIds) return@batch false
                val scope = intent.scope.copy(cardIds = intent.scope.cardIds.filterNot { it in stackIds })
                val bound = bind(scope, intent.sources) ?: return@batch false
                basic.handle(intent.copy(scope = TableScope(bound.sourceIds.firstOrNull() ?: return@batch false, bound.sourceIds)))
            }
            is TableIntent.Submit -> submit(intent.action, intent.invocationId ?: invocation)
            else -> basic.handle(intent)
        }
    }
    fun perform(intent: TableActionIntent): Boolean = batch {
        table.perform(intent).also { refreshAttackSelection() }
    }
    fun refreshAttackSelection() {
        current()?.let { selectionState.value = selectionState.value.atStep(TurnPosition.of(it.board.turn)) }
    }
    override fun selectAttackers(intent: TableActionIntent.SelectAttackers) {
        if (intent.advance && !submit(PlayAction.ChooseStep(TurnPosition.DECLARE_ATTACKERS))) return
        selectionState.value = selectionState.value.selectAttackers()
    }

    override fun library(intent: TableActionIntent.Library) {
        if (intent.tool == LibraryTool.MULLIGAN_MINUS_ONE) {
            val controller = libraryController(intent.targets.libraryId, intent.targets.handId) ?: return
            controller.mulligan(intent.handIds, minusOne = true)
        } else openLibrary(intent.targets.libraryId, intent.tool, intent.handIds)
    }
    override fun player(intent: TableActionIntent.Player) { current()?.let { dismiss(); manual.player(it, intent.accountId) } }
    override fun token(intent: TableActionIntent.Token) { dismiss(); tokens.catalog() }
    override fun ending(intent: TableActionIntent.Ending) { if (!ending.invoke(intent)) error = "Result action is unavailable." }
    override fun combat(intent: TableActionIntent.Combat) {
        when (intent.tool) {
            CombatTool.CREATE -> submit(PlayAction.Combat(CombatAction.Open))
            CombatTool.CLOSE -> intent.areaId?.let { submit(PlayAction.Combat(CombatAction.Close(it))) }
            CombatTool.ADVANCE_AND_CLOSE -> {
                submit(PlayAction.NextStep)
                intent.areaId?.let { submit(PlayAction.Combat(CombatAction.Close(it))) }
            }
            CombatTool.NO_ATTACK -> {
                val combat = current()?.board?.combat
                if (combat != null && combat.id == intent.areaId && CombatStackRules.occurrences(combat).isEmpty())
                    submit(PlayAction.Combat(CombatAction.Close(combat.id)))
                submit(PlayAction.ChooseStep(TurnPosition.POSTCOMBAT_MAIN))
            }
            CombatTool.DECLARE, CombatTool.DECLARE_AND_TAP -> declare(intent.sourceIds, intent.tool == CombatTool.DECLARE_AND_TAP)
        }
    }
    fun declare(ids: List<String>, tap: Boolean): Boolean = batch {
        val play = current()?.takeIf { enabled() } ?: return@batch false
        val eligible = ids.distinct().filter { id -> play.board.cards.any { card -> card.id == id &&
            play.board.containers.any { it.id == card.containerId && it.zone == PlayZone.BATTLEFIELD } } }
        if (eligible.isEmpty()) return@batch false
        if (play.board.combat == null) submit(PlayAction.Combat(CombatAction.Open))
        val area = current()?.board?.combat?.id ?: return@batch false
        var sent = false
        eligible.forEach { sent = submit(PlayAction.Combat(CombatAction.Declare(area, it))) || sent }
        if (tap) eligible.forEach { submit(PlayAction.SetTapped(it, true)) }
        if (sent) selectionState.value = selectionState.value.afterDeclare()
        sent
    }
    fun addEffects(ids: List<String>): Boolean = batch {
        var sent = false
        ids.distinct().forEach { id -> current()?.takeIf { play -> play.board.cards.any { card -> card.id == id &&
            CombatStackRules.canProject(play.board, card) } }
            ?.let { sent = submit(PlayAction.Stack(StackAction.AddEffect(id, CombatStackRules.stackDigest(it)))) || sent } }
        sent
    }
    fun removeOccurrences(bound: ToolkitCapture): Boolean = batch {
        var sent = false
        for (id in bound.viewIds) {
            val board = current()?.board ?: break
            val action = when {
                board.stackEffects.any { it.id == id } -> PlayAction.Stack(StackAction.RemoveEffect(id))
                bound.areaId != null && CombatStackRules.occurrences(board.combat).any { it.id == id } ->
                    PlayAction.Combat(CombatAction.Remove(bound.areaId, id))
                else -> null
            }
            if (action != null) sent = submit(action) || sent
        }
        sent
    }
    fun reorderEffect(id: String, expected: IdentityGap, destination: IdentityGap): Boolean =
        submit(PlayAction.Stack(StackAction.ReorderEffect(id, expected, destination)))
    override fun stack(intent: TableActionIntent.Stack) {
        if (intent.target.kind == StackTargetKind.EFFECT) submit(PlayAction.Stack(StackAction.RemoveEffect(intent.target.id)))
        else {
            openCard(TableScope(intent.target.id, listOf(intent.target.id)))
            val play = current() ?: return
            val owner = CombatStackRules.liveSource(play.board, intent.target.id)?.owner ?: return
            val zone = if (intent.target.kind == StackTargetKind.PERMANENT) PlayZone.BATTLEFIELD else PlayZone.GRAVEYARD
            stackChoices = play.board.containers.filter { it.zone == zone && it.accountId == owner }.map { container ->
                val occupied = play.board.cards.filter { it.containerId == container.id }.mapNotNull { it.placement?.position }.toSet()
                val destination = if (zone == PlayZone.BATTLEFIELD) MoveDestination(container.id, MoveInsertion.EMPTY,
                    BoardPosition(generateSequence(0) { it + 1 }.first { BoardPosition(it, 0) !in occupied }, 0))
                    else MoveDestination(container.id, MoveInsertion.UNORDERED)
                StackMoveChoice(moveZoneLabel(zone), zone, destination)
            }
            manual.close(); panel = "stackDestination"
        }
    }

    /** Whole-drop structural preflight uses the existing shared helper; only real-session callbacks send.
     * Request-derived occurrence IDs are read from that session's immediate prediction, never the probe. */
    fun place(bound: ToolkitCapture, destination: CombatDestination): Boolean = batch {
        val initial = current()?.takeIf { enabled() } ?: return@batch false
        val probe = runCatching { CombatStackController.place(initial, viewer, bound.viewIds, destination) { action ->
            GameCommand(UUID.randomUUID().toString(), GameScope(initial.gameId, playId = initial.id),
                GameAction.Play(action), initial.id, "0")
        } }
        if (probe.isFailure) { error = probe.exceptionOrNull()?.message ?: "Unavailable combat destination"; return@batch false }
        val ids = CombatStackController.sources(CombatStackController.orderedTargets(initial.board, bound.viewIds))
        var target = destination
        var prefix: String? = null
        var right = destination.cardGap.beforeId
        var sent = false
        for ((index, source) in ids.withIndex()) {
            val board = current()?.board ?: break
            val area = board.combat?.takeIf { it.id == destination.areaId } ?: break
            val old = CombatStackRules.occurrences(area).firstOrNull { it.sourceId == source }
            target = if (prefix == null) target.copy(pendingSourceIds = ids.drop(index + 1)) else target.copy(
                insertion = CombatInsertion.GAP, cardGap = IdentityGap(prefix, right), pendingSourceIds = ids.drop(index + 1))
            sent = submit(PlayAction.Combat(CombatAction.Place(source, target, old?.let { CombatStackRules.location(area, it.id) }))) || sent
            val next = current()?.board?.combat?.takeIf { it.id == area.id } ?: continue
            val placed = CombatStackRules.occurrences(next).singleOrNull { it.sourceId == source } ?: continue
            val location = CombatStackRules.location(next, placed.id) ?: continue
            if (index == 0) {
                val side = next.pairs.single { it.id == location.pairId }.side(location.role)
                right = side.dropWhile { it.id != placed.id }.drop(1).firstOrNull { it.sourceId !in ids.drop(index + 1) }?.id
                target = target.copy(pairId = location.pairId, insertion = CombatInsertion.GAP)
            }
            prefix = placed.id
        }
        sent
    }
}

/** Rotation retention without adding a gameplay store to the account/session controller. */
class TableToolkitViewModel : ViewModel() {
    private var owner: Any? = null
    private var key: List<String>? = null
    private var retained: TableToolkitController? = null
    fun obtain(owner: Any, key: List<String>, create: () -> TableToolkitController): TableToolkitController {
        if (this.owner !== owner || this.key != key) {
            retained?.dismiss(); retained?.tokens?.close(); retained?.closeInspection()
            this.owner = owner; this.key = key; retained = create()
        }
        return requireNotNull(retained)
    }
    fun release(toolkit: TableToolkitController) {
        if (retained === toolkit) {
            toolkit.dismiss(); toolkit.tokens.close(); toolkit.closeInspection()
            retained = null; owner = null; key = null
        }
    }
    override fun onCleared() { retained?.let(::release) }
}
