package arcanum.engine.resolution

import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.ManaCost
import arcanum.api.ManaSymbol
import arcanum.api.Zone
import arcanum.engine.action.Actions
import arcanum.engine.casting.snapshot
import arcanum.engine.mana.Mana
import arcanum.engine.change.Change
import arcanum.engine.change.Changes
import arcanum.engine.change.GameEvent
import arcanum.engine.change.Meaning
import arcanum.engine.decision.Answer
import arcanum.engine.decision.RandomKind
import arcanum.engine.state.NamedValue
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef

/**
 * Costs paid while a spell or ability resolves (CR 118.12, D40): "you may pay …", "unless … pays", ward. A cost can be
 * chosen only if it can be paid in full (CR 601.2h, 118.3). Mana is paid with a
 * [arcanum.engine.decision.Decision.ManaPayment] (pool mana, simple mana abilities, life for Phyrexian symbols); a cost
 * whose amount isn't known here ("its mana cost", an unannounced X) is paid by hand.
 */
internal object Costs {
    fun describe(cost: Cost): String = cost.parts.joinToString(", ") { part ->
        when (part) {
            is CostPart.Mana -> part.cost.toString()
            is CostPart.PayLife -> "life"
            else -> arcanum.engine.decision.Texts.words(part::class.simpleName ?: "cost")
        }
    }

    fun canPay(scope: Scope, payer: PlayerId, cost: Cost): Boolean = cost.parts.all { canPay(scope, payer, it) }

    private fun canPay(scope: Scope, payer: PlayerId, part: CostPart): Boolean {
        val state = scope.act.state
        return when (part) {
            // CR 118.3, 608.2g: mana abilities may be activated while paying; a cost is payable when a payment is found
            is CostPart.Mana -> manaCost(scope, part)?.let { cost ->
                Mana.needs(cost).isEmpty() || Mana.suggest(scope.act.recorder.snapshot(), payer, cost, null).isNotEmpty()
            } ?: true
            CostPart.ItsManaCost, is CostPart.Manual -> true
            CostPart.TapSelf -> scope.self?.let { state.obj(it) }?.let { it.zone == Zone.Battlefield && !it.tapped } ?: false
            CostPart.UntapSelf -> scope.self?.let { state.obj(it) }?.let { it.zone == Zone.Battlefield && it.tapped } ?: false
            is CostPart.TapObjects -> candidates(scope, payer, part.what, Zone.Battlefield) { !(state.obj(it)?.tapped ?: true) }.size >= count(scope, part.what)
            is CostPart.Sacrifice -> candidates(scope, payer, part.what, Zone.Battlefield).size >= count(scope, part.what)
            is CostPart.Discard -> candidates(scope, payer, part.what, Zone.Hand).size >= count(scope, part.what)
            is CostPart.Exile -> candidates(scope, payer, part.what, part.from).size >= count(scope, part.what)
            is CostPart.ReturnToHand -> candidates(scope, payer, part.what, Zone.Battlefield).size >= count(scope, part.what)
            is CostPart.Reveal -> candidates(scope, payer, part.what, Zone.Hand).size >= count(scope, part.what)
            // CR 119.4: a player can pay life only if their life total is at least that much
            is CostPart.PayLife -> state.player(payer).life >= scope.value(part.amount)
            is CostPart.PayEnergy -> state.player(payer).counters(CounterKind.Energy) >= scope.value(part.amount)
            is CostPart.RemoveCounters -> candidates(scope, payer, part.from, Zone.Battlefield).any { id ->
                val o = state.obj(id)!!
                (if (part.kind == null) o.counters.sumOf { it.count } else o.counters(part.kind!!)) >= scope.value(part.amount)
            }
            is CostPart.PutCounters -> true
            is CostPart.Loyalty -> scope.value(part.amount) >= 0 ||
                (scope.self?.let { state.obj(it)?.counters(CounterKind.Loyalty) } ?: 0) >= -scope.value(part.amount)
            is CostPart.Mill -> state.player(payer).library.size >= scope.value(part.count)
            is CostPart.OneOf -> part.options.any { canPay(scope, payer, it) }
            is CostPart.Action -> true
        }
    }

