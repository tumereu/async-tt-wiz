package arcanum.engine.casting

import arcanum.engine.state.castOrNone
import arcanum.api.Ability
import arcanum.api.Color
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaPaymentWay
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.Step
import arcanum.api.Zone
import arcanum.engine.Pending
import arcanum.engine.Progress
import arcanum.engine.action.Act
import arcanum.engine.action.Actions
import arcanum.engine.action.Asker
import arcanum.engine.action.Cause
import arcanum.engine.action.NeedDecision
import arcanum.engine.action.PlannedMove
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Paying
import arcanum.engine.change.Change
import arcanum.engine.change.Changes
import arcanum.engine.change.GameEvent
import arcanum.engine.change.Meaning
import arcanum.engine.change.Recorder
import arcanum.engine.decision.Continuation
import arcanum.engine.decision.Decision
import arcanum.engine.decision.ManaUse
import arcanum.engine.mana.Mana
import arcanum.engine.state.Bindings
import arcanum.engine.state.EffectId
import arcanum.engine.state.EffectObject
import arcanum.engine.state.ManaSpent
import arcanum.engine.state.ManaUnit
import arcanum.engine.state.NamedValue
import arcanum.engine.state.ObjectId
import arcanum.engine.state.ObjectSnapshot
import arcanum.engine.state.PaidCost
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import arcanum.engine.view.AbilityOrigin

/**
 * Paying the total cost (CR 601.2g–h: mana abilities first, then every part, no partial payments) and finishing a
 * cast or activation (CR 601.2i, 602.2): the spell's cast facts (D22), its Stack item, the "becomes cast" event, the
 * effects that ride along (flashback's exile, mana riders), and priority back to the caster (CR 117.3c). A mana
 * ability adds its mana at once instead (CR 605.3b).
 *
 * The events of paying (objects sacrificed, discarded, exiled or returned, counters put) go through the event pipeline
 * ([Actions]), so replacement effects apply to them (CR 614: unearth's exile, madness); a replacement that needs a
 * decision stops the payment, which is redone from its start with the answers so far ([Paying], D29).
 */
internal object Payment {
    /** ecl: names of cost-kept cards the permanent the spell becomes remembers (CR 607.2a). */
    const val REMEMBERED = "remembered:"

    private fun refuse(why: String): Nothing = throw IllegalArgumentException(why)

    fun finish(recorder: Recorder, c: Continuation.Announcing, uses: List<ManaUse>, from: Int, answers: List<Answer> = emptyList()): Progress {
        val mark = recorder.changes.size
        val act = Act(recorder, Asker(answers, askerPrefix(recorder.state, "pay")))
        return try {
            pay(act, c, uses)
        } catch (n: NeedDecision) {
            recorder.rollback(mark)
            Progress.Waiting(Pending(n.decision, c.copy(choices = c.choices.copy(paying = Paying(uses, answers)),
                made = c.made + recorder.madeSince(from))))
        }
    }

    /** [nested]: a mana ability activated while another payment is made (CR 605.3a): it resolves at once and its asks go on [act]. */
    private fun pay(act: Act, c: Continuation.Announcing, uses: List<ManaUse>, nested: Boolean = false, triggered: List<Color> = emptyList()): Progress {
        val recorder = act.recorder
        val plan = Plan(recorder.state, c)
        val total = plan.total
        val bound = mutableMapOf<String, NamedValue>()
        val snapshots = mutableListOf<ObjectSnapshot>()
        var spent: List<ManaUnit> = emptyList()
        var paidWith: Map<ManaPaymentWay, List<ObjectId>> = emptyMap()
        // CR 702.132a: the assisting player pays their part of the generic mana first, with their own mana
        // (CR 106.6: spent to cast this spell, so mana restricted to such spells pays; CR 800.4f: not paid by a player who left)
        plan.choices.assist?.let { a ->
            if (a.player != null && plan.assistPaid > 0) payOnResolution(act, a.player, ManaCost(listOf(arcanum.api.ManaSymbol.Generic(plan.assistPaid))),
                a.uses ?: refuse("The assisting player hasn't paid"), spell = plan)
        }
        if (total.hasMana && plan.manaToPay.symbols.isEmpty() && uses.isNotEmpty()) refuse("Nothing left to pay with mana")
        if (total.hasMana && plan.manaToPay.symbols.isNotEmpty()) {
            val paid = payMana(act, plan, plan.manaToPay, uses)
            spent = paid.first
            paidWith = paid.second
        } else if (uses.isNotEmpty()) refuse("Nothing to pay with mana")
        total.parts.forEach { payPart(act, plan, it, bound, snapshots) }
        // tla: paying a waterbend cost is waterbending, however it was paid (CR 701.67c)
        if (total.waterbends) recorder.record(Change.Happened(GameEvent.KeywordActionDone(arcanum.api.KeywordActionKind.Waterbend, plan.player)))
        // blb: each forage paid (its parts' keys carry it, Costs.total) is a forage (CR 701.61a)
        total.parts.mapNotNull { kp -> kp.key.substringBefore(Costs.FORAGE, "").takeIf { it.isNotEmpty() } }.distinct().forEach {
            recorder.record(Change.Happened(GameEvent.KeywordActionDone(arcanum.api.KeywordActionKind.Forage, plan.player)))
        }
        // a keyword action paid as the cost part it is (CR 701.59a: collecting evidence)
        total.parts.mapNotNull { it.doneAs }.forEach { recorder.record(Change.Happened(GameEvent.KeywordActionDone(it, plan.player))) }
        val names = Bindings(bound)
        return when {
            plan.isManaAbility -> finishManaAbility(act, plan, names, nested, triggered)
            plan.isSpecialAction -> finishSpecialAction(act, plan, names)
            plan.isSpell -> finishSpell(recorder, plan, names, snapshots, spent, paidWith)
            else -> finishAbility(recorder, plan, names, snapshots)
        }
    }

    // ---- Mana (CR 601.2g–h, 605.3)

