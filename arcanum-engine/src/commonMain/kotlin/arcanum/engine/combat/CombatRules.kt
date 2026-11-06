package arcanum.engine.combat

import arcanum.api.CardType
import arcanum.api.Mechanic
import arcanum.api.Recipients
import arcanum.api.Zone
import arcanum.engine.casting.Snapshot
import arcanum.engine.input.Attack
import arcanum.engine.input.Block
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import arcanum.engine.view.AppliedMechanic

/**
 * Who may attack what and who may block whom (CR 508.1, 509.1): the basic requirements (untapped creatures, haste or
 * controlled since the turn began), then restrictions (can't attack or block, defender, flying, menace, "can block only",
 * protection, shadow) and requirements (must attack). Mechanics come from the view (D24), each read with its own "you"
 * and source.
 */
internal object CombatRules {
    private fun creature(snap: Snapshot, id: ObjectId) = snap.view[id]?.characteristics?.isType(CardType.Creature) == true

    private fun onBattlefield(snap: Snapshot, id: ObjectId) =
        snap.state.obj(id)?.let { it.zone == Zone.Battlefield && !it.phasedOut } == true

    private fun mechanics(snap: Snapshot, id: ObjectId): List<AppliedMechanic> = snap.view[id]?.mechanics.orEmpty()

    /** What [player] may attack: each opponent still in the game, the planeswalkers they control and the battles they
     * protect, a Siege [player] controls among them (CR 506.2, 508.1b, 310.9b). */
    fun attackTargets(snap: Snapshot, player: PlayerId): List<TargetRef> {
        val opponents = snap.state.opponents(player)
        val walkers = snap.state.battlefield.filter { id ->
            onBattlefield(snap, id) && snap.view[id]?.characteristics?.isType(CardType.Planeswalker) == true &&
                snap.board.controller(id) in opponents &&
                // dft: "can't be attacked" (CR 508.1b)
                snap.view[id]?.has { it == Mechanic.CantBeAttacked } != true
        }
        val battles = snap.state.battlefield.filter { id ->
            onBattlefield(snap, id) && snap.view[id]?.characteristics?.isType(CardType.Battle) == true &&
                snap.state.obj(id)?.protector in opponents
        }
        return opponents.map { TargetRef.Player(it) } + walkers.map { TargetRef.Object(it) } + battles.map { TargetRef.Object(it) }
    }

    /** The defending player of an attack on [target] (CR 506.2, 310.9d). */
    fun defenderOf(snap: Snapshot, target: TargetRef): PlayerId? = Battles.defender(snap.state, { snap.board.controller(it) }, target)

    /** CR 508.1a, c: [id] could attack [target] (any target when null): an untapped creature [player] controls, not
     * summoning sick unless it has haste (CR 302.6), not restricted (defender, "can't attack"). */
    fun canAttack(snap: Snapshot, player: PlayerId, id: ObjectId, target: TargetRef?): Boolean {
        val o = snap.state.obj(id) ?: return false
        if (!onBattlefield(snap, id) || !creature(snap, id) || o.tapped || snap.board.controller(id) != player) return false
        if (snap.view[id]?.characteristics?.isType(CardType.Battle) == true) return false
        val view = snap.view[id]!!
        if (view.summoningSick && !view.has { it == Mechanic.IgnoresSummoningSickness }) return false
        if (target != null && target !in attackTargets(snap, player)) return false
        val despiteDefender = view.has { it == Mechanic.CanAttackDespiteDefender }
        return mechanics(snap, id).none { applied ->
            val m = applied.mechanic as? Mechanic.CantAttack ?: return@none false
            when {
                m.fromDefender && despiteDefender -> false
                m.target == null -> true
                target == null -> false
                else -> target in recipients(snap, applied, id, m.target!!)
            }
        }
    }

    private fun recipients(snap: Snapshot, applied: AppliedMechanic, id: ObjectId, r: Recipients): List<TargetRef> {
        val e = snap.evaluator(applied.controller, self = applied.source ?: id, names = applied.names).let { it.with(it.ctx.copy(affected = id)) }
        return r.objects?.let { e.objects(it).map { o -> TargetRef.Object(o) } }.orEmpty() +
            r.players?.let { e.players(it).map { p -> TargetRef.Player(p) } }.orEmpty()
    }

