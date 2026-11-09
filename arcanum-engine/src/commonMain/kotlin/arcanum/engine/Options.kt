package arcanum.engine

import arcanum.api.Ability
import arcanum.api.Subtype
import arcanum.api.Zone
import arcanum.engine.casting.Casting
import arcanum.engine.casting.Legality
import arcanum.engine.casting.Plan
import arcanum.engine.casting.Question
import arcanum.engine.casting.Snapshot
import arcanum.engine.change.Recorder
import arcanum.engine.combat.Banding
import arcanum.engine.combat.Combat
import arcanum.engine.combat.CombatRules
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Continuation
import arcanum.engine.decision.Decision
import arcanum.engine.decision.Share
import arcanum.engine.input.Attack
import arcanum.engine.input.Block
import arcanum.engine.input.CastWay
import arcanum.engine.input.Input
import arcanum.engine.mana.Mana
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.setup.started
import arcanum.engine.view.SubtypeKind
import arcanum.engine.view.Subtypes

/**
 * [Engine.options]: what a player may do now (CR 116, 117.1). The list is exact: [Engine.perform] accepts every input
 * listed (an answer or a cast may then open the next decision), and every open decision has an answer listed when one
 * can be found, so a host that offers only these doesn't offer dead buttons or get stuck.
 *
 * - With a decision open: answers to it, each checked by performing it on a copy: yes/no; each target, object, mode,
 *   colour, option, player, card type, replacement or legend to keep; the triggers in the order they are; a number at
 *   its least (and most); an announced X at the least values that work, up to what can be paid; the suggested mana
 *   payment (when Autotap finds one: a way may exist that only a player's own picks make); a division; card names and creature types found in the game; for choices of several, the first ones that
 *   work together. In a cast or activation, only answers after which it can still be finished (CR 601.2c, 601.2g–h), denied
 *   only with proof (castability §1): whenever a search stops early, the answer stands.
 *   Not every combination is listed: the decision says what is allowed, and any allowed answer is accepted. Random
 *   results and host fills (D25, D60) are the host's to answer and aren't listed. Any player may answer (P7), so answers
 *   are offered to everyone; [Input.Cancel] for a cast or an activation only to the player casting or activating it.
 * - While attackers or blockers are awaited: the least declaration that obeys the requirements (none when there are
 *   none, CR 508.1d, 509.1c), and it with each one more legal attack or block (or an attacker's other target); the
 *   declaration inputs accept any legal combination.
 * - With priority: passing, each land they may play, each spell they may begin to cast (every castable face, from the
 *   hand or with a permission, a permission for the hand too, and each alternative cost that applies) and each ability
 *   they may activate, mana abilities included. A cast or activation is listed when it can be begun, everything known at
 *   its start can be done (targets, objects for costs, CR 601.2c, 601.2h), a payment may exist for its mana cost as it
 *   stands then ([Mana.verdict]: every mana ability counted; X as 0, cost changes applied, restricted mana that may pay for it,
 *   convoke and delve counted) or at the end of a choice that lowers it (the target or the creature to sacrifice of a reduction,
 *   another player's assist, CR 601.2f, 702.132a), and its decisions have answers that lead to its end. It is left out only when
 *   the search has shown there is no way: every combination of targets, objects and modes, every X, every payment. A search
 *   stopped by a bound lists it ("seems castable"); a false approval costs a look, a false denial a game action.
 */
internal object Options {
    /** [view], when given, is [state]'s view (the caller has it: the layers aren't worked out again for the search). */
    fun of(state: GameState, player: PlayerId, view: arcanum.engine.view.View? = null): List<Input> {
        // CR 104.1, 800.4a: nothing to do once the game is over or the player has left it
        if (arcanum.engine.setup.GameEnd.over(state) || state.player(player).lost) return emptyList()
        val snap = snapshot(state, view)
        state.pending?.let { return answers(snap, it, player) + arcanum.engine.setup.Setup.earlyAnswers(state, it, player) }
        // CR 103: a game made by Engine.newGame starts with Input.StartGame
        if (!state.started) return listOf(Input.StartGame())
        if (Combat.waitingForDeclaration(state)) return declarations(snap, player)
        if (state.priority.holder != player) return emptyList()
        return buildList {
            add(Input.PassPriority(player))
            lands(snap, player).forEach { add(it) }
            spells(snap, player).forEach { add(it) }
            abilities(snap, player).forEach { add(it) }
        }
    }

    /**
     * Whether [of] lists an input that [predicate] accepts: the same answer, but with priority the search stops at the
     * first one found and [predicate] (cheap) is asked before the trial cast or activation of each spell and ability (a
     * host asks this after every outcome, [AutoPass]).
     */
    fun any(state: GameState, player: PlayerId, view: arcanum.engine.view.View? = null, predicate: (Input) -> Boolean): Boolean {
        if (arcanum.engine.setup.GameEnd.over(state) || state.player(player).lost || state.pending != null || !state.started ||
            Combat.waitingForDeclaration(state) || state.priority.holder != player) return of(state, player, view).any(predicate)
        val snap = snapshot(state, view)
        return predicate(Input.PassPriority(player)) || lands(snap, player).any(predicate) ||
            spellWays(snap, player).any { predicate(it) && startable(snap, it) } ||
            abilityWays(snap, player).any { predicate(it) && startable(snap, it) }
    }