    private fun payMana(act: Act, plan: Plan, cost: ManaCost, uses: List<ManaUse>): Pair<List<ManaUnit>, Map<ManaPaymentWay, List<ObjectId>>> {
        val recorder = act.recorder
        val player = plan.player
        val poolBefore = recorder.state.player(player).manaPool
        if (plan.cantSpendMana && uses.any { it is ManaUse.Pool || it is ManaUse.Ability }) refuse("Mana can't be spent to cast this spell")
        val chosenPool = uses.filterIsInstance<ManaUse.Pool>().map { it.index }.distinct()
        if (chosenPool.any { it !in poolBefore.indices }) refuse("No such mana in the pool")
        val lifeFor = uses.filterIsInstance<ManaUse.Life>().map { it.symbol }.toSet()
        if (lifeFor.any { cost.symbols.getOrNull(it).let { s -> s !is arcanum.api.ManaSymbol.Phyrexian && s !is arcanum.api.ManaSymbol.PhyrexianHybrid } }) {
            refuse("Life pays only Phyrexian mana (CR 107.4f)")
        }
        // CR 601.2g: mana abilities are activated before costs are paid
        val funds = activateAll(act, player, uses, chosenPool, Mana.needs(cost, lifeFor))
        val poolNow = recorder.state.player(player).manaPool
        val specials = uses.filterIsInstance<ManaUse.Special>()
        // tla: CR 701.67b: tapping pays only the generic mana of the waterbend cost
        if (specials.filter { it.way == ManaPaymentWay.Waterbend }.sumOf { it.objects.size } > plan.total.waterbend)
            refuse("Tapping pays only the generic mana of the waterbend cost")
        val board = Plan(recorder.state, plan.c).snap
        val allowedSpecials = Mana.specialPayers(board, player, plan.manaWays)
        val specialPayers = specials.flatMap { use ->
            use.objects.map { id ->
                allowedSpecials.firstOrNull { (it.use as ManaUse.Special).way == use.way && (it.use as ManaUse.Special).objects == listOf(id) }
                    ?: refuse("$id can't pay that way")
            }
        }
        val payers = Mana.poolPayers(board, player, plan.itemId, funds, plan.manaAsAnyColor) + specialPayers
        if (Mana.leastMana(cost) > payers.size) refuse("That doesn't pay $cost")
        val used = Mana.assign(Mana.needs(cost, lifeFor), payers) ?: refuse("That doesn't pay $cost")
        // CR 107.4f, 119.4: 2 life for each Phyrexian symbol paid with life, only with that much life
        if (lifeFor.isNotEmpty()) {
            val life = 2 * lifeFor.size
            if (recorder.state.player(player).life < life) refuse("Not enough life to pay $life")
            Actions.payLife(act, Cause(player, null, key = "life:${recorder.state.nextTimestamp()}"), player, life)
            // CR 702.150a: compleated reads how many Phyrexian symbols of a spell were paid with life
            if (plan.isSpell) recorder.state.obj(plan.itemId)?.let { o ->
                arcanum.engine.change.Changes.cast(recorder.state, plan.itemId, o.castOrNone.copy(phyrexianPaidWithLife = lifeFor.size))?.let(recorder::record)
            }
        }
        val usedIndices = used.mapNotNull { (it.use as? ManaUse.Pool)?.index }.toSet()
        val spent = poolNow.filterIndexed { i, _ -> i in usedIndices }
        if (spent.isNotEmpty()) {
            recorder.record(Changes.manaPool(recorder.state, player, poolNow.filterIndexed { i, _ -> i !in usedIndices }, listOf(Meaning.ManaSpent)))
        }
        val byWay = used.mapNotNull { it.use as? ManaUse.Special }.groupBy({ it.way }, { it.objects.single() })
        byWay.forEach { (way, ids) ->
            when (way) {
                // CR 702.51a, 702.126a: tap them
                ManaPaymentWay.TapCreatures, ManaPaymentWay.TapArtifacts, ManaPaymentWay.TapCreaturesForAnyMana, ManaPaymentWay.Waterbend ->
                    ids.forEach { recorder.record(Changes.tap(recorder.state, it)) }
                // CR 702.66a: exile them from the graveyard
                ManaPaymentWay.ExileFromGraveyard -> Actions.move(act, costCause(plan), ids.map { PlannedMove(it, Zone.Exile) })
            }
        }
        return spent to byWay
    }

    /**
     * Pays mana [cost] for [player] while a spell or ability resolves (CR 118.12, 608.2g): the mana abilities and pool
     * mana [uses] names, life for Phyrexian symbols (CR 107.4f); refused unless it pays the whole cost (CR 118.3). The
     * mana pays for no spell, so restricted mana can't be spent; [spell]: the cast it is spent on instead (assist, CR 702.132a),
     * restrictions read against that spell, provisionally on the Stack (CR 106.6).
     */
    fun payOnResolution(act: Act, player: PlayerId, cost: ManaCost, uses: List<ManaUse>, spell: Plan? = null) {
        val recorder = act.recorder
        if (uses.any { it is ManaUse.Special }) refuse("Only mana and life pay a cost while resolving")
        val poolBefore = recorder.state.player(player).manaPool
        val chosenPool = uses.filterIsInstance<ManaUse.Pool>().map { it.index }.distinct()
        if (chosenPool.any { it !in poolBefore.indices }) refuse("No such mana in the pool")
        val lifeFor = uses.filterIsInstance<ManaUse.Life>().map { it.symbol }.toSet()
        if (lifeFor.any { cost.symbols.getOrNull(it).let { s -> s !is arcanum.api.ManaSymbol.Phyrexian && s !is arcanum.api.ManaSymbol.PhyrexianHybrid } }) {
            refuse("Life pays only Phyrexian mana (CR 107.4f)")
        }
        // CR 605.3a: every payment may use mana abilities, also one a spell or ability asks as it resolves
        val funds = activateAll(act, player, uses, chosenPool, Mana.needs(cost, lifeFor))
        val poolNow = recorder.state.player(player).manaPool
        val board = spell?.let { Plan(recorder.state, it.c).snap } ?: recorder.snapshot()
        val payers = Mana.poolPayers(board, player, spell?.itemId, funds)
        val used = Mana.assign(Mana.needs(cost, lifeFor), payers) ?: refuse("That doesn't pay $cost")
        if (lifeFor.isNotEmpty()) {
            val life = 2 * lifeFor.size
            if (recorder.state.player(player).life < life) refuse("Not enough life to pay $life")
            Actions.payLife(act, Cause(player, null, key = "life:${recorder.state.nextTimestamp()}"), player, life)
        }
        val usedIndices = used.mapNotNull { (it.use as? ManaUse.Pool)?.index }.toSet()
        if (usedIndices.isNotEmpty()) {
            recorder.record(Changes.manaPool(recorder.state, player, poolNow.filterIndexed { i, _ -> i !in usedIndices }, listOf(Meaning.ManaSpent)))
        }
    }