    /** CR 702.154a, c, d: each attacker enlists at most one creature per enlist ability it has; each creature enlisted is an
     * untapped creature [player] controls, chosen to attack by nobody, with haste or under their control since the turn began, and
     * enlisted only once (it is tapped as the cost, CR 508.1g). */
    private fun enlistProblem(snap: Snapshot, player: PlayerId, attacks: List<Attack>): String? {
        val all = attacks.flatMap { it.enlisted }
        if (all.isEmpty()) return null
        if (all.distinct().size != all.size) return "A creature is enlisted only once"
        val attackers = attacks.map { it.attacker }.toSet()
        attacks.filter { it.enlisted.isNotEmpty() }.forEach { a ->
            val instances = snap.view[a.attacker]?.characteristics?.abilities.orEmpty().count { it.ability == arcanum.api.Keyword.Enlist }
            if (a.enlisted.size > instances) return "${a.attacker} has $instances enlist abilities (CR 702.154d)"
            a.enlisted.forEach { id ->
                val o = snap.state.obj(id) ?: return "$id isn't on the battlefield"
                val view = snap.view[id]
                val ok = id !in attackers && onBattlefield(snap, id) && creature(snap, id) && !o.tapped && snap.board.controller(id) == player &&
                    view != null && (!view.summoningSick || view.has { it == Mechanic.IgnoresSummoningSickness })
                if (!ok) return "${a.attacker} can't enlist $id (CR 702.154a)"
            }
        }
        return null
    }

    /** Why [attacks] isn't a legal declaration for [player], or null (CR 508.1a–d): it breaks a restriction, or obeys
     * fewer requirements than a declaration that breaks none could (CR 508.1d). */
    fun illegalAttack(snap: Snapshot, player: PlayerId, attacks: List<Attack>): String? {
        attackRestriction(snap, player, attacks)?.let { return it }
        val requirements = attackRequirements(snap, player)
        if (requirements.isEmpty()) return null
        val obeyed = requirements.count { it.obeyedBy(attacks) }
        val most = mostObeyedAttack(snap, player, requirements).first
        if (obeyed >= most) return null
        val missed = requirements.first { !it.obeyedBy(attacks) }
        return "${missed.creature} attacks each combat if able: $obeyed of the $most requirements that can be obeyed are (CR 508.1d)"
    }

    /** A declaration of attackers that obeys as many requirements as any legal one can (CR 508.1d): only creatures that
     * must attack, each attacking what obeys the most of its requirements; empty when there are none. */
    fun requiredAttack(snap: Snapshot, player: PlayerId): List<Attack> =
        attackRequirements(snap, player).takeIf { it.isNotEmpty() }?.let { mostObeyedAttack(snap, player, it).second }.orEmpty()