    /** A snapshot of [state] with [view], its view, when the caller has it (else worked out when first needed). */
    fun snapshot(state: GameState, view: arcanum.engine.view.View?): Snapshot = if (view == null) Snapshot(state) else Snapshot(state) { view }

    /** Whether [Engine.perform] accepts [input] in the snapshot's state (its view worked out once for every input tried). */
    private fun accepted(snap: Snapshot, input: Input): Boolean = performed(snap, input) != null

    /** [Engine.perform] of [input] on a working copy of the snapshot's state: the copy as it leaves it and the decision
     * it waits for, or null when refused. */
    private fun performed(snap: Snapshot, input: Input): Pair<GameState, Pending?>? = try {
        val recorder = Recorder(snap.state.deepCopy(), snap.view)
        recorder.state to Perform.runOn(recorder, input).pending
    } catch (e: IllegalArgumentException) {
        null
    }

    /** How many more answers a look-ahead ([goesOn]) may try; once spent, everything is taken to go on (deny only with proof). */
    private class Budget(var left: Int = LOOKAHEAD)

    /**
     * Whether a cast or activation that waits for [pending] in [state] can still be finished (anything else goes on): a
     * payment is found for its mana cost as it stands (CR 601.2g–h, 605.3a; values not announced yet as 0) unless a choice
     * still to come may lower it ([Plan.costMayFall]), and its open decision has an answer after which it goes on in turn
     * (a later target that depends on an earlier one or on X, CR 601.2c; a cost the payment can't make, CR 118.3; the
     * target that makes it cheap enough, CR 601.2f), looking ahead within [budget].
     */
    private fun goesOn(state: GameState, pending: Pending?, budget: Budget = Budget()): Boolean {
        val c = pending?.continuation as? Continuation.Announcing ?: return true
        if (c.choices.names[Plan.MANUAL] != null || c.choices.paying != null) return true
        if (!payableYet(Plan(state, c))) return false
        val d = pending.decision
        if (d is Decision.Random || d is Decision.HostFill || d is Decision.OtherView) return true
        // The payment itself: [payableYet] says a way may exist; finding it is the player's and Autotap's (CR 601.2g-h)
        if (d is Decision.ManaPayment) return true
        val snap = Snapshot(state)
        if (d is Decision.Announce) return announced(snap, pending, 1, budget).isNotEmpty()
        // A sample that fails isn't proof (deny only with proof)
        return tries(snap, pending).any { goesOnAfter(snap, pending, it, budget) } || !everyTried(d, pending)
    }

    /** Whether [tries] of [d] is every answer it has, not a sample: creature types and card names found in the game, a few divisions, a number's ends. */
    private fun everyTried(d: Decision, pending: Pending): Boolean = when (d) {
        is Decision.ChooseSubtype -> d.among != null
        is Decision.ChooseCardName, is Decision.Divide -> false
        is Decision.ChooseNumber -> pending.continuation is Continuation.Announcing && d.max != null
        else -> true
    }

    /** Whether [answer] to the open decision of a cast or activation is accepted and it [goesOn] after it; with the [budget] spent,
     * it is taken to (deny only with proof). */
    private fun goesOnAfter(snap: Snapshot, pending: Pending, answer: Answer, budget: Budget = Budget()): Boolean {
        if (budget.left-- <= 0) return true
        val (after, next) = performed(snap, Input.Answer(pending.decision.id, answer)) ?: return false
        return goesOn(after, next, budget)
    }

    /**
     * Every answer a look-ahead must try before it may deny (castability §1.1): a choice of targets, objects or modes in every
     * combination, the smallest first, since a cost can fall as more are chosen ("{5}, {1} less for each target"), a set may be
     * legal where others aren't, and the cost may read what was chosen. Lazily: the search stops at the first that works.
     */
    private fun tries(snap: Snapshot, pending: Pending): Sequence<Answer> = when (val d = pending.decision) {
        is Decision.Targets -> selections(d.options, d.min, d.max).map { Answer.Targets(it) }
        is Decision.ChooseObjects -> (sharing(snap, pending).orEmpty().asSequence() + listOfNotNull(reachingTotal(snap, pending)) + selections(d.options, d.min, d.max))
            .distinct().map { Answer.Objects(it) }
        is Decision.Modes -> {
            val available = d.options.filter { it.available }.map { it.index }
            val worth = { modes: List<Int> -> d.pawprintLimit == null || modes.sumOf { i -> d.options.first { it.index == i }.pawprints } <= d.pawprintLimit }
            val repeated = if (d.repeatable && d.min > 1) available.map { m -> List(d.min) { m } } else emptyList()
            (selections(available, d.min, d.max) + repeated).filter(worth).map { Answer.Modes(it) }
        }
        else -> candidates(snap, pending).distinct().asSequence()
    }

    /** Every choice of [min] to [max] of [options], the fewest first (none last, as a choice of one lists it). */
    private fun <T> selections(options: List<T>, min: Int, max: Int?): Sequence<List<T>> = sequence {
        val top = minOf(max ?: options.size, options.size)
        for (size in maxOf(min, 1)..top) yieldAll(subsets(options, size))
        if (min == 0) yield(emptyList())
    }