    /**
     * Activates the mana abilities of a payment (CR 601.2g, 605.3a), each once it can be: those paying life before those dealing
     * damage (damage isn't a cost, CR 118.3; life can be paid only while it is there, CR 119.4), what taps before what sacrifices
     * (a creature tapped for mana can then be sacrificed), and an ability with its own mana once the payment has that mana (a
     * Plains into a Signet, whichever order the player picked them). Returns the mana of the pool the payment may spend now
     * ([chosen], by index, and what the abilities added, less what their own costs spent); the rest of the pool is left alone,
     * and mana left over stays in the pool. [needs]: what the mana will pay in the end, so an own cost spends what isn't needed.
     */
    private fun activateAll(act: Act, player: PlayerId, uses: List<ManaUse>, chosen: List<Int>, needs: List<Mana.Need>): List<Int> {
        val recorder = act.recorder
        var funds = chosen
        val left = uses.filterIsInstance<ManaUse.Ability>().sortedBy { order(recorder.snapshot(), it) }.toMutableList()
        while (left.isNotEmpty()) {
            val ready = left.firstNotNullOfOrNull { use -> readied(recorder, player, use, funds)?.let { use to it } }
                ?: refuse("That doesn't pay: nothing pays the mana of ${left.first().source}'s ability (CR 601.2g)")
            left.remove(ready.first)
            funds = activateForMana(act, player, ready.first, ready.second, funds, left, needs)
        }
        return funds
    }

    private fun order(snap: Snapshot, use: ManaUse.Ability): Int {
        val ability = Legality.instance(snap, use.source, use.ability)?.ability as? Ability.Activated ?: return 1
        val parts = ability.cost.parts
        return when {
            parts.any { it is CostPart.Sacrifice || it is CostPart.Exile || it is CostPart.ReturnToHand || it is CostPart.Discard } -> 4
            parts.any { it is CostPart.Mana } -> 3
            !Mana.isSimple(ability) && ability.instructions.steps.any { (it as? Step.Do)?.effect.let { e -> e is Effect.DealDamage || e is Effect.LoseLife } } -> 2
            parts.any { it is CostPart.PayLife } -> 0
            else -> 1
        }
    }

    /** A mana ability's activation made ready: its choices answered from [ManaUse.Ability] (CR 602.2b), [own] the mana it asks. */
    private class Readied(val continuation: Continuation.Announcing?, val own: ManaCost?)

    /** [use] readied to be activated now, or null while [funds] can't pay its own mana. Refused when its choices aren't answered. */
    private fun readied(recorder: Recorder, player: PlayerId, use: ManaUse.Ability, funds: List<Int>): Readied? {
        val snap = recorder.snapshot()
        val inst = Legality.instance(snap, use.source, use.ability) ?: refuse("${use.source} has no ability ${use.ability.key}")
        val ability = inst.ability as? Ability.Activated ?: refuse("Not an activated ability")
        if (!Legality.isManaAbility(ability)) refuse("Only mana abilities can be activated while paying (CR 605.3a)")
        if (!Mana.canActivate(snap, player, use.source, inst)) refuse("${use.source} can't be activated for mana now")
        if (Legality.manaUsesLeft(snap, use.source, inst) <= 0) refuse("${use.source} can't be activated for mana again")
        if (Mana.isSimple(ability) && use.answers.isEmpty()) return Readied(null, null)
        val id = "m${recorder.state.nextTimestamp()}.${use.source}"
        var c = Continuation.Announcing(player, arcanum.engine.decision.Announced.Ability(use.source, use.ability, ability), arcanum.engine.decision.AnnounceStage.Modes,
            arcanum.engine.decision.Choices(item = id))
        val queue = use.answers.toMutableList()
        while (true) {
            val plan = Plan(recorder.state, c)
            val q = plan.next() ?: return Readied(c, null)
            plan.blocker(q)?.let { refuse(it) }
            if (q is Question.Pay) return if (Mana.assign(Mana.needs(q.cost), Mana.poolPayers(plan.snap, player, plan.itemId, funds, plan.manaAsAnyColor)) != null) Readied(c, q.cost) else null
            val answer = next(queue, q) ?: Answers.automatic(plan, q) ?: (q as? Question.ManaColor)?.let { _ -> use.color?.let { Answer.ColorChoice(it) } }
                ?: refuse("Say what ${use.source}'s mana ability asks: ${q.kind}")
            c = c.copy(choices = Answers.apply(plan, q, answer))
        }
    }

    /** The next of a mana ability's given answers, when it is what [q] asks. */
    private fun next(queue: MutableList<Answer>, q: Question): Answer? {
        val head = queue.firstOrNull() ?: return null
        val fits = when (q) {
            is Question.Objects -> head is Answer.Objects
            is Question.Announce -> head is Answer.Number
            is Question.ManaColor -> head is Answer.ColorChoice
            is Question.Modes -> head is Answer.Modes
            is Question.OneOf -> head is Answer.Option
            is Question.Player -> head is Answer.Player
            is Question.CostChoice -> head is Answer.SubtypeChoice || head is Answer.Objects
            else -> false
        }
        return if (fits) queue.removeAt(0) else null
    }