    /** CR 508.1a–c: the restrictions; adding attackers never mends a broken one. */
    private fun attackRestriction(snap: Snapshot, player: PlayerId, attacks: List<Attack>): String? {
        if (attacks.map { it.attacker }.distinct().size != attacks.size) return "A creature attacks only once"
        attacks.forEach { a -> if (!canAttack(snap, player, a.attacker, a.target)) return "${a.attacker} can't attack ${a.target}" }
        enlistProblem(snap, player, attacks)?.let { return it }
        // CR 508.1g: only the attacker's own "as it attacks" costs, each once
        attacks.forEach { a ->
            if (a.paying.distinct().size != a.paying.size) return "An optional cost to attack is paid once"
            val offered = optionalAttackCosts(snap, a.attacker).mapNotNull { it.origin }
            a.paying.firstOrNull { it !in offered }?.let { return "${a.attacker} has no optional cost to attack $it (CR 508.1g)" }
        }
        // CR 508.1c: "can't attack alone"
        attacks.singleOrNull()?.let { a ->
            if (mechanics(snap, a.attacker).any { it.mechanic == Mechanic.CantAttackAlone }) return "${a.attacker} can't attack alone"
        }
        // dgm: CR 508.1c: "can only attack alone"
        if (attacks.size > 1) attacks.firstOrNull { a -> mechanics(snap, a.attacker).any { it.mechanic == Mechanic.CanOnlyAttackAlone } }?.let { a ->
            return "${a.attacker} can only attack alone"
        }
        // CR 508.1c: "no more than N creatures can attack": counted per effect among the creatures it restricts
        attacks.flatMap { a -> mechanics(snap, a.attacker).filter { it.mechanic is Mechanic.MaximumAttackers }.map { it to a } }
            .groupBy({ (applied, _) -> Triple(applied.source, applied.origin, applied.effect) }, { (applied, a) -> applied to a })
            .forEach { (_, list) ->
                val m = list.first().first.mechanic as Mechanic.MaximumAttackers
                val counted = list.filter { (applied, a) -> m.target == null || a.target in recipients(snap, applied, a.attacker, m.target!!) }
                if (counted.size > m.count) return "No more than ${m.count} creature(s) can attack"
            }
        return null
    }

    /** One cost to attack or block with [creature] (CR 508.1h, 509.1d): [cost] of a "can't attack/block … unless" or a
     * chosen "as it attacks" ability, read with its own "you" ([controller], [source], [names]); [then] its "when you
     * do" (CR 607.2h); X and repetitions worked out into the mana parts. */
    data class CombatCost(
        val creature: ObjectId, val cost: arcanum.api.Cost, val controller: PlayerId, val source: ObjectId?,
        val names: arcanum.engine.state.Bindings, val then: arcanum.api.Instructions? = null, val origin: arcanum.engine.view.AbilityOrigin? = null,
    )

    /** The "as this creature attacks" optional costs [id] has (CR 508.1g, 701.43d): its [Mechanic.AsAttacks] with an origin. */
    fun optionalAttackCosts(snap: Snapshot, id: ObjectId): List<AppliedMechanic> =
        mechanics(snap, id).filter { it.mechanic is Mechanic.AsAttacks && it.origin != null }

    /** [m]'s cost parts with X and repetitions of mana worked out (the mana fixed, CR 508.1h "locked in"). */
    private fun fixed(snap: Snapshot, applied: AppliedMechanic, creature: ObjectId, cost: arcanum.api.Cost): arcanum.api.Cost {
        val e = snap.evaluator(applied.controller, self = applied.source ?: creature, names = applied.names).let { it.with(it.ctx.copy(affected = creature)) }
        return arcanum.api.Cost(cost.parts.flatMap { part ->
            if (part !is arcanum.api.CostPart.Mana) return@flatMap listOf(part)
            val mana = part.x?.let { arcanum.engine.mana.Mana.withX(part.cost, mapOf('X' to e.value(it))) } ?: part.cost
            List(part.times?.let { e.value(it) } ?: 1) { arcanum.api.CostPart.Mana(mana) }
        })
    }

    /**
     * rtr chunk 07: the costs to attack with [attacks] (CR 508.1h): each "can't attack … unless their controller pays" that
     * applies to an attacker and what it attacks ([Mechanic.AttackCost]), and each optional "as it attacks" cost chosen for it
     * ([arcanum.engine.input.Attack.paying], [Mechanic.AsAttacks], CR 508.1g).
     */
    fun attackCosts(snap: Snapshot, attacks: List<Attack>): List<CombatCost> = attacks.flatMap { a ->
        mechanics(snap, a.attacker).mapNotNull { applied ->
            when (val m = applied.mechanic) {
                is Mechanic.AttackCost -> if (m.target != null && a.target !in recipients(snap, applied, a.attacker, m.target!!)) null
                    else CombatCost(a.attacker, fixed(snap, applied, a.attacker, m.cost), applied.controller, applied.source, applied.names)
                is Mechanic.AsAttacks -> if (applied.origin == null || applied.origin !in a.paying) null
                    else CombatCost(a.attacker, fixed(snap, applied, a.attacker, m.cost), applied.controller, applied.source ?: a.attacker,
                        applied.names, m.then, applied.origin)
                else -> null
            }
        }
    }

