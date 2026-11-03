package org.asyncttwiz.android

import androidx.compose.runtime.*
import org.asyncttwiz.protocol.*
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
    private val continuation: () -> Continuation? = { null },
    /** One automated batch (Automatum, I-D11) with its tags, sent with the given request id. */
    automatedBatch: ((GameAction.PlayBatch, String) -> Boolean)? = null,
    /** I-D19, I-D24 (finalreview-sync #6, #7): switching Automatum off or clearing its data during an open flow, as one
     * server step ([GameAction.ExitFlow]) that takes the flow back when it can and always goes through. */
    private val exitFlow: ((MtgAction) -> Boolean)? = null,
    /** Where a cast's engine work runs (SYS-025): off the main thread in the app. */
    automatumWork: AutomatumWork = AutomatumWork.Inline,
    /** I-D47: a complete cast shown at once on this device while its batch is worked out (null: not here). */
    castPreview: CastPreview? = null) : TableActionRoutes {
    private val identity = state()?.let { TableActionScope(it.gameId, it.id) }
    private var draft: PlayBatchDraft? = null
    fun current(): PlayState? = (draft?.state ?: state())?.takeIf { TableActionScope(it.gameId, it.id) == identity }
    fun enabled() = connected() && current() != null
    /** The history's tags up to the shown board (the engine's event log, D33). */
    fun engineTags(): List<MtgTag?> = current()?.let { engineTags(playTree?.invoke(), it.currentNodeId) }.orEmpty()
    /** Who made the shown board's latest event (null at the game's start or without the history). */
    fun latestAuthor(): String? = current()?.let { playTree?.invoke()?.nodes?.get(it.currentNodeId)?.author }
    /** Who made the event [nodeId] (null at the game's start or without the history). */
    fun authorOf(nodeId: String): String? = playTree?.invoke()?.nodes?.get(nodeId)?.author
    /** Automatum's pauses on this table (I-D41): the one shown and the one the viewer holds. */
    internal val pause = PauseController()

    /**
     * The board Automatum plans the viewer's casts and passes on (pausereview #10): the one shown, or, when what the viewer
     * does next continues from a point the game has gone on from (a held pause, a timeline point), that point's board as
     * the branch will begin, every library's unknown order reseeded (HIS-042), so the batch applies there. Kept while the
     * shown board and the history's answer stay the same, so a cast following its board isn't worked out again.
     */
    internal fun acting(): PlayState? {
        val play = current() ?: return null
        if (draft != null || !play.board.automatum) return play
        val from = continuation()?.nodeId?.takeIf { it == play.currentNodeId } ?: return play
        val tree = playTree?.invoke() ?: return play
        if (tree.initial.id != play.id || !tree.forksAt(from)) return play
        branchStart?.takeIf { it.first === play }?.let { return it.second }
        val start = tree.branchStart(play, viewer) ?: play
        branchStart = play to start
        return start
    }
    private var branchStart: Pair<PlayState, PlayState>? = null

    /** #1, #2 (STK-039): a reaction the viewer begins at their own pause (a cast or ability, Respond) holds the pause
     * first, so it continues from there (a branch once the game went on) instead of being dropped when the game moves. */
    private fun holdForReaction() = pause.holdForReaction(current(), viewer)

    /** Casting from the hand through Automatum (I2a): a private flow; while it is open nothing else changes (D32). */
    internal val cast = CastController(viewer, { acting()?.takeIf { enabled() } }, automatedBatch, ::engineTags, { error = it },
        automatumWork, beginning = ::holdForReaction, preview = castPreview)
    /** Priority, responding, resolution and triggers through Automatum (I2b): shared flows; while one waits for an
     * answer nothing else changes (D32). */
    internal val flows = SharedFlowController(viewer, { acting()?.takeIf { enabled() } }, automatedBatch, ::engineTags, { error = it },
        automatumWork, onRespond = ::holdForReaction)
    private val automatumWork = automatumWork
    /** Combat through Automatum (I3 on today's lanes): declarations, passing on to the damage, early divisions. */
    internal val combat = CombatController({ current()?.takeIf { enabled() } }, { submit(it) }, ::engineTags, automatumWork,
        { error }, { error = it }, board = { current() })
    /** Bumped when hand plays worked out in the background are ready, so the hand menu shows them (SYS-025). */
    private var handPlaysReady by mutableStateOf(0)

    /** What the viewer may cast or play from their hand on [play] (I-D28), or null while it is being worked out off the
     * main thread (SYS-025); the hand menu recomposes once it is ready. */
    internal fun handPlays(play: PlayState): org.asyncttwiz.automatum.HandPlays? {
        handPlaysReady
        AutomatumBoards.cachedHandPlays(play, viewer)?.let { return it }
        if (automatumWork === AutomatumWork.Inline) return AutomatumBoards.handPlays(play, viewer, ::engineTags)
        if (handPlaysPending !== play.board) {
            handPlaysPending = play.board
            automatumWork.run({ AutomatumBoards.handPlays(play, viewer, ::engineTags) }) { handPlaysPending = null; handPlaysReady++ }
        }
        return null
    }
    private var handPlaysPending: Any? = null

    /** What the engine offers the viewer for each card on [play] (§3 I4: the card menus), or null while it is being
     * worked out off the main thread (SYS-025); the menus recompose once it is ready. */
    internal fun cardPlays(play: PlayState): org.asyncttwiz.automatum.CardPlays? {
        handPlaysReady
        AutomatumBoards.cachedCardPlays(play, viewer)?.let { return it }
        if (automatumWork === AutomatumWork.Inline) return AutomatumBoards.cardPlays(play, viewer, ::engineTags)
        if (cardPlaysPending !== play.board) {
            cardPlaysPending = play.board
            automatumWork.run({ AutomatumBoards.cardPlays(play, viewer, ::engineTags) }) { cardPlaysPending = null; handPlaysReady++ }
        }
        return null
    }
    private var cardPlaysPending: Any? = null

    /** Begins what a card menu offers through Automatum ([CastController.begin]): the cast panel's flow. */
    internal fun play(cardId: String, play: org.asyncttwiz.automatum.CardPlay): Boolean {
        val input = play.input ?: return false
        error = null
        return cast.begin(cardId, input, play.text)
    }
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
    val table = TableActionController(::current, ::enabled, { submit(it) }, this, batch = { block -> batchResult(block = block) })
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
    /** Shows why a greyed control can't be used ([MenuControl.refused]). */
    fun refuse(message: String) { error = message }
    /** Automatum on (I-D28): the ordinary actions an Automatum interaction is made of (moving a card within its own
     * Battlefield or hand, the combat draft) go through while this is above zero; other manual changes are refused. */
    private var engineRoute = 0
    private fun <T> engineRoute(block: () -> T): T { engineRoute++; try { return block() } finally { engineRoute-- } }
    var stackBubbleEntry by mutableStateOf<String?>(null); private set
    fun dismissTapUi() { dismiss(); showStackBubbles(null) }
    private data class PendingStackBubble(val nodeId: String, val before: Set<String>, val sourceId: String)
    private var pendingStackBubble: PendingStackBubble? = null
    private data class PendingStackAdvance(val nodeId: String, val entryId: String)
    private var pendingStackAdvance: PendingStackAdvance? = null
    fun showStackBubbles(id: String?) { pendingStackBubble = null; pendingStackAdvance = null; stackBubbleEntry = id }
    /** Follow the session's prediction as soon as it is published, without waiting for acceptance.
     * Request-derived effect IDs are identical in prediction and accepted state. */
    fun refreshStackBubbles() {
        val play = current() ?: return
        val stack = play.board.containers.singleOrNull { it.zone == PlayZone.STACK }?.cards.orEmpty()
        pendingStackBubble?.let { pending ->
            val added = stack.firstOrNull { id -> id !in pending.before &&
                play.board.stackEffects.any { it.id == id && it.sourceId == pending.sourceId } }
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
    private fun batchResult(requestId: String = DeviceRandom.uuid(), block: () -> Boolean): Boolean {
        if (draft != null) return block()
        if (cast.open) { error = CastController.BUSY; return false }
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
        refusal(action)?.let { error = it; return false }
        val play = current()
        val source = ((action as? PlayAction.Stack)?.action as? StackAction.AddEffect)?.sourceId
        val baseline = draft?.initial ?: play
        val pending = if (source != null && baseline != null) PendingStackBubble(baseline.currentNodeId,
            CombatStackRules.stack(baseline.board).cards.toSet(), source) else null
        return (draft?.add(action) ?: gameplay(action, invocationId)).also { sent ->
            if (!sent) error = "Action could not be queued. Reconnect and try again."
            else {
                if (pending != null) pendingStackBubble = pending
                if (draft == null) refreshStackBubbles()
            }
        }
    }

    /**
     * Why [action] can't be made now with Automatum (D32): a cast is open; a shared flow waits for an answer and the
     * action changes what Automatum reads (only Automatum's own controls and what is only shown, such as notes,
     * statuses, marks, positions and whose move it is, go on); a step or turn change made by hand (TRN-057, I-D28: the
     * turn moves on only through Automatum, [automatumStep]). Null when it can.
     */
    private fun refusal(action: PlayAction): String? {
        if (cast.open) return CastController.BUSY
        val play = current() ?: return null
        val board = play.board
        if (!board.automatum) return null
        val own = (action as? PlayAction.Mtg)?.action.let {
            it is MtgAction.SetAutomatum || it is MtgAction.SetSkipWhenNoChoice || it is MtgAction.SetFullControl ||
                it is MtgAction.ClearRecords }
        if (own) return null
        // I-D37: while Automatum waits for something done by hand, manual edits are what it waits for (until Done)
        val byHand = AutomatumBoards.byHand(play)
        if (Automatum.flowOpen(board) && !byHand && !shownOnly(play, action)) return SharedFlowController.BUSY
        val step = action == PlayAction.NextStep || action == PlayAction.EndTurn || action is PlayAction.ChooseStep ||
            action is PlayAction.BeginTurn
        if (step && CombatStackRules.stack(board).cards.isNotEmpty()) return SharedFlowController.STACK_WAITING
        if (step) return SharedFlowController.STEPS_FORWARD
        // I4, I-D28: only what the rules allow; manual changes only while Automatum waits for something done by hand
        if (!byHand && engineRoute == 0 && AutomatumDrags.manual(action)) return when (action) {
            is PlayAction.Stack -> AutomatumDrags.STACK
            is PlayAction.SetLife, is PlayAction.AdjustLife -> AutomatumDrags.LIFE
            else -> AutomatumDrags.MANUAL
        }
        return null
    }

    /**
     * The step button, the step arrow and the phase dialog with Automatum on (TRN-057): Next step, End turn and a later
     * step go through Automatum ([SharedFlowController.step]); an earlier step, the step it is in, and a step passed into
     * for a quick action's own work are refused. Null with Automatum off (the manual step change goes on).
     */
    override fun automatumStep(intent: TableActionIntent): Boolean? {
        val play = current() ?: return null
        if (!play.board.automatum) return null
        if (cast.open) { error = CastController.BUSY; return false }
        val until = when (intent) {
            is TableActionIntent.Next -> org.asyncttwiz.automatum.StepFlow.Until.Pass
            is TableActionIntent.End -> org.asyncttwiz.automatum.StepFlow.Until.EndOfTurn
            is TableActionIntent.Choose -> {
                val now = TurnPosition.of(play.board.turn)
                if (intent.pass || now == null || intent.position.ordinal <= now.ordinal) {
                    error = SharedFlowController.STEPS_FORWARD; return false
                }
                org.asyncttwiz.automatum.StepFlow.Until.Step(intent.position)
            }
            else -> return null
        }
        error = null
        return flows.step(until)
    }

    /** Whether [action] changes only what is shown, nothing Automatum reads (the values an automated batch's board
     * digest covers, SYS-057). An action the rules refuse anyway counts as shown-only: its own refusal is reported. */
    private fun shownOnly(play: PlayState, action: PlayAction): Boolean {
        val command = GameCommand(DeviceRandom.uuid(), GameScope(play.gameId, playId = play.id), GameAction.Play(action),
            DeviceRandom.uuid(), "1")
        val after = GameplayRules.preview(play, viewer, command).state ?: return true
        if (after.board === play.board) return true
        // #12 (SYS-025): the shown board's digest once per board
        val before = shownDigest?.takeIf { it.first === play.board }?.second
            ?: MtgFacts.digest(play.board).also { shownDigest = play.board to it }
        return MtgFacts.digest(after.board) == before
    }
    private var shownDigest: Pair<BoardState, String>? = null

    /** Convenience members are lowered against the current draft, then sent together. */
    fun submit(action: PlayAction, invocationId: String? = invocation): Boolean {
        val play = current()?.takeIf { enabled() } ?: return false
        // Onto the Stack always as an entry pointing at the card: a real card is never in the Stack (STK-001, STK-003).
        if (action is PlayAction.MoveCard && action.removeStackEntryId == null && play.board.containers.any {
                it.id == action.destination.containerId && it.zone == PlayZone.STACK }) {
            return enqueue(BasicTableController.projection(play, action) ?: return false,
                invocationId ?: DeviceRandom.uuid())
        }
        if (action is PlayAction.InspectFace && acceptedInspection != null) return loggedInspection(action)
        if (draft == null && exitFlow != null && leavesFlow(play, action)) {
            error = null
            return exitFlow.invoke((action as PlayAction.Mtg).action).also { sent ->
                if (!sent) error = "Action could not be queued. Reconnect and try again." }
        }
        val group = invocationId ?: DeviceRandom.uuid()
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

    fun dismiss() { panel = null; library = null; manual.close(); capture = null; popupItem = null; stackTargeting = false }
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
        val lifetime = DeviceRandom.uuid()
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
    /** The Automatum switch's hold panel (integration spec §5). */
    fun automatumPanel() { dismiss(); panel = "automatum" }

    /**
     * The Automatum switch (integration spec §5): turns it [on] or off for the whole game, one shared event. Switching off
     * during an open flow is one server step ([exitFlow], I-D19, finalreview-sync #6) that takes the flow back when it
     * can and otherwise closes it where it is (I-D24); it always goes through. It also drops the player's own open
     * cast, which nothing was sent of. A refusal (I-D15) shows its reason like any refused action.
     */
    fun setAutomatum(on: Boolean): Boolean {
        val play = current() ?: return false
        org.asyncttwiz.automatum.AutomatumLog.switch(play.gameId, on)
        // The player's own open cast was never sent (I-D11): switching off simply drops it (i12review #2)
        if (!on) cast.cancel("dropped: switched off")
        return submit(PlayAction.Mtg(MtgAction.SetAutomatum(on)))
    }

    /** Switching off or "Clear Automatum data" during an open flow goes as one [GameAction.ExitFlow] (finalreview-sync #6,
     * #7): the flow is taken back first when it can be. */
    private fun leavesFlow(play: PlayState, action: PlayAction): Boolean {
        val mtg = (action as? PlayAction.Mtg)?.action
        return (mtg == MtgAction.SetAutomatum(false) || mtg == MtgAction.ClearRecords) && Automatum.flowOpen(play.board)
    }
    fun stackContents() { dismiss(); tokens.close(); showStackBubbles(null); panel = "manageStack" }
    /** Manage Stack is open to pick a target from (an entry below the top, I-D6). */
    var stackTargeting by mutableStateOf(false); private set
    /** The Stack entries the open cast or choice may target now, top first; empty when none. */
    fun stackTargets(play: PlayState): List<String> = if (cast.targeting) cast.stackTargets(play)
        else if (flows.targeting) flows.stackTargets(play) else emptyList()
    /** Picks the Stack entry [entryId] as a target in Manage Stack; the panel closes once picked. */
    fun pickStackTarget(entryId: String) {
        val play = current() ?: return
        val entry = play.board.stackEffects.singleOrNull { it.id == entryId } ?: return
        val item = TableItem("card/$entryId", TableBounds(0f, 0f, 0f, 0f), "Stack entry", "", zone = PlayZone.STACK,
            kind = TableItemKind.CARD, cardId = entryId, sourceId = entry.sourceId)
        if (cast.targeting) cast.tapTarget(item) else flows.tapTarget(item)
        dismiss()
    }

    /** Open information is a personal route, not a shared View/Reveal reference display.
     * [toggle] closes the pile's panel when it is already open; the pile menu's Search and the
     * pile double-tap always open (or keep) it. */
    fun openPublicPile(id: String, toggle: Boolean = true) {
        val container = current()?.board?.containers?.singleOrNull { it.id == id &&
            it.zone in setOf(PlayZone.GRAVEYARD, PlayZone.EXILE, PlayZone.STACK) } ?: return
        if (container.zone == PlayZone.STACK) { stackContents(); return }
        dismiss(); showStackBubbles(null)
        layoutState.value = layoutState.value.copy(localPileId = container.id.takeUnless { toggle && it == layoutState.value.localPileId },
            localPileTargets = false)
    }
    /** A Graveyard/Exile pile tap opens that pile's menu (either player's pile), like the Library menu. */
    fun openPileMenu(id: String) {
        current()?.board?.containers?.singleOrNull { it.id == id && it.zone in PublicPiles.zones } ?: return
        dismiss(); showStackBubbles(null)
        panel = "pile/$id"
    }
    /** Personal selection only: a whole card panel joins (or leaves) the current selection. */
    fun selectAll(ids: List<String>, selected: Boolean) {
        val current = selectionState.value
        selectionState.value = current.copy(ids = if (selected) current.ids + ids else current.ids - ids.toSet())
    }
    fun closeLocalPile() { layoutState.value = layoutState.value.copy(localPileId = null, localPileTargets = false); dismiss() }
    /** The pile opened to pick targets from ([open]), closed again once the targets are chosen. */
    private var targetPile: String? = null
    internal fun targetingEnded() {
        val pile = targetPile ?: return
        targetPile = null
        if (layoutState.value.localPileId == pile) layoutState.value = layoutState.value.copy(localPileId = null, localPileTargets = false)
    }
    fun publicContents(): PileContents? {
        val id = layoutState.value.localPileId?.takeIf { panel == null } ?: return null
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
        dismiss(); tokens.close(); capture = bound; showStackBubbles(null)
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
        // Targets are picked on the table while a cast asks for them (I-D5, I-D6); a Graveyard or Exile pile holding some
        // opens as its card panel to pick them from (i12review #14)
        if (cast.targeting || flows.targeting) {
            val play = current()
            if (play != null && item.kind == TableItemKind.PILE && item.zone in PublicPiles.zones &&
                item.id in cast.glow(play) + flows.glow(play)) {
                openPublicPile(item.id, toggle = false); targetPile = item.id
                layoutState.value = layoutState.value.copy(localPileTargets = true)
                return
            }
            // An entry below the top of the Stack is a legal target: Manage Stack opens to pick it from
            if (play != null && item.zone == PlayZone.STACK && CombatStackRules.stack(play.board).id in cast.glow(play) + flows.glow(play)) {
                stackContents(); stackTargeting = true
                return
            }
            if (cast.targeting) cast.tapTarget(item) else flows.tapTarget(item)
            return
        }
        val play = current() ?: return
        // CMB-028, CMB-032, CMB-034: with Automatum on a tap in combat stages, blocks or selects
        if (combatTap(play, item)) { dismiss(); return }
        if (item.viewport.startsWith("cardPanel/")) {
            val display = (play.board.contentsDisplays + listOfNotNull(localPanelDisplay(play, viewer, layoutState.value)))
                .singleOrNull { it.id == item.viewport.substringAfter('/') } ?: return
            // Either player acts on any panel's cards (rules v30), blind on faces hidden from them.
            if (!enabled()) return
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
            item.zone in PublicPiles.zones -> openPileMenu(item.id)
            else -> openPublicPile(item.id)
        }
        popupItem = item
    }

    fun openLibrary(id: String, tool: LibraryTool = LibraryTool.CONTENTS, handIds: List<String>? = null) {
        val play = current() ?: return
        val source = play.board.containers.singleOrNull { it.id == id && it.zone == PlayZone.LIBRARY }
        // A Temp pile that is not created yet (pile 1 placeholder) still opens its pile menu.
        val ref = if (source == null) TempPiles.refs(play)[id] ?: return else null
        val owner = source?.accountId ?: ref!!.accountId
        val hand = play.board.containers.single { it.zone == PlayZone.HAND && it.accountId == owner }
        dismiss()
        val controller = libraryController(id, hand.id) ?: return
        library = ToolkitLibrary(controller, handIds?.toList() ?: hand.cards.toList(), tool)
        panel = if (source?.temp != false) "temp" else if (tool == LibraryTool.MULLIGAN_N) "mulligan" else "library"
    }
    /** The captured cards into each owner's Temp pile [pile] (1..4, created/turned on by the first move)
     * as ONE invocation. Tokens and cards already in that pile stay where they are. */
    fun toTemp(sourceIds: List<String>, pile: Int): Boolean {
        val play = current()?.takeIf { enabled() } ?: return false
        val cards = tempPileMovable(play, sourceIds, pile)
        if (cards.isEmpty()) return false
        val actions = cards.groupBy { it.owner }.flatMap { (owner, members) ->
            val board = TempPiles.ensure(play.board, play.id, owner, pile)
            org.asyncttwiz.protocol.LibraryActions.moveCaptured(board, members.map { it.id },
                MoveDestination(TempPiles.container(board, owner, pile)!!.id, MoveInsertion.TOP))
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
    /** The cards onto the top or bottom of their owners' Libraries in random order: one event (and one hidden
     * layer) per owner's Library, each with a fresh seed, as ONE invocation. Tokens are left out. */
    fun toLibraryRandom(ids: List<String>, bottom: Boolean): Boolean {
        val play = current()?.takeIf { enabled() } ?: return false
        val actions = org.asyncttwiz.protocol.LibraryActions.toLibraryRandom(play.board, ids, bottom) { DeviceRandom.seed() }
        if (actions.isEmpty()) return false
        return batchResult { actions.all { submit(it) } }.also { if (it) dismiss() }
    }
    /** The whole Temp pile, in its current order, onto the top of its owner's pile [number] as ONE invocation. */
    fun tempToPile(tempId: String, number: Int): Boolean {
        val play = current()?.takeIf { enabled() } ?: return false
        val actions = TempPiles.allToPile(play.board, play.id, tempId, number)
        if (actions.isEmpty()) return false
        return batchResult { actions.all { submit(it) } }.also { if (it) dismiss() }
    }
    /** The Temp pile's top card onto the top of its owner's pile [number] (created by the move if needed). */
    fun tempTopToPile(tempId: String, number: Int): Boolean {
        val play = current()?.takeIf { enabled() } ?: return false
        val actions = TempPiles.topToPile(play.board, play.id, tempId, number)
        if (actions.isEmpty()) return false
        return batchResult { actions.all { submit(it) } }.also { if (it) dismiss() }
    }
    /** The whole Temp pile into its owner's Graveyard or Exile as ONE invocation. */
    fun tempToZone(tempId: String, zone: PlayZone): Boolean {
        val play = current()?.takeIf { enabled() } ?: return false
        val actions = TempPiles.allToZone(play.board, tempId, zone)
        if (actions.isEmpty()) return false
        return batchResult { actions.all { submit(it) } }.also { if (it) dismiss() }
    }
    /** The whole Temp pile into its owner's Library, then that Library shuffled, as ONE invocation (one Undo). */
    fun tempShuffleIntoLibrary(tempId: String): Boolean {
        val play = current()?.takeIf { enabled() } ?: return false
        val seed = DeviceRandom.seed()
        val actions = TempPiles.shuffleIntoLibrary(play.board, tempId, seed)
        if (actions.isEmpty()) return false
        return batchResult { actions.all { submit(it) } }.also { if (it) dismiss() }
    }
    /** Turns a player's Temp pile 2..4 off (only while empty); a shared, undoable event. Piles are only
     * created by moving cards there. */
    fun closeTempPile(owner: String, number: Int): Boolean {
        if (current() == null || !enabled()) return false
        return submit(PlayAction.SetTempPile(owner, number, false)).also { if (it) dismiss() }
    }
    /** Shared panels render directly from the board, including after reconnect and during review. */
    fun showLibraryContents(selection: ContentsSelection, count: Int, reveal: Boolean) {
        val controller = library?.controller ?: return
        val result = controller.show(selection, count, reveal)
        if (result.refused != 0 || result.submitted == 0) return
        dismiss()
    }
    /** Either player closes any panel (rules v29) with the author's flow, so a forgotten panel never blocks the game. */
    fun closeCardPanel(display: ContentsDisplay) {
        if (!enabled()) return
        // Hand reveals return their cards in place with Done; Library reveals keep the rest chooser.
        if (display.reveal && current()?.board?.containers?.any { it.id == display.containerId && it.zone == PlayZone.HAND } == true) {
            if (submit(PlayAction.DismissContents(display.id))) dismiss()
            return
        }
        // A Temp pile panel just closes, leaving the pile in its shown order: no rest choices, no shuffle.
        val temp = current()?.board?.containers?.any { it.id == display.containerId && it.temp } == true
        if (!temp && !display.search && display.cardIds.isNotEmpty()) { dismiss(); panel = "panelRest/${display.id}" }
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
            if (play.board.containers.none { it.id == card.containerId && it.zone == PlayZone.HAND }) return@all false
            val destination = handPlayDestination(play, card) ?: return@all false
            basic.handle(TableIntent.Move(TableScope(id, listOf(id)), destination,
                BasicTableController.sources(play, listOf(id)), handDoubleTap = true))
        }
    }
    /** Morph/disguise (STK-005): every selected hand card gets a face-down Stack entry that, always being a 2/2
     * permanent, auto-resolves onto its owner's Battlefield face down like a hand double-tap: separate history
     * group, same invocation (one-step Undo), all in one batch. */
    fun castFaceDown(ids: List<String>): Boolean = batchResult {
        ids.isNotEmpty() && ids.distinct().all { id ->
            val play = current() ?: return@all false
            val before = CombatStackRules.stack(play.board).cards.toSet()
            if (!submit(HandActions.castFaceDown(play, id) ?: return@all false)) return@all false
            val next = current()?.takeIf { enabled() } ?: return@all true
            val entry = CombatStackRules.stack(next.board).cards.firstOrNull { entry -> entry !in before &&
                next.board.stackEffects.any { it.id == entry && it.sourceId == id && it.faceDown } } ?: return@all true
            // Exactly the manual resolver, as the double-tap uses: it keeps the card face down as a 2/2.
            for (action in StackMenuActions.destination(next, entry, PlayZone.BATTLEFIELD))
                if (!enqueue(action, invocation)) break
            true
        }
    }
    fun randomHand(handId: String, selectedIds: List<String>, result: HandRandomResult): Boolean {
        val play = current()?.takeIf { enabled() } ?: return false
        val seed = DeviceRandom.seed()
        val action = runCatching { HandActions.random(play.board, handId, selectedIds, seed, result) }.getOrNull() ?: return false
        return submit(action)
    }
    fun cardPanelAction(display: ContentsDisplay, id: String, zone: PlayZone, reveal: Boolean = false,
        bottom: Boolean = false, tapped: Boolean = false) = cardPanelAction(display, listOf(id), zone, reveal, bottom, tapped)
    /** Panel cards [ids] (the tapped card, or the selected cards of this panel) as ONE invocation.
     * Disclosure and onward movement keep distinct history groups inside it. */
    fun cardPanelAction(display: ContentsDisplay, ids: List<String>, zone: PlayZone, reveal: Boolean = false,
        bottom: Boolean = false, tapped: Boolean = false, shuffle: Boolean = false): Boolean {
        if (ids.isEmpty() || ids.any { it !in display.cardIds } || !enabled()) return false
        return moveToOwnerZones(ids, zone, bottom, tapped, shuffle, reveal = display.takeIf { reveal }).also { if (it) dismiss() }
    }
    fun localPanelAction(id: String, zone: PlayZone, bottom: Boolean = false, tapped: Boolean = false) =
        localPanelAction(listOf(id), zone, bottom, tapped)
    /** Cards of the open Graveyard/Exile panel, each into its OWNER's [zone], as ONE invocation. */
    fun localPanelAction(ids: List<String>, zone: PlayZone, bottom: Boolean = false, tapped: Boolean = false,
        shuffle: Boolean = false): Boolean {
        val play = current()?.takeIf { enabled() } ?: return false
        val pile = layoutState.value.localPileId ?: return false
        if (ids.isEmpty() || ids.any { id -> play.board.cards.none { it.id == id && it.containerId == pile } }) return false
        return moveToOwnerZones(ids, zone, bottom, tapped, shuffle).also { if (it) dismiss() }
    }
    /** The whole Graveyard/Exile pile [containerId] into its cards' owners' [zone] (Library: random order,
     * or shuffled in with [shuffle]) as ONE invocation. Never into a Hand. */
    fun pileToZone(containerId: String, zone: PlayZone, bottom: Boolean = false, shuffle: Boolean = false): Boolean {
        val play = current()?.takeIf { enabled() } ?: return false
        if (zone == PlayZone.HAND) return false
        val pile = play.board.containers.singleOrNull { it.id == containerId && it.zone in PublicPiles.zones } ?: return false
        return moveToOwnerZones(PublicPiles.movable(play.board, pile.cards, zone), zone, bottom, shuffle = shuffle)
            .also { if (it) dismiss() }
    }
    /** [ids] each into its OWNER's [zone] as ONE invocation. Two or more cards onto a Library top or bottom
     * go in random order ([toLibraryRandom]); [shuffle] instead shuffles every receiving Library afterwards.
     * [reveal] first reveals the not yet revealed cards of that panel. */
    internal fun moveToOwnerZones(ids: List<String>, zone: PlayZone, bottom: Boolean = false, tapped: Boolean = false,
        shuffle: Boolean = false, reveal: ContentsDisplay? = null): Boolean = batchResult {
        val play = current()?.takeIf { enabled() } ?: return@batchResult false
        val cards = PublicPiles.movable(play.board, ids, zone)
        if (cards.isEmpty()) return@batchResult false
        if (reveal != null) for (id in cards.filter { it !in reveal.revealedIds })
            if (!submit(PlayAction.RevealContentsCard(reveal.id, id))) return@batchResult false
        if (zone == PlayZone.LIBRARY && !shuffle && cards.size >= 2) return@batchResult toLibraryRandom(cards, bottom)
        if (!moveEachToOwnerZone(cards, zone, bottom, tapped)) return@batchResult false
        if (shuffle) {
            val board = current()?.board ?: return@batchResult false
            for (library in cards.mapNotNull { id -> board.cards.singleOrNull { it.id == id }?.owner }.distinct()
                .mapNotNull { board.zoneContainer(it, PlayZone.LIBRARY) })
                if (!submit(PlayAction.ShuffleLibrary(library.id, DeviceRandom.seed()))) return@batchResult false
        }
        true
    }
    /** One ordinary move per card, in [ids] order, into each card's OWNER's [zone], inside the current batch. */
    internal fun moveEachToOwnerZone(ids: List<String>, zone: PlayZone, bottom: Boolean = false, tapped: Boolean = false): Boolean = batchResult {
        var sent = false
        // Battlefield cells planned earlier in this batch, in case a later read does not show them yet.
        val taken = mutableSetOf<BoardPosition>()
        for (id in ids.distinct()) {
            val play = current()?.takeIf { enabled() } ?: return@batchResult false
            val card = play.board.cards.singleOrNull { it.id == id } ?: return@batchResult false
            val destination = libraryDestinations(play.board, sourceIds = listOf(id)).map { it.second }.firstOrNull { d ->
                play.board.containers.any { it.id == d.containerId && it.zone == zone } &&
                    (zone != PlayZone.LIBRARY || d.insertion == if (bottom) MoveInsertion.BOTTOM else MoveInsertion.TOP)
            }?.let { d -> if (zone != PlayZone.BATTLEFIELD) d
                else d.copy(position = BattlefieldPlacement.cell(play, d.containerId, card, taken).also { taken += it }) }
                ?: return@batchResult false
            if (!submit(PlayAction.MoveCard(id, MoveSource(card.containerId, card.placement), destination))) return@batchResult false
            if (tapped && !submit(PlayAction.SetTapped(id, true))) return@batchResult false
            sent = true
        }
        sent
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
            val sent = batchResult(intent.invocationId ?: DeviceRandom.uuid()) { adapter.handle(intent) }
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
        // Automatum on (I2a, ZON-004): a hand double-tap casts (or plays, a land) through the engine
        if (intent is TableIntent.Move && intent.handDoubleTap && current()?.board?.automatum == true) return cast.start(intent.scope.contextId)
        // Automatum on (I-D36, I-D28): a drag is the engine action it means, or refused with the reason; while Automatum
        // waits for something done by hand drags work as with it off (I-D37, SYS-069)
        val engineDrags = current()?.let { it.board.automatum && !AutomatumBoards.byHand(it) } == true
        if (intent is TableIntent.Move && intent.drag && engineDrags) return automatumDrag(intent)
        // I-D47: a double-tap on a battlefield card taps it for mana through Automatum (the mana goes to the pool)
        if (intent is TableIntent.Rotate && intent.toggle && engineDrags) tapForMana(intent)?.let { return it }
        if (intent is TableIntent.PlayerDelta && intent.color == null && engineDrags) {
            error = AutomatumDrags.LIFE; return false
        }
        if (intent is TableIntent.Batch) return batchResult(intent.invocationId) {
            intent.intents.all { handle(it) }
        }
        val multiple = when (intent) {
            is TableIntent.Move -> intent.scope.cardIds.size > 1 || intent.handDoubleTap
            is TableIntent.Rotate -> intent.scope.cardIds.size > 1
            is TableIntent.StackDestination, is TableIntent.StackBubbleRemove -> true
            else -> false
        }
        return if (multiple) batchResult((intent as? TableIntent.Move)?.invocationId ?: DeviceRandom.uuid()) {
            handleOne(intent)
        } else handleOne(intent)
    }
    /**
     * I-D47 (TBL-043): a double-tap on a battlefield card with Automatum on activates its mana ability, the mana going to
     * its controller's pool for the next cast (autotap pays with the pool first): the one mana ability Automatum allows
     * now goes as from its menu (TBL-115); with several (or one asking a colour) the panel or the card's menu lets the
     * player choose. Null when the card has no mana ability allowed now (then the double-tap is what it was).
     */
    private fun tapForMana(intent: TableIntent.Rotate): Boolean? {
        val play = current()?.takeIf { enabled() } ?: return null
        val card = CombatStackRules.liveSource(play.board, intent.scope.contextId)?.id ?: return null
        val plays = cardPlays(play) ?: run { error = WORKING_OUT; return false }
        val mana = plays.of(card).filter { it.kind == org.asyncttwiz.automatum.CardPlay.Kind.Mana && it.legal }
        return when (mana.size) {
            0 -> null
            1 -> play(card, mana.single())
            else -> { openCard(TableScope(card, listOf(card)), intent.sources); true }
        }
    }

    /**
     * A drag with Automatum on (I-D36): a hand card dropped on the Battlefield or the Stack is played or cast (the cast
     * panel, as a double-tap, ZON-004); a creature dropped into the combat area joins the draft of the declaration the
     * engine awaits ([CombatController.draftRefusal]); a card moved within its own Battlefield or hand only changes its
     * place. Everything else would change the game by hand and is refused with the reason (I-D28).
     */
    private fun automatumDrag(intent: TableIntent.Move): Boolean {
        val play = current()?.takeIf { enabled() } ?: return false
        when (val route = AutomatumDrags.route(play, intent)) {
            AutomatumDrags.Route.Cast -> {
                if (intent.scope.cardIds.distinct().size > 1) { error = AutomatumDrags.ONE_AT_A_TIME; return false }
                return cast.start(intent.scope.contextId)
            }
            AutomatumDrags.Route.Place -> return engineRoute { handle(intent.copy(drag = false)) }
            AutomatumDrags.Route.Combat -> {
                val destination = (intent.destination as TableDestination.Combat).destination
                val bound = bind(intent.scope, intent.sources) ?: return false
                // CMB-034: one of the defender's creatures dropped on an attacker's pair blocks it, as tapping does
                val choices = combat.choices(play).also { if (combat.working(it)) { error = CombatController.WORKING_OUT; return false } }.choices
                val card = bound.sourceIds.singleOrNull()
                val pair = destination.pairId
                if (choices.declaring != null && !choices.attackers && card != null && pair != null &&
                    controllerOf(play, card) == choices.declaring && play.board.combat?.let { area ->
                        CombatStackRules.occurrences(area).firstOrNull { it.sourceId == card }
                            ?.let { CombatStackRules.blockedPairs(area, it.id).firstOrNull() } } != pair)
                    return block(play, card, pair)
                combat.draftRefusal(play, bound.sourceIds, destination.role, destination.pairId, destination.attackTarget)
                    ?.let { error = it; return false }
                return engineRoute { handle(intent.copy(drag = false)) }
            }
            AutomatumDrags.Route.LeaveDraft -> {
                val bound = bind(intent.scope, intent.sources) ?: return false
                return leaveDraft(play, bound)
            }
            is AutomatumDrags.Route.Refused -> { error = route.reason; return false }
        }
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
                if (intent.destination is TableDestination.Combat) current()?.takeIf { enabled() }
                    ?.let { arrowDrop(it, bound, intent.destination.destination) } ?: place(bound, intent.destination.destination)
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
    // ---- The combat area with Automatum on (I-D32)

    private fun creature(play: PlayState, card: String): Boolean {
        val c = play.board.cards.firstOrNull { it.id == card } ?: return false
        if (c.isFaceDown) return true
        val face = play.definitions.firstOrNull { it.digest == c.definition }?.definition?.faces?.firstOrNull { it.faceId == c.faceId }
        return AutomatumBoards.cached(play, viewer)?.cards?.get(card)?.typeLine?.contains("Creature", ignoreCase = true)
            ?: face?.typeLine?.contains("Creature", ignoreCase = true) ?: false
    }

    private fun controllerOf(play: PlayState, card: String): String? =
        play.board.containers.firstOrNull { it.id == play.board.cards.firstOrNull { c -> c.id == card }?.containerId }?.accountId

    /**
     * A tap on [item] with Automatum on while combat is being declared or shown (CMB-028, CMB-032, CMB-034): one of the
     * attacking player's creatures is staged or unstaged; one of the defending player's creatures is picked as the blocker
     * to place, and an attacker tapped then is blocked or unblocked by it; other combat copies are selected, the selected
     * one's menu opening on the next tap. False when the tap opens the card's menu as usual.
     */
    private fun combatTap(play: PlayState, item: TableItem): Boolean {
        if (!play.board.automatum || AutomatumBoards.byHand(play) || item.kind != TableItemKind.CARD) return false
        val card = item.sourceId ?: return false
        val copy = item.viewport == "combat"
        if (!copy && (item.zone != PlayZone.BATTLEFIELD || item.projection)) return false
        if (copy && play.board.combat == null) return false
        // Finalreview-combat #5: not worked out yet (the other player's event just arrived): said, never worked out here
        val choices = combat.choices(play).also { if (combat.working(it)) { error = CombatController.WORKING_OUT; return true } }.choices
        if (choices.unsupported.isNotEmpty()) return false
        val area = play.board.combat
        val occurrence = CombatStackRules.occurrences(area).firstOrNull { it.sourceId == card }
        val attacking = occurrence != null && area!!.pairs.any { p -> p.attackers.any { it.id == occurrence.id } }
        val declaring = choices.declaring
        if (declaring != null && enabled() && controllerOf(play, card) == declaring) {
            if (choices.attackers) {
                if (attacking) return unstage(occurrence!!.id)
                if (card !in choices.attacks && !creature(play, card)) return false
                return stage(play, listOf(card), combat.activeTarget(play))
            }
            if (!attacking) {
                if (card !in choices.blocks && !creature(play, card)) return false
                if (card !in choices.blocks) { error = "${combatName(play, card)} can't block now."; return true }
                combat.select(if (combat.selected == card) null else card)
                return true
            }
        }
        val blocker = combat.selected
        // CMB-034: the attacker's copy or its real card (combat review #12)
        if (declaring != null && !choices.attackers && attacking && blocker != null && blocker in choices.blocks && enabled()) {
            val pair = area!!.pairs.first { p -> p.attackers.any { it.id == occurrence!!.id } }.id
            block(play, blocker, pair)
            return true
        }
        if (!copy) return false
        // CMB-032: select a combatant; the selected one's own tap opens its Combat menu
        if (combat.selected == card) { combat.clearSelection(); return false }
        combat.select(card)
        return true
    }

    /** CMB-028: stages [cards] as attackers of [target] (null: the defending player), each in a pair of its own at the end,
     * opening the combat area first when needed; one Undo step. Refused with Automatum's reason when one can't attack it. */
    internal fun stage(play: PlayState, cards: List<String>, target: String?): Boolean {
        if (cards.isEmpty()) return false
        combat.draftRefusal(play, cards, CombatRole.ATTACKER, null, target)?.let { error = it; return false }
        error = null
        return engineRoute { batchResult {
            if (current()?.board?.combat == null && !submit(PlayAction.Combat(CombatAction.Open))) return@batchResult false
            var sent = false
            for (card in cards) {
                val area = current()?.board?.combat ?: break
                sent = submit(PlayAction.Combat(CombatAction.Place(card, CombatDestination(area.id, CombatRole.ATTACKER,
                    pairGap = IdentityGap(area.pairs.lastOrNull()?.id), attackTarget = target)))) || sent
            }
            sent
        } }
    }

    private fun unstage(occurrenceId: String): Boolean {
        val area = current()?.board?.combat ?: return false
        error = null
        return engineRoute { submit(PlayAction.Combat(CombatAction.Remove(area.id, occurrenceId))) }
    }

    /** CMB-034: [blocker] blocks the attackers of [pairId] too, or no longer when it does; one that can block one attacker
     * moves to it. Deselected once it blocks as many attackers as it can. */
    internal fun block(play: PlayState, blocker: String, pairId: String): Boolean {
        val area = play.board.combat ?: return false
        val choices = combat.choices(play).also { if (combat.working(it)) { error = CombatController.WORKING_OUT; return false } }.choices
        val occurrence = CombatStackRules.occurrences(area).firstOrNull { it.sourceId == blocker }
        val now = occurrence?.let { CombatStackRules.blockedPairs(area, it.id) }.orEmpty()
        val capacity = if (blocker in choices.capacity) choices.capacity[blocker] else 1
        val next = if (pairId in now) now - pairId else {
            combat.draftRefusal(play, listOf(blocker), CombatRole.BLOCKER, pairId, null)?.let { error = it; return false }
            when {
                capacity == null || now.size < capacity -> now + pairId
                capacity == 1 -> listOf(pairId)
                else -> { error = "${combatName(play, blocker)} can block only $capacity attackers."; return false }
            }
        }
        error = null
        val sent = engineRoute { submit(PlayAction.Combat(CombatAction.SetBlocks(area.id, blocker, next, now))) }
        if (sent && pairId !in now && capacity != null && next.size >= capacity) combat.clearSelection()
        return sent
    }

    /** CMB-028, CMB-034: the staged attackers, or the blocks, leave the lanes; one Undo step. */
    internal fun clearCombatDraft(play: PlayState): Boolean {
        val area = play.board.combat ?: return false
        val attackers = combat.choices(play).also { if (combat.working(it)) { error = CombatController.WORKING_OUT; return false } }.choices.attackers
        val ids = area.pairs.flatMap { if (attackers) it.attackers else it.blockers }.map { it.id }
        if (ids.isEmpty()) return false
        combat.clearSelection()
        return engineRoute { batchResult { ids.map { submit(PlayAction.Combat(CombatAction.Remove(area.id, it))) }.any { it } } }
    }

    /** CMB-031: with Automatum off, a card of the defending player dropped on an attacker ([destination] in an attacker
     * row) blocks it: one more arrow for a blocker of another attacker, the arrow removed when it already blocks it by
     * one. Null when the drop is an ordinary placement. */
    private fun arrowDrop(play: PlayState, bound: ToolkitCapture, destination: CombatDestination): Boolean? {
        val pair = destination.pairId?.takeIf { destination.role == CombatRole.ATTACKER } ?: return null
        val area = play.board.combat?.takeIf { it.id == destination.areaId } ?: return null
        val card = bound.sourceIds.singleOrNull() ?: return null
        if (controllerOf(play, card) != CombatStackRules.defendingPlayer(play.board)) return null
        val occurrence = CombatStackRules.occurrences(area).firstOrNull { it.sourceId == card }
        if (occurrence != null && area.pairs.any { p -> p.attackers.any { it.id == occurrence.id } }) return null
        val now = occurrence?.let { CombatStackRules.blockedPairs(area, it.id) }.orEmpty()
        val next = when {
            now.firstOrNull() == pair -> return true
            pair in now -> now - pair
            else -> now + pair
        }
        return submit(PlayAction.Combat(CombatAction.SetBlocks(area.id, card, next, now)))
    }

    fun perform(intent: TableActionIntent): Boolean {
        // Automatum on (CMB-028, CMB-034): staging and clearing the declaration's draft
        if (intent is TableActionIntent.Combat && intent.tool in setOf(CombatTool.AUTOMATUM_STAGE, CombatTool.AUTOMATUM_CLEAR,
                CombatTool.AUTOMATUM_NO_BLOCKS)) {
            if (!table.enabled(intent.scope)) return false
            if (cast.open) { error = CastController.BUSY; return false }
            return automatumCombatTool(intent)
        }
        // Automatum on (I3): declarations and passing on to the damage are engine calls of their own
        if (intent is TableActionIntent.Combat && (intent.tool == CombatTool.AUTOMATUM_DECLARE || intent.tool == CombatTool.AUTOMATUM_PASS)) {
            if (!table.enabled(intent.scope)) return false
            if (cast.open) { error = CastController.BUSY; return false }
            // The same as Next step (TRN-057): a declaration awaited declares what the lanes hold, else the holder passes
            error = null
            return flows.step(org.asyncttwiz.automatum.StepFlow.Until.Pass).also {
                if (it) { selectionState.value = TableSelection(); combat.clearSelection() }
            }
        }
        // Close + next returns its whole-batch success, not the void route receipt.
        if (intent is TableActionIntent.Combat && intent.tool in combatDamageTools) {
            if (!table.enabled(intent.scope)) return false
            return performCombatDamage(intent)
        }
        // Deliberately NOT one batch: two events; Declare blockers stays a timeline point (rules v29).
        if (intent is TableActionIntent.Combat && intent.tool == CombatTool.ASSUME_NO_BLOCKS) {
            if (!table.enabled(intent.scope)) return false
            return assumeNoBlocks().also { refreshAttackSelection() }
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
        if (intent.advance && !table.perform(TableActionIntent.Choose(intent.scope, TurnPosition.DECLARE_ATTACKERS))) return
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
                // Rules v33: End of combat is recorded on the way. The close stays first so it all reads as one group.
                current()?.let { play -> StepRun.choose(play.board, TurnPosition.POSTCOMBAT_MAIN).forEach { submit(it) } }
            }
            CombatTool.DECLARE, CombatTool.DECLARE_AND_TAP -> declare(intent.sourceIds, intent.tool == CombatTool.DECLARE_AND_TAP)
            CombatTool.ASSUME_NO_BLOCKS -> assumeNoBlocks()
            CombatTool.DAMAGE_CLOSE_AND_NEXT -> performCombatDamage(intent)
            CombatTool.AUTOMATUM_DECLARE, CombatTool.AUTOMATUM_PASS -> flows.step(org.asyncttwiz.automatum.StepFlow.Until.Pass)
            CombatTool.AUTOMATUM_STAGE, CombatTool.AUTOMATUM_CLEAR, CombatTool.AUTOMATUM_NO_BLOCKS -> automatumCombatTool(intent)
        }
    }
    /** "Rest → <target>", "Clear" and "No blocks" with Automatum on (CMB-028, CMB-034). */
    private fun automatumCombatTool(intent: TableActionIntent.Combat): Boolean {
        val play = current() ?: return false
        return when (intent.tool) {
            CombatTool.AUTOMATUM_STAGE -> stage(play, intent.sourceIds, combat.activeTarget(play))
            CombatTool.AUTOMATUM_CLEAR -> clearCombatDraft(play)
            else -> noBlocks(play, confirmed = null)
        }.also { if (it) combat.clearSelection() }
    }

    /** "No blocks" (CMB-034): the blocks in the lanes are cleared, then the declaration is the step button's (TRN-057).
     * I-D40: for the other player it asks first, before anything is cleared ([confirmed]: the player agreed to). */
    private fun noBlocks(play: PlayState, confirmed: String?): Boolean {
        val clear = CombatStackRules.blocks(play.board.combat).isNotEmpty()
        val defender = CombatStackRules.defendingPlayer(play.board)
        if (clear && defender != null && defender != viewer && defender != confirmed) {
            flows.ask(ActingFor(play.board, defender, org.asyncttwiz.automatum.StepFlow.Until.Pass, ActingFor.Kind.NoBlocks) { player ->
                (current()?.let { noBlocks(it, player) } ?: false).also { if (it) combat.clearSelection() }
            })
            return false
        }
        if (clear && !clearCombatDraft(play)) return false
        error = null
        return flows.step(org.asyncttwiz.automatum.StepFlow.Until.Pass, confirmed)
    }
    /** Declare blockers, then Combat damage, as two separate invocations (two undo steps, two events). */
    private fun assumeNoBlocks(): Boolean {
        if (draft != null) return false
        current()?.takeIf { enabled() && TurnPosition.of(it.board.turn) == TurnPosition.DECLARE_ATTACKERS } ?: return false
        if (!submit(PlayAction.ChooseStep(TurnPosition.DECLARE_BLOCKERS), null)) return false
        return submit(PlayAction.ChooseStep(TurnPosition.COMBAT_DAMAGE), null)
    }
    private val combatDamageTools = setOf(CombatTool.DAMAGE_CLOSE_AND_NEXT)
    /** CMB-022: "Close + next" at Combat damage. */
    private fun performCombatDamage(intent: TableActionIntent.Combat): Boolean {
        current()?.takeIf { enabled() && it.board.combat?.id == intent.areaId } ?: return false
        // Rules v33: End of combat is recorded on the way. The close comes first: a close after a step change would
        // start a second history group.
        return batchResult {
            (intent.areaId == null || submit(PlayAction.Combat(CombatAction.Close(intent.areaId)))) &&
                StepRun.choose(current()!!.board, TurnPosition.POSTCOMBAT_MAIN).all { submit(it) }
        }
    }
    fun declare(ids: List<String>, tap: Boolean): Boolean = batchResult {
        val play = current()?.takeIf { enabled() } ?: return@batchResult false
        val eligible = ids.distinct().filter { id -> play.board.cards.any { card -> card.id == id &&
            play.board.containers.any { it.id == card.containerId && it.zone == PlayZone.BATTLEFIELD } } }
        if (eligible.isEmpty()) return@batchResult false
        // Automatum on (I3): Declare puts them into the draft in the lanes, only as the engine allows; attacking taps
        // them when the attack is declared (CR 508.1f)
        if (play.board.automatum) {
            combat.draftRefusal(play, eligible, CombatRole.ATTACKER, null, null)?.let { error = it; return@batchResult false }
            return@batchResult engineRoute {
                if (current()?.board?.combat == null) submit(PlayAction.Combat(CombatAction.Open))
                val area = current()?.board?.combat?.id ?: return@engineRoute false
                var drafted = false
                eligible.forEach { drafted = submit(PlayAction.Combat(CombatAction.Declare(area, it))) || drafted }
                if (drafted) selectionState.value = selectionState.value.afterDeclare()
                drafted
            }
        }
        // Taps first: a tap group folds the following declarations into one history group.
        if (tap) eligible.forEach { submit(PlayAction.SetTapped(it, true)) }
        if (current()?.board?.combat == null) submit(PlayAction.Combat(CombatAction.Open))
        val area = current()?.board?.combat?.id ?: return@batchResult false
        var sent = false
        eligible.forEach { sent = submit(PlayAction.Combat(CombatAction.Declare(area, it))) || sent }
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
    /** Combat copies leave the combat area: with Automatum on only what is still a draft of the declaration it awaits
     * (CMB-028): any copy while attackers are awaited, blockers while blockers are. */
    fun removeFromCombat(bound: ToolkitCapture): Boolean {
        val play = current()?.takeIf { enabled() } ?: return false
        return if (play.board.automatum && !AutomatumBoards.byHand(play)) leaveDraft(play, bound) else removeOccurrences(bound)
    }
    private fun leaveDraft(play: PlayState, bound: ToolkitCapture): Boolean {
        val choices = combat.choices(play).also { if (combat.working(it)) { error = CombatController.WORKING_OUT; return false } }.choices
        val blockers = bound.viewIds.all { id -> play.board.combat?.pairs?.any { p -> p.blockers.any { it.id == id } } == true }
        if (choices.declaring == null || (!choices.attackers && !blockers)) {
            error = if (choices.declaring == null) CombatController.DECLARED else CombatController.DECLARED_ATTACKERS
            return false
        }
        return engineRoute { removeOccurrences(bound) }
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
    fun setAttackTarget(bound: ToolkitCapture, cardId: String?): Boolean {
        val play = current()?.takeIf { enabled() } ?: return false
        if (!play.board.automatum || AutomatumBoards.byHand(play)) return retarget(bound, cardId)
        // Automatum on (CMB-028): a drafted attacker attacks another target only as Automatum allows
        val sources = CombatStackController.attackerPairs(play.board, bound.viewIds).flatMap { it.attackers }.map { it.sourceId }
        sources.firstNotNullOfOrNull { combat.draftRefusal(play, listOf(it), CombatRole.ATTACKER, null, cardId) }?.let { error = it; return false }
        return engineRoute { retarget(bound, cardId) }
    }
    private fun retarget(bound: ToolkitCapture, cardId: String?): Boolean = batchResult {
        val board = current()?.board ?: return@batchResult false
        if (board.combat?.id != bound.areaId) return@batchResult false
        var sent = false
        for (pair in CombatStackController.attackerPairs(board, bound.viewIds))
            sent = submit(PlayAction.Combat(CombatAction.SetAttackTarget(bound.areaId!!, pair.id, cardId))) || sent
        sent
    }

    /** Whole-drop structural preflight uses the existing shared helper; only real-session callbacks send.
     * Request-derived occurrence IDs are read from that session's immediate prediction, never the probe. */
    fun place(bound: ToolkitCapture, destination: CombatDestination): Boolean = batchResult {
        val initial = current()?.takeIf { enabled() } ?: return@batchResult false
        if (CombatStackController.retargetDrop(initial.board, bound.viewIds, destination).isNotEmpty())
            return@batchResult setAttackTarget(bound,
                initial.board.combat?.pairs?.find { it.id == destination.pairId }?.attackTarget ?: destination.attackTarget)
        val probe = runCatching { CombatStackController.place(initial, viewer, bound.viewIds, destination) { action ->
            GameCommand(DeviceRandom.uuid(), GameScope(initial.gameId, playId = initial.id),
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