    /** Activates a mana ability while paying (CR 605.3a): pays its cost and adds its mana. Returns [funds] as they are after it. */
    private fun activateForMana(act: Act, player: PlayerId, use: ManaUse.Ability, ready: Readied, funds: List<Int>, later: List<ManaUse.Ability>, needs: List<Mana.Need>): List<Int> {
        val recorder = act.recorder
        val before = recorder.state.player(player).manaPool.size
        val c = ready.continuation
        if (c != null) {
            val plan = Plan(recorder.state, c)
            val spent = ready.own?.let { Mana.spendFor(plan.snap, player, it, plan.itemId, funds, later, needs) } ?: emptyList()
            pay(act, c, spent.map { ManaUse.Pool(it) }, nested = true, triggered = use.triggered)
            val kept = funds.filter { it !in spent }.map { i -> i - spent.count { it < i } }
            return kept + ((before - spent.size) until recorder.state.player(player).manaPool.size)
        }
        val snap = recorder.snapshot()
        val inst = Legality.instance(snap, use.source, use.ability) ?: refuse("${use.source} has no ability ${use.ability.key}")
        val ability = inst.ability as Ability.Activated
        val yields = Mana.yields(snap, player, use.source, ability)
        yields.filter { it.choice }.forEach { y -> if (use.color == null || use.color !in y.colors) refuse("Choose a colour it can make") }
        ability.cost.parts.forEach { part ->
            when (part) {
                CostPart.TapSelf -> recorder.record(Changes.tap(recorder.state, use.source, meanings = listOf(Meaning.ManaAdded)))
                CostPart.UntapSelf -> recorder.record(Changes.tap(recorder.state, use.source, tapped = false))
                is CostPart.Sacrifice -> Actions.move(act, Cause(player, use.source, key = "cost:${use.source}"),
                    listOf(PlannedMove(use.source, Zone.Graveyard, meanings = listOf(Meaning.Sacrificed))))
                is CostPart.PayLife -> Actions.payLife(act, Cause(player, use.source, key = "cost:${use.source}"), player,
                    snap.evaluator(player, self = use.source).value(part.amount))
                else -> refuse("Not a simple mana ability")
            }
        }
        addMana(recorder, player, yields.flatMap { Mana.units(it, use.color, use.source) })
        recorder.record(Change.Happened(GameEvent.Activated(player, use.source, inst.origin.key, manaAbility = true, label = ability.label)))
        countActivation(recorder, use.source, inst.origin, loyalty = false)
        arcanum.engine.trigger.Triggers.resolveManaTriggers(act, use.triggered)
        return funds + (before until recorder.state.player(player).manaPool.size)
    }

    private fun addMana(recorder: Recorder, player: PlayerId, units: List<ManaUnit>) {
        if (units.isEmpty()) return
        val pool = recorder.state.player(player).manaPool
        recorder.record(Changes.manaPool(recorder.state, player, pool + units, listOf(Meaning.ManaAdded)))
    }

    // ---- Other cost parts (CR 601.2h, 118)

    /** Who pays a cost (D19): the player, with the spell's or ability's source; no Stack item, so no own replacements. */
    private fun costCause(plan: Plan) = Cause(plan.player, plan.sourceId, key = "cost:${plan.itemId}")

