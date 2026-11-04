package org.asyncttwiz.android

import androidx.compose.runtime.*
import org.asyncttwiz.protocol.*
import org.asyncttwiz.automatum.AttackChoice
import org.asyncttwiz.automatum.PreAnswer
import org.asyncttwiz.automatum.PreAnswers

data class ToolkitCapture(val viewIds: List<String>, val contextViewId: String,
    val sourceIds: List<String>, val sources: Map<String, TableMoveSource>, val areaId: String?)

/** Personal routes/captured input only. All mutations enter the one existing gameplay session.
 * No acknowledgement gate, board store, command envelope factory or second request queue lives here. */
class TableToolkitController(val viewer: String, private val state: () -> TableBoard?,
    private val connected: () -> Boolean, private val gameplay: (PlayAction, String?) -> Boolean,
    private val ending: (TableActionIntent.Ending) -> Boolean = { false },
    private val waitingForConnection: () -> Boolean = { false },
    private val playBatch: ((List<PlayAction>, String) -> Boolean)? = null,
    /** Where a cast's engine work runs (SYS-025): off the main thread in the app. */
    automatumWork: AutomatumWork = AutomatumWork.Inline,
    /** The pictures of tokens Automatum makes (§4.6): the catalog's best matching token printings in the app. */
    tokenArt: org.asyncttwiz.automatum.TokenArt = org.asyncttwiz.automatum.TokenArt.None,
    /** I-D56 step C: the routes of the game played on engine state (null: none, as in tests of the table alone). */
    val engine: EngineRoutes? = null) : TableActionRoutes {
    private val identity = state()?.let { TableActionScope(it.gameId, it.playId) }
    /** R4a: the batch being drafted on the board of a game on engine state ([EngineStaging]). */
    private var engineDraft: EngineDraft? = null
    fun current(): TableBoard? = (engineDraft?.state ?: state())
        ?.takeIf { TableActionScope(it.gameId, it.playId) == identity }
    fun enabled() = connected() && current() != null
    /** Casting from the hand through Automatum (I2a): a private flow; while it is open nothing else changes (D32). */
    internal val cast = CastController(viewer, { current()?.takeIf { enabled() } }, { castMessage(it) },
        automatumWork, tokenArt = tokenArt, shake = { shake(it) }, engineSend = engine?.let { e -> e::send })
    /** Priority, responding, resolution and triggers through Automatum (I2b): shared flows; while one waits for an
     * answer nothing else changes (D32). */
    internal val flows = SharedFlowController(viewer, { current()?.takeIf { enabled() } }, { error = it },
        automatumWork, tokenArt = tokenArt, autotap = { cast.autotap }, engineSend = engine?.let { e -> e::send })
    private val automatumWork = automatumWork

    /** The words the step arrow shows on [play] ([AutomatumStepArrow]): the board's own. */
    private fun arrowStep(play: TableBoard): AutomatumStep = AutomatumStep.of(play)
    /** Combat through Automatum (I3 on today's lanes): declarations, passing on to the damage, early divisions. */
    internal val combat = CombatController({ current()?.takeIf { enabled() } }, { submit(it) }, automatumWork,
        { error }, { error = it }, board = { current() })
    /** Bumped when hand plays worked out in the background are ready, so the hand menu shows them (SYS-025). */
    private var handPlaysReady by mutableStateOf(0)

    /** What the viewer may cast or play from their hand on [play] (I-D28), or null while it is being worked out off the
     * main thread (SYS-025); the hand menu recomposes once it is ready. */
    internal fun handPlays(play: TableBoard): org.asyncttwiz.automatum.HandPlays? {
        handPlaysReady
        AutomatumBoards.cachedHandPlays(play, viewer)?.let { return it }
        if (automatumWork === AutomatumWork.Inline) return AutomatumBoards.handPlays(play, viewer)
        if (handPlaysPending !== play.board) {
            handPlaysPending = play.board
            automatumWork.run({ AutomatumBoards.handPlays(play, viewer) }) { handPlaysPending = null; handPlaysReady++ }
        }
        return null
    }
    private var handPlaysPending: Any? = null

    /** What the engine offers the viewer for each card on [play] (§3 I4: the card menus), or null while it is being
     * worked out off the main thread (SYS-025); the menus recompose once it is ready. */
    internal fun cardPlays(play: TableBoard): org.asyncttwiz.automatum.CardPlays? {
        handPlaysReady
        AutomatumBoards.cachedCardPlays(play, viewer)?.let { return it }
        if (automatumWork === AutomatumWork.Inline) return AutomatumBoards.cardPlays(play, viewer)
        if (cardPlaysPending !== play.board) {
            cardPlaysPending = play.board
            automatumWork.run({ AutomatumBoards.cardPlays(play, viewer) }) { cardPlaysPending = null; handPlaysReady++ }
        }
        return null
    }
    private var cardPlaysPending: Any? = null

    /**
     * I-D46 (a): the cards the viewer may do something with through Automatum now (cast or play it, activate one of its
     * abilities other than mana abilities, special actions included): exactly the engine's options for the viewer
     * ([org.asyncttwiz.automatum.CardPlays], worked out off the main thread and kept per board). Empty with Automatum
     * off, while a cast or an Automatum choice is open, while the viewer can't act, and until it is worked out.
     */
    internal fun playable(play: TableBoard): Set<String> = playableOrNull(play).orEmpty()

    /** [playable], or null while Automatum still works the board out (the glow keeps what it showed meanwhile, TBL-117). */
    internal fun playableOrNull(play: TableBoard): Set<String>? {
        if (!enabled() || cast.open || play.flowOpen) return emptySet()
        // The game begins only once both players are Ready (TRN-062)
        if (play.board.turn.number == "1" && PlayerStatus.anyOpening(play.board)) return emptySet()
        val plays = cardPlays(play) ?: return null
        playableCache?.takeIf { it.first === plays }?.let { return it.second }
        // A mana ability alone is no reason to glow (owner, 2026-11-03): lands and mana rocks would glow whenever you hold
        // priority
        return plays.byCard.filterValues { offered -> offered.any { it.legal && it.kind != org.asyncttwiz.automatum.CardPlay.Kind.Mana } }
            .keys.also { playableCache = plays to it }
    }
    private var playableCache: Pair<org.asyncttwiz.automatum.CardPlays, Set<String>>? = null

    /** Whether [cardId], a card in the viewer's hand, is known to have no cast or play Automatum allows now (I-D46). */
    private fun unplayable(play: TableBoard, cardId: String): Boolean {
        val plays = AutomatumBoards.cachedCardPlays(play, viewer) ?: return false
        if (plays.unsupported.isNotEmpty() || !CastController.ownHand(play, cardId, viewer)) return false
        return plays.of(cardId).none { it.legal && (it.kind == org.asyncttwiz.automatum.CardPlay.Kind.Cast ||
            it.kind == org.asyncttwiz.automatum.CardPlay.Kind.Play) }
    }

    /** Begins what a card menu offers through Automatum ([CastController.begin]): the cast panel's flow. */
    internal fun play(cardId: String, play: org.asyncttwiz.automatum.CardPlay): Boolean {
        val input = play.input ?: return false
        error = null
        return cast.begin(cardId, input, play.text)
    }

    /** TBL-122 (async play §29.5): the battlefield card whose ability chips are open (personal UI), null when none. */
    var abilityCard by mutableStateOf<String?>(null); private set
    /** The ability chip held for its details (an index of [activatable]), null when none. */
    var heldAbility by mutableStateOf<Int?>(null)

    /** TBL-122: the abilities of the battlefield card [cardId] the viewer can activate on [play] now (mana abilities and
     * special actions included), in printed order; empty while a cast or an Automatum choice is open, null while
     * Automatum still works the board out. */
    internal fun activatable(play: TableBoard, cardId: String): List<org.asyncttwiz.automatum.CardPlay>? {
        if (!enabled() || cast.open || play.flowOpen) return emptyList()
        val plays = cardPlays(play) ?: return null
        return plays.of(cardId).filter { it.legal && it.kind != org.asyncttwiz.automatum.CardPlay.Kind.Cast &&
            it.kind != org.asyncttwiz.automatum.CardPlay.Kind.Play }
    }

    /**
     * TBL-122 (async play §29.5, MTG Arena): a tap on the viewer's battlefield card [cardId] activates the one ability they
     * can activate now (as from its menu, TBL-115: a mana ability adds its mana to the pool at once; costs, targets and
     * Autotap go through the cast flow), or opens the ability chips above it when there are several. False when it has
     * none (or Automatum still works the board out): the tap opens the card's menu as before.
     */
    internal fun tapPermanent(cardId: String): Boolean {
        val play = current() ?: return false
        val offered = activatable(play, cardId)
        if (offered.isNullOrEmpty()) return false
        dismiss()
        if (offered.size == 1) play(cardId, offered.single()) else abilityCard = cardId
        return true
    }

    /** TBL-122: a tap on the ability chip [index] of the open chips activates that ability. */
    internal fun activateChip(index: Int): Boolean {
        val card = abilityCard ?: return false
        val play = current() ?: return false
        val chosen = activatable(play, card)?.getOrNull(index) ?: return false
        closeAbilities()
        return play(card, chosen)
    }

    /** Closes the ability chips (a tap elsewhere, an activation). */
    fun closeAbilities() { abilityCard = null; heldAbility = null }
    val layoutState = mutableStateOf(TableLayoutState())
    /** I-D50 (ZON-079, ZON-080): the viewer's hand with Automatum on: a tap raises a card with its cost chips. */
    internal val hand = HandController(viewer, { current()?.takeIf { enabled() } }, layoutState, automatumWork,
        cast = { card, way, extras -> error = null; castGesture = card; cast.start(card, way, extras) },
        shake = { shake(it) }, unplayable = { play, card -> AutomatumBoards.cachedCardPlays(play, viewer)?.let { unplayable(play, card) } })
    val table = TableActionController(::current, ::enabled, this)
    private val basic = BasicTableController(::current, ::enabled, ::enqueue)
    private var invocation: String? = null
    private var lastInvocation: String? = null
    var panel by mutableStateOf<String?>(null); private set
    var popupItem by mutableStateOf<TableItem?>(null); private set
    /** The player whose Life panel is open (TRN-030), tapped on their life square; personal UI. */
    var player by mutableStateOf<String?>(null); private set
    var capture by mutableStateOf<ToolkitCapture?>(null); private set
    var inspection by mutableStateOf<ManualInspection?>(null); private set
    var peek by mutableStateOf<ManualInspection?>(null); private set
    fun peek(id: String?) {
        if (id == null) { peek = null; return }
        val play = current() ?: return
        val card = CombatStackRules.inspection(play.board, id) ?: return
        peek = ManualInspection(play.gameId, play.playId, id,
            ManualDisclosure.permittedFaces(play.board, play.definitions, card, viewer, inspecting = true).map { it.faceId },
            stackEntryId = id.takeIf { it in CombatStackRules.stack(play.board).cards })
    }
    var error by mutableStateOf<String?>(null); private set
    fun dismissError() { error = null }

    /** I-D46: what Automatum doesn't allow, tried on a card or pile by a gesture, shakes it instead of a message; why
     * shows when the card is inspected. [serial] tells one shake from the next of the same item. */
    data class Shake(val id: String, val serial: Int)
    var shaking by mutableStateOf<Shake?>(null); private set
    private var shakes = 0
    fun shake(id: String) { error = null; shaking = Shake(id, ++shakes) }
    /** UI review round 2 #4 (I-D46 b): an action refused because the viewer's own cast is open ("Finish or cancel the cast
     * first.") shakes what it was made on (the card or pile whose menu it came from), else the card being cast, instead
     * of a message. */
    private fun busyShake() {
        val target = capture?.contextViewId ?: popupItem?.id ?: cast.castCard
        if (target != null) shake(target) else error = null
    }
    /** Automatum on (I-D28): the ordinary actions an Automatum interaction is made of (moving a card within its own
     * Battlefield or hand, the combat draft) go through while this is above zero; other manual changes are refused. */
    private var engineRoute = 0
    private fun <T> engineRoute(block: () -> T): T { engineRoute++; try { return block() } finally { engineRoute-- } }
    private var reviewContext: String? = null
    fun reviewContext(context: String) {
        if (reviewContext != null && reviewContext != context) { closeInspection(); dismiss() }
        reviewContext = context
    }

    private fun batchResult(requestId: String = DeviceRandom.uuid(), block: () -> Boolean): Boolean {
        if (engineDraft != null) return block()
        if (cast.open) { busyShake(); return false }
        val initial = current() ?: return block()
        return engineBatch(initial, requestId, block)
    }
    /** [batchResult]: its members drafted on the board shown ([EngineDraft]), then sent together. */
    private fun engineBatch(initial: TableBoard, requestId: String, block: () -> Boolean): Boolean {
        val pending = EngineDraft(initial, viewer, requestId)
        engineDraft = pending; invocation = pending.requestId; error = null
        val result = try { block() } finally { engineDraft = null; invocation = null }
        if (pending.overflow) { error = "An action may contain at most 100 changes."; return false }
        if (!result) return false
        if (pending.actions.isEmpty()) return result
        val sent = if (pending.actions.size == 1) gameplay(pending.actions.single(), pending.requestId)
            else playBatch?.invoke(pending.actions.toList(), pending.requestId) == true
        if (!sent) error = "Action could not be queued. Reconnect and try again."
        return result && sent
    }

    private fun enqueue(action: PlayAction, invocationId: String?): Boolean {
        if (lastInvocation != invocationId) { lastInvocation = invocationId; error = null }
        refusal(action)?.let { if (it == CastController.BUSY) busyShake() else error = it; return false }
        return (engineDraft?.add(action) ?: gameplay(action, invocationId)).also { sent ->
            if (!sent) error = "Action could not be queued. Reconnect and try again."
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
        val own = (action as? PlayAction.Mtg)?.action.let { it is MtgAction.SetSkipWhenNoChoice || it is MtgAction.SetFullControl }
        if (own) return null
        if (play.flowOpen && !shownOnly(play, action)) return SharedFlowController.BUSY
        val step = action == PlayAction.NextStep || action == PlayAction.EndTurn || action is PlayAction.ChooseStep ||
            action is PlayAction.BeginTurn
        if (step && CombatStackRules.stack(board).cards.isNotEmpty()) return SharedFlowController.STACK_WAITING
        if (step) return SharedFlowController.STEPS_FORWARD
        // TRN-027: the viewer's own mulligans and library bottom during their opening, the host's setup before the game
        if (AutomatumDrags.openingAction(board, viewer, action)) return null
        // I4, I-D28, I-D52: only what the rules allow; nothing changes the game by hand. The server's own check
        // (pivot review #7), so the device never shows a change the server refuses
        if (engineRoute == 0 && !AutomatumOnly.allowed(board, viewer, action)) return when (action) {
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
    /** I-D49: the viewer's Keep / Bottom screen is open ([OpeningKeepHost]); personal UI. */
    var keeping by mutableStateOf(false); private set
    fun closeKeep() { keeping = false }

    /**
     * The viewer's opening with Automatum on (I-D49, TRN-062): "Mulligan" sends the London mulligan as one batch (one
     * Undo); "Keep" sets Ready at once when nothing is owed to the bottom, otherwise opens the Keep / Bottom screen.
     */
    override fun opening(intent: TableActionIntent): Boolean {
        val play = current()?.takeIf { enabled() && AutomatumDrags.opening(it.board, viewer) } ?: return false
        // I-D56 step C: the opening is the engine's setup (simultaneous mulligans, I-D54 §8)
        engine?.let { e ->
            return when (intent) {
                is TableActionIntent.OpeningMulligan -> { keeping = false; e.mulligan().also { if (!it) error = QuickActions.NO_MORE_MULLIGANS } }
                is TableActionIntent.Keep -> if (AutomatumOpening.owed(play.board, viewer) > 0) { error = null; keeping = true; true }
                    else e.keep(emptyList())
                else -> false
            }
        }
        return false
    }

    /** Keeps the viewer's hand with [bottom] put on the bottom of their library, then Ready (I-D49), as one batch. */
    fun confirmKeep(bottom: List<String>): Boolean {
        val play = current()?.takeIf { enabled() && AutomatumDrags.opening(it.board, viewer) } ?: return false
        val e = engine ?: return false
        return e.keep(bottom).also { if (it) keeping = false }
    }

    override fun automatumStep(intent: TableActionIntent): Boolean {
        val play = current() ?: return false
        if (cast.open) { busyShake(); return false }
        // I-D56 step C (TRN-057): the arrow passes only the viewer's own priority, or declares what the lanes hold; End
        // turn passes for them until the turn ends; the phase dialog is view-only
        engine?.let { e ->
            error = null
            return when (intent) {
                is TableActionIntent.Next -> when {
                    declares(play) -> flows.step(org.asyncttwiz.automatum.StepFlow.Until.Pass)
                    // Smoke 2026-11-04 (TRN-057, TRN-063): an arrow that says "End turn" ends the turn; a plain pass
                    // would stop at the viewer's own main phase on the way (STK-036)
                    arrowStep(play) == AutomatumStep.EndTurn -> e.endTurn()
                    else -> e.pass()
                }
                is TableActionIntent.End -> e.endTurn()
                else -> { error = SharedFlowController.STEPS_FORWARD; false }
            }
        }
        return false
    }

    /** Whether the viewer declares attackers or blockers on [play] now (the step arrow then declares what the lanes hold),
     * from the board's own flow. Problem report XGN86Q: no panel shows at a declaration, so the panel's flow ([flows]'
     * view) isn't there to ask. */
    private fun declares(play: TableBoard): Boolean =
        (flows.view?.takeIf { it.board === play.board } ?: AutomatumBoards.sharedFlow(play))?.declaring == viewer

    /** Whether [action] changes only what is shown, nothing Automatum reads (the values an automated batch's board
     * digest covers, SYS-057). An action the rules refuse anyway counts as shown-only: its own refusal is reported. */
    private fun shownOnly(play: TableBoard, action: PlayAction): Boolean {
        // The table's own changes are worked out on the board shown; only its combat lanes and the records other than
        // early answers are what Automatum reads of them (the board digest's, SYS-057)
        if (!EngineStaging.handles(play.board, action)) return false
        val after = try { EngineStaging.apply(play.board, play.definitions, viewer, action, DeviceRandom.uuid()) }
            catch (_: IllegalArgumentException) { return true }
        fun read(b: BoardState) = b.combat to b.engineRecords.filterNot { it.kind == EngineRecordKind.PreAnswer }
        return read(after) == read(play.board)
    }

    /** Convenience members are lowered against the current draft, then sent together. */
    fun submit(action: PlayAction, invocationId: String? = invocation): Boolean {
        val play = current()?.takeIf { enabled() } ?: return false
        // Onto the Stack always as an entry pointing at the card: a real card is never in the Stack (STK-001, STK-003).
        if (action is PlayAction.MoveCard && action.removeStackEntryId == null && play.board.containers.any {
                it.id == action.destination.containerId && it.zone == PlayZone.STACK }) {
            return enqueue(BasicTableController.projection(play, action) ?: return false,
                invocationId ?: DeviceRandom.uuid())
        }
        val group = invocationId ?: DeviceRandom.uuid()
        // A move is its own only member (r13: its defaults belong to the move itself)
        val actions = if (action is PlayAction.MoveCard) listOf<PlayAction>(
            action.copy(destination = ownerMoveDestination(play.board, action.cardId, action.destination, play.playId))) else listOf(action)
        if (!enqueue(actions.first(), group)) return false
        actions.drop(1).forEach { if (enabled()) enqueue(it, group) }
        // This is ordinary provisional disclosure, NOT an accepted result inferred from Boolean receipt.
        if (action is PlayAction.InspectFace) {
            val card = current()?.board?.cards?.singleOrNull { it.id == action.cardId && it.containerId == action.containerId }
            if (card != null) inspection = ManualInspection(play.gameId, play.playId, card.id,
                listOf(action.faceId), explicit = true, provisional = true)
        }
        return true
    }

    fun dismiss() { panel = null; player = null; capture = null; popupItem = null; stackTargeting = false; closeAbilities() }
    fun dismissTapUi() = dismiss()
    internal val hasBackOverlay get() = inspection != null || peek != null || capture != null || player != null ||
        panel != null || keeping
    internal fun dismissBackOverlay() {
        when {
            inspection != null -> closeInspection()
            peek != null -> peek(null)
            capture != null || player != null || panel != null -> dismiss()
            keeping -> closeKeep()
        }
    }
    fun closeInspection() { inspection = null }
    fun tableMenu() { dismiss(); panel = "table" }
    fun phasePicker() { dismiss(); panel = "phase" }
    fun stackContents() { dismiss(); panel = "manageStack" }
    /** The Stack target picker is open (an entry below the top is a legal target, I-D6; [StackTargetPicker]). */
    var stackTargeting by mutableStateOf(false); private set
    /** Back in the Stack target picker: targets are picked on the table again. */
    fun closeStackTargets() { stackTargeting = false }
    /** The Stack entries the open cast or choice may target now, top first; empty when none. */
    fun stackTargets(play: TableBoard): List<String> = if (cast.targeting) cast.stackTargets(play)
        else if (flows.targeting) flows.stackTargets(play) else emptyList()
    /** Picks the Stack entry [entryId] as a target in the Stack target picker, which closes once it is picked. */
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
        dismiss()
        layoutState.value = layoutState.value.copy(localPileId = container.id.takeUnless { toggle && it == layoutState.value.localPileId },
            localPileTargets = false)
    }
    /** A Graveyard/Exile pile tap opens that pile's menu (either player's pile), like the Library menu. */
    fun openPileMenu(id: String) {
        current()?.board?.containers?.singleOrNull { it.id == id && it.zone in PublicPiles.zones } ?: return
        dismiss()
        panel = "pile/$id"
    }
    fun closeLocalPile() { layoutState.value = layoutState.value.copy(localPileId = null, localPileTargets = false); dismiss() }
    /** The pile opened to pick targets from ([open]), closed again once the targets are chosen. */
    private var targetPile: String? = null
    internal fun targetingEnded() {
        stackTargeting = false
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
        // A combat copy whose card left play has nothing to offer (TBL-116)
        CombatStackRules.liveSource(play.board, scope.contextId) ?: return
        dismiss(); capture = bound
    }

    fun open(captured: TableIntent.Actions, item: TableItem) {
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
            // An entry below the top of the Stack is a legal target: the Stack target picker opens to pick it from
            if (play != null && item.zone == PlayZone.STACK && CombatStackRules.stack(play.board).id in cast.glow(play) + flows.glow(play)) {
                dismiss(); stackTargeting = true
                return
            }
            // UI review round 2 #4 (I-D46 b): a tap on anything that isn't a legal target shakes it
            val taken = if (cast.targeting) cast.tapTarget(item) else flows.tapTarget(item)
            if (taken == false) shake(item.id)
            return
        }
        val play = current() ?: return
        // TBL-122: a tap on anything closes open ability chips; on their own card again it does only that
        val chips = abilityCard
        closeAbilities()
        // ZON-079 (I-D50): a hand has no menu: a tap raises the card (or casts the raised one); a tap on anything else
        // lowers it
        if (item.zone == PlayZone.HAND && item.kind == TableItemKind.CARD) {
            dismiss()
            item.cardId?.let(hand::tap)
            return
        }
        hand.lower()
        // TRN-069: a tap on a dungeon inspects it (holding shows it large)
        if (item.kind == TableItemKind.DUNGEON) { item.cardId?.let(::inspect); return }
        // CMB-028, CMB-032, CMB-034: with Automatum on a tap in combat stages, blocks or selects
        if (combatTap(play, item)) { dismiss(); return }
        // TBL-122: one tap on a battlefield card activates its ability, or opens the chips of several; else its menu
        if (item.kind == TableItemKind.CARD && item.zone == PlayZone.BATTLEFIELD && item.viewport != "combat" &&
            !item.viewport.startsWith("cardPanel/") && !item.projection) {
            val card = item.cardId?.let { CombatStackRules.liveSource(play.board, it)?.id }
            if (card != null && card == chips) return
            if (card != null && tapPermanent(card)) return
        }
        // A card in a Graveyard or Exile panel: its engine menu (TBL-116)
        if (item.viewport.startsWith("cardPanel/")) {
            item.cardId?.let { openCard(TableScope(it, listOf(it))) }
            popupItem = item
            return
        }
        when {
            item.zone == PlayZone.STACK -> stackContents()
            captured.scope != null -> openCard(captured.scope, captured.sources)
            item.kind == TableItemKind.LIFE -> { dismiss(); player = requireNotNull(item.owner) }
            item.zone == PlayZone.LIBRARY -> if (zoneMenuEmpty(play, item.id)) { dismiss(); shake(item.id); return }
                else openLibrary(item.id)
            item.zone in PublicPiles.zones -> openPileMenu(item.id)
            else -> openPublicPile(item.id)
        }
        popupItem = item
    }

    /** I-D46: a Library whose top card Automatum offers nothing for has nothing in its menu: a tap shakes the pile
     * instead of opening it. */
    private fun zoneMenuEmpty(play: TableBoard, id: String): Boolean {
        val pile = play.board.containers.singleOrNull { it.id == id }
        if (pile == null || pile.temp) return true
        if (pile.zone != PlayZone.LIBRARY) return false
        val top = pile.cards.firstOrNull() ?: return true
        // Not worked out yet: the menu opens and shows the rows once they are
        val plays = cardPlays(play) ?: return false
        return plays.of(top).none { it.legal }
    }

    /** ZON-078: the Library menu: what Automatum offers for its top card. */
    fun openLibrary(id: String) {
        current()?.board?.containers?.singleOrNull { it.id == id && it.zone == PlayZone.LIBRARY } ?: return
        dismiss(); panel = "library/$id"
    }

    fun inspect(viewId: String) {
        val play = current() ?: return
        val card = CombatStackRules.inspection(play.board, viewId) ?: return
        val live = CombatStackRules.liveSource(play.board, viewId)
        val faces = ManualDisclosure.permittedFaces(play.board, play.definitions, card, viewer, inspecting = true)
        closeInspection()
        inspection = ManualInspection(play.gameId, play.playId, viewId, faces.map { it.faceId }, snapshot = if (live == null) card else null,
            stackEntryId = viewId.takeIf { it in CombatStackRules.stack(play.board).cards })
    }
    fun handle(intent: TableIntent): Boolean {
        // I-D46: with Automatum on, a gesture Automatum doesn't allow shakes the card, pile or life square it was made on
        // instead of saying why (Inspect says it); genuine failures (offline, still working out) keep their message
        val target = gestureTarget(intent)
        if (target == null) return handleIntent(intent)
        castGesture = target
        error = null
        val result = try { handleIntent(intent) } finally { if (!cast.open) castGesture = null }
        val message = error
        if (!result && message != null && refusal(message)) shake(target)
        return result
    }

    /** The item a refused gesture shakes (I-D46): a double-tapped or dragged card; null for anything else. */
    private fun gestureTarget(intent: TableIntent): String? = when (intent) {
        is TableIntent.Move -> intent.scope.contextId.takeIf { intent.handDoubleTap || intent.drag }
        is TableIntent.Rotate -> intent.scope.contextId
        else -> null
    }

    /** A hand card double-tapped or dragged onto the Battlefield or Stack with Automatum on (ZON-004, I-D36): cast or
     * played; one already known to have no cast or play Automatum allows now shakes at once (I-D46). */
    private fun castFromHand(cardId: String): Boolean {
        val play = current()?.takeIf { enabled() }
        // I-D50: the raised card goes the way its chips chose
        val (way, extras) = hand.castChoice(cardId)
        hand.lower()
        if (play != null && !cast.open && unplayable(play, cardId)) { shake(cardId); return false }
        return cast.start(cardId, way, extras)
    }

    /** The card a cast was begun on by a gesture, so its refusal worked out in the background shakes it too. */
    private var castGesture: String? = null

    /** Whether [message] says Automatum doesn't allow something (shaken, I-D46) rather than a failure to report. */
    private fun refusal(message: String): Boolean = "Reconnect" !in message && message != CombatController.WORKING_OUT &&
        message != CastController.FAILED && !message.startsWith(CastController.TAKE_OVER) && "at most 100" !in message

    /** The cast's own messages: one saying the card can't be cast or played now, worked out after a gesture, shakes the
     * card instead (I-D46). */
    private fun castMessage(message: String?) {
        val card = castGesture
        castGesture = null
        val unplayable = message != null && !message.startsWith("The table changed") &&
            (message.endsWith("can't be cast now.") || message.endsWith("can't be played now."))
        if (unplayable && card != null) shake(card) else error = message
    }

    private fun handleIntent(intent: TableIntent): Boolean {
        // ZON-004: a hand double-tap casts (or plays, a land) through the engine
        if (intent is TableIntent.Move && intent.handDoubleTap) return castFromHand(intent.scope.contextId)
        // I-D36, I-D52: a drag is the engine action it means, or refused
        if (intent is TableIntent.Move && intent.drag) return automatumDrag(intent)
        // TBL-043: a double-tap on a battlefield card does no more than its first tap (TBL-122); nothing is tapped by hand
        if (intent is TableIntent.Rotate) return secondTap(intent)
        if (intent is TableIntent.Batch) return batchResult(intent.invocationId) {
            intent.intents.all { handle(it) }
        }
        val multiple = intent is TableIntent.Move && intent.scope.cardIds.size > 1
        return if (multiple) batchResult((intent as TableIntent.Move).invocationId ?: DeviceRandom.uuid()) {
            handleOne(intent)
        } else handleOne(intent)
    }
    /**
     * TBL-043: the second tap of a double-tap on a battlefield card does nothing: its first tap already activated the
     * card's ability, opened its ability chips or its menu (TBL-122). Turning any other card is tapping by hand, refused.
     */
    private fun secondTap(intent: TableIntent.Rotate): Boolean {
        val play = current()
        val card = play?.let { CombatStackRules.liveSource(it.board, intent.scope.contextId) }
        if (card != null && play.board.containers.singleOrNull { it.id == card.containerId }?.zone == PlayZone.BATTLEFIELD) return true
        error = AutomatumDrags.MANUAL
        return false
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
                return castFromHand(intent.scope.contextId)
            }
            AutomatumDrags.Route.Place -> return engineRoute { handle(intent.copy(drag = false)) }
            AutomatumDrags.Route.Stay -> return false
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
            is TableIntent.Submit -> submit(intent.action, intent.invocationId ?: invocation)
            else -> basic.handle(intent)
        }
    }
    // ---- The combat area with Automatum on (I-D32)

    private fun creature(play: TableBoard, card: String): Boolean {
        val c = play.board.cards.firstOrNull { it.id == card } ?: return false
        if (c.isFaceDown) return true
        val face = play.definitions.firstOrNull { it.digest == c.definition }?.definition?.faces?.firstOrNull { it.faceId == c.faceId }
        return AutomatumBoards.cached(play, viewer)?.cards?.get(card)?.typeLine?.contains("Creature", ignoreCase = true)
            ?: face?.typeLine?.contains("Creature", ignoreCase = true) ?: false
    }

    private fun controllerOf(play: TableBoard, card: String): String? =
        play.board.containers.firstOrNull { it.id == play.board.cards.firstOrNull { c -> c.id == card }?.containerId }?.accountId

    /**
     * A tap on [item] with Automatum on while combat is being declared or shown (CMB-028, CMB-032, CMB-034): one of the
     * attacking player's creatures is staged or unstaged; one of the defending player's creatures is picked as the blocker
     * to place, and an attacker tapped then is blocked or unblocked by it; other combat copies are selected, the selected
     * one's menu opening on the next tap. False when the tap opens the card's menu as usual.
     */
    private fun combatTap(play: TableBoard, item: TableItem): Boolean {
        if (item.kind != TableItemKind.CARD) return false
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
                // CMB-036: picking what a staged attacker enlists, a creature that may be enlisted is enlisted (or no longer)
                (combat.picked as? CombatController.Picking.Enlist)?.let { picking ->
                    if (!attacking && card in choices.enlistable) {
                        combat.toggleEnlisted(play, picking.attacker, card, choices.enlist[picking.attacker] ?: 1)
                        return true
                    }
                }
                // CMB-038: forming a band, another staged attacker joins it or leaves it
                (combat.picked as? CombatController.Picking.Band)?.let { picking ->
                    if (attacking && card != picking.attacker) { combat.toggleBandMember(play, card); return true }
                }
                combat.stopPicking()
                if (attacking) return unstage(occurrence!!.id, card)
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
    internal fun stage(play: TableBoard, cards: List<String>, target: String?): Boolean {
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

    /** CMB-028: the staged attacker [card] leaves the staging, with its choices (CMB-036–CMB-038); one Undo step. */
    private fun unstage(occurrenceId: String, card: String): Boolean {
        val play = current() ?: return false
        val area = play.board.combat ?: return false
        error = null
        val choice = combat.choiceAction(play, card) { AttackChoice(card) }
        return engineRoute { batchResult {
            submit(PlayAction.Combat(CombatAction.Remove(area.id, occurrenceId))).also { if (it && choice != null) submit(choice) }
        } }
    }

    /** CMB-034: [blocker] blocks the attackers of [pairId] too, or no longer when it does; one that can block one attacker
     * moves to it. Deselected once it blocks as many attackers as it can. */
    internal fun block(play: TableBoard, blocker: String, pairId: String): Boolean {
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
    internal fun clearCombatDraft(play: TableBoard): Boolean {
        val area = play.board.combat ?: return false
        val attackers = combat.choices(play).also { if (combat.working(it)) { error = CombatController.WORKING_OUT; return false } }.choices.attackers
        val ids = area.pairs.flatMap { if (attackers) it.attackers else it.blockers }.map { it.id }
        if (ids.isEmpty()) return false
        combat.clearSelection()
        // CMB-036–CMB-038: the attacker's choices go with the staging
        val choices = if (attackers) PreAnswers.withdraw(play.board, PreAnswer.ATTACK_CHOICES) else null
        return engineRoute { batchResult {
            (ids.map { submit(PlayAction.Combat(CombatAction.Remove(area.id, it))) } + listOfNotNull(choices?.let(::submit))).any { it }
        } }
    }

    fun perform(intent: TableActionIntent): Boolean {
        // Automatum on (CMB-028, CMB-034): staging and clearing the declaration's draft
        if (intent is TableActionIntent.Combat && intent.tool in setOf(CombatTool.AUTOMATUM_STAGE, CombatTool.AUTOMATUM_CLEAR)) {
            if (!table.enabled(intent.scope)) return false
            if (cast.open) { busyShake(); return false }
            return automatumCombatTool(intent)
        }
        return table.perform(intent)
    }
    override fun ending(intent: TableActionIntent.Ending) { if (!ending.invoke(intent)) error = "Result action is unavailable." }
    override fun combat(intent: TableActionIntent.Combat) { automatumCombatTool(intent) }
    /** "Rest → <target>" and "Clear" with Automatum on (CMB-028, CMB-034). */
    private fun automatumCombatTool(intent: TableActionIntent.Combat): Boolean {
        val play = current() ?: return false
        return when (intent.tool) {
            CombatTool.AUTOMATUM_STAGE -> stage(play, intent.sourceIds, combat.activeTarget(play))
            else -> clearCombatDraft(play)
        }.also { if (it) combat.clearSelection() }
    }
    /** Combat copies leave the combat area: with Automatum on only what is still a draft of the declaration it awaits
     * (CMB-028): any copy while attackers are awaited, blockers while blockers are. */
    fun removeFromCombat(bound: ToolkitCapture): Boolean {
        val play = current()?.takeIf { enabled() } ?: return false
        return leaveDraft(play, bound)
    }
    private fun leaveDraft(play: TableBoard, bound: ToolkitCapture): Boolean {
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
        panel = "attackTarget"
    }
    fun setAttackTarget(bound: ToolkitCapture, cardId: String?): Boolean {
        val play = current()?.takeIf { enabled() } ?: return false
        // CMB-028: a drafted attacker attacks another target only as Automatum allows
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

    /**
     * Engine-responsiveness R4a: the whole-drop preflight of [place] on the board of a game on engine state, as
     * [CombatStackController.place] makes it on today's model: every card of [selected] placed in order at [destination]
     * on the board shown ([EngineStaging]), each member named as a batch's; throws with the reason when one is refused.
     */
    private fun engineProbe(initial: TableBoard, selected: List<String>, destination: CombatDestination) {
        val requestId = DeviceRandom.uuid()
        var board = initial.board
        var members = 0
        fun emit(action: PlayAction) {
            val next = EngineStaging.apply(board, initial.definitions, viewer, action, CommandIds.uuidV5(requestId, "member/$members"))
            if (next != board) members++
            board = next
        }
        val targets = CombatStackController.orderedTargets(board, selected)
        val sourceIds = CombatStackController.sources(targets)
        require(sourceIds.size == targets.size && sourceIds.isNotEmpty()) { "No live source" }
        var target = destination
        var prefix: String? = null
        var rightAnchor = destination.cardGap.beforeId
        for ((index, source) in sourceIds.withIndex()) {
            val area = requireNotNull(board.combat)
            val occurrence = CombatStackRules.occurrences(area).find { it.sourceId == source }
            val pending = sourceIds.drop(index + 1)
            target = if (prefix != null) target.copy(insertion = CombatInsertion.GAP, cardGap = IdentityGap(prefix, rightAnchor),
                pendingSourceIds = pending) else target.copy(pendingSourceIds = pending)
            emit(PlayAction.Combat(CombatAction.Place(source, target, occurrence?.let { CombatStackRules.location(area, it.id) })))
            val placedArea = board.combat!!
            val placed = CombatStackRules.occurrences(placedArea).single { it.sourceId == source }
            val location = CombatStackRules.location(placedArea, placed.id)!!
            if (index == 0) {
                val side = placedArea.pairs.single { it.id == location.pairId }.side(location.role)
                val right = side.dropWhile { it.id != placed.id }.drop(1).firstOrNull { it.sourceId !in pending }?.id
                rightAnchor = right
                target = target.copy(pairId = location.pairId, insertion = CombatInsertion.GAP, cardGap = IdentityGap(placed.id, right))
            }
            prefix = placed.id
        }
    }

    /** Whole-drop structural preflight uses the existing shared helper; only real-session callbacks send.
     * Request-derived occurrence IDs are read from that session's immediate prediction, never the probe. */
    fun place(bound: ToolkitCapture, destination: CombatDestination): Boolean = batchResult {
        val initial = current()?.takeIf { enabled() } ?: return@batchResult false
        if (CombatStackController.retargetDrop(initial.board, bound.viewIds, destination).isNotEmpty())
            return@batchResult setAttackTarget(bound,
                initial.board.combat?.pairs?.find { it.id == destination.pairId }?.attackTarget ?: destination.attackTarget)
        val probe = runCatching { engineProbe(initial, bound.viewIds, destination) }
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