    /** CR 509.1d: the costs to block with [blocks]: each "can't block … unless their controller pays" ([Mechanic.BlockCost])
     * that applies to a blocker and what it blocks, once for each attacker it blocks. */
    fun blockCosts(snap: Snapshot, blocks: List<Block>): List<CombatCost> = blocks.flatMap { b ->
        mechanics(snap, b.blocker).mapNotNull { applied ->
            val m = applied.mechanic as? Mechanic.BlockCost ?: return@mapNotNull null
            val e = snap.evaluator(applied.controller, self = applied.source ?: b.blocker, names = applied.names).let { it.with(it.ctx.copy(affected = b.blocker)) }
            if (m.attackers != null && !e.matches(m.attackers!!, b.attacker)) null
            else CombatCost(b.blocker, fixed(snap, applied, b.blocker, m.cost), applied.controller, applied.source, applied.names)
        }
    }

    /** The mana of [costs] as one total (CR 508.1h, 509.1d); null when no mana is asked. */
    fun manaOf(costs: List<CombatCost>): arcanum.api.ManaCost? =
        arcanum.api.ManaCost(costs.flatMap { c -> c.cost.parts.filterIsInstance<arcanum.api.CostPart.Mana>().flatMap { it.cost.symbols } })
            .takeIf { arcanum.engine.mana.Mana.needs(it).isNotEmpty() }

    /** The total mana cost to attack with [attacks] (CR 508.1h), or null. */
    fun attackCost(snap: Snapshot, attacks: List<Attack>): arcanum.api.ManaCost? = manaOf(attackCosts(snap, attacks))

    /** rtr chunk 07: whether [player] could pay the costs to attack with [attacks] (CR 508.1d: a declaration needing a cost they
     * don't pay isn't made). */
    fun canPayToAttack(snap: Snapshot, player: PlayerId, attacks: List<Attack>): Boolean {
        val costs = attackCosts(snap, attacks)
        return costs.isEmpty() || canPay(tappedForAttack(snap, attacks), player, costs)
    }

    /** The creatures declaring [attacks] taps before the costs to attack are paid: each attacker without vigilance (CR 508.1f)
     * and each creature enlisted (CR 702.154a, 508.1g: tapped as a cost, so it can't also pay another one). */
    fun tappedByAttack(snap: Snapshot, attacks: List<Attack>): List<ObjectId> =
        attacks.filter { a -> snap.view[a.attacker]?.has { it == Mechanic.AttacksWithoutTapping } != true }.map { it.attacker } +
            attacks.flatMap { it.enlisted }

    /** CR 508.1f, i, j: [snap] as the costs to attack with [attacks] are paid, the creatures [tappedByAttack] tapped: a tapped
     * attacker is no mana source for them (a mana creature can't pay its own cost to attack). */
    private fun tappedForAttack(snap: Snapshot, attacks: List<Attack>): Snapshot {
        val tapping = tappedByAttack(snap, attacks).filter { snap.state.obj(it)?.tapped == false }
        if (tapping.isEmpty()) return snap
        val s = snap.state.deepCopy()
        tapping.forEach { id -> s.objects[id] = s.objOrThrow(id).copy(tapped = true) }
        return Snapshot(s)
    }

    /** CR 509.1d: whether [player] could pay the costs to block with [blocks]. */
    fun canPayToBlock(snap: Snapshot, player: PlayerId, blocks: List<Block>): Boolean = canPay(snap, player, blockCosts(snap, blocks))

