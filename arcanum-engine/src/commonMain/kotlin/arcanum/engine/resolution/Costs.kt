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

    /**
     * Whether [payer] being able to pay [cost] depends on cards only they may see (I-D18): a part that chooses cards in a
     * hidden zone by what they are ("discard a land card") could go either way, and every part that can't be paid is one
     * of those. Unknown cards it looks at are filled in for [payer] first (D60).
     */
    fun hiddenDecides(scope: Scope, payer: PlayerId, cost: Cost): Boolean {
        val deciding = cost.parts.filter { hiddenDecides(scope, payer, it) }
        return deciding.isNotEmpty() && cost.parts.all { it in deciding || canPay(scope, payer, it) }
    }

    private fun hiddenDecides(scope: Scope, payer: PlayerId, part: CostPart): Boolean = when (part) {
        is CostPart.Discard -> !part.random && poolDecides(scope, payer, part.what, Zone.Hand)
        is CostPart.Exile -> poolDecides(scope, payer, part.what, part.from)
        is CostPart.Reveal -> poolDecides(scope, payer, part.what, Zone.Hand)
        // Each option is looked at, so every unknown card is filled in
        is CostPart.OneOf -> part.options.map { hiddenDecides(scope, payer, it) }.any { it }
        else -> false
    }

    private fun poolDecides(scope: Scope, payer: PlayerId, what: CostObjects, zone: Zone): Boolean {
        val choose = what as? CostObjects.Choose ?: return false
        val e = scope.eval()
        return scope.act.hiddenDecides(payer, scope.self, pool(scope, payer, zone)) { e.identityDecides(choose.filter, it) }
    }

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
            is CostPart.PutCounters, is CostPart.GetCounters -> true
            is CostPart.Loyalty -> scope.value(part.amount) >= 0 ||
                (scope.self?.let { state.obj(it)?.counters(CounterKind.Loyalty) } ?: 0) >= -scope.value(part.amount)
            is CostPart.Mill -> state.player(payer).library.size >= scope.value(part.count)
            is CostPart.OneOf -> part.options.any { canPay(scope, payer, it) }
            // blb: forage can be paid when one of its two ways can (CR 701.61a)
            is CostPart.Action -> when (val action = part.action) {
                arcanum.api.KeywordAction.Forage -> canPay(scope, payer, arcanum.engine.casting.Costs.forage)
                // fra: something to behold (CR 701.4a)
                is arcanum.api.KeywordAction.Behold -> Effects.beholdable(scope, payer, action.filter).size >= scope.value(action.count)
                // ecl: CR 701.68b: blighting needs a creature to put the counters on
                is arcanum.api.KeywordAction.Blight -> Effects.blightable(scope, payer).isNotEmpty()
                else -> true
            }
            is CostPart.ChoosePlayer -> state.players.any { scope.eval().matches(part.filter, it.id) }
            // ecl: a choice made as the cost is paid can always be made
            is CostPart.Choose -> true
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
            // msh: "Get five poison counters" (ward, CR 122.1)
            is CostPart.GetCounters -> Actions.putCounters(act, cause, TargetRef.Player(payer), part.kind, scope.value(part.amount))
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
            // Only a casting cost (gift, CR 702.174a)
            is CostPart.ChoosePlayer -> Unit
            // ecl: a choice made while paying (as a step of the same name)
            is CostPart.Choose -> Steps.run(scope, listOf(arcanum.api.Step.Choose(part.choice, part.bind, chooser = arcanum.api.Players.You)))
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
                pool(scope, payer, zone).filter { e.matches(what.filter, it) }
            }
        }.filter(extra)
    }

    /** What [payer] may choose from for a cost in [zone]: their own permanents, or cards they own there. */
    private fun pool(scope: Scope, payer: PlayerId, zone: Zone): List<ObjectId> {
        val state = scope.act.state
        return when (zone) {
            Zone.Battlefield -> state.battlefield.filter { scope.eval().controllerOf(it) == payer }
            Zone.Exile, Zone.Stack, Zone.Command -> state.zone(zone, null).filter { state.obj(it)?.owner == payer }
            else -> state.zone(zone, payer)
        }
    }

    private fun choose(scope: Scope, payer: PlayerId, what: CostObjects, zone: Zone, verb: String, extra: (ObjectId) -> Boolean = { true }): List<ObjectId> {
        // I-D18: a forced choice of cards only the payer sees ("discard a land card" with one land card in hand)
        val hidden = zone != Zone.Battlefield && poolDecides(scope, payer, what, zone)
        val options = candidates(scope, payer, what, zone, extra)
        return when (what) {
            is CostObjects.Choose -> {
                val n = what.count?.let { scope.value(it) }
                scope.act.chooseObjects(payer, scope.self, options, n ?: 0, n, "Choose to $verb", hidden)
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
