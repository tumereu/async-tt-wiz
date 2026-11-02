package arcanum.engine.action

import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.ObjectFilter
import arcanum.api.Subtype
import arcanum.engine.Engine
import arcanum.engine.change.Change
import arcanum.engine.change.Recorder
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.DecisionId
import arcanum.engine.decision.RandomKind
import arcanum.engine.query.Context
import arcanum.engine.query.Evaluator
import arcanum.engine.query.ViewBoard
import arcanum.engine.state.GameObject
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.ObjectSnapshot
import arcanum.engine.state.PlayerId
import arcanum.engine.state.StackItem
import arcanum.engine.state.TargetRef
import arcanum.engine.view.View

/** Thrown when a step needs a decision it has no answer for yet: the step is taken back and redone from its start
 * once the decision is answered (D29, D31). */
internal class NeedDecision(val decision: Decision) : Exception("Waiting for ${decision::class.simpleName}")

/**
 * The answers given so far to the decisions of one step, handed out in the order the step asks (D29): a step asks the
 * same questions in the same order each time it is redone, so answer n belongs to question n. [prefix] makes each
 * decision's id.
 */
internal class Asker(
    private val answers: List<Answer>,
    private val prefix: String,
    /** Set while a resolution is worked out as in another player's view ([Decision.OtherView]). */
    val otherView: OtherViewFills? = null,
    /** A private fill for another player than the viewer switches to [Decision.OtherView] (a resolution's steps). */
    val mayAskOtherView: Boolean = false,
) {
    private var used = 0

    fun ask(decision: (DecisionId) -> Decision): Answer {
        val answer = answers.getOrNull(used) ?: throw NeedDecision(decision(DecisionId("$prefix.$used")))
        // Answers sent from another player's view are checked as the steps ask them (the one validation path)
        otherView?.check?.invoke(decision(DecisionId("$prefix.$used")), answer)
        used++
        return answer
    }

    /** The next answer is the fill of [ids] asked before (answered in this view before it switched to
     * [Decision.OtherView]): it is used up and returned. */
    fun filledNext(ids: Set<ObjectId>): Answer.Filled? {
        val next = answers.getOrNull(used) as? Answer.Filled ?: return null
        if (next.objects.map { it.id }.toSet() != ids) return null
        used++
        return next
    }

    /** The answers not used (yet). */
    val unused: List<Answer> get() = answers.drop(used)

    companion object {
        /** For flows that must not ask anything. */
        val NONE = Asker(emptyList(), "none")
    }
}

/**
 * A resolution worked out as in [player]'s view ([Decision.OtherView], D60, D31): the identities the host sent with the
 * answer ([filled]; never kept in the state), the unknown objects the steps needed so far ([needed]), the cards filled
 * only to work it out ([hidden]: only [player] may see them; [Knowledge.forgetHidden] keeps them out of the changes),
 * and [check] for the answers as the steps ask them.
 */
internal class OtherViewFills(
    val player: PlayerId,
    val filled: Map<ObjectId, arcanum.api.CardDefinition>,
    val check: ((Decision, Answer) -> Unit)? = null,
) {
    val needed = mutableListOf<ObjectId>()
    val hidden = mutableMapOf<arcanum.engine.state.CardId, arcanum.api.CardDefinition>()
}

/** Thrown in a viewer's state when a step reaches cards only another player may look at ([Decision.OtherView]). */
internal class NeedOtherView(val player: PlayerId, val source: ObjectId?, val objects: List<ObjectId>, val reason: String) :
    Exception("Done in $player's view")

/**
 * Who does something (D19): [controller] is "you", [source] the object it comes from, [item] the Stack item being
 * resolved (its own replacements apply first, CR 614.15), [key] what token ids are made from (D35, D42).
 */
internal data class Cause(
    val controller: PlayerId,
    val source: ObjectId?,
    val item: StackItem? = null,
    val key: String,
)

/**
 * One flow's working context: the [recorder] whose state changes are made on, and the [asker] for its decisions. The
 * view of the game is worked out again only when something changed.
 */