    private fun payPart(act: Act, plan: Plan, kp: KeyedPart, bound: MutableMap<String, NamedValue>, snapshots: MutableList<ObjectSnapshot>) {
        val recorder = act.recorder
        val cause = costCause(plan)
        val player = plan.player
        val s = recorder.state
        val snap = recorder.snapshot()
        // hob: a permission's cost reads the spell as the affected object, as the plan does
        val eval = snap.evaluator(player, self = plan.sourceId, names = plan.names, stackItem = plan.item).let { e ->
            if (plan.isSpell) e.with(e.ctx.copy(affected = plan.itemId)) else e
        }
        fun objects(what: CostObjects): List<ObjectId> = when (what) {
            CostObjects.Self -> listOf(plan.sourceId)
            is CostObjects.These -> eval.objects(what.objects)
            is CostObjects.Choose -> (plan.choices.names[Plan.OBJECTS + kp.key] as? NamedValue.Objects)?.ids
                ?: refuse("Nothing chosen for ${Costs.describe(kp.part)}")
        }
        fun check(ids: List<ObjectId>, zone: Zone?) = ids.forEach { id ->
            val o = s.obj(id) ?: refuse("$id is gone")
            if (zone != null && o.zone != zone) refuse("$id isn't in $zone")
        }
        fun move(ids: List<ObjectId>, to: Zone, meanings: List<Meaning>, preBind: String?, postBind: String?): List<ObjectId> {
            ids.forEach { snap.snapshot(it)?.let { lki -> snapshots += lki } }
            // One event through the pipeline (CR 614): where each goes may be replaced (unearth's exile, madness); each
            // keeps what was seen of it as it left (CR 608.2h)
            val moved = Actions.move(act, cause, ids.map { PlannedMove(it, to, meanings = meanings) })
            preBind?.let { bound[it] = NamedValue.Objects(ids) }
            val after = ids.mapNotNull { moved[it] }
            postBind?.let { bound[it] = NamedValue.Objects(after) }
            return after
        }
        when (val part = kp.part) {
            // tla: a waterbend cost is paid with the mana (Costs.total)
            is CostPart.Waterbend -> Unit
            // CR 302.6, 602.5a: checked when activating; {T} taps the source
            CostPart.TapSelf -> {
                if (s.obj(plan.sourceId)?.tapped != false) refuse("It's already tapped")
                // Tapped for mana (CR 106.12): what "whenever … is tapped for mana" looks for
                recorder.record(Changes.tap(s, plan.sourceId, meanings = if (plan.isManaAbility) listOf(Meaning.ManaAdded) else emptyList()))
            }
            CostPart.UntapSelf -> {
                if (s.obj(plan.sourceId)?.tapped != true) refuse("It isn't tapped")
                recorder.record(Changes.tap(s, plan.sourceId, tapped = false))
            }
            is CostPart.TapObjects -> {
                val ids = objects(part.what)
                check(ids, Zone.Battlefield)
                // msh: tapped to pay a keyword's cost ("becomes tapped to pay a teamwork cost", CR 702.194a)
                val paidFor = plan.costKeyword(kp.key)?.let { listOf(Meaning.PaidFor(it)) }.orEmpty() +
                    // dft: tapped for a Vehicle's crew or a Mount's saddle ability: it crewed or saddled that permanent (CR 702.122b, 702.171c)
                    listOfNotNull(plan.keyword()?.takeIf { it is Keyword.Crew || it is Keyword.Saddle }?.let { Meaning.PaidFor(it.kind, source = plan.sourceId) }) +
                    // tmt: tapped for a Vehicle's crew ability: they crewed it (CR 702.122b)
                    listOfNotNull(Meaning.Crewed(plan.sourceId).takeIf { plan.keyword() is Keyword.Crew })
                ids.forEach { if (recorder.state.obj(it)?.tapped != false) refuse("$it is tapped"); recorder.record(Changes.tap(recorder.state, it, meanings = paidFor)) }
                part.bind?.let { bound[it] = NamedValue.Objects(ids) }
            }
            is CostPart.Sacrifice -> {
                val ids = objects(part.what)
                check(ids, Zone.Battlefield)
                move(ids, Zone.Graveyard, listOf(Meaning.Sacrificed), part.preBind, part.postBind)
            }
            is CostPart.Discard -> {
                if (part.random) refuse("Random discards as a cost aren't supported yet")
                val ids = objects(part.what)
                check(ids, Zone.Hand)
                // CR 702.29c: discarding the card to pay a cycling (or typecycling) cost is cycling it
                val cycled = part.what == CostObjects.Self && plan.keyword().let { it is Keyword.Cycling || it is Keyword.Typecycling }
                // dft: the X of a cycling {X} cost, for "When you cycle this card, … X …" (CR 107.3a)
                val costX = plan.item.announced["X"]?.takeIf { cycled }?.let { Meaning.CostX(it) }
                move(ids, Zone.Graveyard, listOf(Meaning.Discarded) + listOfNotNull(Meaning.Cycled.takeIf { cycled }, costX), part.preBind, part.postBind)
            }
            is CostPart.Exile -> {
                val ids = objects(part.what)
                check(ids, part.from)
                val after = move(ids, Zone.Exile, emptyList(), part.preBind, part.postBind)
                part.remember?.let {
                    bound[it] = NamedValue.Objects(after)
                    // ecl: kept by the permanent the spell becomes (CR 607.2a, 400.7d)
                    bound[REMEMBERED + it] = NamedValue.Objects(after)
                }
            }
            is CostPart.ReturnToHand -> {
                val ids = objects(part.what)
                check(ids, Zone.Battlefield)
                move(ids, Zone.Hand, listOf(Meaning.ReturnedToHand), part.preBind, part.postBind)
            }
            is CostPart.Reveal -> {
                val ids = objects(part.what)
                recorder.record(Change.Happened(GameEvent.Revealed(player, ids)))
                part.bind?.let { bound[it] = NamedValue.Objects(ids) }
            }
            is CostPart.PayLife -> {
                // CR 119.4: a player can pay life only up to their life total
                val n = eval.value(part.amount)
                if (s.player(player).life < n) refuse("Not enough life to pay $n")
                if (n > 0) Actions.payLife(act, cause, player, n)
            }
            is CostPart.PayEnergy -> {
                val n = eval.value(part.amount)
                if (s.player(player).counters(CounterKind.Energy) < n) refuse("Not enough energy")
                if (n > 0) recorder.record(Changes.counters(s, TargetRef.Player(player), CounterKind.Energy, -n, listOf(Meaning.Paid)))
            }
            is CostPart.RemoveCounters -> {
                val n = eval.value(part.amount)
                // gtc: "remove N counters from among …": taken from the chosen objects in the order chosen
                if (part.distributed) {
                    var left = n
                    objects(part.from).forEach { id ->
                        val o = recorder.state.obj(id) ?: refuse("$id is gone")
                        val kind = part.kind ?: o.counters.firstOrNull()?.kind ?: return@forEach
                        val taken = minOf(left, o.counters(kind))
                        if (taken > 0) recorder.record(Changes.counters(recorder.state, TargetRef.Object(id), kind, -taken, listOf(Meaning.Paid)))
                        left -= taken
                    }
                    if (left > 0) refuse("Not enough counters among them")
                    return
                }
                objects(part.from).forEach { id ->
                    val o = recorder.state.obj(id) ?: refuse("$id is gone")
                    val kind = part.kind ?: o.counters.firstOrNull()?.kind ?: refuse("No counters on $id")
                    if (o.counters(kind) < n) refuse("Not enough counters on $id")
                    recorder.record(Changes.counters(recorder.state, TargetRef.Object(id), kind, -n, listOf(Meaning.Paid)))
                }
            }
            // msh: "Get five poison counters" (ward, CR 122.1)
            is CostPart.GetCounters -> Actions.putCounters(act, cause, TargetRef.Player(player), part.kind, eval.value(part.amount), listOf(Meaning.Paid))
            is CostPart.PutCounters -> {
                val n = eval.value(part.amount)
                // CR 122.6, 614.1a: counters put as a cost are put on it: replacements that change counters being put apply
                objects(part.on).forEach { id -> Actions.putCounters(act, cause, TargetRef.Object(id), part.kind, n, listOf(Meaning.Paid)) }
            }
            is CostPart.Loyalty -> {
                // CR 606.4, 606.6: put or remove loyalty counters; a negative cost needs that many
                // mh2: "cost an additional [+1]" (CR 606.4, 601.2f)
                val n = eval.value(part.amount) + plan.snap.view.players[player]?.mechanics.orEmpty()
                    .sumOf { (it.mechanic as? arcanum.api.Mechanic.AdditionalLoyaltyCost)?.amount ?: 0 }
                val o = s.obj(plan.sourceId) ?: refuse("It's gone")
                if (n < 0 && o.counters(CounterKind.Loyalty) < -n) refuse("Not enough loyalty")
                if (n > 0) Actions.putCounters(act, cause, TargetRef.Object(plan.sourceId), CounterKind.Loyalty, n, listOf(Meaning.Paid))
                else if (n < 0) recorder.record(Changes.counters(s, TargetRef.Object(plan.sourceId), CounterKind.Loyalty, n, listOf(Meaning.Paid)))
            }
            is CostPart.Mill -> {
                val n = eval.value(part.count)
                val ids = s.player(player).library.take(n)
                if (ids.size < n) refuse("Not enough cards to mill")
                move(ids, Zone.Graveyard, listOf(Meaning.Milled), null, null)
            }
            // Reminder text the definition can't express: left to the players (P4)
            is CostPart.Manual -> Unit
            // blb: the chosen player is recorded with the spell's facts as it becomes cast (CR 702.174a)
            is CostPart.ChoosePlayer -> Unit
            // ecl: CR 701.68a: N -1/-1 counters on the chosen creature you control; it is "the blighted creature" (701.68c)
            is CostPart.Action if part.action is arcanum.api.KeywordAction.Blight -> {
                val ids = objects(CostObjects.Choose(ObjectFilter.Any, count = arcanum.api.Value.Fixed(1)))
                check(ids, Zone.Battlefield)
                val n = eval.value((part.action as arcanum.api.KeywordAction.Blight).count)
                ids.forEach { id -> Actions.putCounters(act, cause, TargetRef.Object(id), CounterKind.PowerToughness(-1, -1), n, listOf(Meaning.Paid)) }
                part.bind?.let { bound[it] = NamedValue.Objects(ids) }
                recorder.record(Change.Happened(GameEvent.KeywordActionDone(arcanum.api.KeywordActionKind.Blight, player, ids)))
            }
            // ecl: a choice made with the costs ("choose a creature type and behold two creatures of that type"): asked by the plan
            is CostPart.Choose -> Unit
            // CR 701.43a, b: exerting the source (or what the cost names) is always possible on the battlefield; another keyword
            // action as a cost is done as the action, its source as "this"
            is CostPart.Action if part.action !is arcanum.api.KeywordAction.Behold -> {
                val scope = arcanum.engine.resolution.Scope(act, cause, plan.names, null)
                arcanum.engine.resolution.Effects.perform(scope, arcanum.api.Effect.Action(part.action, part.bind))
                part.bind?.let { b -> scope.names[b]?.let { bound[b] = it } }
            }
            is CostPart.Action -> {
                // CR 701.4a: a card from the hand is revealed; a permanent is only chosen
                val ids = objects(CostObjects.Choose(ObjectFilter.Any, count = arcanum.api.Value.Fixed(1)))
                val revealed = ids.filter { s.obj(it)?.zone == Zone.Hand }
                if (revealed.isNotEmpty()) recorder.record(Change.Happened(GameEvent.Revealed(player, revealed)))
                part.bind?.let { bound[it] = NamedValue.Objects(ids) }
                recorder.record(Change.Happened(GameEvent.KeywordActionDone(arcanum.api.KeywordActionKind.Behold, player, ids)))
            }
            is CostPart.OneOf -> refuse("Choose which cost to pay first")
            is CostPart.Mana, CostPart.ItsManaCost, is CostPart.ItsManaCostLess -> Unit
        }
    }

