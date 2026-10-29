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
    private val waitingForConnection: () -> Boolean = { false },
    private val playBatch: ((List<PlayAction>, String) -> Boolean)? = null,
    private val playTree: (() -> GameplayTree?)? = null,
    private val continuation: () -> Continuation? = { null }) : TableActionRoutes {
    private val identity = state()?.let { TableActionScope(it.gameId, it.id) }
    private var draft: PlayBatchDraft? = null
    fun current(): PlayState? = (draft?.state ?: state())?.takeIf { TableActionScope(it.gameId, it.id) == identity }
    fun enabled() = connected() && current() != null
    val manual = ManualActions()
    val layoutState = mutableStateOf(TableLayoutState())
    val selectionState = mutableStateOf(TableSelection())
    val tokens = TokenLinkController(::current, { action -> submit(action) }, submitInvocation = { actions ->
        batchResult {
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

    fun batch(block: () -> Unit) { batchResult { block(); true } }
    private fun batchResult(requestId: String = UUID.randomUUID().toString(), block: () -> Boolean): Boolean {
        if (draft != null) return block()
        val initial = current() ?: return block()
        val pending = if (playTree == null) PlayBatchDraft(initial, viewer, requestId)
            else PlayBatchDraft(initial, viewer, requestId, playTree.invoke() ?: return false, continuation())
        draft = pending; invocation = pending.requestId; error = null
        val result = try { block() } finally { draft = null; invocation = null }
        if (pending.overflow) { error = "An action may contain at most 100 changes."; return false }
        if (!result) return false
        if (pending.actions.isEmpty()) return result
        val sent = if (pending.actions.size == 1) gameplay(pending.actions.single(), pending.requestId)
            else playBatch?.invoke(pending.actions.toList(), pending.requestId) == true
        if (!sent) error = "Action could not be queued. Reconnect and try again."
        refreshStackBubbles()
        return result && sent
    }
    fun submitAll(actions: List<PlayAction>) { batch { actions.forEach { submit(it) } } }
    fun completeValue(): Boolean {
        val play = current()?.takeIf { enabled() } ?: return false
        val editor = manual.editor
        val actions = manual.valueActions(play, viewer)
        if (actions.isEmpty()) return false
        val sent = if (actions.size == 1) submit(actions.single()) else batchResult {
            actions.fold(true) { all, action -> submit(action) && all }
        }
        if (sent && manual.editor === editor) manual.cancelEditor()
        return sent
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
        val baseline = draft?.initial ?: play
        val pending = if (source != null && baseline != null) PendingStackBubble(baseline.currentNodeId,
            CombatStackRules.stack(baseline.board).cards.toSet(), source, action is PlayAction.Stack) else null
        return (draft?.add(action) ?: gameplay(action, invocationId)).also { sent ->
            if (!sent) error = "Action could not be queued. Reconnect and try again."
            else {
                if (pending != null) pendingStackBubble = pending
                if (draft == null) refreshStackBubbles()
            }
        }
    }

    /** Convenience members are lowered against the current draft, then sent together. */
    fun submit(action: PlayAction, invocationId: String? = invocation): Boolean {
        val play = current()?.takeIf { enabled() } ?: return false
        if (action is PlayAction.CreateToken) {
            return batchResult(invocationId ?: UUID.randomUUID().toString()) {
                val before = current() ?: return@batchResult false
                if (!enqueue(action, invocation)) return@batchResult false
                val after = current() ?: return@batchResult false
                val token = after.board.cards.singleOrNull { card -> before.board.cards.none { it.id == card.id } }
                    ?: return@batchResult false
                val row = TokenActions.row(action.definition.definition)
                if (token.placement?.position?.y == row) return@batchResult true
                val occupied = after.board.cards.filter { it.containerId == token.containerId && it.id != token.id }
                    .mapNotNull { it.placement?.position }.toSet()
                var column = 0
                while (BoardPosition(column, row) in occupied) column++
                enqueue(PlayAction.MoveCard(token.id, MoveSource(token.containerId, token.placement),
                    MoveDestination(token.containerId, MoveInsertion.EMPTY, BoardPosition(column, row))), invocation)
            }
        }
        // A face-down cast moves the real card: a projection would reveal its hand source.
        if (action is PlayAction.MoveCard && action.removeStackEntryId == null && action.faceDown != true && play.board.containers.any {
                it.id == action.destination.containerId && it.zone == PlayZone.STACK }) {
            return enqueue(BasicTableController.projection(play, action) ?: return false,
                invocationId ?: UUID.randomUUID().toString())
        }
        if (action is PlayAction.InspectFace && acceptedInspection != null) return loggedInspection(action)
        val group = invocationId ?: UUID.randomUUID().toString()
        val actions = if (action is PlayAction.MoveCard) GameplayRules.moveActions(play,
            action.copy(destination = ownerMoveDestination(play.board, action.cardId, action.destination, play.id))) else listOf(action)
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
        tokens.showingCatalog || tokens.showingLinks
    internal fun dismissBackOverlay() {
        when {
            inspection != null || manual.inspection != null -> closeInspection()
            peek != null -> peek(null)
            manual.editor != null -> manual.cancelEditor()
            tokens.showingCatalog || tokens.showingLinks -> tokens.close()
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
    /** Personal selection only: a whole card panel joins (or leaves) the current selection. */
    fun selectAll(ids: List<String>, selected: Boolean) {
        val current = selectionState.value
        selectionState.value = current.copy(ids = if (selected) current.ids + ids else current.ids - ids.toSet())
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
        val real = play.board.cards.singleOrNull { it.id == scope.contextId }
        if (real != null && play.board.containers.any { it.id == real.containerId && it.zone == PlayZone.HAND && it.accountId != viewer }) return
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
            manual.cards(play, source, bound.sourceIds, scope.contextId.takeIf {
                CombatStackRules.projectionNote(play.board, it) != null })
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
        when {
            item.zone == PlayZone.STACK -> stackContents()
            captured.scope != null -> openCard(captured.scope, captured.sources)
            item.kind in listOf(TableItemKind.LIFE, TableItemKind.COUNTER) -> {
                dismiss(); manual.player(play, requireNotNull(item.owner), counters = item.kind == TableItemKind.COUNTER)
            }
            item.zone == PlayZone.LIBRARY -> openLibrary(item.id)
            else -> openPublicPile(item.id)
        }
        popupItem = item
    }

    fun openLibrary(id: String, tool: LibraryTool = LibraryTool.CONTENTS, handIds: List<String>? = null) {
        val play = current() ?: return
        val source = play.board.containers.singleOrNull { it.id == id && it.zone == PlayZone.LIBRARY } ?: return
        val hand = play.board.containers.single { it.zone == PlayZone.HAND && it.accountId == source.accountId }
        dismiss()
        val controller = libraryController(source.id, hand.id) ?: return
        library = ToolkitLibrary(controller, handIds?.toList() ?: hand.cards.toList(), tool)
        panel = if (source.temp) "temp" else if (tool == LibraryTool.MULLIGAN_N) "mulligan" else "library"
    }
    /** The captured cards into each owner's Temp pile (created by the first move) as ONE invocation. */
    fun toTemp(sourceIds: List<String>): Boolean {
        val play = current()?.takeIf { enabled() } ?: return false
        val cards = sourceIds.distinct().mapNotNull { id -> play.board.cards.singleOrNull { it.id == id } }
            .filter { card -> !card.token && play.board.containers.none { it.id == card.containerId && it.temp } }
        if (cards.isEmpty()) return false
        val actions = cards.groupBy { it.owner }.flatMap { (owner, members) ->
            val board = TempPiles.ensure(play.board, play.id, owner)
            org.asyncttwiz.protocol.LibraryActions.moveCaptured(board, members.map { it.id },
                MoveDestination(TempPiles.container(board, owner)!!.id, MoveInsertion.TOP))
        }
        return batchResult { actions.all { submit(it) } }.also { if (it) dismiss() }
    }
    /** The whole Temp pile, in its current order, onto its owners' Library top or bottom as ONE invocation. */
    fun tempToLibrary(tempId: String, bottom: Boolean): Boolean {
        val play = current()?.takeIf { enabled() } ?: return false
        val actions = TempPiles.allToLibrary(play.board, tempId, bottom)
        if (actions.isEmpty()) return false
        return batchResult { actions.all { submit(it) } }.also { if (it) dismiss() }
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
        // Hand reveals return their cards in place with Done; Library reveals keep the rest chooser.
        if (display.reveal && current()?.board?.containers?.any { it.id == display.containerId && it.zone == PlayZone.HAND } == true) {
            if (submit(PlayAction.DismissContents(display.id))) dismiss()
            return
        }
        if (!display.search && display.cardIds.isNotEmpty()) { dismiss(); panel = "panelRest/${display.id}" }
        else resolveCardPanel(display)
    }
    fun resolveCardPanel(display: ContentsDisplay, rest: ContentsRest = ContentsRest.TOP) {
        val play = current() ?: return
        val owner = play.board.containers.single { it.id == display.containerId }.accountId
        val hand = play.board.containers.single { it.zone == PlayZone.HAND && it.accountId == owner }
        val result = libraryController(display.containerId, hand.id)?.dismiss(display.id, rest)
        if (result?.refused == 0) dismiss()
    }
    fun playHand(ids: List<String>): Boolean = batchResult {
        ids.isNotEmpty() && ids.distinct().all { id ->
            val play = current() ?: return@all false
            val card = play.board.cards.singleOrNull { it.id == id } ?: return@all false
            if (play.board.containers.none { it.id == card.containerId && it.zone == PlayZone.HAND && it.accountId == viewer }) return@all false
            val destination = handPlayDestination(play, card) ?: return@all false
            basic.handle(TableIntent.Move(TableScope(id, listOf(id)), destination,
                BasicTableController.sources(play, listOf(id)), handDoubleTap = true))
        }
    }
    /** Morph/disguise: every selected own hand card enters the Stack face down and, being always a 2/2
     * permanent, auto-resolves onto the Battlefield like a hand double-tap: separate history group, same
     * invocation (one-step Undo), all in one batch. */
    fun castFaceDown(ids: List<String>): Boolean = batchResult {
        ids.isNotEmpty() && ids.distinct().all { id ->
            val play = current() ?: return@all false
            val card = play.board.cards.singleOrNull { it.id == id } ?: return@all false
            if (play.board.containers.none { it.id == card.containerId && it.zone == PlayZone.HAND && it.accountId == viewer }) return@all false
            if (!submit(HandActions.castFaceDown(play, id))) return@all false
            val next = current()?.takeIf { enabled() && id in CombatStackRules.stack(it.board).cards } ?: return@all true
            // Exactly the manual resolver, as the double-tap uses: rules v24 keep it face down as a 2/2.
            for (action in StackMenuActions.destination(next, id, PlayZone.BATTLEFIELD))
                if (!enqueue(action, invocation)) break
            true
        }
    }
    fun randomHand(handId: String, selectedIds: List<String>, result: HandRandomResult): Boolean {
        val play = current()?.takeIf { enabled() } ?: return false
        val seed = RandomV1.encodeSeed(ByteArray(32).also { java.security.SecureRandom().nextBytes(it) })
        val action = runCatching { HandActions.random(play.board, handId, selectedIds, seed, result) }.getOrNull() ?: return false
        return submit(action)
    }
    fun cardPanelAction(display: ContentsDisplay, id: String, zone: PlayZone, reveal: Boolean = false,
        bottom: Boolean = false, tapped: Boolean = false) = batch {
        if (display.author != viewer || id !in display.cardIds || !enabled()) return@batch
        // Disclosure and onward movement keep distinct history groups inside one invocation.
        if (reveal && !submit(PlayAction.RevealContentsCard(display.id, id))) return@batch
        val play = current() ?: return@batch
        val destination = libraryDestinations(play.board, sourceIds = listOf(id)).map { it.second }.firstOrNull { d ->
            play.board.containers.any { it.id == d.containerId && it.zone == zone } &&
                (zone != PlayZone.LIBRARY || d.insertion == if (bottom) MoveInsertion.BOTTOM else MoveInsertion.TOP)
        } ?: return@batch
        val card = play.board.cards.single { it.id == id }
        if (submit(PlayAction.MoveCard(id, MoveSource(card.containerId, card.placement), destination), UUID.randomUUID().toString())) {
            if (tapped) submit(PlayAction.SetTapped(id, true))
            dismiss()
        }
    }
    fun localPanelAction(id: String, zone: PlayZone, bottom: Boolean = false, tapped: Boolean = false) = batch {
        val play = current()?.takeIf { enabled() } ?: return@batch
        val card = play.board.cards.singleOrNull { it.id == id && it.containerId == layoutState.value.localPileId } ?: return@batch
        val destination = libraryDestinations(play.board, sourceIds = listOf(id)).map { it.second }.firstOrNull { d ->
            play.board.containers.any { it.id == d.containerId && it.zone == zone } &&
                (zone != PlayZone.LIBRARY || d.insertion == if (bottom) MoveInsertion.BOTTOM else MoveInsertion.TOP)
        } ?: return@batch
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
            val sent = batchResult(intent.invocationId ?: UUID.randomUUID().toString()) { adapter.handle(intent) }
            LibrarySubmission(if (sent) submitted else 0, if (sent) intent.scope.cardIds.size - submitted else intent.scope.cardIds.size)
        }, viewer = viewer, batchRequest = { actions, group ->
            if (actions.size == 1) submit(actions.single(), group) else batchResult(group) {
                var sent = false
                actions.forEach { sent = submit(it) || sent }
                sent
            }
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
    fun handle(intent: TableIntent): Boolean {
        if (intent is TableIntent.Batch) return batchResult(intent.invocationId) {
            intent.intents.all { handle(it) }
        }
        val multiple = when (intent) {
            is TableIntent.Move -> intent.scope.cardIds.size > 1 || intent.handDoubleTap
            is TableIntent.Rotate -> intent.scope.cardIds.size > 1
            is TableIntent.StackDestination, is TableIntent.StackBubbleRemove -> true
            else -> false
        }
        return if (multiple) batchResult((intent as? TableIntent.Move)?.invocationId ?: UUID.randomUUID().toString()) {
            handleOne(intent)
        } else handleOne(intent)
    }
    private fun handleOne(intent: TableIntent): Boolean {
        return when (intent) {
            is TableIntent.StackBubbles -> { showStackBubbles(intent.entryId); true }
            is TableIntent.StackDestination, is TableIntent.StackBubbleRemove -> {
                val play = current()?.takeIf { enabled() } ?: return false
                val actions = when (intent) {
                    is TableIntent.StackDestination -> runCatching { StackMenuActions.destination(play, intent.entryId, intent.zone) }.getOrNull()
                    is TableIntent.StackBubbleRemove -> listOf(StackMenuActions.remove(intent.entryId))
                    else -> null
                } ?: return false
                val advance = stackBubbleAdvanceEntry(intent, play.board)?.let { PendingStackAdvance(play.currentNodeId, it) }
                showStackBubbles(null)
                var sent = false
                // The shared lowering already includes move-entry defaults; do not expand the move twice.
                for (action in actions) { if (!enqueue(action, invocation)) return sent; sent = true }
                if (sent) { pendingStackAdvance = advance; if (draft == null) refreshStackBubbles() }
                sent
            }
            is TableIntent.Move -> {
                val board = current()?.board ?: return false
                if (intent.scope.cardIds.any { id -> board.cards.none { it.id == id } && board.stackEffects.none { it.id == id } &&
                    CombatStackRules.occurrences(board.combat).none { it.id == id } }) return false
                if (intent.scope.cardIds.any { it in CombatStackRules.stack(board).cards })
                    return basic.handle(intent.copy(invocationId = intent.invocationId ?: invocation))
                val bound = bind(intent.scope, intent.sources) ?: return false
                if (intent.destination is TableDestination.Combat) place(bound, intent.destination.destination)
                else if (bound.sourceIds.isEmpty()) false else basic.handle(intent.copy(
                    scope = TableScope(bound.contextViewId.takeIf { it in bound.sourceIds }
                        ?: intent.sources[bound.contextViewId]?.sourceId ?: bound.sourceIds.first(), bound.sourceIds), sources = bound.sources))
            }
            is TableIntent.Rotate -> {
                val stackIds = current()?.board?.let { CombatStackRules.stack(it).cards }.orEmpty()
                if (intent.scope.contextId in stackIds) return false
                val scope = intent.scope.copy(cardIds = intent.scope.cardIds.filterNot { it in stackIds })
                val bound = bind(scope, intent.sources) ?: return false
                val context = CombatStackRules.liveSource(current()!!.board, scope.contextId)?.id ?: return false
                val board = current()!!.board
                if (board.cards.none { card -> card.id == context && board.containers.any {
                         it.id == card.containerId && it.zone == PlayZone.BATTLEFIELD } }) return false
                basic.handle(intent.copy(scope = TableScope(context, bound.sourceIds))).also {
                    if (!it && error == null) error = "Rotation could not be queued. Reconnect and try again."
                }
            }
            is TableIntent.Submit -> submit(intent.action, intent.invocationId ?: invocation)
            else -> basic.handle(intent)
        }
    }
    fun perform(intent: TableActionIntent): Boolean {
        // Experimental damage shortcuts return their whole-batch success, not the void route receipt.
        if (intent is TableActionIntent.Combat && intent.tool in combatDamageTools) {
            if (!table.enabled(intent.scope)) return false
            return performCombatDamage(intent)
        }
        val multiple = when (intent) {
            is TableActionIntent.Bundle, is TableActionIntent.Combat -> true
            is TableActionIntent.Untap -> intent.cardIds.size + (if (intent.advance) 1 else 0) > 1
            is TableActionIntent.Orient -> intent.cardIds.size > 1
            is TableActionIntent.Draw -> intent.advance && intent.cardId != null
            else -> false
        }
        return (if (multiple) batchResult { table.perform(intent) } else table.perform(intent)).also { refreshAttackSelection() }
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
            CombatTool.DEAL_DAMAGE, CombatTool.FIRST_STRIKE_DAMAGE, CombatTool.REGULAR_DAMAGE,
            CombatTool.DAMAGE_CLOSE_AND_NEXT -> performCombatDamage(intent)
        }
    }
    // Experimental combat damage: calculate once, publish existing edits in one batch.
    private val combatDamageTools = setOf(CombatTool.DEAL_DAMAGE, CombatTool.FIRST_STRIKE_DAMAGE,
        CombatTool.REGULAR_DAMAGE, CombatTool.DAMAGE_CLOSE_AND_NEXT)
    private fun performCombatDamage(intent: TableActionIntent.Combat): Boolean {
        val play = current()?.takeIf { enabled() && it.board.combat?.id == intent.areaId } ?: return false
        if (intent.tool == CombatTool.DAMAGE_CLOSE_AND_NEXT) return batchResult {
            submit(PlayAction.ChooseStep(TurnPosition.POSTCOMBAT_MAIN)) &&
                (intent.areaId == null || submit(PlayAction.Combat(CombatAction.Close(intent.areaId))))
        }
        val step = when (intent.tool) {
            CombatTool.FIRST_STRIKE_DAMAGE -> CombatDamageStep.FIRST_STRIKE
            CombatTool.REGULAR_DAMAGE -> CombatDamageStep.REGULAR
            else -> CombatDamageStep.ALL
        }
        val sent = dealCombatDamage(CombatStackRules.occurrences(play.board.combat).map { it.id },
            step = step, areaId = intent.areaId)
        if (sent) selectionState.value = TableSelection()
        return sent
    }
    internal fun dealCombatDamage(ids: List<String>, targetOnly: Boolean = false,
        step: CombatDamageStep = CombatDamageStep.ALL, areaId: String?): Boolean {
        val play = current()?.takeIf { enabled() && it.board.combat?.id == areaId } ?: return false
        val assignment = CombatDamage.assign(play, ids, targetOnly, step)
        val sent = batchResult { assignment.actions.all { submit(it) } }
        if (sent && assignment.notes.isNotEmpty()) error = assignment.notes.joinToString("\n")
        return sent
    }
    fun declare(ids: List<String>, tap: Boolean): Boolean = batchResult {
        val play = current()?.takeIf { enabled() } ?: return@batchResult false
        val eligible = ids.distinct().filter { id -> play.board.cards.any { card -> card.id == id &&
            play.board.containers.any { it.id == card.containerId && it.zone == PlayZone.BATTLEFIELD } } }
        if (eligible.isEmpty()) return@batchResult false
        if (play.board.combat == null) submit(PlayAction.Combat(CombatAction.Open))
        val area = current()?.board?.combat?.id ?: return@batchResult false
        var sent = false
        eligible.forEach { sent = submit(PlayAction.Combat(CombatAction.Declare(area, it))) || sent }
        if (tap) eligible.forEach { submit(PlayAction.SetTapped(it, true)) }
        if (sent) selectionState.value = selectionState.value.afterDeclare()
        sent
    }
    fun addEffects(ids: List<String>): Boolean = batchResult {
        var sent = false
        ids.distinct().forEach { id -> current()?.takeIf { play -> play.board.cards.any { card -> card.id == id &&
            CombatStackRules.canProject(play.board, card) } }
            ?.let { sent = submit(PlayAction.Stack(StackAction.AddEffect(id, CombatStackRules.stackDigest(it)))) || sent } }
        sent
    }
    fun removeOccurrences(bound: ToolkitCapture): Boolean = batchResult {
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
    /** [viewIds] re-captures attacker copies when opened from the real card's menu. */
    fun openAttackTargets(viewIds: List<String>? = null) {
        if (viewIds != null) capture = capture?.copy(viewIds = viewIds)
        manual.close(); panel = "attackTarget"
    }
    fun setAttackTarget(bound: ToolkitCapture, cardId: String?): Boolean = batchResult {
        val board = current()?.board ?: return@batchResult false
        if (board.combat?.id != bound.areaId) return@batchResult false
        var sent = false
        for (pair in CombatStackController.attackerPairs(board, bound.viewIds))
            sent = submit(PlayAction.Combat(CombatAction.SetAttackTarget(bound.areaId!!, pair.id, cardId))) || sent
        sent
    }
    override fun stack(intent: TableActionIntent.Stack) {
        if (intent.target.kind == StackTargetKind.EFFECT) submit(PlayAction.Stack(StackAction.RemoveEffect(intent.target.id)))
        else {
            openCard(TableScope(intent.target.id, listOf(intent.target.id)))
            val play = current() ?: return
            val owner = CombatStackRules.liveSource(play.board, intent.target.id)?.owner ?: return
            val zone = if (intent.target.kind == StackTargetKind.PERMANENT) PlayZone.BATTLEFIELD else PlayZone.GRAVEYARD
            stackChoices = listOfNotNull(play.board.zoneContainer(owner, zone)).map { container ->
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
    fun place(bound: ToolkitCapture, destination: CombatDestination): Boolean = batchResult {
        val initial = current()?.takeIf { enabled() } ?: return@batchResult false
        if (CombatStackController.retargetDrop(initial.board, bound.viewIds, destination).isNotEmpty())
            return@batchResult setAttackTarget(bound,
                initial.board.combat?.pairs?.find { it.id == destination.pairId }?.attackTarget ?: destination.attackTarget)
        val probe = runCatching { CombatStackController.place(initial, viewer, bound.viewIds, destination) { action ->
            GameCommand(UUID.randomUUID().toString(), GameScope(initial.gameId, playId = initial.id),
                GameAction.Play(action), initial.id, "0")
        } }
        if (probe.isFailure) { error = probe.exceptionOrNull()?.message ?: "Unavailable combat destination"; return@batchResult false }
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