    /** Whether [player] could pay all of [costs]: the mana as one payment, and each other part (objects to choose, life);
     * parts that take no resource (exert, CR 701.43b) always can. CR 508.1j "Partial payments are not allowed": an object
     * sacrificed, tapped, discarded, exiled or returned pays one such part only, so those parts together need distinct
     * objects (one land doesn't pay two "sacrifice a land"). */
    private fun canPay(snap: Snapshot, player: PlayerId, costs: List<CombatCost>): Boolean {
        if (costs.isEmpty()) return true
        manaOf(costs)?.let { if (arcanum.engine.mana.Mana.suggest(snap, player, it, null).isEmpty()) return false }
        var life = snap.state.player(player).life
        // Each object a using-up part takes, as one slot with its options
        val slots = mutableListOf<List<ObjectId>>()
        val each = costs.all { c ->
            val e = snap.evaluator(player, self = c.creature, names = c.names)
            c.cost.parts.all { part ->
                when (part) {
                    is arcanum.api.CostPart.Mana -> true
                    is arcanum.api.CostPart.PayLife -> e.value(part.amount).let { n -> (life >= n).also { life -= n } }
                    arcanum.api.CostPart.TapSelf -> snap.state.obj(c.creature)?.tapped == false
                    arcanum.api.CostPart.UntapSelf -> snap.state.obj(c.creature)?.tapped == true
                    else -> arcanum.engine.casting.Costs.objectChoice(snap, player, null, part, e)?.let { choice ->
                        if (usesUp(part)) repeat(choice.min) { slots += choice.options }
                        choice.options.size >= choice.min
                    } ?: true
                }
            }
        }
        return each && distinctObjects(slots)
    }

    /** Whether paying [part] uses its chosen objects up, so no other part can choose them. */
    private fun usesUp(part: arcanum.api.CostPart): Boolean = part is arcanum.api.CostPart.Sacrifice || part is arcanum.api.CostPart.TapObjects ||
        part is arcanum.api.CostPart.Discard || part is arcanum.api.CostPart.Exile || part is arcanum.api.CostPart.ReturnToHand

    /** Whether each of [slots] can take a different object among its options (a bipartite matching, augmenting paths). */
    private fun distinctObjects(slots: List<List<ObjectId>>): Boolean {
        if (slots.size < 2) return true
        val holder = mutableMapOf<ObjectId, Int>()
        fun place(slot: Int, seen: MutableSet<ObjectId>): Boolean = slots[slot].any { id ->
            seen.add(id) && (holder[id]?.let { place(it, seen) } ?: true).also { ok -> if (ok) holder[id] = slot }
        }
        return slots.indices.all { place(it, mutableSetOf()) }
    }

    /** One requirement on [creature] (CR 508.1d): it attacks, anything when [targets] is null. */
    private class AttackRequirement(val creature: ObjectId, val targets: List<TargetRef>?) {
        fun obeyedBy(target: TargetRef) = targets == null || target in targets
        fun obeyedBy(attacks: List<Attack>) = attacks.any { it.attacker == creature && obeyedBy(it.target) }
    }

    /** Each "attacks (…) each combat if able" on a creature [player] controls that could attack, and each goad on it:
     * it attacks, and attacks a player other than the goading player (CR 701.15b, c). */
    private fun attackRequirements(snap: Snapshot, player: PlayerId): List<AttackRequirement> =
        snap.state.battlefield.filter { id ->
            (mechanics(snap, id).any { it.mechanic is Mechanic.MustAttack } || snap.state.obj(id)?.goadedBy?.isNotEmpty() == true) &&
                canAttack(snap, player, id, null)
        }.flatMap { id ->
            mechanics(snap, id).mapNotNull { applied ->
                val m = applied.mechanic as? Mechanic.MustAttack ?: return@mapNotNull null
                AttackRequirement(id, m.target?.let { recipients(snap, applied, id, it) })
            } + snap.state.obj(id)!!.goadedBy.flatMap { goader ->
                val others = snap.state.players.filter { !it.lost && it.id != goader && it.id != player }.map { TargetRef.Player(it.id) }
                listOf(AttackRequirement(id, null), AttackRequirement(id, others))
            }
        }

