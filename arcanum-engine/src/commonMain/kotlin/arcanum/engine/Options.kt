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
 *   payment; a division; card names and creature types found in the game; for choices of several, the first ones that
 *   work together. In a cast or activation, only answers after which it can still be finished (CR 601.2c, 601.2g–h).
 *   Not every combination is listed: the decision says what is allowed, and any allowed answer is accepted. Random
 *   results and host fills (D25, D60) are the host's to answer and aren't listed. Any player may answer (P7), so answers
 *   are offered to everyone; [Input.Cancel] for a cast or an activation only to the player casting or activating it.
 * - While attackers or blockers are awaited: the least declaration that obeys the requirements (none when there are
 *   none, CR 508.1d, 509.1c), and it with each one more legal attack or block (or an attacker's other target); the
 *   declaration inputs accept any legal combination.
 * - With priority: passing, each land they may play, each spell they may begin to cast (every castable face, from the
 *   hand or with a permission, a permission for the hand too, and each alternative cost that applies) and each ability
 *   they may activate, mana abilities included. A cast or activation is listed when it can be begun, everything known at
 *   its start can be done (targets, objects for costs, CR 601.2c, 601.2h), a payment is found for its mana cost as it
 *   stands then (X as 0, cost changes applied, restricted mana that may pay for it, convoke and delve counted), and its
 *   decisions have answers that lead to its end (looked ahead within a bound).
 */
internal object Options {
    fun of(state: GameState, player: PlayerId): List<Input> {
        // CR 104.1, 800.4a: nothing to do once the game is over or the player has left it
        if (arcanum.engine.setup.GameEnd.over(state) || state.player(player).lost) return emptyList()
        val snap = Snapshot(state)
        state.pending?.let { return answers(snap, it, player) }
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

    /** How many more answers a look-ahead ([goesOn]) may try; once spent, what is accepted is taken to go on. */
    private class Budget(var left: Int = LOOKAHEAD)

    /**
     * Whether a cast or activation that waits for [pending] in [state] can still be finished (anything else goes on): a
     * payment is found for its mana cost as it stands (CR 601.2g–h, 605.3a; values not announced yet as 0), and its open
     * decision has an answer after which it goes on in turn (a later target that depends on an earlier one or on X,
     * CR 601.2c; a cost the payment can't make, CR 118.3), looking ahead within [budget].
     */
    private fun goesOn(state: GameState, pending: Pending?, budget: Budget = Budget()): Boolean {
        val c = pending?.continuation as? Continuation.Announcing ?: return true
        if (c.choices.names[Plan.MANUAL] != null || c.choices.paying != null) return true
        if (!manaFound(Plan(state, c))) return false
        val d = pending.decision
        if (d is Decision.Random || d is Decision.HostFill || d is Decision.OtherView) return true
        val snap = Snapshot(state)
        if (d is Decision.Announce) return announced(snap, pending, 1, budget).isNotEmpty()
        return candidates(snap, pending).distinct().any { goesOnAfter(snap, pending, it, budget) }
    }

    /** Whether [answer] to the open decision of a cast or activation is accepted and it [goesOn] after it. */
    private fun goesOnAfter(snap: Snapshot, pending: Pending, answer: Answer, budget: Budget = Budget()): Boolean {
        val (after, next) = performed(snap, Input.Answer(pending.decision.id, answer)) ?: return false
        return budget.left-- <= 0 || goesOn(after, next, budget)
    }

    /** Whether a payment is found for the plan's total mana cost as it stands (§7.8), with its other ways to pay too
     * (convoke, delve, CR 702.51, 702.66) when mana alone doesn't. */
    private fun manaFound(plan: Plan): Boolean =
        !plan.total.hasMana || Mana.suggest(plan.snap, plan.player, plan.total.mana, plan.itemId, plan.manaWays, !plan.cantSpendMana, plan.manaAsAnyColor,
            plan.tappedByCost).isNotEmpty()

    /**
     * Answers to an X (or another value) announced for a cost (CR 107.3, 601.2b): the least [count] values from the
     * decision's least after which the cast can go on ([goesOnAfter]; targets may need a larger X, CR 601.2c). Values are
     * tried up to the most a payment is found for (CR 601.2g–h), [MAX_TRIED] more than the least at most. Each value
     * has its own look-ahead unless [budget] is shared.
     */
    private fun announced(snap: Snapshot, pending: Pending, count: Int, budget: Budget? = null): List<Answer> {
        val d = pending.decision as Decision.Announce
        val c = pending.continuation as Continuation.Announcing
        val found = mutableListOf<Answer>()
        val most = minOf(d.max ?: Int.MAX_VALUE, d.min + MAX_TRIED)
        var n = d.min
        while (n <= most && found.size < count) {
            // A larger value costs no less mana
            if (!manaFound(Plan(snap.state, c.copy(choices = c.choices.copy(announced = c.choices.announced + (d.name to n)))))) break
            val answer = Answer.Number(n)
            if (goesOnAfter(snap, pending, answer, budget ?: Budget())) found += answer
            n++
        }
        return found
    }

    private fun answers(snap: Snapshot, pending: Pending, player: PlayerId): List<Input> {
        val d = pending.decision
        val announcing = pending.continuation is Continuation.Announcing
        // D60: another player isn't offered answers naming cards they can't see (which of a searched library match)
        val hidden = if (player == d.player) emptySet() else objectsOf(d) - arcanum.engine.action.Knowledge.visible(snap.state, snap.view, player)
        // In a cast or activation, only answers after which it can still be finished (no dead end, CR 601.2c, 601.2g–h)
        val found = if (d is Decision.Announce && announcing) announced(snap, pending, NUMBERS)
            else candidates(snap, pending).distinct().filter { hidden.isEmpty() || objectsOf(it).none { id -> id in hidden } }
                .filter { if (announcing) goesOnAfter(snap, pending, it) else accepted(snap, Input.Answer(d.id, it)) }
        val cancel = (pending.continuation as? Continuation.Announcing)?.takeIf { it.player == player }?.let { Input.Cancel(player) }
        return found.map { Input.Answer(d.id, it) } + listOfNotNull(cancel)
    }

    /** The objects a decision offers. */
    private fun objectsOf(d: Decision): Set<ObjectId> = when (d) {
        is Decision.ChooseObjects -> d.options.toSet()
        is Decision.Targets -> d.options.mapNotNull { (it as? arcanum.engine.state.TargetRef.Object)?.id }.toSet()
        is Decision.LegendRule -> d.among.toSet()
        else -> emptySet()
    }

    /** The objects an answer names. */
    private fun objectsOf(a: Answer): List<ObjectId> = when (a) {
        is Answer.Objects -> a.chosen
        is Answer.Targets -> a.chosen.mapNotNull { (it as? arcanum.engine.state.TargetRef.Object)?.id }
        is Answer.Keep -> listOf(a.kept)
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
        is Decision.ChooseNumber -> listOfNotNull(d.min, d.max).map { Answer.Number(it) }
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
    private const val LOOKAHEAD = 40

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
                (listOf(base) + retargeted + others.map { base + it } + swapped).distinct().filter { CombatRules.illegalAttack(snap, player, it) == null }
                    // rtr chunk 07: a declaration whose cost to attack can't be paid isn't made (CR 508.1d)
                    .filter { CombatRules.canPayToAttack(snap, player, it) }
                    .map { Input.DeclareAttackers(player, it) }
            }
            player in Combat.pendingDefenders(snap) -> {
                // CR 509.1c: from the least that obeys the requirements (none without any), one more block
                val base = CombatRules.requiredBlock(snap, player)
                val more = s.battlefield.flatMap { b ->
                    s.combat!!.attackers.filter { CombatRules.canBlock(snap, player, b, it.id) }.map { a -> base + Block(b, a.id) }
                }
                (listOf(base) + more).distinct().filter { CombatRules.illegalBlock(snap, player, it) == null }
                    .map { Input.DeclareBlockers(player, it) }
            }
            else -> emptyList()
        }
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

    private fun spells(snap: Snapshot, player: PlayerId): List<Input> = candidates(snap, player).flatMap { card ->
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
            .filter { startable(snap, it) }
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

    private fun abilities(snap: Snapshot, player: PlayerId): List<Input> {
        val s = snap.state
        // Outside the game: the revealed companion's special action (CR 702.139a)
        val sources = s.battlefield + s.player(player).hand + s.player(player).graveyard + s.exile + s.command +
            s.player(player).outside
        return sources.flatMap { id ->
            (snap.view[id]?.characteristics?.abilities.orEmpty() + Legality.faceDownActions(snap, id))
                .filter { it.ability is Ability.Activated || it.ability is Ability.SpecialAction }
                .filter { Legality.cantActivate(snap, player, id, it) == null }
                .map { Input.Activate(player, id, it.origin) }
                .filter { startable(snap, it) }
        }
    }
}