    private fun <T> subsets(options: List<T>, size: Int, from: Int = 0): Sequence<List<T>> = sequence {
        if (size == 0) { yield(emptyList()); return@sequence }
        for (i in from..options.size - size) subsets(options, size - 1, i + 1).forEach { yield(listOf(options[i]) + it) }
    }

    /** Whether a payment may exist for the mana the plan's caster pays as it stands ([Mana.verdict]; what an assisting player pays
     * is theirs, CR 702.132a), with its other ways to pay too (convoke, delve, CR 702.51, 702.66). */
    private fun manaFound(plan: Plan): Boolean =
        !plan.total.hasMana || plan.manaToPay.symbols.isEmpty() ||
            Mana.mayPay(plan.snap, plan.player, plan.manaToPay, plan.itemId, plan.manaWays, !plan.cantSpendMana, plan.manaAsAnyColor, plan.reserved)

    /** [manaFound], or a choice still to come may lower the cost ([Plan.costMayFall]) by enough: a cost that doesn't pay even at the most it
     * can fall ([Plan.mostFall]) is proof without trying every choice. */
    private fun payableYet(plan: Plan): Boolean = manaFound(plan) || (plan.costMayFall && paysAtTheMostItCanFall(plan))

    private fun paysAtTheMostItCanFall(plan: Plan): Boolean = plan.mostFall.let { most ->
        most == null || Mana.mayPay(plan.snap, plan.player, arcanum.engine.casting.Costs.reduce(plan.manaToPay, most), plan.itemId, plan.manaWays, !plan.cantSpendMana, plan.manaAsAnyColor, plan.reserved)
    }

    /**
     * Answers to an X (or another value) announced for a cost (CR 107.3, 601.2b): the least [count] values from the
     * decision's least after which the cast can go on ([goesOnAfter]; targets may need a larger X, CR 601.2c). Values are
     * tried up to the most a payment may exist for (CR 601.2g–h), [MAX_TRIED] more than the least at most; a search stopped there
     * lists the next value untried (deny only with proof). Each value has its own look-ahead unless [budget] is shared.
     */
    private fun announced(snap: Snapshot, pending: Pending, count: Int, budget: Budget? = null): List<Answer> {
        val d = pending.decision as Decision.Announce
        val c = pending.continuation as Continuation.Announcing
        val found = mutableListOf<Answer>()
        val most = minOf(d.max ?: Int.MAX_VALUE, d.min + MAX_TRIED)
        var n = d.min
        while (n <= most && found.size < count) {
            // A larger value costs no less mana, unless a reduction starts to apply with it (a filter reading mana value, CR 202.3e, 601.2f): proof only
            // when it doesn't pay at the most every reduction could take
            val plan = Plan(snap.state, c.copy(choices = c.choices.copy(announced = c.choices.announced + (d.name to n))))
            if (!manaFound(plan) && !(plan.anyReduction && paysAtTheMostItCanFall(plan))) break
            val answer = Answer.Number(n)
            if (goesOnAfter(snap, pending, answer, budget ?: Budget())) found += answer
            n++
        }
        if (found.isEmpty() && n > most && most < (d.max ?: Int.MAX_VALUE)) found += Answer.Number(n)
        return found
    }

    /** [Engine.finishable]. */
    fun finishable(state: GameState): Boolean = goesOn(state, state.pending)

    /** [Engine.anyReduction]. */
    fun anyReduction(state: GameState): Boolean = (state.pending?.continuation as? Continuation.Announcing)?.let { Plan(state, it).anyReduction } == true

    /** [Engine.finishableWith]. */
    fun finishableWith(state: GameState, answer: Answer): Boolean {
        val pending = state.pending ?: return false
        return goesOnAfter(Snapshot(state), pending, answer)
    }

    /** [Engine.selection]: see there. Candidates are tried with as few others as can be, so the budget goes to the near ones. */
    fun selection(state: GameState, picked: List<arcanum.engine.state.TargetRef>): Selection {
        val pending = state.pending ?: return Selection(emptyList(), false)
        val d = pending.decision
        val options = when (d) {
            is Decision.Targets -> d.options
            is Decision.ChooseObjects -> d.options.map { arcanum.engine.state.TargetRef.Object(it) }
            else -> return Selection(emptyList(), false)
        }
        val min = if (d is Decision.Targets) d.min else (d as Decision.ChooseObjects).min
        val max = minOf(if (d is Decision.Targets) d.max ?: options.size else (d as Decision.ChooseObjects).max ?: options.size, options.size)
        val answerOf = { chosen: List<arcanum.engine.state.TargetRef> ->
            if (d is Decision.Targets) Answer.Targets(chosen) else Answer.Objects(chosen.map { (it as arcanum.engine.state.TargetRef.Object).id })
        }
        val c = pending.continuation as? Continuation.Announcing
        val plan = c?.takeIf { it.choices.names[Plan.MANUAL] == null && it.choices.paying == null }?.let { Plan(state, it) }
        val limits = plan != null && d is Decision.Targets && plan.targetsLimitEachOther(d.slot)
        // Not a cast or activation: as the decision says
        if (plan == null) return Selection(options, picked.size in min..max)
        val snap = Snapshot(state)
        val budget = Budget(SELECTION)
        val done = picked.size in min..max && goesOnAfter(snap, pending, answerOf(picked))
        // Targets all alike, the next thing being the payment: nothing to work out per candidate (objects a cost uses do change what can pay, Plan.reserved)
        if (d is Decision.Targets && !plan.costMayVary && !limits && min <= 1 && options.isNotEmpty() &&
            performed(snap, Input.Answer(d.id, answerOf(listOf(options.first()))))?.second?.decision.let { it == null || it is Decision.ManaPayment }) return Selection(options, done)
        val rest = options.filter { it !in picked }
        val undecided = rest.toMutableList()
        val selectable = picked.toMutableSet()
        // Each round: a candidate with [extra] others beside the picks
        var extra = 0
        while (undecided.isNotEmpty() && budget.left > 0 && picked.size + 1 + extra <= max) {
            val iterator = undecided.iterator()
            while (iterator.hasNext() && budget.left > 0) {
                val candidate = iterator.next()
                val others = rest.filter { it != candidate }
                if (picked.size + 1 + extra < min) continue
                if (subsets(others, extra).any { more -> goesOnAfter(snap, pending, answerOf(picked + candidate + more), budget) }) {
                    selectable += candidate
                    iterator.remove()
                }
            }
            extra++
        }
        // A bound keeps a candidate selectable (the final check is exact)
        if (budget.left <= 0) selectable += undecided
        return Selection(options.filter { it in selectable }, done)
    }