    // ---- Finishing

    private fun finishSpell(
        recorder: Recorder, plan: Plan, names: Bindings, snapshots: List<ObjectSnapshot>, spent: List<ManaUnit>,
        paidWith: Map<ManaPaymentWay, List<ObjectId>>,
    ): Progress {
        val s = recorder.state
        val id = plan.itemId
        val o = s.objOrThrow(id)
        val facts = o.castOrNone.copy(
            castFrom = plan.castFrom,
            x = plan.choices.announced["X"],
            manaSpent = (Color.all + listOf<Color?>(null)).mapNotNull { c ->
                spent.count { it.color == c }.takeIf { it > 0 }?.let { ManaSpent(c, it) }
            },
            manaSources = spent.mapNotNull { it.source },
            paid = paidCosts(plan),
            alternative = alternativePaid(plan),
            modesChosen = (o.castOrNone.modesChosen + plan.choices.modes).distinct(),
            paidWith = Bindings(paidWith.map { (way, ids) -> way.toString() to NamedValue.Objects(ids) as NamedValue }.toMap()),
            // blb: the opponent chosen for a gift (CR 702.174a)
            chosenPlayer = plan.total.parts.firstNotNullOfOrNull { kp ->
                (plan.choices.names[Plan.PLAYER + kp.key] as? NamedValue.Players)?.ids?.firstOrNull()?.takeIf { kp.part is CostPart.ChoosePlayer }
            },
        )
        arcanum.engine.change.Changes.cast(s, id, facts)?.let(recorder::record)
        val item = plan.item.copy(names = plan.item.names.with(names), snapshots = plan.item.snapshots + snapshots,
            divided = plan.item.divided + plan.wholeShares(), timestamp = s.nextTimestamp())
        // CR 702.47a: the spliced cards are revealed
        if (plan.spliced.isNotEmpty()) recorder.record(Change.Happened(GameEvent.Revealed(plan.player, plan.spliced.map { it.card })))
        // CR 601.2i: the spell becomes cast
        recorder.record(Change.StackItemAdded(item, null, listOf(Meaning.Cast)))
        NextSpells.useUp(recorder, id)
        arcanum.engine.prepare.Prepare.onCast(recorder, id) // fra: CR 722.3c
        spentOnSpells(recorder, plan.player, spent.size)
        plan.permission?.first?.let { permission ->
            permission.then?.let { then -> recorder.record(Changes.effect(recorder.state) { t -> leavesStackTo(id, plan.player, then, permission.thenInsteadOf, t, s.turn.number) }) }
            if (permission.perTurn != null) countPermission(recorder, plan)
            permission.total?.let { total -> usePermission(recorder, plan, total) }
            // blb: what the permission gives a spell cast with it ("that creature enters with a finality counter", CR 400.7d)
            if (permission.spellGets.isNotEmpty()) recorder.record(Changes.effect(recorder.state) { t ->
                val next = (id as? ObjectId.Card)?.next()
                EffectObject(EffectId("permission:$id"), permission.spellGets, affects = null, locked = listOfNotNull(id, next), source = null,
                    controller = plan.player, timestamp = t, createdTurn = s.turn.number, duration = Duration.Indefinitely,
                    names = Bindings(mapOf(arcanum.engine.resolution.Effects.FOLLOWS_SPELL to NamedValue.Objects(listOfNotNull(id, next)))))
            })
        }
        // eoe: cast for a warp cost another object gave the card in hand (Tannuk, CR 702.185a): the spell keeps warp, so the permanent it
        // becomes is exiled at the beginning of the next end step and may be cast later
        plan.alternative?.let { (inst, m) ->
            if (m.keyword != Keyword.Warp.Kind) return@let
            if (recorder.snapshot().view[id]?.characteristics?.hasKeyword(Keyword.Warp.Kind) == true) return@let
            recorder.record(Changes.effect(recorder.state) { t ->
                val next = (id as? ObjectId.Card)?.next()
                EffectObject(EffectId("warp:$id"), listOf(Mechanic.GainAbilities(listOf(Keyword.Warp(m.cost)))),
                    affects = arcanum.api.Affected.Objects(Objects.Self), locked = listOfNotNull(id, next), source = id,
                    controller = plan.player, timestamp = t, createdTurn = s.turn.number, duration = Duration.Indefinitely)
            })
        }
        // dsk gaps: a granted alternative cost with a limit each turn is used once more
        plan.alternative?.let { (inst, m) ->
            val origin = inst.origin as? AbilityOrigin.FromAbility ?: return@let
            if (m.perTurn == null) return@let
            val source = recorder.state.obj(origin.source) ?: return@let
            recorder.record(arcanum.engine.change.Changes.uses(recorder.state, source.id, source.uses.copy(
                activations = Legality.counted(source.uses.activations, Legality.alternativeKey(origin), recorder.state.turn.number))))
        }
        // CR 106.6a: mana that gives the spell something
        spent.filter { it.spellGets.isNotEmpty() }
            .filter { unit -> unit.spellGetsIf?.let { recorder.snapshot().evaluator(plan.player, self = unit.source).matches(it, id) } ?: true }
            .forEachIndexed { i, unit ->
            recorder.record(Changes.effect(recorder.state) { t ->
                // the permanent a creature spell becomes keeps what the mana gave it (CR 106.6a, "it gains haste")
                EffectObject(EffectId("mana:$id:$i"), unit.spellGets, affects = null, locked = listOfNotNull(id, (id as? ObjectId.Card)?.next()), source = unit.source,
                    controller = plan.player, timestamp = t, createdTurn = s.turn.number, duration = unit.spellGetsUntil ?: Duration.Indefinitely)
            })
        }
        // CR 106.6, 603.7a: mana that creates a delayed trigger "when that mana is spent": one trigger for each such mana, with the
        // spell as its event object (an ability on the Stack above the spell, CR 603.3)
        spent.filter { it.whenSpent != null }.map { unit ->
            arcanum.engine.decision.WaitingTrigger(plan.player, Ability.Triggered(arcanum.api.Trigger.On(emptyList()), unit.whenSpent!!), null, unit.source,
                event = arcanum.engine.state.TriggerEvent(objects = listOf(arcanum.engine.state.RoleObjects(arcanum.api.EventRole.Object, listOf(id)))))
        }.let { arcanum.engine.trigger.Triggers.addWaiting(recorder, it) }
        // CR 608.2g: no player receives priority after a spell cast while another resolves
        if (!plan.byEffect) Casting.keepPriority(recorder, plan.player)
        return Progress.Done
    }