    private fun mostObeyedAttack(snap: Snapshot, player: PlayerId, requirements: List<AttackRequirement>): Pair<Int, List<Attack>> {
        val targets = attackTargets(snap, player)
        val choices = requirements.map { it.creature }.distinct().map { id ->
            // CR 508.1d: a cost to attack needn't be paid to obey a requirement
            targets.filter { canAttack(snap, player, id, it) && attackCosts(snap, listOf(Attack(id, it))).isEmpty() }
                .map { t -> Attack(id, t) to requirements.count { it.creature == id && it.obeyedBy(t) } }
        }
        return mostObeyed(choices, { attackRestriction(snap, player, it) == null }, { true })
    }

    /** Declarations searched for the most requirements before taking the best found (a guard against huge boards). */
    private const val SEARCH_BUDGET = 20_000

    /**
     * The most requirements a legal declaration obeys and one that does (CR 508.1d, 509.1c): each creature takes one of
     * its [choices] (with the requirements that choice obeys) or none; [partial] refuses a declaration no addition could
     * make legal, [whole] checks a complete one. Branches that can't beat the best so far are cut.
     */
    private fun <T> mostObeyed(choices: List<List<Pair<T, Int>>>, partial: (List<T>) -> Boolean, whole: (List<T>) -> Boolean): Pair<Int, List<T>> {
        val sorted = choices.map { list -> list.sortedByDescending { it.second } }
        val bound = IntArray(sorted.size + 1)
        for (i in sorted.indices.reversed()) bound[i] = bound[i + 1] + (sorted[i].maxOfOrNull { it.second } ?: 0).coerceAtLeast(0)
        var best = -1
        var bestDeclaration = emptyList<T>()
        var budget = SEARCH_BUDGET
        fun search(i: Int, declaration: List<T>, score: Int) {
            if (budget-- <= 0 || score + bound[i] <= best) return
            if (i == sorted.size) {
                if (whole(declaration)) { best = score; bestDeclaration = declaration }
                return
            }
            sorted[i].forEach { (choice, value) -> (declaration + choice).let { if (partial(it)) search(i + 1, it, score + value) } }
            search(i + 1, declaration, score)
        }
        search(0, emptyList(), 0)
        return best.coerceAtLeast(0) to bestDeclaration
    }

    /** Could anything [player] controls attack at all (P5: nothing to declare otherwise)? */
    fun anyAttacker(snap: Snapshot, player: PlayerId): Boolean =
        attackTargets(snap, player).isNotEmpty() && snap.state.battlefield.any { canAttack(snap, player, it, null) }

    // ---- Blocking

    /** CR 509.1a–b: [blocker] could block [attacker] for [player]: an untapped creature they control, an attacker
     * attacking them or a planeswalker they control, and no restriction on either (flying and reach, "can't be blocked
     * by", "can block only", protection, shadow). */
    fun canBlock(snap: Snapshot, player: PlayerId, blocker: ObjectId, attacker: ObjectId): Boolean {
        val o = snap.state.obj(blocker) ?: return false
        if (!onBattlefield(snap, blocker) || !creature(snap, blocker) || o.tapped || snap.board.controller(blocker) != player) return false
        if (snap.view[blocker]?.characteristics?.isType(CardType.Battle) == true) return false
        val attack = snap.state.combat?.attackers?.firstOrNull { it.id == attacker } ?: return false
        if (!onBattlefield(snap, attacker)) return false
        // CR 310.9c: only a battle's protector blocks the creatures attacking it
        if (defenderOf(snap, attack.target) != player) return false
        val blockerOk = mechanics(snap, blocker).none { applied ->
            val e = snap.evaluator(applied.controller, self = applied.source ?: blocker, names = applied.names)
            when (val m = applied.mechanic) {
                is Mechanic.CantBlock -> e.matches(m.attackers, attacker)
                is Mechanic.CanBlockOnly -> !e.matches(m.attackers, attacker)
                else -> false
            }
        }
        val attackerOk = mechanics(snap, attacker).none { applied ->
            // "can't be blocked by creatures with greater power" given by another object compares with the attacker (Objects.Affected)
            val e = snap.evaluator(applied.controller, self = applied.source ?: attacker, names = applied.names, affected = attacker)
            when (val m = applied.mechanic) {
                Mechanic.CantBeBlocked -> true
                is Mechanic.CanBeBlockedOnlyBy -> !e.matches(m.blockers, blocker)
                is Mechanic.CantBeBlockedBy -> e.matches(m.blockers, blocker)
                // CR 702.16e: can't be blocked by creatures with the quality
                is Mechanic.Protection -> e.matches(m.from, blocker)
                else -> false
            }
        }
        return blockerOk && attackerOk
    }