    /** [Engine.castCosts]: see there. */
    fun castCosts(state: GameState): CastCosts {
        val pending = state.pending
        val first = (pending?.continuation as? Continuation.Announcing)?.takeIf { it.choices.names[Plan.MANUAL] == null && it.choices.paying == null }
            ?: return CastCosts(null, several = false, asks = false, castable = true, suggestion = emptyList(), answers = emptyList())
        val budget = Budget(COST_LEAVES)
        // Every way through while the cost may vary; else the first that goes on
        class Leaf(val cost: arcanum.api.ManaCost?, val payable: Boolean, val suggestion: List<arcanum.engine.decision.ManaUse>, val answers: List<Answer>, val x: Int)
        val leaves = mutableListOf<Leaf>()
        var stopped = false
        var asks = false
        var xes = 0
        fun walk(s: GameState, p: Pending?, path: List<Answer>) {
            val c = (p?.continuation as? Continuation.Announcing)?.takeIf { it.choices.names[Plan.MANUAL] == null && it.choices.paying == null }
            val x = path.filterIsInstance<Answer.Number>().firstOrNull()?.value ?: 0
            if (c == null) { leaves += Leaf(null, true, emptyList(), path, x); return }
            val d = p.decision
            if (d is Decision.ManaPayment && d.player == c.player) {
                leaves += Leaf(d.cost, manaFound(Plan(s, c)), d.suggestion, path, x)
                return
            }
            if (d is Decision.Random || d is Decision.HostFill || d is Decision.OtherView) { leaves += Leaf(null, true, emptyList(), path, x); stopped = true; return }
            asks = true
            xes = maxOf(xes, Plan(s, c).contributions.sumOf { co -> co.cost.parts.sumOf { pa -> (pa as? arcanum.api.CostPart.Mana)?.cost?.symbols.orEmpty().count { it is arcanum.api.ManaSymbol.Variable } } })
            val snap = Snapshot(s)
            val plan = Plan(s, c)
            if (d is Decision.Announce) {
                val least = announced(snap, p, 1).firstOrNull() ?: return
                val (after, next) = performed(snap, Input.Answer(d.id, least)) ?: return
                return walk(after, next, path + least)
            }
            val every = plan.costMayVary
            for (answer in tries(snap, p)) {
                if (budget.left-- <= 0) { stopped = true; return }
                val (after, next) = performed(snap, Input.Answer(d.id, answer)) ?: continue
                val before = leaves.size
                walk(after, next, path + answer)
                // The first that can be paid when the cost is the same every way (a cost's objects may leave nothing to pay with, Plan.reserved)
                if (!every && leaves.drop(before).any { it.payable }) return
            }
            if (!everyTried(d, p)) stopped = true
        }
        walk(state, pending, emptyList())
        val costs = leaves.map { it.cost?.toString().orEmpty() }.distinct()
        val payable = leaves.filter { it.payable }
        val least = (payable.ifEmpty { leaves }).minByOrNull { manaValue(it.cost) }
        return CastCosts(least?.cost?.let { printedX(it, xes, least.x) }, several = costs.size > 1 || stopped, asks = asks, castable = goesOn(state, pending),
            suggestion = least?.suggestion.orEmpty(), answers = least?.answers.orEmpty())
    }

    /** [cost] with its X shown as {X} ([count] of them, of value [x]) rather than as what X is at its least (castability §2). */
    private fun printedX(cost: arcanum.api.ManaCost, count: Int, x: Int): arcanum.api.ManaCost {
        if (count == 0) return cost
        var left = count * x
        val rest = cost.symbols.mapNotNull { s ->
            if (s !is arcanum.api.ManaSymbol.Generic || left == 0) return@mapNotNull s
            val taken = minOf(left, s.amount)
            left -= taken
            if (s.amount - taken > 0) arcanum.api.ManaSymbol.Generic(s.amount - taken) else null
        }
        return arcanum.api.ManaCost(List(count) { arcanum.api.ManaSymbol.Variable('X') } + rest)
    }

