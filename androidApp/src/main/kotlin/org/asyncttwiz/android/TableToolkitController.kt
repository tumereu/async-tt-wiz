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
        automatumWork, tokenArt = tokenArt, shake = { shake(it) }, engineSend = engine?.let { e -> e::send },
        revealing = { revealing() }, takeBacks = { engine?.takeBacks() ?: 0L }, lastTakeBack = { engine?.lastTakeBack().orEmpty() })
    /** Priority, responding, resolution and triggers through Automatum (I2b): shared flows; while one waits for an
     * answer nothing else changes (D32). */
    internal val flows = SharedFlowController(viewer, { current()?.takeIf { enabled() } },
        { if (it == SharedFlowController.BUSY || it == REVEAL_BUSY) busyShake() else error = it },
        automatumWork, tokenArt = tokenArt, autotap = { cast.autotap }, engineSend = engine?.let { e -> e::send },
        takeBacks = { engine?.takeBacks() ?: 0L }, lastTakeBack = { engine?.lastTakeBack().orEmpty() })
    private val automatumWork = automatumWork

    /**
     * Runs [work] for the table's shown board as [automatumWork] says: off the main thread on [dispatcher] in the app
     * (SYS-025), at once with [AutomatumWork.Inline] (tests: what the board shows is worked out before the screen is idle).
     */
    internal suspend fun <T> boardWork(dispatcher: kotlinx.coroutines.CoroutineDispatcher, work: () -> T): T =
        if (automatumWork === AutomatumWork.Inline) work() else kotlinx.coroutines.withContext(dispatcher) { work() }

    init {
        // STK-025, HIS-084: Undo with a cast or activation open drops it (the interaction's undo point is its start)
        (engine as? EngineTableRoutes)?.dropInteraction = { cast.open.also { open -> if (open) cast.cancel("undone") } }
    }

    /** The words the step arrow shows on [play] ([AutomatumStepArrow]): the board's own, with the viewer's response mode. */
    private fun arrowStep(play: TableBoard): AutomatumStep = AutomatumStep.of(play, engine?.control() == true)
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
        if (!enabled() || cast.open || play.flowOpen || revealing()) return emptySet()
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

    /** Whether [cardId], a card in the viewer's hand, is known to have no cast, play, special action or ability from the
     * hand (foretell, plot, cycling, channel: a way chip, ZON-080) Automatum allows now (I-D46). */
    private fun unplayable(play: TableBoard, cardId: String): Boolean {
        val plays = AutomatumBoards.cachedCardPlays(play, viewer) ?: return false
        if (!CastController.ownHand(play, cardId, viewer)) return false
        return plays.of(cardId).none { it.legal && (it.kind == org.asyncttwiz.automatum.CardPlay.Kind.Cast ||
            it.kind == org.asyncttwiz.automatum.CardPlay.Kind.Play || it.kind == org.asyncttwiz.automatum.CardPlay.Kind.Special ||
            it.kind == org.asyncttwiz.automatum.CardPlay.Kind.Ability || it.kind == org.asyncttwiz.automatum.CardPlay.Kind.Mana) }
    }

    /** Begins what a card menu offers through Automatum ([CastController.begin]): the cast panel's flow. */
    internal fun play(cardId: String, play: org.asyncttwiz.automatum.CardPlay): Boolean {
        val input = play.input ?: return false
        error = null
        return cast.begin(cardId, input, play.text)
    }

    /** TBL-122 (async play §29.5): the battlefield card whose ability chips are open (personal UI), null when none. */
    var abilityCard by mutableStateOf<String?>(null); private set
    /** The ability chip held for its details (an index of [abilities]), null when none. */
    var heldAbility by mutableStateOf<Int?>(null)

    /** TBL-122: the abilities of the battlefield card [cardId] the viewer can activate on [play] now (mana abilities and
     * special actions included), in printed order; empty while a cast or an Automatum choice is open, null while
     * Automatum still works the board out. */
    internal fun activatable(play: TableBoard, cardId: String): List<org.asyncttwiz.automatum.CardPlay>? {
        if (!enabled() || cast.open || play.flowOpen || revealing()) return emptyList()
        val plays = cardPlays(play) ?: return null
        return plays.of(cardId).filter { it.legal && it.kind != org.asyncttwiz.automatum.CardPlay.Kind.Cast &&
            it.kind != org.asyncttwiz.automatum.CardPlay.Kind.Play }
    }

    /** TBL-124 (owner, 2026-11-09): every activated ability of the battlefield card [cardId] (mana abilities too, and its
     * special actions), usable now or not, in printed order: what its chips show. Empty and null as [activatable]. */
    internal fun abilities(play: TableBoard, cardId: String): List<org.asyncttwiz.automatum.CardPlay>? {
        if (!enabled() || cast.open || play.flowOpen || revealing()) return emptyList()
        val plays = cardPlays(play) ?: return null
        return plays.of(cardId).filter { it.kind != org.asyncttwiz.automatum.CardPlay.Kind.Cast && it.kind != org.asyncttwiz.automatum.CardPlay.Kind.Play }
    }

    /**
     * TBL-122, TBL-124 (async play §29.5, MTG Arena; owner, 2026-11-09): a tap on the viewer's battlefield card [cardId]
     * activates the ability they can activate now when it is the card's only one (as from its menu, TBL-115: a mana ability
     * adds its mana to the pool at once; costs, targets and Autotap go through the cast flow), or opens the ability chips
     * above it when it has two or more, even with one usable now: the unusable ones greyed with why, so the tap never picks
     * one for them. False when none is usable (or Automatum still works the board out): the tap opens the card's menu as before.
     */
    internal fun tapPermanent(cardId: String): Boolean {
        val play = current() ?: return false
        val offered = activatable(play, cardId)
        if (offered.isNullOrEmpty()) return false
        dismiss()
        val all = abilities(play, cardId).orEmpty()
        AppLog.info("table", "Tap on $cardId: ${all.size} abilities, ${offered.size} usable: ${if (all.size < 2) "activated" else "chips"}")
        if (all.size < 2) play(cardId, offered.single()) else abilityCard = cardId
        return true
    }

    /** TBL-122: a tap on the ability chip [index] of the open chips activates that ability; greyed, it does nothing. */
    internal fun activateChip(index: Int): Boolean {
        val card = abilityCard ?: return false
        val play = current() ?: return false
        val chosen = abilities(play, card)?.getOrNull(index)?.takeIf { it.legal } ?: run {
            AppLog.info("table", "Chip $index of $card greyed or gone: nothing activated")
            return false
        }
        closeAbilities()
        collapse()
        return play(card, chosen)
    }

    /**
     * TBL-122 (Arena style): whether a tap on the battlefield card [cardId] could do something on [play] now, as [open]
     * would: be picked while a cast or choice picks targets, be staged as an attacker or picked as a blocker while those are
     * declared, else activate an ability (a mana ability or special action too).
     */
    internal fun tapUsable(play: TableBoard, cardId: String): Boolean {
        if (cast.targeting || flows.targeting) return cardId in cast.glow(play) + flows.glow(play)
        // STK-024 (owner 2026-11-07): while the cast panel is open a tap adds a mana source to its payment
        if (cast.paying) return cardId in cast.payableSources()
        val shown = combat.choices(play)
        if (!combat.working(shown)) {
            val choices = shown.choices
            val declaring = choices.declaring
            if (declaring != null && enabled() && controllerOf(play, cardId) == declaring) {
                // CMB-047 (owner playtest 2026-11-07): a staged attacker or placed blocker isn't taken again, so a tap on
                // its stack takes the next one
                if (cardId in BattlefieldLayout.staged(play.board)) return false
                return if (choices.attackers) cardId in choices.attacks else cardId in choices.blocks
            }
        }
        return !activatable(play, cardId).isNullOrEmpty()
    }

    /** TBL-122: what a tap on the collapsed battlefield [group] does on [play] now ([GroupTap.of]); TBL-130 (owner
     * 2026-11-07): while targets are picked a group of several with a legal target in it always opens. */
    internal fun groupTap(play: TableBoard, group: BattlefieldGroup): GroupTap =
        GroupTap.of(group, { tapUsable(play, it) }, { id -> play.board.cards.firstOrNull { it.id == id }?.tapped == true },
            alwaysOpen = cast.targeting || flows.targeting)

    /** TBL-122: the battlefield group the viewer opened (personal UI), null when none. */
    val expandedGroup: String? get() = layoutState.value.expandedGroup
    /** TBL-122: closes the opened battlefield group (a tap elsewhere, or acting). */
    fun collapse() { if (layoutState.value.expandedGroup != null) layoutState.value = layoutState.value.copy(expandedGroup = null) }

    /**
     * TBL-122 (Arena style): a tap on [item] of [scene]. A battlefield card in a collapsed group of several is the group's
     * tap: it acts at once on the one member it can mean ([GroupTap.Act]), opens the group with its members side by side
     * when it could mean several ([GroupTap.Expand]), else is a tap on the group's top card. In an opened group the tap is
     * the member's own, and acting closes the group; a tap anywhere else closes it too. TBL-130 (owner 2026-11-07): while
     * targets are picked a group opens on any tap and stays open while targets are picked from it, one tap per member.
     */
    fun tap(captured: TableIntent.Actions, item: TableItem, scene: TableScene) {
        val group = if (item.battlefieldCard) scene.groupOf(item.cardId) else null
        val opened = scene.expandedGroup
        if (opened != null && group?.key != opened) collapse()
        val play = current()
        if (play == null || group == null || group.members.size < 2 || group.key == opened) {
            val picking = cast.targeting || flows.targeting
            open(captured, item)
            // Acting closes the group; its member's chips or menu keep it open, and so does picking targets from it (it
            // closes once they are chosen, [targetingEnded])
            if (opened != null && group?.key == opened && abilityCard == null && capture == null && !picking) collapse()
            return
        }
        fun member(id: String) {
            val shown = scene.items.firstOrNull { it.cardId == id && it.battlefieldCard } ?: return open(captured, item)
            open(TableIntent.Actions(shown.id, TableScope(id, listOf(id)), scene.sources.filterKeys { it == id }), shown)
        }
        when (val tap = groupTap(play, group)) {
            GroupTap.Expand -> {
                // The first tap of a group that could mean several things only opens it
                dismiss(); hand.lower()
                if (!cast.targeting && !flows.targeting) combat.clearSelection() else targetGroup = group.key
                layoutState.value = layoutState.value.copy(expandedGroup = group.key)
            }
            // STK-024: in a stack of identical sources a tap adds the next one to the payment, else takes the last out
            is GroupTap.Act -> member(if (cast.paying) payMember(group, tap.cardId) else tap.cardId)
            GroupTap.Top -> member(group.top)
        }
    }

    /** STK-024, TBL-130 (owner playtest 2026-11-07): which card of [card]'s stack in [group] a tap on it adds to the open
     * payment: the topmost one not paying yet, else (all paying) the lowest one, the one added last (taken out). */
    private fun payMember(group: BattlefieldGroup, card: String): String {
        val unit = group.units.firstOrNull { card in it } ?: return card
        val payable = cast.payableSources()
        val paying = cast.paySources()
        return unit.lastOrNull { it in payable && it !in paying } ?: unit.firstOrNull { it in paying } ?: card
    }

    /** Closes the ability chips (a tap elsewhere, an activation). */
    fun closeAbilities() { abilityCard = null; heldAbility = null }
    val layoutState = mutableStateOf(TableLayoutState())
    /** I-D50 (ZON-079, ZON-080): the viewer's hand with Automatum on: a tap raises a card with its cost chips. */
    internal val hand = HandController(viewer, { current()?.takeIf { enabled() } }, layoutState, automatumWork,
        cast = { card, chip -> error = null; castGesture = card; cast.start(card, chip.input, chip.times) },
        panel = { card, known -> error = null; castGesture = card; cast.startFromHand(card, known) },
        shake = { shake(it) }, unplayable = { play, card -> AutomatumBoards.cachedCardPlays(play, viewer)?.let { unplayable(play, card) } })
    val table = TableActionController(::current, ::enabled, this)
    private val basic = BasicTableController(::current, ::enabled, ::enqueue)
    private var invocation: String? = null
    private var lastInvocation: String? = null
    var panel by mutableStateOf<String?>(null); private set
    /** LOB-135: changes the game's assume reveal settings (the host's; Table actions → Game settings); null: not offered. */
    var assumeReveals: ((org.asyncttwiz.protocol.AssumeReveals) -> Unit)? = null
    var popupItem by mutableStateOf<TableItem?>(null); private set
    /** The player whose Life panel is open (TRN-030), tapped on their life square; personal UI. */
    var player by mutableStateOf<String?>(null); private set
    var capture by mutableStateOf<ToolkitCapture?>(null); private set
    /** The card held to inspect it (TBL-104), shown while the finger stays; the only way to inspect a table card. */
    var peek by mutableStateOf<ManualInspection?>(null); private set
    /** Holds [id] to inspect it; [group]: a battlefield group's members held together (TBL-111), the first shown first. */
    fun peek(id: String?, group: List<String>? = null) {
        if (id == null) { peek = null; return }
        val play = current() ?: return
        val first = group?.firstOrNull() ?: id
        val card = CombatStackRules.inspection(play.board, first) ?: return
        peek = ManualInspection(play.gameId, play.playId, first,
            ManualDisclosure.permittedFaces(play.board, play.definitions, card, viewer, inspecting = true).map { it.faceId },
            stackEntryId = first.takeIf { it in CombatStackRules.stack(play.board).cards }, group = group.orEmpty().drop(1))
    }
    var error by mutableStateOf<String?>(null); private set
    fun dismissError() { error = null }

    /** I-D46: what Automatum doesn't allow, tried on a card or pile by a gesture, shakes it instead of a message;
     * [serial] tells one shake from the next of the same item. */
    data class Shake(val id: String, val serial: Int)
    var shaking by mutableStateOf<Shake?>(null); private set
    private var shakes = 0
    fun shake(id: String) { error = null; shaking = Shake(id, ++shakes); refusedTap() }
    /** SYS-012, SYS-053: bumped to shake the main button, for a refusal made on it or on nothing the table shows. */
    var mainShakes by mutableIntStateOf(0); private set
    /** SYS-012, SYS-069 (owner, 2026-11-08): a refusal of the player's own action shakes the card [card] it was made on
     * when the table shows it, else the main button; the board shows where the game is. */
    fun refusedShake(card: String?) {
        if (card != null && current()?.board?.cards?.any { it.id == card } == true) shake(card) else { error = null; mainShakes++ }
    }

    /** TBL-118, HIS-075: bumped by every refusal (a shake, a greyed control's tap) while the game waits for the other
     * player's choice ([EngineRoutes.othersChoice]): the event text saying so flashes once in the warning colour. */
    var choiceFlashes by mutableIntStateOf(0); private set
    /** A refused tap or gesture: the event text flashes while the other player's choice is open. */
    fun refusedTap() { if (othersChoice()) choiceFlashes++ }
    private fun othersChoice(): Boolean = engine?.othersChoice() == true
    /** HIS-110 (owner, 2026-11-07): the other player's reveal panel is open: game actions are refused as while a choice
     * is open (STK-032) until its OK. */
    internal fun revealing(): Boolean = engine?.revealing() == true
    /** UI review round 2 #4 (I-D46 b): an action refused because the viewer's own cast is open ("Finish or cancel the cast
     * first.") or Automatum's choice is open (STK-032) shakes what it was made on (the card or pile whose menu it came
     * from), else the card being cast, instead of a message. */
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
        if (reviewContext != null && reviewContext != context) { peek(null); dismiss() }
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
        refusal(action)?.let {
            when (it) {
                CastController.BUSY, SharedFlowController.BUSY, REVEAL_BUSY -> busyShake()
                // SYS-069: a change made by hand shakes what it was made on (or the main button), without a message
                AutomatumDrags.MANUAL -> refusedShake(castGesture ?: capture?.contextViewId ?: popupItem?.id)
                else -> error = it
            }
            return false
        }
        return (engineDraft?.add(action) ?: gameplay(action, invocationId)).also { sent ->
            if (!sent) error = "Action could not be queued. Reconnect and try again."
        }
    }

    /**
     * Why [action] can't be made now with Automatum (D32): a cast is open; a shared flow waits for an answer and the
     * action changes what Automatum reads (only Automatum's own controls and what is only shown, such as notes,
     * statuses, marks, positions and whose move it is, go on); a change made by hand ([AutomatumDrags.allowed], I-D52).
     * Null when it can.
     */
    private fun refusal(action: PlayAction): String? {
        if (cast.open) return CastController.BUSY
        val play = current() ?: return null
        val board = play.board
        if (revealing() && !shownOnly(play, action)) return REVEAL_BUSY
        if (play.flowOpen && !shownOnly(play, action)) return SharedFlowController.BUSY
        // TRN-027: the viewer's own mulligans and library bottom during their opening, the host's setup before the game
        if (AutomatumDrags.openingAction(board, viewer, action)) return null
        // I4, I-D28, I-D52: only what the rules allow; nothing changes the game by hand. The server's own check
        // (pivot review #7), so the device never shows a change the server refuses
        if (engineRoute == 0 && !AutomatumDrags.allowed(board, viewer, action)) return when (action) {
            is PlayAction.Stack -> AutomatumDrags.STACK
            else -> AutomatumDrags.MANUAL
        }
        return null
    }

    /**
     * The step button, the step arrow and the phase dialog with Automatum on (TRN-057): Next step, End turn and a later
     * step go through Automatum ([SharedFlowController.step]); an earlier step, the step it is in, and a step passed into
     * for a quick action's own work are refused. Null with Automatum off (the manual step change goes on).
     */
    /**
     * The viewer's opening with Automatum on (I-D49, TRN-062; owner problem report 5DDSKK): "Mulligan" sends the London
     * mulligan (one Undo); "Keep" only keeps. Right after a mulligan that puts cards on the bottom, the Keep / Bottom screen
     * is open ([OpeningKeepHost]) and both are refused until its OK.
     */
    override fun opening(intent: TableActionIntent): Boolean {
        val play = current()?.takeIf { enabled() && AutomatumDrags.opening(it.board, viewer) } ?: return false
        val owed = AutomatumOpening.bottomOwed(play)
        if (owed > 0) {
            AppLog.info("opening", "${intent::class.simpleName} refused: $owed still to put on the bottom")
            error = bottomFirst(owed)
            return false
        }
        // I-D56 step C: the opening is the engine's setup (simultaneous mulligans, I-D54 §8)
        engine?.let { e ->
            return when (intent) {
                is TableActionIntent.OpeningMulligan -> e.mulligan().also { if (!it) error = QuickActions.NO_MORE_MULLIGANS }
                is TableActionIntent.Keep -> e.keep()
                else -> false
            }
        }
        return false
    }

    /** TRN-062: puts [bottom] on the bottom of the viewer's library, right after a mulligan (the Keep / Bottom screen). */
    fun confirmBottom(bottom: List<String>): Boolean {
        current()?.takeIf { enabled() && AutomatumDrags.opening(it.board, viewer) } ?: return false
        val e = engine ?: return false
        return e.bottom(bottom)
    }

    override fun automatumStep(intent: TableActionIntent): Boolean {
        val play = current() ?: return false
        if (cast.open || revealing()) { busyShake(); return false }
        // I-D56 step C (TRN-057): the arrow passes only the viewer's own priority, or declares what the lanes hold; End
        // turn passes for them until the turn ends; the phase dialog is view-only
        engine?.let { e ->
            error = null
            return when (intent) {
                is TableActionIntent.Next -> when {
                    declares(play) -> flows.step()
                    // Smoke 2026-11-04 (TRN-057, TRN-063): an arrow that says "End turn" ends the turn; a plain pass
                    // would stop at the viewer's own main phase on the way (STK-036). In Control the second main phase's
                    // arrow says "End step" (owner bug 2026-11-07): a plain pass, stopping at the end step
                    arrowStep(play) == AutomatumStep.EndTurn -> e.endTurn()
                    else -> e.pass()
                }
                is TableActionIntent.End -> e.endTurn()
                else -> false
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
        return true
    }

    fun dismiss() { panel = null; player = null; capture = null; popupItem = null; stackTargeting = false; closeAbilities() }
    fun dismissTapUi() = dismiss()
    internal val hasBackOverlay get() = peek != null || capture != null || player != null ||
        panel != null
    internal fun dismissBackOverlay() {
        when {
            peek != null -> peek(null)
            capture != null || player != null || panel != null -> dismiss()
        }
    }
    fun tableMenu() { dismiss(); panel = "table" }
    fun phasePicker() { dismiss(); panel = "phase" }
    fun stackContents() { dismiss(); panel = "manageStack" }
    /** The Stack opened in place as its list to pick a target from (an entry below the top is a legal target, STK-048;
     * [ManageStackDialog] with the legal entries glowing). */
    var stackTargeting by mutableStateOf(false); private set
    /** The Stack's list closed without a pick: targets are picked on the table again. */
    fun closeStackTargets() { stackTargeting = false; popupItem = null }
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
     * [toggle] closes the pile's panel when it is already open; the pile double-tap always keeps it. */
    fun openPublicPile(id: String, toggle: Boolean = true) {
        val container = current()?.board?.containers?.singleOrNull { it.id == id &&
            it.zone in setOf(PlayZone.GRAVEYARD, PlayZone.EXILE, PlayZone.STACK) } ?: return
        if (container.zone == PlayZone.STACK) { stackContents(); return }
        dismiss()
        layoutState.value = layoutState.value.copy(localPileId = container.id.takeUnless { toggle && it == layoutState.value.localPileId },
            localPileTargets = false)
    }
    fun closeLocalPile() { layoutState.value = layoutState.value.copy(localPileId = null, localPileTargets = false); dismiss() }
    /** The pile opened to pick targets from ([open]), closed again once the targets are chosen. */
    private var targetPile: String? = null
    /** TBL-130: the battlefield group opened to pick targets from ([tap]), closed again once the targets are chosen. */
    private var targetGroup: String? = null
    internal fun targetingEnded() {
        stackTargeting = false
        targetGroup?.let { key -> targetGroup = null; if (expandedGroup == key) collapse() }
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
        // CMB-003, TBL-116 (owner 2026-11-06): a combat copy opens no menu
        if (CardRingPages.combatCopy(play.board, scope.contextId)) { dismiss(); return }
        // A combat copy whose card left play has nothing to offer (TBL-116)
        val card = CombatStackRules.liveSource(play.board, scope.contextId) ?: return
        // TBL-116: a menu with nothing in it (no rows, no cells) doesn't open
        val layout = CardRingPages.context(play.board, scope.contextId) ?: CardMenuLayout.CARD
        if (CardRingPages.page(layout).actions.isEmpty() && cardPlays(play)?.of(card.id)?.isEmpty() == true) { dismiss(); return }
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
            // STK-048: an entry below the top of the Stack is a legal target: the Stack opens in place as its list, the legal
            // entries glowing, to pick it from
            if (play != null && item.zone == PlayZone.STACK && CombatStackRules.stack(play.board).id in cast.glow(play) + flows.glow(play)) {
                dismiss(); stackTargeting = true; popupItem = item
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
            // TBL-118: while the other player's choice is open the viewer's own hand card only shakes
            val card = item.cardId
            if (card != null && othersChoice() && CastController.ownHand(play, card, viewer)) { hand.lower(); shake(card); return }
            card?.let(hand::tap)
            return
        }
        hand.lower()
        // TRN-069: a tap on a dungeon does nothing (holding inspects it)
        if (item.kind == TableItemKind.DUNGEON) return
        // STK-024 (owner 2026-11-07): while the cast panel is open a tap on a mana source adds it to the payment or takes it
        // out
        if (cast.paying && item.kind == TableItemKind.CARD && item.zone == PlayZone.BATTLEFIELD && item.viewport != "combat" && !item.projection) {
            val card = item.cardId?.let { CombatStackRules.liveSource(play.board, it)?.id }
            if (card != null && cast.tapSource(card)) return
        }
        // CMB-040 (owner, 2026-11-05): while waiting for the defender's blocks, taps place blocks to assume as they would
        engine?.assuming()?.let { placed -> if (assumeTap(placed, play, item)) { dismiss(); return } }
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
            // ZON-033: a Graveyard or Exile tap (either player's) opens its search view at once; an empty one has nothing
            // to show and shakes
            item.zone in PublicPiles.zones -> {
                if (play.board.containers.singleOrNull { it.id == item.id }?.cards.isNullOrEmpty()) { dismiss(); shake(item.id) }
                else openPublicPile(item.id)
                return
            }
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
        // ZON-093: your own Library's menu always has View deck
        if (pile.accountId == viewer) return false
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

    /** ZON-093: the View deck panel, from your own Library's menu (looking only: nothing in the game changes). */
    fun openDeckView() {
        val item = popupItem
        dismiss(); panel = "deck"; popupItem = item
    }

    fun handle(intent: TableIntent): Boolean {
        // I-D46: with Automatum on, a gesture Automatum doesn't allow shakes the card, pile or life square it was made on
        // instead of saying why; genuine failures (offline, still working out) keep their message
        val target = gestureTarget(intent)
        // CMB-040: one of the defender's creatures dragged onto an attacker's pair while waiting for their blocks
        val placed = engine?.assuming()
        if (placed != null && intent is TableIntent.Move && intent.drag && intent.destination is TableDestination.Combat)
            return assumeDrag(placed, intent).also { if (!it && target != null) shake(target) }
        if (target == null) return handleIntent(intent)
        // TBL-118, HIS-075: while the other player's choice is open a double-tap or drag of a card is refused: it shakes
        // and the event text flashes (a card dropped back where it was only goes back)
        if (othersChoice() && intent is TableIntent.Move && !stays(intent)) { shake(target); return false }
        castGesture = target
        error = null
        val result = try { handleIntent(intent) } finally { if (!cast.open) castGesture = null }
        val message = error
        if (!result && message != null && refusal(message)) shake(target)
        return result
    }

    /** Whether [intent] is a drag that leaves the card where it was ([AutomatumDrags.Route.Stay]). */
    private fun stays(intent: TableIntent.Move): Boolean =
        intent.drag && current()?.let { AutomatumDrags.route(it, intent) } == AutomatumDrags.Route.Stay

    /** The item a refused gesture shakes (I-D46): a double-tapped or dragged card; null for anything else. */
    private fun gestureTarget(intent: TableIntent): String? = when (intent) {
        is TableIntent.Move -> intent.scope.contextId.takeIf { intent.handDoubleTap || intent.drag }
        else -> null
    }

    /** A hand card double-tapped or dragged onto the Battlefield or Stack with Automatum on (ZON-004, I-D36): cast or
     * played; one already known to have no cast or play Automatum allows now shakes at once (I-D46). */
    private fun castFromHand(cardId: String): Boolean {
        val play = current()?.takeIf { enabled() }
        // ZON-004, ZON-077 (owner 2026-11-07): as a tap on the raised card: cast at once without any choice, else the
        // cast panel
        val known = hand.known(cardId)
        hand.lower()
        if (play != null && !cast.open && unplayable(play, cardId)) { shake(cardId); return false }
        return cast.startFromHand(cardId, known)
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
        // STK-032 (I-D46 b): a cast refused while Automatum's choice is open says nothing: its card shakes
        if (message == SharedFlowController.BUSY || message == REVEAL_BUSY) { if (card != null) shake(card) else busyShake(); return }
        val unplayable = message != null && !message.startsWith("The table changed") &&
            (message.endsWith("can't be cast now.") || message.endsWith("can't be played now."))
        if (unplayable && card != null) shake(card) else error = message
    }

    private fun handleIntent(intent: TableIntent): Boolean {
        // ZON-004: a hand double-tap casts (or plays, a land) through the engine
        if (intent is TableIntent.Move && intent.handDoubleTap) return castFromHand(intent.scope.contextId)
        // I-D36, I-D52: a drag is the engine action it means, or refused
        if (intent is TableIntent.Move && intent.drag) return automatumDrag(intent)
        if (intent is TableIntent.Batch) return batchResult(intent.invocationId) {
            intent.intents.all { handle(it) }
        }
        val multiple = intent is TableIntent.Move && intent.scope.cardIds.size > 1
        return if (multiple) batchResult((intent as TableIntent.Move).invocationId ?: DeviceRandom.uuid()) {
            handleOne(intent)
        } else handleOne(intent)
    }
    /**
     * A drag with Automatum on (I-D36): a hand card dropped on the Battlefield or the Stack is played or cast (the cast
     * panel, as a double-tap, ZON-004); a creature dropped into the combat area joins the draft of the declaration the
     * engine awaits ([CombatController.draftRefusal]); a card dropped within its own Battlefield or hand goes back
     * (ZON-077). Everything else would change the game by hand and is refused with the reason (I-D28).
     */
    private fun automatumDrag(intent: TableIntent.Move): Boolean {
        val play = current()?.takeIf { enabled() } ?: return false
        when (val route = AutomatumDrags.route(play, intent)) {
            AutomatumDrags.Route.Cast -> {
                if (intent.scope.cardIds.distinct().size > 1) { error = AutomatumDrags.ONE_AT_A_TIME; return false }
                return castFromHand(intent.scope.contextId)
            }
            AutomatumDrags.Route.Stay -> return false
            AutomatumDrags.Route.Combat -> {
                val destination = (intent.destination as TableDestination.Combat).destination
                val bound = bind(intent.scope, intent.sources) ?: return false
                val choices = combat.choices(play).also { if (combat.working(it)) { error = CombatController.WORKING_OUT; return false } }.choices
                val pair = destination.pairId?.let { id -> play.board.combat?.pairs?.singleOrNull { it.id == id } }
                val target = pair?.attackTarget ?: destination.attackTarget
                if (choices.declaring != null && choices.attackers) {
                    // CMB-015: staged attackers dropped into another lane attack its target; in their own lane they stay
                    val staged = CombatStackController.attackerPairs(play.board, bound.viewIds)
                    if (staged.isNotEmpty() && bound.viewIds.all { id -> staged.any { p -> p.attackers.any { it.id == id } } })
                        return staged.any { it.attackTarget != target } && setAttackTarget(bound, target)
                    // CMB-028: a creature dropped into a lane (the collapsed band's chips too) is staged attacking its target
                    return stage(play, bound.sourceIds, target)
                }
                // CMB-034: one of the defender's creatures dropped on an attacker's pair blocks it, as tapping does (dropped
                // back on the pair it blocks, it stays)
                val card = bound.sourceIds.singleOrNull()
                if (choices.declaring != null && card != null && pair != null && controllerOf(play, card) == choices.declaring) {
                    val blocks = play.board.combat?.let { area -> CombatStackRules.occurrences(area).firstOrNull { it.sourceId == card }
                        ?.let { CombatStackRules.blockedPairs(area, it.id) } }.orEmpty()
                    return pair.id !in blocks && block(play, card, pair.id)
                }
                combat.draftRefusal(play, bound.sourceIds, destination.role, destination.pairId, destination.attackTarget)
                    ?.let { error = it }
                return false
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
                // CMB-028, CMB-034: combat drops are Automatum's staging ([automatumDrag]), never placed by hand
                if (intent.destination is TableDestination.Combat) false
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
     * to place, and an attacker tapped then is blocked or unblocked by it. A combat copy's first tap only selects it; a
     * tap on the selected copy unstages its attacker or removes its blocker while they are declared, else deselects it
     * (owner, 2026-11-06: no Combat menu). False when the tap opens the card's menu as usual.
     */
    private fun combatTap(play: TableBoard, item: TableItem): Boolean {
        if (item.kind != TableItemKind.CARD) return false
        val card = item.sourceId ?: return false
        val copy = item.viewport == "combat"
        if (!copy && (item.zone != PlayZone.BATTLEFIELD || item.projection)) return false
        if (copy && play.board.combat == null) return false
        // Finalreview-combat #5: not worked out yet (the other player's event just arrived): said, never worked out here
        val choices = combat.choices(play).also { if (combat.working(it)) { error = CombatController.WORKING_OUT; return true } }.choices
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
                // CMB-003, CMB-032 (owner, 2026-11-06): a staged attacker's copy is selected first, unstaged by a tap on it
                // selected; its real card's tap unstages it at once (CMB-028)
                if (attacking && copy && combat.selected != card) { combat.select(card); return true }
                if (attacking) { combat.clearSelection(); return unstage(occurrence!!.id, card) }
                if (card !in choices.attacks && !creature(play, card)) return false
                return stage(play, listOf(card), combat.activeTarget(play))
            }
            if (!attacking) {
                if (card !in choices.blocks && !creature(play, card)) return false
                if (card !in choices.blocks) { error = "${combatName(play, card)} can't block now."; return true }
                // CMB-034 (owner, 2026-11-06): a placed blocker's copy tapped while selected leaves combat
                val placed = CombatStackRules.occurrences(area).filter { it.sourceId == card }
                if (copy && placed.isNotEmpty() && combat.selected == card) { combat.clearSelection(); return removeBlocker(placed.map { it.id }) }
                combat.select(if (combat.selected == card && !copy) null else card)
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
        // CMB-032: a tap selects a combatant, a tap on the selected one deselects it (no Combat menu, owner 2026-11-06)
        combat.select(if (combat.selected == card) null else card)
        return true
    }

    /**
     * CMB-034, CMB-040 (owner, 2026-11-08): a tap at [point] on the slot of an attacker with a creature selected as the blocker
     * to place: it blocks that attacker, as tapping the attacker does. False when the tap is no such thing.
     */
    fun tapCombatSlot(scene: TableScene, point: TablePoint): Boolean {
        val pair = scene.combatSlots.firstOrNull { it.role == CombatRole.BLOCKER && it.bounds.contains(point) }?.pairId ?: return false
        val blocker = combat.selected ?: return false
        val live = current() ?: return false
        val placed = engine?.assuming()
        val play = placed?.board(live) ?: live
        val shown = combat.choices(play)
        if (combat.working(shown)) { error = CombatController.WORKING_OUT; return true }
        val choices = shown.choices
        if (choices.declaring == null || choices.attackers || blocker !in choices.blocks) return false
        if (placed != null) assumeBlock(placed, play, blocker, pair, toggle = true)
        else if (enabled() && viewer == choices.declaring) block(play, blocker, pair)
        else return false
        return true
    }

    /** CMB-034 (owner, 2026-11-06): a placed blocker leaves the blocks being declared: its copies [occurrenceIds] go. */
    private fun removeBlocker(occurrenceIds: List<String>): Boolean {
        val area = current()?.board?.combat ?: return false
        error = null
        return engineRoute { batchResult {
            occurrenceIds.map { submit(PlayAction.Combat(CombatAction.Remove(area.id, it))) }.any { it }
        } }
    }

    /**
     * CMB-040 (owner, 2026-11-05): a tap while the viewer waits for the defender's blocks ([placed]), as the defender's own
     * taps (CMB-034) on the lanes showing the blocks placed: one of the defender's creatures is picked as the blocker to
     * place, and an attacker tapped then is blocked by it (again: not). False when the tap does something else.
     */
    private fun assumeTap(placed: AssumedBlocks, live: TableBoard, item: TableItem): Boolean {
        if (item.kind != TableItemKind.CARD) return false
        val card = item.sourceId ?: return false
        if (item.viewport != "combat" && (item.zone != PlayZone.BATTLEFIELD || item.projection)) return false
        val play = placed.board(live)
        val area = play.board.combat ?: return false
        val choices = combat.choices(play).also { if (combat.working(it)) { error = CombatController.WORKING_OUT; return true } }.choices
        val defender = choices.declaring ?: return false
        val occurrence = CombatStackRules.occurrences(area).firstOrNull { it.sourceId == card }
        val attacking = occurrence != null && area.pairs.any { p -> p.attackers.any { it.id == occurrence.id } }
        if (!attacking && controllerOf(play, card) == defender) {
            if (card !in choices.blocks) {
                if (!creature(play, card)) return false
                error = "${combatName(play, card)} can't block now."; return true
            }
            // CMB-040 (owner, 2026-11-06): a placed blocker's copy is selected first; tapped selected, its blocks go
            val copy = item.viewport == "combat"
            if (copy && placed.blocks[card].orEmpty().isNotEmpty() && combat.selected == card) {
                combat.clearSelection(); placed.set(card, emptyList()); return true
            }
            combat.select(if (combat.selected == card && !copy) null else card)
            return true
        }
        val blocker = combat.selected?.takeIf { it in choices.blocks } ?: return false
        if (!attacking) return false
        assumeBlock(placed, play, blocker, area.pairs.first { p -> p.attackers.any { it.id == occurrence!!.id } }.id, toggle = true)
        return true
    }

    /** CMB-040: one of the defender's creatures dragged onto an attacker's pair blocks it (to assume); elsewhere refused. */
    private fun assumeDrag(placed: AssumedBlocks, intent: TableIntent.Move): Boolean {
        val live = current() ?: return false
        val play = placed.board(live)
        val destination = (intent.destination as TableDestination.Combat).destination
        val card = intent.scope.cardIds.singleOrNull()?.let { CombatStackRules.liveSource(play.board, it)?.id } ?: return false
        val pair = destination.pairId ?: return false
        val choices = combat.choices(play).also { if (combat.working(it)) { error = CombatController.WORKING_OUT; return false } }.choices
        if (controllerOf(play, card) != choices.declaring) return false
        return assumeBlock(placed, play, card, pair, toggle = false)
    }

    /** CMB-040: [blocker] blocks the attackers of [pairId] too (with [toggle], no longer when it does), as [block] does. */
    private fun assumeBlock(placed: AssumedBlocks, play: TableBoard, blocker: String, pairId: String, toggle: Boolean): Boolean {
        val choices = combat.choices(play).choices
        val now = placed.blocks[blocker].orEmpty()
        val capacity = if (blocker in choices.capacity) choices.capacity[blocker] else 1
        val next = if (pairId in now) { if (toggle) now - pairId else return true } else {
            combat.draftRefusal(play, listOf(blocker), CombatRole.BLOCKER, pairId, null)?.let { error = it; return false }
            when {
                capacity == null || now.size < capacity -> now + pairId
                capacity == 1 -> listOf(pairId)
                else -> { error = "${combatName(play, blocker)} can block only $capacity attackers."; return false }
            }
        }
        error = null
        placed.set(blocker, next)
        if (pairId !in now && capacity != null && next.size >= capacity) combat.clearSelection()
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
        // CMB-040: Clear of the blocks placed to assume, while waiting for the defender (the viewer can't act otherwise)
        if (intent is TableActionIntent.Combat && intent.tool == CombatTool.ASSUME_CLEAR)
            return engine?.assuming()?.let { combat.clearSelection(); it.clear(); true } ?: false
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
    /** CMB-015: the staged attackers [bound] dropped into another lane attack its target [cardId] (null: the player). */
    private fun setAttackTarget(bound: ToolkitCapture, cardId: String?): Boolean {
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
}