    /** How many creatures [blocker] can block at once (CR 509.1a); null: any number. */
    fun blockCapacity(snap: Snapshot, blocker: ObjectId): Int? {
        val ms = mechanics(snap, blocker).map { it.mechanic }
        if (ms.any { it == Mechanic.CanBlockAnyNumber }) return null
        return 1 + ms.filterIsInstance<Mechanic.CanBlockAdditional>().sumOf { it.count }
    }

    /** Why [blocks] isn't a legal declaration for [player], or null (CR 509.1a–c): it breaks a restriction, or obeys
     * fewer requirements than a declaration that breaks none could (CR 509.1c). */
    fun illegalBlock(snap: Snapshot, player: PlayerId, blocks: List<Block>): String? {
        blockRestriction(snap, player, blocks, whole = true)?.let { return it }
        val requirements = blockRequirements(snap, player)
        if (requirements.isEmpty()) return null
        val obeyed = requirements.count { it.obeyedBy(blocks) }
        val best = mostObeyedBlock(snap, player, requirements).second
        val most = requirements.count { it.obeyedBy(best) }
        if (obeyed >= most) return null
        val missed = requirements.first { !it.obeyedBy(blocks) }
        val what = if (missed.anyBlocker) "must be blocked if able" else "blocks each combat if able"
        return "${missed.creature} $what: $obeyed of the $most requirements that can be obeyed are (CR 509.1c)"
    }

    /** A declaration of blockers that obeys as many requirements as any legal one can (CR 509.1c); empty when there are
     * none. Each blocker blocks one attacker in it. */
    fun requiredBlock(snap: Snapshot, player: PlayerId): List<Block> =
        blockRequirements(snap, player).takeIf { it.isNotEmpty() }?.let { mostObeyedBlock(snap, player, it).second }.orEmpty()

    /** CR 509.1a–b: the restrictions; only "except by N or more" ([whole]) can be mended by adding blockers. */
    private fun blockRestriction(snap: Snapshot, player: PlayerId, blocks: List<Block>, whole: Boolean): String? {
        if (blocks.distinct().size != blocks.size) return "The same block twice"
        blocks.forEach { b -> if (!canBlock(snap, player, b.blocker, b.attacker)) return "${b.blocker} can't block ${b.attacker}" }
        blocks.groupBy { it.blocker }.forEach { (blocker, bs) ->
            val capacity = blockCapacity(snap, blocker)
            if (capacity != null && bs.size > capacity) return "$blocker can block only $capacity creature(s)"
        }
        // CR 509.1b: "can't block alone" (another blocker mends it)
        blocks.map { it.blocker }.distinct().singleOrNull()?.let { blocker ->
            if (whole && mechanics(snap, blocker).any { it.mechanic == Mechanic.CantBlockAlone }) return "$blocker can't block alone"
        }
        blocks.groupBy { it.attacker }.forEach { (attacker, bs) ->
            mechanics(snap, attacker).forEach { applied ->
                when (val m = applied.mechanic) {
                    // CR 702.111b: menace and similar
                    is Mechanic.MinimumBlockers -> if (whole && bs.size < m.count) return "$attacker can't be blocked except by ${m.count} or more creatures"
                    is Mechanic.MaximumBlockers -> if (bs.size > m.count) return "$attacker can't be blocked by more than ${m.count} creatures"
                    else -> Unit
                }
            }
        }
        return null
    }

    /** One requirement on [creature] (CR 509.1c): it blocks one of [attackers] (those it could block); with [anyBlocker]
     * ("must be blocked if able"), [creature] is the attacker and any creature blocking it obeys. */
    private class BlockRequirement(val creature: ObjectId, val attackers: Set<ObjectId>, val anyBlocker: Boolean = false) {
        fun obeyedBy(blocks: List<Block>) = blocks.any { (anyBlocker || it.blocker == creature) && it.attacker in attackers }
    }