    /** CR 700.14: the mana [player] spent to cast spells this turn, counted in the engine's bookkeeping (the previous turn's
     * count replaced), and the [GameEvent.SpentOnSpells] event "whenever you expend N" triggers on. */
    private fun spentOnSpells(recorder: Recorder, player: PlayerId, amount: Int) {
        if (amount <= 0) return
        val s = recorder.state
        val counted = s.spent[player]
        val before = counted?.takeIf { it.turn == s.turn.number }?.amount ?: 0
        recorder.record(Change.SpentCounted(player, counted, arcanum.engine.state.SpentOnSpells(s.turn.number, before + amount)))
        recorder.record(Change.Happened(GameEvent.SpentOnSpells(player, before, before + amount)))
    }


    /** "If it would leave the Stack, put it [then] instead" (flashback, CR 702.34a): an effect object on the spell. */
    private fun leavesStackTo(spell: ObjectId, player: PlayerId, then: arcanum.api.Destination, insteadOf: Zone?, timestamp: Long, turn: Int) =
        EffectObject(
            id = EffectId("cast:$spell"),
            mechanics = listOf(Mechanic.Replacement(
                EventPattern.ZoneChange(ObjectFilter.Is(Objects.Bound(SPELL)), from = Zone.Stack, to = insteadOf),
                modify = listOf(Modification.ChangeDestination(then)),
            )),
            affects = null, source = spell, controller = player, timestamp = timestamp, createdTurn = turn,
            duration = Duration.AsLongAs(Condition.ObjectMatches(Objects.Bound(SPELL), ObjectFilter.InZone(Zone.Stack))),
            names = Bindings.EMPTY.with(SPELL, NamedValue.Objects(listOf(spell))),
        )

    /** Optional and additional costs paid, with how often (CR 702.33, D22, D44). */
    private fun paidCosts(plan: Plan): List<PaidCost> = plan.additionalCosts.mapNotNull { (inst, m) ->
        val times = when {
            m.optional -> plan.optionalTimes(inst) ?: 0
            m.times != null -> plan.eval.value(m.times!!)
            else -> 1
        }
        if (times <= 0) null
        else PaidCost(m.keyword, times, costIndex = if (m.keyword == Keyword.Kicker.Kind) (inst.origin as? AbilityOrigin.Expansion)?.index else null)
    }

    /** The alternative cost the spell was cast for (CR 118.9): its keyword, or [PaidCost.WITHOUT_KEYWORD]. */
    private fun alternativePaid(plan: Plan): PaidCost? {
        (plan.choices.names[Casting.FACE_DOWN] as? NamedValue.Option)?.let { return PaidCost(Casting.keywordKind(it.option), 1) }
        plan.alternative?.let { (_, m) -> return PaidCost(m.keyword, 1) }
        val (permission, origin) = plan.permission ?: return null
        if (permission.cost == null && !permission.withoutPaying) return null
        // A permission from a keyword (flashback, escape) names that keyword
        val root = (origin as? AbilityOrigin.Expansion)?.root
        val keyword = root?.let { r -> recorderKeyword(plan, r) }
        return PaidCost(keyword, 1)
    }

    private fun recorderKeyword(plan: Plan, root: AbilityOrigin) =
        plan.snap.view[plan.itemId]?.characteristics?.abilities?.firstOrNull { it.origin == root }?.let { (it.ability as? Keyword)?.kind }

    /** mom: a spell cast with a permission limited to [total] spells in all uses one up; the effect that grants it keeps the count (and its
     * other mechanics). */
    private fun usePermission(recorder: Recorder, plan: Plan, total: Int) {
        val effect = plan.way.permission?.effect?.let { recorder.state.effects[it] } ?: return
        recorder.record(Change.EffectUpdated(effect, effect.copy(usesLeft = (effect.usesLeft ?: total) - 1)))
    }

    private fun countPermission(recorder: Recorder, plan: Plan) {
        val p = plan.way.permission ?: return
        val source = p.source?.let { recorder.state.obj(it)?.id } ?: return
        val key = Legality.PERMISSION_PREFIX + (p.ability?.key ?: p.effect?.value ?: "")
        val o = recorder.state.objOrThrow(source)
        recorder.record(arcanum.engine.change.Changes.uses(recorder.state, source, o.uses.copy(activations = Legality.counted(o.uses.activations, key, recorder.state.turn.number))))
    }