internal class Act(val recorder: Recorder, val asker: Asker) {
    val state: GameState get() = recorder.state

    /** Keys of the replacements whose "instead" instructions are running: they don't apply to their own events (CR 614.5). */
    val replacing: MutableSet<String> = mutableSetOf()

    private var viewSize = -1
    private var viewLast: Change? = null
    private var cached: View? = null

    /** The view (CR 613) of the game as it is now. */
    fun view(): View {
        val last = recorder.changes.lastOrNull()
        if (cached == null || viewSize != recorder.changes.size || viewLast !== last) {
            cached = Engine.view(state)
            viewSize = recorder.changes.size
            viewLast = last
        }
        return cached!!
    }

    fun board(): ViewBoard = ViewBoard(state, view())

    fun evaluator(ctx: Context): Evaluator = Evaluator(board(), ctx)

    fun record(change: Change) = recorder.record(change)

    fun obj(id: ObjectId): GameObject? = state.obj(id)

    /** The controller of [id] after layers, else its owner (CR 108.4a). */
    fun controllerOf(id: ObjectId): PlayerId? = board().controller(id)

    /** What everyone can see of [id] now (D33): its characteristics after layers, for last known information. */
    fun snapshot(id: ObjectId): ObjectSnapshot? {
        val o = state.obj(id) ?: return copySnapshot(id)
        val v = view()[id]
        return ObjectSnapshot(
            id = id, owner = o.owner, controller = v?.controller ?: o.controller, zone = o.zone,
            characteristics = v?.characteristics, counters = o.counters, damage = o.damage, tapped = o.tapped,
            token = o.token, attachedTo = o.attachedTo, facts = o.facts, memory = o.memory,
        )
    }

    /** A copy of a spell on the Stack as everyone sees it (CR 707.10: a spell without a card). */
    private fun copySnapshot(id: ObjectId): ObjectSnapshot? {
        val item = state.stackItems[id]?.takeIf { it.kind is arcanum.engine.state.StackItemKind.SpellCopy } ?: return null
        val v = view()[id]
        return ObjectSnapshot(id = id, owner = item.controller, controller = v?.controller ?: item.controller, zone = arcanum.api.Zone.Stack,
            characteristics = v?.characteristics, facts = item.facts ?: arcanum.engine.state.ObjectFacts())
    }

    // ---- Decisions (each asked only when there is a choice, P5)

    fun yesNo(player: PlayerId, source: ObjectId?, text: String, cost: arcanum.api.Cost? = null): Boolean =
        (asker.ask { Decision.May(it, player, source, text, cost) } as Answer.YesNo).yes

    /** I-D18: [player] is asked a decision the rules leave them no choice in when they turned "Skip when I have no
     * choice" off and the skip depends on cards only they may see ([hiddenDecides]). */
    fun asksAnyway(player: PlayerId, hiddenDecides: Boolean): Boolean = hiddenDecides && !state.player(player).skipWhenNoChoice

    /** A cost [player] can't pay ("you may discard a land card", CR 118.3): asked anyway when [asksAnyway] (I-D18), with
     * only "no" accepted ([Decision.May.payable]). */
    fun cannotPay(player: PlayerId, source: ObjectId?, text: String, cost: arcanum.api.Cost?, hiddenDecides: Boolean) {
        if (!asksAnyway(player, hiddenDecides)) return
        val yes = (asker.ask { Decision.May(it, player, source, text, cost, payable = false) } as Answer.YesNo).yes
        require(!yes) { "The cost can't be paid" }
    }

    /**
     * Whether what [player] may choose among [pool] depends on cards only they may see (I-D18): a card of the pool in
     * their hand, outside the game or that only they may look at (not a face-down permanent or spell, which everyone
     * sees as it is, CR 708.2a) for which [decides] (whether it would be offered depends on what card it is). Those
     * still unknown in the state are filled in for [player] first (D60), so the choice is worked out on what they are,
     * unless not [fill] (a flow that offers unknown cards as they are).
     */
    fun hiddenDecides(
        player: PlayerId, source: ObjectId?, pool: List<ObjectId>, fill: Boolean = true, decides: (ObjectId) -> Boolean,
    ): Boolean {
        if (pool.isEmpty()) return false
        val private = Knowledge.privateTo(state, view(), player)
        val deciding = pool.filter { id ->
            id in private && state.obj(id)?.zone.let { it != arcanum.api.Zone.Battlefield && it != arcanum.api.Zone.Stack } && decides(id)
        }
        val unknown = deciding.filter { state.obj(it)?.unknown == true }
        if (fill && unknown.isNotEmpty()) fill(player, source, unknown, "choose among them")
        return deciding.isNotEmpty()
    }