    fun pay(scope: Scope, payer: PlayerId, cost: Cost) = cost.parts.forEach { pay(scope, payer, it) }

    private fun pay(scope: Scope, payer: PlayerId, part: CostPart) {
        val act = scope.act
        val cause = scope.cause
        when (part) {
            is CostPart.Mana -> manaCost(scope, part)?.let { act.payMana(payer, scope.self, it) }
                ?: payMana(scope, payer, part.cost.toString() + (part.times?.let { " × ${scope.value(it)}" } ?: ""))
            CostPart.ItsManaCost -> payMana(scope, payer, "its mana cost")
            is CostPart.Manual -> act.manual(payer, scope.self, part.text)
            CostPart.TapSelf -> scope.self?.let { Actions.tap(act, listOf(it), true, listOf(Meaning.Paid)) }
            CostPart.UntapSelf -> scope.self?.let { Actions.tap(act, listOf(it), false, listOf(Meaning.Paid)) }
            is CostPart.TapObjects -> {
                val chosen = choose(scope, payer, part.what, Zone.Battlefield, "tap") { !(act.state.obj(it)?.tapped ?: true) }
                Actions.tap(act, chosen, true, listOf(Meaning.Paid))
                scope.bind(part.bind, NamedValue.Objects(chosen))
            }
            is CostPart.Sacrifice -> {
                val chosen = choose(scope, payer, part.what, Zone.Battlefield, "sacrifice")
                scope.bind(part.preBind, NamedValue.Objects(chosen))
                val moved = Actions.sacrifice(act, cause, chosen)
                scope.bind(part.postBind, NamedValue.Objects(moved.values.toList()))
            }
            is CostPart.Discard -> {
                val chosen = if (part.random) randomFromHand(scope, payer, count(scope, part.what))
                    else choose(scope, payer, part.what, Zone.Hand, "discard")
                scope.bind(part.preBind, NamedValue.Objects(chosen))
                scope.bind(part.postBind, NamedValue.Objects(Actions.discard(act, cause, chosen).values.toList()))
            }
            is CostPart.Exile -> {
                val chosen = choose(scope, payer, part.what, part.from, "exile")
                scope.bind(part.preBind, NamedValue.Objects(chosen))
                val moved = Actions.moveTo(act, cause, chosen, Zone.Exile, listOf(Meaning.Paid))
                scope.bind(part.postBind, NamedValue.Objects(moved.values.toList()))
            }
            is CostPart.ReturnToHand -> {
                val chosen = choose(scope, payer, part.what, Zone.Battlefield, "return")
                scope.bind(part.preBind, NamedValue.Objects(chosen))
                scope.bind(part.postBind, NamedValue.Objects(Actions.moveTo(act, cause, chosen, Zone.Hand, listOf(Meaning.ReturnedToHand)).values.toList()))
            }
            is CostPart.Reveal -> {
                val chosen = choose(scope, payer, part.what, Zone.Hand, "reveal")
                act.fill(payer, scope.self, chosen, "revealed", public = true)
                act.record(Change.Happened(GameEvent.Revealed(payer, chosen)))
                scope.bind(part.bind, NamedValue.Objects(chosen))
            }
            is CostPart.PayLife -> {
                val amount = scope.value(part.amount)
                if (amount > 0) Actions.payLife(act, cause, payer, amount)
            }
            is CostPart.PayEnergy -> Actions.removeCounters(act, TargetRef.Player(payer), CounterKind.Energy, scope.value(part.amount))
            is CostPart.RemoveCounters -> choose(scope, payer, part.from, Zone.Battlefield, "remove counters from").forEach { id ->
                val kind = part.kind ?: act.state.obj(id)?.counters?.firstOrNull()?.kind ?: return@forEach
                Actions.removeCounters(act, TargetRef.Object(id), kind, scope.value(part.amount))
            }
            is CostPart.PutCounters -> choose(scope, payer, part.on, Zone.Battlefield, "put counters on").forEach { id ->
                Actions.putCounters(act, cause, TargetRef.Object(id), part.kind, scope.value(part.amount))
            }
            is CostPart.Loyalty -> scope.self?.let { id ->
                val n = scope.value(part.amount)
                if (n > 0) Actions.putCounters(act, cause, TargetRef.Object(id), CounterKind.Loyalty, n)
                else Actions.removeCounters(act, TargetRef.Object(id), CounterKind.Loyalty, -n)
            }
            is CostPart.Mill -> Actions.mill(act, cause, payer, scope.value(part.count))
            is CostPart.OneOf -> {
                val payable = part.options.filter { canPay(scope, payer, it) }
                val chosen = act.chooseOption(payer, scope.self, payable.map { describe(it) })
                payable.firstOrNull { describe(it) == chosen }?.let { pay(scope, payer, it) }
            }
            is CostPart.Action -> Effects.perform(scope, arcanum.api.Effect.Action(part.action, part.bind))
        }
    }