    /** Each "blocks (…) each combat if able" on a creature [player] controls that could block a matching attacker. */
    private fun blockRequirements(snap: Snapshot, player: PlayerId): List<BlockRequirement> {
        val attackers = snap.state.combat?.attackers.orEmpty().map { it.id }
        return snap.state.battlefield.flatMap { id ->
            mechanics(snap, id).mapNotNull { applied ->
                val m = applied.mechanic as? Mechanic.MustBlock ?: return@mapNotNull null
                val e = snap.evaluator(applied.controller, self = applied.source ?: id, names = applied.names)
                attackers.filter { a -> e.matches(m.attackers, a) && canBlock(snap, player, id, a) }.toSet()
                    .takeIf { it.isNotEmpty() }?.let { BlockRequirement(id, it) }
            }
        } + attackers.filter { a ->
            // "must be blocked if able", when a creature of [player]'s could block it
            mechanics(snap, a).any { it.mechanic == Mechanic.MustBeBlocked } && snap.state.battlefield.any { canBlock(snap, player, it, a) }
        }.map { a -> BlockRequirement(a, setOf(a), anyBlocker = true) }
    }

    /** Creatures that must block choose among what they could block; others join only where "except by N or more"
     * makes a second blocker matter. */
    private fun mostObeyedBlock(snap: Snapshot, player: PlayerId, requirements: List<BlockRequirement>): Pair<Int, List<Block>> {
        val attackers = snap.state.combat?.attackers.orEmpty().map { it.id }
        val required = requirements.filter { !it.anyBlocker }.map { it.creature }.distinct()
        val mustBeBlocked = requirements.filter { it.anyBlocker }.flatMap { it.attackers }
        val needCompany = attackers.filter { a -> mechanics(snap, a).any { it.mechanic is Mechanic.MinimumBlockers } }
        // CR 509.1b: "can't block alone" makes a second blocker matter too
        val alone = snap.state.battlefield.any { b -> mechanics(snap, b).any { it.mechanic == Mechanic.CantBlockAlone } }
        val helped = (needCompany + mustBeBlocked + if (alone) attackers else emptyList()).distinct()
        val helpers = snap.state.battlefield.filter { it !in required && helped.any { a -> canBlock(snap, player, it, a) } }
        fun score(id: ObjectId, a: ObjectId) = requirements.count { (it.anyBlocker || it.creature == id) && a in it.attackers }
        // CR 509.1c: a cost to block needn't be paid to obey a requirement
        fun free(id: ObjectId, a: ObjectId) = canBlock(snap, player, id, a) && blockCosts(snap, listOf(Block(id, a))).isEmpty()
        val choices = required.map { id ->
            attackers.filter { free(id, it) }.map { a -> Block(id, a) to score(id, a) }
        } + helpers.map { id -> helped.filter { free(id, it) }.map { a -> Block(id, a) to score(id, a) } }
        return mostObeyed(choices, { blockRestriction(snap, player, it, whole = false) == null },
            { blockRestriction(snap, player, it, whole = true) == null })
    }

    /** Could [player] block anything at all (P5: nothing to declare otherwise)? */
    fun anyBlocker(snap: Snapshot, player: PlayerId): Boolean {
        val attackers = snap.state.combat?.attackers.orEmpty().map { it.id }
        return snap.state.battlefield.any { b -> attackers.any { a -> canBlock(snap, player, b, a) } }
    }

    /** The defending players (CR 506.2): those attacked, controllers of attacked planeswalkers and protectors of attacked
     * battles (310.9d). */
    fun defendingPlayers(snap: Snapshot): List<PlayerId> {
        val combat = snap.state.combat ?: return emptyList()
        val players = combat.attackers.mapNotNull { a -> defenderOf(snap, a.target) }.toSet()
        return snap.state.apnap().filter { it in players && !snap.state.player(it).lost }
    }
}