    /**
     * Chooses [min]..[max] of [options]; with no more options than [min] all are chosen without asking (CR 609.3), and
     * with none nothing is asked, unless [asksAnyway] for [hiddenDecides] (I-D18): then the forced choice is asked with
     * only that answer (all of them, or none).
     */
    fun chooseObjects(
        player: PlayerId, source: ObjectId?, options: List<ObjectId>, min: Int, max: Int?, text: String,
        hiddenDecides: Boolean = false,
    ): List<ObjectId> {
        val top = max?.coerceAtMost(options.size) ?: options.size
        val forced = when {
            options.isEmpty() || top == 0 -> emptyList()
            options.size <= min -> options
            else -> null
        }
        if (forced != null) {
            if (!asksAnyway(player, hiddenDecides)) return forced
            val chosen = (asker.ask { Decision.ChooseObjects(it, player, source, forced, forced.size, forced.size, text) } as Answer.Objects).chosen
            require(chosen.toSet() == forced.toSet()) { "Not a valid choice: $chosen" }
            return forced
        }
        val chosen = (asker.ask { Decision.ChooseObjects(it, player, source, options, min.coerceAtMost(top), top, text) } as Answer.Objects).chosen
        require(chosen.all { it in options } && chosen.size in min.coerceAtMost(top)..top) { "Not a valid choice: $chosen" }
        return chosen
    }

    /** Puts all of [options] in an order (first: top of a library). */
    fun order(player: PlayerId, source: ObjectId?, options: List<ObjectId>, text: String): List<ObjectId> {
        if (options.size < 2) return options
        val chosen = (asker.ask { Decision.ChooseObjects(it, player, source, options, options.size, options.size, text) } as Answer.Objects).chosen
        require(chosen.toSet() == options.toSet() && chosen.size == options.size) { "Order every one once: $chosen" }
        return chosen
    }

    fun chooseColor(player: PlayerId, source: ObjectId?, among: List<Color>): Color =
        if (among.size == 1) among.single() else (asker.ask { Decision.ChooseColor(it, player, source, among) } as Answer.ColorChoice).color

    fun chooseNumber(player: PlayerId, source: ObjectId?, min: Int, max: Int?): Int =
        if (max == min) min else (asker.ask { Decision.ChooseNumber(it, player, source, min, max) } as Answer.Number).value

    fun choosePlayer(player: PlayerId, source: ObjectId?, options: List<PlayerId>): PlayerId? = when (options.size) {
        0 -> null
        1 -> options.single()
        else -> (asker.ask { Decision.ChoosePlayer(it, player, source, options) } as Answer.Player).player
    }

    fun chooseOption(player: PlayerId, source: ObjectId?, options: List<String>): String =
        if (options.size == 1) options.single() else (asker.ask { Decision.ChooseOption(it, player, source, options) } as Answer.Option).chosen

    fun chooseSubtype(player: PlayerId, source: ObjectId?, among: List<Subtype>?): Subtype =
        (asker.ask { Decision.ChooseSubtype(it, player, source, among) } as Answer.SubtypeChoice).subtype

    fun chooseCardType(player: PlayerId, source: ObjectId?, among: List<CardType>): CardType =
        if (among.size == 1) among.single() else (asker.ask { Decision.ChooseCardType(it, player, source, among) } as Answer.CardTypeChoice).cardType

    fun chooseCardName(player: PlayerId, source: ObjectId?, filter: ObjectFilter): String =
        (asker.ask { Decision.ChooseCardName(it, player, source, filter) } as Answer.CardName).name

