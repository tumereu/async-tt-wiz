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

    /** What [player] may attack: each opponent still in the game and the planeswalkers they control (CR 506.2, 508.1b). */
    fun attackTargets(snap: Snapshot, player: PlayerId): List<TargetRef> {
        val opponents = snap.state.opponents(player)
        val walkers = snap.state.battlefield.filter { id ->
            onBattlefield(snap, id) && snap.view[id]?.characteristics?.isType(CardType.Planeswalker) == true &&
                snap.board.controller(id) in opponents
        }
        return opponents.map { TargetRef.Player(it) } + walkers.map { TargetRef.Object(it) }
    }

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

    /** Why [attacks] isn't a legal declaration for [player], or null (CR 508.1a–d). */
    fun illegalAttack(snap: Snapshot, player: PlayerId, attacks: List<Attack>): String? {
        if (attacks.map { it.attacker }.distinct().size != attacks.size) return "A creature attacks only once"
        attacks.forEach { a -> if (!canAttack(snap, player, a.attacker, a.target)) return "${a.attacker} can't attack ${a.target}" }
        // CR 508.1c: "no more than N creatures can attack": counted per effect among the creatures it restricts
        attacks.flatMap { a -> mechanics(snap, a.attacker).filter { it.mechanic is Mechanic.MaximumAttackers }.map { it to a } }
            .groupBy({ (applied, _) -> Triple(applied.source, applied.origin, applied.effect) }, { (applied, a) -> applied to a })
            .forEach { (_, list) ->
                val m = list.first().first.mechanic as Mechanic.MaximumAttackers
                val counted = list.filter { (applied, a) -> m.target == null || a.target in recipients(snap, applied, a.attacker, m.target!!) }
                if (counted.size > m.count) return "No more than ${m.count} creature(s) can attack"
            }
        // CR 508.1d: requirements, as many as can be obeyed (simplified: each creature that must attack and can, does)
        val attacking = attacks.map { it.attacker }.toSet()
        snap.state.battlefield.forEach { id ->
            if (id !in attacking && mechanics(snap, id).any { it.mechanic is Mechanic.MustAttack } && canAttack(snap, player, id, null)) {
                return "$id attacks each combat if able"
            }
        }
        return null
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
        val defending = when (val t = attack.target) {
            is TargetRef.Player -> t.id
            is TargetRef.Object -> snap.board.controller(t.id)
        }
        if (defending != player) return false
        val blockerOk = mechanics(snap, blocker).none { applied ->
            val e = snap.evaluator(applied.controller, self = applied.source ?: blocker, names = applied.names)
            when (val m = applied.mechanic) {
                is Mechanic.CantBlock -> e.matches(m.attackers, attacker)
                is Mechanic.CanBlockOnly -> !e.matches(m.attackers, attacker)
                else -> false
            }
        }
        val attackerOk = mechanics(snap, attacker).none { applied ->
            val e = snap.evaluator(applied.controller, self = applied.source ?: attacker, names = applied.names)
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

    /** Why [blocks] isn't a legal declaration for [player], or null (CR 509.1a–c). */
    fun illegalBlock(snap: Snapshot, player: PlayerId, blocks: List<Block>): String? {
        if (blocks.distinct().size != blocks.size) return "The same block twice"
        blocks.forEach { b -> if (!canBlock(snap, player, b.blocker, b.attacker)) return "${b.blocker} can't block ${b.attacker}" }
        blocks.groupBy { it.blocker }.forEach { (blocker, bs) ->
            val capacity = blockCapacity(snap, blocker)
            if (capacity != null && bs.size > capacity) return "$blocker can block only $capacity creature(s)"
        }
        // CR 509.1c: requirements (simplified: a creature that must block and can block a matching attacker, does)
        val blocking = blocks.map { it.blocker }.toSet()
        snap.state.battlefield.forEach { id ->
            if (id in blocking) return@forEach
            mechanics(snap, id).forEach { applied ->
                val m = applied.mechanic as? Mechanic.MustBlock ?: return@forEach
                val e = snap.evaluator(applied.controller, self = applied.source ?: id, names = applied.names)
                val could = snap.state.combat?.attackers.orEmpty().any { a -> e.matches(m.attackers, a.id) && canBlock(snap, player, id, a.id) }
                if (could) return "$id blocks each combat if able"
            }
        }
        blocks.groupBy { it.attacker }.forEach { (attacker, bs) ->
            mechanics(snap, attacker).forEach { applied ->
                when (val m = applied.mechanic) {
                    // CR 702.111b: menace and similar
                    is Mechanic.MinimumBlockers -> if (bs.size < m.count) return "$attacker can't be blocked except by ${m.count} or more creatures"
                    is Mechanic.MaximumBlockers -> if (bs.size > m.count) return "$attacker can't be blocked by more than ${m.count} creatures"
                    else -> Unit
                }
            }
        }
        return null
    }

    /** Could [player] block anything at all (P5: nothing to declare otherwise)? */
    fun anyBlocker(snap: Snapshot, player: PlayerId): Boolean {
        val attackers = snap.state.combat?.attackers.orEmpty().map { it.id }
        return snap.state.battlefield.any { b -> attackers.any { a -> canBlock(snap, player, b, a) } }
    }

    /** The defending players (CR 506.2): those attacked, and controllers of attacked planeswalkers. */
    fun defendingPlayers(snap: Snapshot): List<PlayerId> {
        val combat = snap.state.combat ?: return emptyList()
        val players = combat.attackers.mapNotNull { a ->
            when (val t = a.target) {
                is TargetRef.Player -> t.id
                is TargetRef.Object -> snap.board.controller(t.id)
            }
        }.toSet()
        return snap.state.apnap().filter { it in players && !snap.state.player(it).lost }
    }
}
