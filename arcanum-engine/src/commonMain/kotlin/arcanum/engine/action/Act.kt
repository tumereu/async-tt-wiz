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
internal class NeedDecision(val decision: Decision) : Exception("Waiting for ${decision.kind}")

/**
 * The answers given so far to the decisions of one step, handed out in the order the step asks (D29): a step asks the
 * same questions in the same order each time it is redone, so answer n belongs to question n. [prefix] makes each
 * decision's id.
 */
internal class Asker(
    private val answers: List<Answer>,
    private val prefix: String,
) {
    private var used = 0

    /** The id the next question gets. */
    fun nextId(): DecisionId = DecisionId("$prefix.$used")

    fun ask(decision: (DecisionId) -> Decision): Answer {
        val answer = answers.getOrNull(used) ?: throw NeedDecision(decision(DecisionId("$prefix.$used")))
        used++
        return answer
    }

    /** The answers not used (yet). */
    val unused: List<Answer> get() = answers.drop(used)

    companion object {
        /** For flows that must not ask anything. */
        val NONE = Asker(emptyList(), "none")
    }
}

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
    // hob
    /** Names bound by the resolving spell's own replacement as it applied ("exile it instead … You may cast that card"): the
     * resolving steps take them after the effect that made the replaced event (CR 614.15). */
    val ownInsteadBinds: MutableMap<String, arcanum.engine.state.NamedValue> = mutableMapOf()

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
            token = o.token, attachedTo = o.attachedTo, cast = o.cast, linked = o.linked, statuses = arcanum.engine.state.snapshotStatuses(o),
            attacking = state.combat?.attackers.orEmpty().any { it.id == id }, marks = o.marks,
            copiable = v?.copiable?.takeIf { it != arcanum.engine.view.Copiable.plain(o) },
        )
    }

    /** A copy of a spell on the Stack as everyone sees it (CR 707.10: a spell without a card). */
    private fun copySnapshot(id: ObjectId): ObjectSnapshot? {
        val item = state.stackItems[id]?.takeIf { it.kind is arcanum.engine.state.StackItemKind.SpellCopy } ?: return null
        val v = view()[id]
        return ObjectSnapshot(id = id, owner = item.controller, controller = v?.controller ?: item.controller, zone = arcanum.api.Zone.Stack,
            characteristics = v?.characteristics, cast = item.cast)
    }

    // ---- Decisions (each asked only when there is a choice, P5)

    /** Asks [decision] through the [asker]; one of a player who left the game is decided as CR 800.4f–h say
     * ([arcanum.engine.setup.LeftPlayers]): a cost not paid, without asking; the next player, or a player the object's
     * controller chooses first, deciding it. */
    fun ask(decision: (DecisionId) -> Decision): Answer {
        if (state.players.none { it.lost }) return asker.ask(decision)
        val first = decision(asker.nextId())
        return when (val r = arcanum.engine.setup.LeftPlayers.redirect(state, first)) {
            null -> asker.ask(decision)
            // A payment they were making isn't handed to anyone else (CR 800.4f): it stays theirs, for the host
            arcanum.engine.setup.LeftPlayers.Redirect.NotPaid -> if (first is Decision.May) Answer.YesNo(false) else asker.ask(decision)
            is arcanum.engine.setup.LeftPlayers.Redirect.To ->
                asker.ask { id -> arcanum.engine.setup.LeftPlayers.reassigned(decision(id), id, r.player) }
            is arcanum.engine.setup.LeftPlayers.Redirect.ChosenBy -> {
                val chosen = (asker.ask { id -> Decision.ChoosePlayer(id, r.chooser, first.source, r.options) } as? Answer.Player)?.player
                    ?.takeIf { it in r.options } ?: throw IllegalArgumentException("Choose who decides among ${r.options} (CR 800.4g)")
                asker.ask { id -> arcanum.engine.setup.LeftPlayers.reassigned(decision(id), id, chosen) }
            }
        }
    }

    fun yesNo(player: PlayerId, source: ObjectId?, text: String, cost: arcanum.api.Cost? = null): Boolean =
        (ask { Decision.May(it, player, source, text, cost) } as Answer.YesNo).yes

    /** I-D18: [player] is asked a decision the rules leave them no choice in when they turned "Skip when I have no
     * choice" off and the skip depends on cards only they may see ([hiddenDecides]). */
    fun asksAnyway(player: PlayerId, hiddenDecides: Boolean): Boolean = hiddenDecides && !recorder.prefs.skipsWhenNoChoice(player)

    /** A cost [player] can't pay ("you may discard a land card", CR 118.3): asked anyway when [asksAnyway] (I-D18), with
     * only "no" accepted ([Decision.May.payable]). */
    fun cannotPay(player: PlayerId, source: ObjectId?, text: String, cost: arcanum.api.Cost?, hiddenDecides: Boolean) {
        if (!asksAnyway(player, hiddenDecides)) return
        val yes = (ask { Decision.May(it, player, source, text, cost, payable = false) } as Answer.YesNo).yes
        require(!yes) { "The cost can't be paid" }
    }

    /**
     * Whether what [player] may choose among [pool] depends on cards only they may see (I-D18): a card of the pool in
     * their hand, outside the game or that only they may look at (not a face-down permanent or spell, which everyone
     * sees as it is, CR 708.2a) for which [decides] (whether it would be offered depends on what card it is).
     */
    fun hiddenDecides(player: PlayerId, pool: List<ObjectId>, decides: (ObjectId) -> Boolean): Boolean {
        if (pool.isEmpty()) return false
        val private = Knowledge.privateTo(state, view(), player)
        val deciding = pool.filter { id ->
            id in private && state.obj(id)?.zone.let { it != arcanum.api.Zone.Battlefield && it != arcanum.api.Zone.Stack } && decides(id)
        }
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
            val chosen = (ask { Decision.ChooseObjects(it, player, source, forced, forced.size, forced.size, text) } as Answer.Objects).chosen
            require(chosen.toSet() == forced.toSet()) { "Not a valid choice: $chosen" }
            return forced
        }
        val chosen = (ask { Decision.ChooseObjects(it, player, source, options, min.coerceAtMost(top), top, text) } as Answer.Objects).chosen
        require(chosen.all { it in options } && chosen.size in min.coerceAtMost(top)..top) { "Not a valid choice: $chosen" }
        return chosen
    }

    /**
     * Chooses at least [min] of [options] whose [total] is reached, any number beyond that, as one selection (CR 701.59a; the
     * answer is checked as it comes, [AnswerCheck]). Asked whenever there is anything to choose: unlike [chooseObjects] nothing
     * is forced, since which cards go is the player's to confirm even when only all of them reach the total. With a public
     * pool nothing hidden decides anything ([asksAnyway]).
     */
    fun chooseReaching(player: PlayerId, source: ObjectId?, options: List<ObjectId>, min: Int, total: arcanum.api.Total, text: String): List<ObjectId> {
        if (options.isEmpty()) return emptyList()
        return (ask { Decision.ChooseObjects(it, player, source, options, min, null, text, total) } as Answer.Objects).chosen
    }

    /** Puts all of [options] in an order (first: top of a library). */
    fun order(player: PlayerId, source: ObjectId?, options: List<ObjectId>, text: String): List<ObjectId> {
        if (options.size < 2) return options
        val chosen = (ask { Decision.ChooseObjects(it, player, source, options, options.size, options.size, text) } as Answer.Objects).chosen
        require(chosen.toSet() == options.toSet() && chosen.size == options.size) { "Order every one once: $chosen" }
        return chosen
    }

    /** Colours already said for the choices coming (a payment's triggered mana abilities, CR 605.4a). */
    var colorsAhead: MutableList<Color>? = null

    fun chooseColor(player: PlayerId, source: ObjectId?, among: List<Color>): Color {
        if (among.size == 1) return among.single()
        colorsAhead?.firstOrNull { it in among }?.let { said -> colorsAhead?.remove(said); return said }
        return (ask { Decision.ChooseColor(it, player, source, among) } as Answer.ColorChoice).color
    }

    fun chooseNumber(player: PlayerId, source: ObjectId?, min: Int, max: Int?): Int =
        if (max == min) min else (ask { Decision.ChooseNumber(it, player, source, min, max) } as Answer.Number).value

    fun choosePlayer(player: PlayerId, source: ObjectId?, options: List<PlayerId>): PlayerId? = when (options.size) {
        0 -> null
        1 -> options.single()
        else -> (ask { Decision.ChoosePlayer(it, player, source, options) } as Answer.Player).player
    }

    /** [naming]: the object an option names ([Decision.ChooseOption.naming]). */
    fun chooseOption(player: PlayerId, source: ObjectId?, options: List<String>, naming: Map<String, ObjectId> = emptyMap(), text: String? = null): String =
        if (options.size == 1) options.single() else (ask { Decision.ChooseOption(it, player, source, options, text, naming) } as Answer.Option).chosen

    fun chooseSubtype(player: PlayerId, source: ObjectId?, among: List<Subtype>?): Subtype =
        (ask { Decision.ChooseSubtype(it, player, source, among) } as Answer.SubtypeChoice).subtype

    fun chooseCardType(player: PlayerId, source: ObjectId?, among: List<CardType>): CardType =
        if (among.size == 1) among.single() else (ask { Decision.ChooseCardType(it, player, source, among) } as Answer.CardTypeChoice).cardType

    fun chooseCardName(player: PlayerId, source: ObjectId?, filter: ObjectFilter): String =
        (ask { Decision.ChooseCardName(it, player, source, filter) } as Answer.CardName).name

    fun chooseTargets(player: PlayerId, source: ObjectId?, slot: String, options: List<TargetRef>, min: Int, max: Int?): List<TargetRef> {
        if (options.isEmpty()) return emptyList()
        val chosen = (ask { Decision.Targets(it, player, source, slot, options, min, max) } as Answer.Targets).chosen
        require(chosen.all { it in options }) { "Not a legal target: $chosen" }
        return chosen
    }

    /** A random result, answered by the host at once (D25). */
    fun random(player: PlayerId, source: ObjectId?, what: RandomKind): Answer = ask { Decision.Random(it, player, source, what) }

    /** [player] pays mana [cost] while something resolves (CR 118.12, 608.2g): how is asked ([Decision.ManaPayment], with
     * the engine's suggestion, §7.8), then it is paid. [xCost]: the cost contains {X} (mh3 fixes, CR 106.6). */
    fun payMana(player: PlayerId, source: ObjectId?, cost: arcanum.api.ManaCost, xCost: Boolean = false) {
        if (arcanum.engine.mana.Mana.needs(cost).isEmpty()) return
        val snap = arcanum.engine.casting.Snapshot(state)
        val uses = (ask {
            Decision.ManaPayment(it, player, source, cost, arcanum.engine.mana.Mana.options(snap, player, cost, null, xCost = xCost),
                arcanum.engine.mana.Mana.suggest(snap, player, cost, null, xCost = xCost), xCost = xCost)
        } as Answer.ManaPayment).uses
        arcanum.engine.casting.Payment.payOnResolution(this, player, cost, uses, xCost = xCost)
    }

    /** [player] looks at [ids], or everyone sees them when [public]: a [Change.Shown] of those not public already (a
     * search, scry, look or reveal; what a host shows each player, [arcanum.engine.Host.revealed]). */
    fun show(player: PlayerId, ids: List<ObjectId>, public: Boolean = false) {
        val hidden = ids.distinct().filter { id -> state.obj(id)?.let { !Knowledge.public(it) } == true }
        if (hidden.isNotEmpty()) record(Change.Shown(if (public) null else player, hidden))
    }

    /** Ids for "[count] of [among] at random"; [library]: the whole library a shuffle is of ([RandomKind.Objects.library]). */
    fun randomObjects(player: PlayerId, source: ObjectId?, among: List<ObjectId>, count: Int, library: List<ObjectId> = emptyList()): List<ObjectId> =
        (random(player, source, RandomKind.Objects(among, count, library)) as Answer.RandomObjects).chosen
}