    fun chooseTargets(player: PlayerId, source: ObjectId?, slot: String, options: List<TargetRef>, min: Int, max: Int?): List<TargetRef> {
        if (options.isEmpty()) return emptyList()
        val chosen = (asker.ask { Decision.Targets(it, player, source, slot, options, min, max) } as Answer.Targets).chosen
        require(chosen.all { it in options }) { "Not a legal target: $chosen" }
        return chosen
    }

    /** A random result, answered by the host at once (D25). */
    fun random(player: PlayerId, source: ObjectId?, what: RandomKind): Answer = asker.ask { Decision.Random(it, player, source, what) }

    /** [player] pays mana [cost] while something resolves (CR 118.12, 608.2g): how is asked ([Decision.ManaPayment], with
     * the engine's suggestion, §7.8), then it is paid. */
    fun payMana(player: PlayerId, source: ObjectId?, cost: arcanum.api.ManaCost) {
        if (arcanum.engine.mana.Mana.needs(cost).isEmpty()) return
        val snap = arcanum.engine.casting.Snapshot(state)
        val uses = (asker.ask {
            Decision.ManaPayment(it, player, source, cost, arcanum.engine.mana.Mana.options(snap, player, cost, null),
                arcanum.engine.mana.Mana.suggest(snap, player, cost, null))
        } as Answer.ManaPayment).uses
        arcanum.engine.casting.Payment.payOnResolution(this, player, cost, uses)
    }

    /** Something to do by hand (D61): shown to [player], answered "done". */
    fun manual(player: PlayerId, source: ObjectId?, text: String) {
        asker.ask { Decision.Manual(it, player, source, text) }
    }

    /** Makes sure the identities of [ids] are known, asking the host for exactly the unknown ones (D60): [player] looks
     * at them, or everyone sees them when [public] ([Decision.HostFill]). */
    fun fill(player: PlayerId, source: ObjectId?, ids: List<ObjectId>, reason: String, public: Boolean = false) {
        val unknown = ids.distinct().filter { state.obj(it)?.unknown == true }
        if (unknown.isEmpty()) return
        // D60, D31: only [player] may look, and the state is another player's view
        val othersOnly = !public && state.viewer != null && player != state.viewer
        val other = asker.otherView
        if (other != null) {
            // Worked out as in another player's view: a fill answered here before the switch, else the host's identities
            asker.filledNext(unknown.toSet())?.let { answered -> return record(unknown, answered.objects) }
            unknown.filter { it !in other.needed }.forEach { other.needed += it }
            if (unknown.any { it !in other.filled }) throw NeedOtherView(other.player, source, other.needed.toList(), reason)
            unknown.forEach { id ->
                val before = state.objOrThrow(id)
                val definition = other.filled.getValue(id)
                if (othersOnly) other.hidden[before.card] = definition
                record(Change.Filled(before, before.copy(definition = definition)))
            }
            return
        }
        if (othersOnly && asker.mayAskOtherView) throw NeedOtherView(player, source, unknown, reason)
        val filled = (asker.ask { Decision.HostFill(it, player, source, unknown, reason, public) } as Answer.Filled).objects
        // Checked against the decision when answered (AnswerCheck): exactly the asked objects, each once
        record(unknown, filled)
    }

    private fun record(unknown: List<ObjectId>, filled: List<arcanum.engine.decision.FilledObject>) = unknown.forEach { id ->
        val before = state.objOrThrow(id)
        record(Change.Filled(before, before.copy(definition = filled.first { it.id == id }.definition)))
    }

    /** Ids for "[count] of [among] at random", or new ids for [among] put in a random order hidden from the viewer
     * ([RandomKind.Objects.newIds], D60); [library]: the whole library a shuffle is of ([RandomKind.Objects.library]). */
    fun randomObjects(player: PlayerId, source: ObjectId?, among: List<ObjectId>, count: Int, newIds: Boolean = false,
        library: List<ObjectId> = emptyList()): List<ObjectId> =
        (random(player, source, RandomKind.Objects(among, count, newIds, library)) as Answer.RandomObjects).chosen
}
