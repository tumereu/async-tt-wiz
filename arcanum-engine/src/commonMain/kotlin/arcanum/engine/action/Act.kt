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
internal class Asker(private val answers: List<Answer>, private val prefix: String) {
    private var used = 0

    fun ask(decision: (DecisionId) -> Decision): Answer {
        val answer = answers.getOrNull(used) ?: throw NeedDecision(decision(DecisionId("$prefix.$used")))
        used++
        return answer
    }

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
        val o = state.obj(id) ?: return null
        val v = view()[id]
        return ObjectSnapshot(
            id = id, owner = o.owner, controller = v?.controller ?: o.controller, zone = o.zone,
            characteristics = v?.characteristics, counters = o.counters, damage = o.damage, tapped = o.tapped,
            token = o.token, attachedTo = o.attachedTo, facts = o.facts,
        )
    }

    // ---- Decisions (each asked only when there is a choice, P5)

    fun yesNo(player: PlayerId, source: ObjectId?, text: String, cost: arcanum.api.Cost? = null): Boolean =
        (asker.ask { Decision.May(it, player, source, text, cost) } as Answer.YesNo).yes

    /** Chooses [min]..[max] of [options]; with no more options than [min] all are chosen without asking (CR 609.3). */
    fun chooseObjects(player: PlayerId, source: ObjectId?, options: List<ObjectId>, min: Int, max: Int?, text: String): List<ObjectId> {
        val top = max?.coerceAtMost(options.size) ?: options.size
        if (options.isEmpty() || top == 0) return emptyList()
        if (options.size <= min) return options
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
        arcanum.engine.casting.Payment.payOnResolution(recorder, player, cost, uses)
    }

    /** Something to do by hand (D61): shown to [player], answered "done". */
    fun manual(player: PlayerId, source: ObjectId?, text: String) {
        asker.ask { Decision.Manual(it, player, source, text) }
    }

    /** Makes sure the identities of [ids] are known, asking the host for the unknown ones (D60). */
    fun fill(player: PlayerId, source: ObjectId?, ids: List<ObjectId>, reason: String) {
        val unknown = ids.filter { state.obj(it)?.unknown == true }
        if (unknown.isEmpty()) return
        val filled = (asker.ask { Decision.HostFill(it, player, source, unknown, reason) } as Answer.Filled).objects
        filled.forEach { f ->
            val before = state.objOrThrow(f.id)
            if (before.unknown) record(Change.Filled(before, before.copy(definition = f.definition)))
        }
    }
}