    private fun finishAbility(recorder: Recorder, plan: Plan, names: Bindings, snapshots: List<ObjectSnapshot>): Progress {
        val s = recorder.state
        val origin = plan.origin!!
        // fra: a loyalty ability's cost, for "if you removed two or more loyalty counters to activate it"
        val loyaltyCost = (plan.ability as? Ability.Activated)?.cost?.parts.orEmpty().filterIsInstance<CostPart.Loyalty>().firstOrNull()?.let { part ->
            plan.snap.evaluator(plan.player, self = plan.sourceId, names = plan.names.with(names), stackItem = plan.item).value(part.amount)
        }
        recorder.record(Change.Happened(GameEvent.Activated(plan.player, plan.sourceId, origin.key, manaAbility = false, loyalty = loyaltyCost,
            keyword = plan.keyword()?.kind?.id, item = plan.item.id, label = (plan.ability as? Ability.Activated)?.label)))
        val item = plan.item.copy(names = plan.item.names.with(names), snapshots = plan.item.snapshots + snapshots,
            divided = plan.item.divided + plan.wholeShares(), timestamp = s.nextTimestamp())
        recorder.record(Change.StackItemAdded(item, s.stack.size, listOf(Meaning.Activated)))
        val loyalty = (plan.ability as? Ability.Activated)?.cost?.parts.orEmpty().any { it is CostPart.Loyalty }
        countActivation(recorder, plan.sourceId, origin, loyalty)
        Casting.keepPriority(recorder, plan.player)
        return Progress.Done
    }

    /** Counts a use of [origin] on [source] this turn (CR 602.5b, 606.3, D41), when the source is still there. */
    private fun countActivation(recorder: Recorder, source: ObjectId, origin: AbilityOrigin, loyalty: Boolean) {
        val o = recorder.state.obj(source) ?: return
        val turn = recorder.state.turn.number
        var counts = Legality.counted(o.uses.activations, origin.key, turn)
        if (loyalty) counts = Legality.counted(counts, Legality.LOYALTY_KEY, turn)
        recorder.record(arcanum.engine.change.Changes.uses(recorder.state, source, o.uses.copy(activations = counts)))
    }

    /**
     * CR 605.3b: a mana ability resolves at once. What else it does ("Each opponent gains 1 life", "you may …", mana added only
     * if something holds) happens right after its mana is added, as resolution steps would (CR 608.2c); what that asks stops the
     * activation, which is redone with the answers so far (D29).
     */
    private fun finishManaAbility(outer: Act, plan: Plan, bound: Bindings, nested: Boolean, triggered: List<Color>): Progress {
        val recorder = outer.recorder
        val ability = plan.ability as Ability.Activated
        val snap = recorder.snapshot()
        ability.instructions.steps.forEachIndexed { i, step ->
            val add = (step as? Step.Do)?.effect as? Effect.AddMana ?: return@forEachIndexed
            val colors = (plan.choices.names[Plan.COLOR + i] as? NamedValue.Colors)?.colors.orEmpty()
            val one = ability.copy(instructions = ability.instructions.copy(steps = listOf(Mana.withChosenColors(ability, step))))
            // CR 608.2h: what a sacrificed or discarded object could produce is read as it last existed, before paying
            val yields = if (add.mana.kind is arcanum.api.ManaKind.AsCouldProduce) Mana.yields(plan.snap, plan.player, plan.sourceId, one, plan.manaNames())
                else Mana.yields(snap, plan.player, plan.sourceId, one, plan.names)
            val combination = (add.mana.kind as? arcanum.api.ManaKind.Choose)?.combination == true
            // In any combination of colours: one mana of each colour chosen
            val units = if (combination && colors.size > 1) yields.flatMap { y -> colors.take(y.amount).flatMap { Mana.units(y.copy(amount = 1), it, plan.sourceId) } }
                else yields.flatMap { Mana.units(it, colors.firstOrNull(), plan.sourceId) }
            addMana(recorder, plan.player, units)
        }
        recorder.record(Change.Happened(GameEvent.Activated(plan.player, plan.sourceId, plan.origin!!.key, manaAbility = true,
            label = (plan.ability as? Ability.Activated)?.label)))
        countActivation(recorder, plan.sourceId, plan.origin, loyalty = false)
        arcanum.engine.trigger.Triggers.resolveManaTriggers(outer, triggered)
        if (!nested) Casting.keepPriority(recorder, plan.player)
        // ecl: "Add one mana of any color. This creature becomes that color": the colour chosen for the mana is what its
        // choice step binds, so that step isn't asked again (CR 605.3b: the ability resolves as a whole)
        var names = plan.names.with(bound)
        val answered = mutableSetOf<String>()
        ability.instructions.steps.forEachIndexed { i, step ->
            val add = (step as? Step.Do)?.effect as? Effect.AddMana ?: return@forEachIndexed
            val colors = (plan.choices.names[Plan.COLOR + i] as? NamedValue.Colors)?.colors.orEmpty()
            if (colors.isEmpty()) return@forEachIndexed
            (add.mana.kind as? arcanum.api.ManaKind.Choose)?.colors.orEmpty().filterIsInstance<arcanum.api.ColorRef.Chosen>().forEach { ref ->
                names = names.with(ref.name, NamedValue.Colors(colors))
                answered += ref.name
            }
        }
        val rest = ability.instructions.steps.filterNot {
            (it is Step.Do && it.effect is Effect.AddMana) || (it is Step.Choose && it.choice is arcanum.api.Choice.Color && it.bind in answered)
        }
        if (rest.isEmpty()) return Progress.Done
        // What it asks is asked of its player (inside a payment, the payment's), the activation (or the payment) redone with the answer (D29)
        val cause = arcanum.engine.action.Cause(plan.player, plan.sourceId, key = "mana:${plan.sourceId}:${recorder.state.nextTimestamp()}")
        arcanum.engine.resolution.Steps.run(arcanum.engine.resolution.Scope(outer, cause, names, null), rest)
        return Progress.Done
    }

    /**
     * CR 116.1, 116.2: a special action doesn't use the Stack: its steps run at once through the step runner, as a resolution's do, and
     * what they ask stops the payment like anything else asked while paying ([Paying]). "This" is the object it was taken from, also
     * after a step has moved it ("It becomes a plotted card", CR 702.170a). CR 116.3: its player has priority afterward.
     */
    private fun finishSpecialAction(act: Act, plan: Plan, bound: Bindings): Progress {
        val recorder = act.recorder
        val action = plan.ability as Ability.SpecialAction
        countActivation(recorder, plan.sourceId, plan.origin!!, loyalty = false)
        var self = plan.sourceId
        var names = plan.names.with(bound)
        action.instructions.steps.forEach { step ->
            val scope = arcanum.engine.resolution.Scope(act, costCause(plan).copy(source = self), names, null)
            arcanum.engine.resolution.Steps.run(scope, step)
            names = scope.names
            if (recorder.state.obj(self) == null) self = (self as? ObjectId.Card)?.next() ?: self
        }
        Casting.keepPriority(recorder, plan.player)
        return Progress.Done
    }

    /** The name the flashback-like replacement uses for the spell. */
    const val SPELL = "spell"
}