    /** The mana [part] costs here: [CostPart.Mana.times] repeats it, [CostPart.Mana.x] is its X (CR 107.3); null when
     * an X is left unknown. */
    private fun manaCost(scope: Scope, part: CostPart.Mana): ManaCost? {
        val withX = part.x?.let { Mana.withX(part.cost, mapOf('X' to scope.value(it))) } ?: part.cost
        if (withX.symbols.any { it is ManaSymbol.Variable }) return null
        val times = part.times?.let { scope.value(it).coerceAtLeast(0) } ?: 1
        return ManaCost(List(times) { withX.symbols }.flatten())
    }

    /** Mana paid on resolution whose amount isn't known here: by hand. */
    private fun payMana(scope: Scope, payer: PlayerId, text: String) {
        scope.act.manual(payer, scope.self, "Pay $text")
    }

    private fun count(scope: Scope, what: CostObjects): Int = when (what) {
        CostObjects.Self -> 1
        is CostObjects.These -> scope.objects(what.objects).size
        is CostObjects.Choose -> what.count?.let { scope.value(it) } ?: 0
    }

    /** The objects [payer] could use for [what] in [zone]: their own permanents, or cards they own there. */
    private fun candidates(scope: Scope, payer: PlayerId, what: CostObjects, zone: Zone, extra: (ObjectId) -> Boolean = { true }): List<ObjectId> {
        val state = scope.act.state
        return when (what) {
            CostObjects.Self -> listOfNotNull(scope.self?.takeIf { state.obj(it)?.zone == zone })
            is CostObjects.These -> scope.objects(what.objects).filter { state.obj(it)?.zone == zone }
            is CostObjects.Choose -> {
                val e = scope.eval()
                val ids = when (zone) {
                    Zone.Battlefield -> state.battlefield.filter { e.controllerOf(it) == payer }
                    Zone.Exile, Zone.Stack, Zone.Command -> state.zone(zone, null).filter { state.obj(it)?.owner == payer }
                    else -> state.zone(zone, payer)
                }
                ids.filter { e.matches(what.filter, it) }
            }
        }.filter(extra)
    }

    private fun choose(scope: Scope, payer: PlayerId, what: CostObjects, zone: Zone, verb: String, extra: (ObjectId) -> Boolean = { true }): List<ObjectId> {
        val options = candidates(scope, payer, what, zone, extra)
        return when (what) {
            is CostObjects.Choose -> {
                val n = what.count?.let { scope.value(it) }
                scope.act.chooseObjects(payer, scope.self, options, n ?: 0, n, "Choose to $verb")
            }
            else -> options
        }
    }

    private fun randomFromHand(scope: Scope, payer: PlayerId, n: Int): List<ObjectId> {
        val hand = scope.act.state.player(payer).hand
        if (hand.size <= n) return hand
        return (scope.act.random(payer, scope.self, RandomKind.Objects(hand, n)) as Answer.RandomObjects).chosen
    }
}