    private fun manaValue(cost: arcanum.api.ManaCost?): Int = cost?.symbols.orEmpty().sumOf { s ->
        when (s) {
            is arcanum.api.ManaSymbol.Generic -> s.amount
            is arcanum.api.ManaSymbol.GenericHybrid -> s.amount
            is arcanum.api.ManaSymbol.Variable -> 0
            else -> 1
        }
    }

    /** [Engine.fillPayment]: see there. */
    fun fillPayment(state: GameState, pinned: List<arcanum.engine.decision.ManaUse>, removed: List<arcanum.engine.decision.ManaUse>): List<arcanum.engine.decision.ManaUse> {
        val pending = state.pending ?: return emptyList()
        val d = pending.decision as? Decision.ManaPayment ?: return emptyList()
        val c = pending.continuation as? Continuation.Announcing
        if (c == null || d.player != c.player) return Mana.suggest(Snapshot(state), d.player, d.cost, null, pinned = pinned, removed = removed)
        val plan = Plan(state, c)
        return Mana.suggest(plan.snap, d.player, d.cost, plan.itemId, plan.manaWays, !plan.cantSpendMana, plan.manaAsAnyColor, plan.reserved, pinned, removed)
    }

    private fun answers(snap: Snapshot, pending: Pending, player: PlayerId): List<Input> {
        val d = pending.decision
        val announcing = pending.continuation is Continuation.Announcing
        // D60: another player isn't offered answers naming cards they can't see (which of a searched library match)
        val hidden = if (player == d.player) emptySet() else objectsOf(d) - arcanum.engine.action.Knowledge.visible(snap.state, snap.view, player)
        // In a cast or activation, only answers after which it can still be finished (no dead end, CR 601.2c, 601.2g–h)
        val found = if (d is Decision.Announce && announcing) announced(snap, pending, NUMBERS)
            else candidates(snap, pending).distinct().filter { hidden.isEmpty() || objectsOf(d, it).none { id -> id in hidden } }
                .filter { if (announcing) goesOnAfter(snap, pending, it) else accepted(snap, Input.Answer(d.id, it)) }
        // A sample that doesn't work isn't proof: the first combination that does (a target that only works as part of a pair)
        val working = if (announcing && found.isEmpty() && d !is Decision.Announce && d !is Decision.ManaPayment)
            tries(snap, pending).filter { a -> hidden.isEmpty() || objectsOf(d, a).none { it in hidden } }.take(LOOKAHEAD).firstOrNull { goesOnAfter(snap, pending, it) }?.let { listOf(it) }
                // A failed sample proves nothing: what it accepts stays on offer
                ?: if (everyTried(d, pending)) emptyList() else candidates(snap, pending).distinct().filter { accepted(snap, Input.Answer(d.id, it)) }
            else found
        val cancel = (pending.continuation as? Continuation.Announcing)?.takeIf { it.player == player }?.let { Input.Cancel(player) }
        return working.map { Input.Answer(d.id, it) } + listOfNotNull(cancel)
    }

    /** The objects a decision offers. */
    private fun objectsOf(d: Decision): Set<ObjectId> = when (d) {
        is Decision.ChooseObjects -> d.options.toSet()
        is Decision.Targets -> d.options.mapNotNull { (it as? arcanum.engine.state.TargetRef.Object)?.id }.toSet()
        is Decision.LegendRule -> d.among.toSet()
        is Decision.ChooseOption -> d.naming.values.toSet()
        else -> emptySet()
    }

    /** The objects an answer to [d] names. */
    private fun objectsOf(d: Decision, a: Answer): List<ObjectId> = when (a) {
        is Answer.Objects -> a.chosen
        is Answer.Targets -> a.chosen.mapNotNull { (it as? arcanum.engine.state.TargetRef.Object)?.id }
        is Answer.Keep -> listOf(a.kept)
        is Answer.Option -> listOfNotNull((d as? Decision.ChooseOption)?.naming?.get(a.chosen))
        else -> emptyList()
    }

