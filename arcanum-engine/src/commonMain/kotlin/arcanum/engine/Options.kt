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
 *   its least (and most); the suggested mana payment; a division; card names and creature types found in the game; for
 *   choices of several, the first ones that work together. Not every combination is listed: the decision says what is
 *   allowed, and any allowed answer is accepted. Random results and host fills (D25, D60) are the host's to answer and
 *   aren't listed. Any player may answer (P7), so answers are offered to everyone; [Input.Cancel] for a cast or an
 *   activation only to the player casting or activating it.
 * - While attackers or blockers are awaited: the least declaration that obeys the requirements (none when there are
 *   none, CR 508.1d, 509.1c), and it with each one more legal attack or block (or an attacker's other target); the
 *   declaration inputs accept any legal combination.
 * - With priority: passing, each land they may play, each spell they may begin to cast (every castable face, from the
 *   hand or with a permission, a permission for the hand too, and each alternative cost that applies) and each ability
 *   they may activate, mana abilities included. A cast or activation is listed when it can be begun, everything known at
 *   its start can be done (targets, objects for costs, CR 601.2c, 601.2h), and mana is found for its cost as it stands
 *   then (X as 0, cost changes applied, restricted mana that may pay for it counted; convoke and delve not checked).
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

    /** Whether [Engine.perform] accepts [input] in [state]. */
    private fun accepted(state: GameState, input: Input): Boolean = try {
        Perform.run(state, input)
        true
    } catch (e: IllegalArgumentException) {
        false
    }

    private fun answers(snap: Snapshot, pending: Pending, player: PlayerId): List<Input> {
        val d = pending.decision
        // D60: another player isn't offered answers naming cards they can't see (which of a searched library match)
        val hidden = if (player == d.player) emptySet() else objectsOf(d) - arcanum.engine.action.Knowledge.visible(snap.state, snap.view, player)
        val inputs = candidates(snap, pending).distinct().filter { hidden.isEmpty() || objectsOf(it).none { id -> id in hidden } }
            .map { Input.Answer(d.id, it) }.filter { accepted(snap.state, it) }
        val cancel = (pending.continuation as? Continuation.Announcing)?.takeIf { it.player == player }?.let { Input.Cancel(player) }
        return inputs + listOfNotNull(cancel)
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
        is Decision.May -> listOf(Answer.YesNo(true), Answer.YesNo(false))
        is Decision.Targets -> some(d.options, d.min, d.max).map { Answer.Targets(it) }
        is Decision.ChooseObjects -> (some(d.options, d.min, d.max) + listOfNotNull(reachingTotal(snap, pending))).map { Answer.Objects(it) }
        is Decision.Modes -> {
            val available = d.options.filter { it.available }.map { it.index }
            val repeated = if (d.repeatable && available.isNotEmpty() && available.size < d.min) listOf(List(d.min) { available.first() }) else emptyList()
            (some(available, d.min, d.max) + repeated).map { Answer.Modes(it) }
        }
        is Decision.ChooseColor -> d.among.map { Answer.ColorChoice(it) }
        is Decision.ChooseOption -> d.options.map { Answer.Option(it) }
        is Decision.ChoosePlayer -> d.options.map { Answer.Player(it) }
        // The least, and a few more: a target may need a larger X (CR 601.2b–c)
        is Decision.Announce -> (d.min..(d.max ?: Int.MAX_VALUE).coerceAtMost(d.min + 3)).map { Answer.Number(it) }
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
        is Decision.Random, is Decision.HostFill -> emptyList()
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
        val alternatives = listOf(null) + Legality.alternativeCosts(snap, card).map { it.first.origin }
        Legality.castableFaces(o).flatMap { face ->
            permissions.flatMap { permission ->
                alternatives.map { alt -> CastWay(face, alt, permission) }
            }
        }.filter { way -> Legality.cantCast(snap, player, card, way) == null }
            .map { Input.Cast(player, card, it) }
            .filter { startable(snap.state, it) }
    }

    /**
     * Whether the cast or activation [input] can be begun ([Engine.perform] accepts it: everything known at its start can
     * be done, CR 601.2c, 601.2h) and mana is found for its cost as it stands then (X as 0, §7.8). A spell with other
     * ways to pay (convoke, delve) or that can't be paid with mana is left to its payment decision.
     */
    private fun startable(state: GameState, input: Input): Boolean {
        val recorder = Recorder(state.deepCopy())
        val progress = try {
            when (input) {
                is Input.Cast -> Casting.cast(recorder, input)
                is Input.Activate -> Casting.activate(recorder, input)
                else -> return accepted(state, input)
            }
        } catch (e: IllegalArgumentException) {
            return false
        }
        val c = (progress as? Progress.Waiting)?.pending?.continuation as? Continuation.Announcing ?: return true
        if (c.choices.names[Plan.MANUAL] != null) return true
        val plan = Plan(recorder.state, c)
        if (plan.cantSpendMana || plan.manaWays.isNotEmpty() || !plan.total.hasMana) return true
        return Mana.suggest(plan.snap, c.player, plan.total.mana, plan.itemId).isNotEmpty()
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
                .filter { startable(s, it) }
        }
    }
}