    /** Answers worth trying for the open decision; [answers] keeps those [Engine.perform] accepts. */
    private fun candidates(snap: Snapshot, pending: Pending): List<Answer> = when (val d = pending.decision) {
        is Decision.May -> listOfNotNull(Answer.YesNo(true).takeIf { d.payable }, Answer.YesNo(false))
        is Decision.Targets -> some(d.options, d.min, d.max).map { Answer.Targets(it) }
        is Decision.ChooseObjects -> (sharing(snap, pending) ?: (some(d.options, d.min, d.max) + listOfNotNull(reachingTotal(snap, pending)))).map { Answer.Objects(it) }
        is Decision.Modes -> {
            val available = d.options.filter { it.available }.map { it.index }
            val repeated = if (d.repeatable && available.isNotEmpty() && available.size < d.min) listOf(List(d.min) { available.first() }) else emptyList()
            // blb: within the pawprint limit (CR 700.2i)
            val worth = { modes: List<Int> -> d.pawprintLimit == null || modes.sumOf { i -> d.options.first { it.index == i }.pawprints } <= d.pawprintLimit }
            (some(available, d.min, d.max) + repeated).filter(worth).map { Answer.Modes(it) }
        }
        is Decision.ChooseColor -> d.among.map { Answer.ColorChoice(it) }
        is Decision.ChooseOption -> d.options.map { Answer.Option(it) }
        is Decision.ChoosePlayer -> d.options.map { Answer.Player(it) }
        // The least, and a few more ([announced] picks those that work in a cast or activation)
        is Decision.Announce -> (d.min..(d.max ?: Int.MAX_VALUE).coerceAtMost(d.min + NUMBERS - 1)).map { Answer.Number(it) }
        // In a cast: how much another player assists with (CR 702.132a), what they can pay and the caster the rest may be any amount
        is Decision.ChooseNumber -> (if (pending.continuation is Continuation.Announcing && d.max != null) (d.min..d.max).toList()
            else listOfNotNull(d.min, d.max)).map { Answer.Number(it) }
        is Decision.ManaPayment -> if (d.suggestion.isNotEmpty()) listOf(Answer.ManaPayment(d.suggestion)) else emptyList()
        is Decision.Divide -> if (d.among.isNotEmpty()) {
            listOfNotNull(d.suggested.takeIf { it.isNotEmpty() }?.let { Answer.Division(it) },
                Answer.Division(evenly(d.amount, d)), Answer.Division(toFirst(d)))
        } else emptyList()
        is Decision.OrderTriggers -> listOf(Answer.Order(d.triggers.map { it.index }))
        is Decision.ChooseReplacement -> d.options.map { Answer.Replacement(it.index) }
        is Decision.LegendRule -> d.among.map { Answer.Keep(it) }
        is Decision.ChooseCardType -> d.among.map { Answer.CardTypeChoice(it) }
        is Decision.ChooseSubtype -> (d.among ?: creatureTypes(snap, d.player)).map { Answer.SubtypeChoice(it) }
        is Decision.ChooseCardName -> cardNames(snap, d).map { Answer.CardName(it) }
        is Decision.Manual -> listOf(Answer.Done)
        // The host's to answer, with its own randomness and knowledge (D25, D60)
        is Decision.Random, is Decision.HostFill, is Decision.OtherView -> emptyList()
    }

    /** Choices of [min] to [max] of [options]: each alone (and none) when one will do, else the first [min]. */
    private fun <T> some(options: List<T>, min: Int, max: Int?): List<List<T>> = when {
        max == 0 -> listOf(emptyList())
        min <= 1 -> options.map { listOf(it) } + (if (min == 0) listOf(emptyList()) else emptyList())
        options.size >= min -> listOf(options.take(min))
        else -> emptyList()
    }

    /** For a cost that asks objects with a total ("tap any number of untapped creatures with total power N or more",
     * crew, CR 702.122a): the largest first until the total is reached. */
    private fun reachingTotal(snap: Snapshot, pending: Pending): List<ObjectId>? {
        val c = pending.continuation as? Continuation.Announcing ?: return null
        if (c.choices.names[Plan.MANUAL] != null) return null
        val plan = Plan(snap.state, c)
        val q = plan.next() as? Question.Objects ?: return null
        val total = q.choice.total ?: return null
        val atLeast = plan.eval.value(total.atLeast)
        val chosen = mutableListOf<ObjectId>()
        var sum = 0
        for (id in q.choice.options.sortedByDescending { plan.eval.property(total.property, it) }) {
            if (sum >= atLeast && chosen.size >= q.choice.min) break
            chosen += id
            sum += plan.eval.property(total.property, id)
        }
        return chosen
    }

    /** rtr chunk 07: for a cost that asks objects sharing a quality ("two nonland cards with the same name"): the first group of
     * [Question.Objects]' options that share it, as many as needed. Null when the question asks no such thing. */
    private fun sharing(snap: Snapshot, pending: Pending): List<List<ObjectId>>? {
        val c = pending.continuation as? Continuation.Announcing ?: return null
        if (c.choices.names[Plan.MANUAL] != null) return null
        val plan = Plan(snap.state, c)
        val q = plan.next() as? Question.Objects ?: return null
        val quality = q.choice.shared ?: return null
        return q.choice.options.map { a -> listOf(a) + q.choice.options.filter { b -> b != a && plan.eval.shares(quality, a, b) } }
            .filter { it.size >= q.choice.min }.map { it.take(q.choice.min) }.distinctBy { it.toSet() }.take(1)
    }

    /** Creature types of the objects in the game, [player]'s first (CR 205.3m: any creature type may be chosen). */
    private fun creatureTypes(snap: Snapshot, player: PlayerId): List<Subtype> =
        snap.view.objects.values.sortedBy { if (it.controller == player || it.owner == player) 0 else 1 }
            .flatMap { it.characteristics?.subtypes.orEmpty() }.distinct()
            .filter { Subtypes.kindOf(it) == SubtypeKind.Creature }.take(MAX_NAMES)

    /** Names of cards in the game that match the decision's filter (CR 201.3). */
    private fun cardNames(snap: Snapshot, d: Decision.ChooseCardName): List<String> {
        val e = snap.evaluator(d.player, self = d.source)
        return snap.state.objects.values.filter { it.definition != null && !it.token && e.matches(d.filter, it.id) }
            .mapNotNull { it.definition?.faces?.firstOrNull()?.name }.distinct().take(MAX_NAMES)
    }

    private fun evenly(amount: Int, d: Decision.Divide): List<Share> {
        val n = d.among.size
        return d.among.mapIndexed { i, t -> Share(t, amount / n + if (i < amount % n) 1 else 0) }
    }

    /** All to the first but the least each other one may get (CR 601.2d, 510.1c). */
    private fun toFirst(d: Decision.Divide): List<Share> = d.among.mapIndexed { i, t ->
        Share(t, if (i == 0) d.amount - d.minEach * (d.among.size - 1) else d.minEach)
    }

    private const val MAX_NAMES = 8

    /** How many announced values are listed, and how many more than the least are tried. */
    private const val NUMBERS = 4
    private const val MAX_TRIED = 20

    /** How many answers a look-ahead tries at most. */
    private const val LOOKAHEAD = 64
    /** How many selections [selection] tries at most for one look at the candidates. */
    private const val SELECTION = 512
    /** How many answers [castCosts] tries at most to tell a card's costs. */
    private const val COST_LEAVES = 64

    private fun declarations(snap: Snapshot, player: PlayerId): List<Input> {
        val s = snap.state
        return when {
            s.combat == null -> {
                if (player != s.turn.active) return emptyList()
                val targets = CombatRules.attackTargets(snap, player)
                // CR 508.1d: from the least that obeys the requirements (none without any), one more attacker, another
                // target, or another attacker in place of one
                val base = CombatRules.requiredAttack(snap, player)
                val retargeted = base.flatMap { a ->
                    targets.filter { it != a.target && CombatRules.canAttack(snap, player, a.attacker, it) }
                        .map { t -> base.map { if (it.attacker == a.attacker) Attack(a.attacker, t) else it } }
                }
                val others = s.battlefield.filter { id -> base.none { it.attacker == id } }.flatMap { id ->
                    targets.filter { CombatRules.canAttack(snap, player, id, it) }.map { Attack(id, it) }
                }
                // Another creature instead of one that must attack (when a restriction lets only some of them)
                val swapped = base.flatMap { a -> others.map { o -> base - a + o } }
                fun legal(d: List<Attack>) = CombatRules.illegalAttack(snap, player, d) == null && Banding.illegal(snap, d) == null &&
                    // rtr chunk 07: a declaration whose cost to attack can't be paid isn't made (CR 508.1d)
                    CombatRules.canPayToAttack(snap, player, d)
                val plain = (listOf(base) + retargeted + others.map { base + it } + swapped).distinct().filter { legal(it) }
                // The choices made with a declaration, each on one of them: optional costs to attack paid (CR 508.1g), creatures
                // enlisted (CR 702.154a), an attacking band (CR 702.22c)
                val extras = plain.flatMap { d -> listOfNotNull(paying(snap, d), enlisting(snap, player, d), banded(snap, d)) }
                (plain + extras.filter { legal(it) }).distinct().map { Input.DeclareAttackers(player, it) }
            }
            player in Combat.pendingDefenders(snap) -> {
                // CR 509.1c: from the least that obeys the requirements (none without any), one more block
                val base = CombatRules.requiredBlock(snap, player)
                val more = s.battlefield.flatMap { b ->
                    s.combat!!.attackers.filter { CombatRules.canBlock(snap, player, b, it.id) }.map { a -> base + Block(b, a.id) }
                }
                (listOf(base) + more).distinct().filter { CombatRules.illegalBlock(snap, player, it) == null }
                    // CR 509.1d: a declaration whose cost to block can't be paid isn't made
                    .filter { CombatRules.canPayToBlock(snap, player, it) }
                    .map { Input.DeclareBlockers(player, it) }
            }
            else -> emptyList()
        }
    }

    /** [d] with every optional cost to attack chosen (CR 508.1g: exert, 701.43d); null when it has none. */
    private fun paying(snap: Snapshot, d: List<Attack>): List<Attack>? {
        val paid = d.map { a -> a.copy(paying = CombatRules.optionalAttackCosts(snap, a.attacker).mapNotNull { it.origin }) }
        return paid.takeIf { it != d }
    }

    /** [d] with each attacker with enlist enlisting creatures that could be (CR 702.154a), one per enlist ability; null when
     * none can. */
    private fun enlisting(snap: Snapshot, player: PlayerId, d: List<Attack>): List<Attack>? {
        val s = snap.state
        val attackers = d.map { it.attacker }.toSet()
        val free = s.battlefield.filter { id ->
            val view = snap.view[id]
            id !in attackers && s.obj(id)?.let { !it.tapped && !it.phasedOut } == true && snap.board.controller(id) == player &&
                view?.characteristics?.isType(arcanum.api.CardType.Creature) == true &&
                (!view.summoningSick || view.has { it == arcanum.api.Mechanic.IgnoresSummoningSickness })
        }.toMutableList()
        val enlisted = d.map { a ->
            val n = snap.view[a.attacker]?.characteristics?.abilities.orEmpty().count { it.ability == arcanum.api.Keyword.Enlist }
            val taken = free.take(n).also { free.removeAll(it) }
            if (taken.isEmpty()) a else a.copy(enlisted = taken)
        }
        return enlisted.takeIf { it != d }
    }

    /** [d] with an attacking band (CR 702.22c, d): the attackers with banding that attack what the first one does, and one
     * without; null when no band of two can be made. */
    private fun banded(snap: Snapshot, d: List<Attack>): List<Attack>? {
        fun banding(id: arcanum.engine.state.ObjectId) = snap.view[id]?.characteristics?.hasKeyword(arcanum.api.Keyword.Banding) == true
        val first = d.firstOrNull { banding(it.attacker) } ?: return null
        val with = d.filter { banding(it.attacker) && it.target == first.target }
        val without = d.firstOrNull { !banding(it.attacker) && it.target == first.target }
        val members = (with + listOfNotNull(without)).map { it.attacker }.toSet()
        if (members.size < 2) return null
        return d.map { if (it.attacker in members) it.copy(band = 1) else it }
    }

    /** Lands in hand, and with a permission elsewhere (CR 305.1, D41). */
    private fun lands(snap: Snapshot, player: PlayerId): List<Input> = candidates(snap, player).flatMap { card ->
        val faces = snap.state.obj(card)?.definition?.faces?.indices?.toList().orEmpty()
        faces.filter { face -> Legality.cantPlayLand(snap, player, card, face) == null }.map { Input.PlayLand(player, card, it) }
    }

    /** Cards in the player's hand and cards elsewhere they have a permission for. */
    private fun candidates(snap: Snapshot, player: PlayerId) = snap.state.player(player).hand +
        listOf(Zone.Graveyard, Zone.Exile, Zone.Library, Zone.Command).flatMap { zone ->
            when (zone) {
                Zone.Exile -> snap.state.exile
                Zone.Command -> snap.state.command
                else -> snap.state.players.flatMap { p -> snap.state.zone(zone, p.id) }
            }
        }.filter { id -> snap.view[id]?.mechanics.orEmpty().any { it.mechanic is arcanum.api.Mechanic.MayPlay } }

    private fun spells(snap: Snapshot, player: PlayerId): List<Input> = spellWays(snap, player).filter { startable(snap, it) }.toList()

    /** The casts [spells] tries (each legal to begin by its card and way), lazily, before their trial casts. */
    private fun spellWays(snap: Snapshot, player: PlayerId): Sequence<Input.Cast> = candidates(snap, player).asSequence().flatMap { card ->
        val o = snap.state.obj(card) ?: return@flatMap emptyList()
        val fromHand = o.zone == Zone.Hand && o.owner == player
        // From the hand: as usual, or under a permission that changes how (without paying its mana cost, CR 601.2f)
        val granted = Legality.permissions(snap, player, card, arcanum.api.PlayKind.Cast).map { it.first }
        val permissions = if (fromHand) listOf(null) + granted else granted
        val alternatives = listOf(null) + Legality.alternativeCosts(snap, card, player).map { it.first.origin }
        (Legality.castableFaces(o).flatMap { face ->
            permissions.flatMap { permission ->
                alternatives.map { alt -> CastWay(face, alt, permission) }
            }
        } + listOfNotNull(CastWay(fused = true).takeIf { fromHand })).filter { way -> Legality.cantCast(snap, player, card, way) == null }
            .map { Input.Cast(player, card, it) }
    }

    /**
     * Whether the cast or activation [input] can be begun ([Engine.perform] accepts it: everything known at its start can
     * be done, CR 601.2c, 601.2h) and can be finished ([goesOn]): a payment is found for its mana cost as it stands then
     * (X as 0, §7.8; convoke and delve counted), and its decisions have answers that lead to the end.
     */
    private fun startable(snap: Snapshot, input: Input): Boolean {
        val recorder = Recorder(snap.state.deepCopy(), snap.view)
        val progress = try {
            when (input) {
                is Input.Cast -> Casting.cast(recorder, input)
                is Input.Activate -> Casting.activate(recorder, input)
                else -> return accepted(snap, input)
            }
        } catch (e: IllegalArgumentException) {
            return false
        }
        val pending = (progress as? Progress.Waiting)?.pending ?: return true
        // As [Engine.perform] leaves it: waiting for the decision, so its answers can be tried
        recorder.record(arcanum.engine.change.Changes.pending(recorder.state, pending))
        return goesOn(recorder.state, pending)
    }

    private fun abilities(snap: Snapshot, player: PlayerId): List<Input> = abilityWays(snap, player).filter { startable(snap, it) }.toList()

    /** The activations [abilities] tries (each legal to begin), lazily, before their trial activations. */
    private fun abilityWays(snap: Snapshot, player: PlayerId): Sequence<Input.Activate> {
        val s = snap.state
        // Outside the game: the revealed companion's special action (CR 702.139a)
        val sources = s.battlefield + s.player(player).hand + s.player(player).graveyard + s.exile + s.command +
            s.player(player).outside
        return sources.asSequence().flatMap { id ->
            (snap.view[id]?.characteristics?.abilities.orEmpty() + Legality.faceDownActions(snap, id) +
                arcanum.engine.foretell.Foretell.actions(snap, id))
                .filter { it.ability is Ability.Activated || it.ability is Ability.SpecialAction }
                .filter { Legality.cantActivate(snap, player, id, it) == null }
                .map { Input.Activate(player, id, it.origin) }
        }
    }
}
