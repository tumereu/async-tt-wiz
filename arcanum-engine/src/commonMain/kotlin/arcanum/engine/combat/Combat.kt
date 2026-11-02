package arcanum.engine.combat

import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.Mechanic
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Pending
import arcanum.engine.Progress
import arcanum.engine.casting.Snapshot
import arcanum.engine.casting.decisionId
import arcanum.engine.change.Change
import arcanum.engine.change.Changes
import arcanum.engine.change.DamageDealt
import arcanum.engine.change.Meaning
import arcanum.engine.change.Recorder
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Assignment
import arcanum.engine.decision.Continuation
import arcanum.engine.decision.Decision
import arcanum.engine.decision.Share
import arcanum.engine.decision.TurnAction
import arcanum.engine.input.Input
import arcanum.engine.state.Attacker
import arcanum.engine.state.Blocker
import arcanum.engine.state.CombatState
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef

/**
 * Combat (CR 506–511): declaring attackers and blockers ([CombatRules]), and the combat damage steps: first and double
 * strike (CR 510.4), trample (CR 702.19), deathtouch (CR 702.2c), lifelink (CR 702.15), infect and wither (CR 120.3),
 * dividing damage among several creatures (and the defender with trample) as its controller chooses whenever more than
 * one division is legal (CR 510.1c–d, 702.19b, [TurnAction.CombatDamage]). Declarations are inputs
 * ([Input.DeclareAttackers], [Input.DeclareBlockers]); while one is awaited nobody has priority. The changes carry
 * [Meaning.AttackersDeclared] and [Meaning.BlockersDeclared] for triggers (CR 508.1m, 509.1i); the last blockers
 * declaration also makes attackers blocked or unblocked (CR 509.1h). What leaves combat is removed after every event
 * ([removeWhatLeft], CR 506.4).
 */
internal object Combat {
    /** Whether the game waits for attackers or blockers to be declared (CR 508.1, 509.1). */
    fun waitingForDeclaration(s: GameState): Boolean = when (s.turn.step) {
        TurnStep.DeclareAttackers -> s.combat == null
        TurnStep.DeclareBlockers -> s.combat != null && pendingDefenders(Snapshot(s)).isNotEmpty()
        else -> false
    }

    /** Defending players who still have to declare blockers. */
    fun pendingDefenders(snap: Snapshot): List<PlayerId> {
        val combat = snap.state.combat ?: return emptyList()
        return CombatRules.defendingPlayers(snap).filter { it !in combat.blockersDeclaredBy }
    }

    /** As the declare attackers step begins: when nothing can attack, no attackers are declared (P5, CR 508.8). */
    fun beginDeclareAttackers(recorder: Recorder) {
        val s = recorder.state
        if (!CombatRules.anyAttacker(Snapshot(s), s.turn.active)) {
            recorder.record(Change.Combat(s.combat, CombatState(), listOf(Meaning.AttackersDeclared)))
        }
    }

    fun declareAttackers(recorder: Recorder, input: Input.DeclareAttackers): Progress {
        val s = recorder.state
        require(s.turn.step == TurnStep.DeclareAttackers && s.combat == null) { "Attackers are declared at the start of the declare attackers step" }
        require(input.player == s.turn.active) { "Only the active player declares attackers (CR 508.1)" }
        val snap = Snapshot(s)
        CombatRules.illegalAttack(snap, input.player, input.attacks)?.let { throw IllegalArgumentException("Illegal attack: $it") }
        // CR 508.1f: attacking creatures become tapped, unless they have vigilance
        input.attacks.forEach { a ->
            if (snap.view[a.attacker]?.has { it == Mechanic.AttacksWithoutTapping } != true) recorder.record(Changes.tap(recorder.state, a.attacker))
        }
        // CR 508.1k: they become attacking creatures
        recorder.record(Change.Combat(null, CombatState(attackers = input.attacks.map { Attacker(it.attacker, it.target) }),
            listOf(Meaning.AttackersDeclared)))
        return Progress.Done
    }

    /** As the declare blockers step begins: defending players who can't block anything declare no blockers (P5). */
    fun beginDeclareBlockers(recorder: Recorder) {
        val combat = recorder.state.combat ?: return
        val snap = Snapshot(recorder.state)
        val idle = pendingDefenders(snap).filterNot { CombatRules.anyBlocker(snap, it) }
        val after = decided(snap, combat.copy(blockersDeclaredBy = combat.blockersDeclaredBy + idle))
        if (after != combat) recorder.record(Change.Combat(combat, after, listOf(Meaning.BlockersDeclared)))
    }

    fun declareBlockers(recorder: Recorder, input: Input.DeclareBlockers): Progress {
        val s = recorder.state
        val snap = Snapshot(s)
        require(s.turn.step == TurnStep.DeclareBlockers && input.player in pendingDefenders(snap)) {
            "${input.player} isn't a defending player waiting to declare blockers (CR 509.1)"
        }
        CombatRules.illegalBlock(snap, input.player, input.blocks)?.let { throw IllegalArgumentException("Illegal block: $it") }
        val combat = s.combat!!
        val blockers = input.blocks.groupBy { it.blocker }.map { (b, bs) -> Blocker(b, bs.map { it.attacker }, input.player) }
        // CR 509.1g: they become blocking creatures; CR 509.1h: with the last declaration, attackers become blocked or
        // unblocked in the same event, so triggers on it see the final state (CR 509.3c, 509.3g)
        recorder.record(Change.Combat(combat, decided(snap, combat.copy(blockers = combat.blockers + blockers,
            blockersDeclaredBy = combat.blockersDeclaredBy + input.player)), listOf(Meaning.BlockersDeclared)))
        return Progress.Done
    }

    /** CR 509.1h: once every defending player has declared blockers, an attacker with blockers is blocked, one without
     * is unblocked; [combat] as it is until then. */
    private fun decided(snap: Snapshot, combat: CombatState): CombatState {
        if (combat.blocksDecided || CombatRules.defendingPlayers(snap).any { it !in combat.blockersDeclaredBy }) return combat
        val blocked = combat.blockers.flatMap { it.attackers }.toSet()
        return combat.copy(attackers = combat.attackers.map { it.copy(blocked = it.blocked || it.id in blocked) }, blocksDecided = true)
    }

    /**
     * CR 506.4: removes from combat what no longer belongs in it: an attacker or blocker that left the battlefield,
     * phased out, stopped being a creature or became a battle, or whose controller changed (an attacker not controlled
     * by the attacking player, CR 506.3b; a blocker not controlled by the player who declared it, CR 509.1g). Run after
     * every event ([Recorder.record]), so the removal is its own change ([Meaning.RemovedFromCombat]) right after the
     * event that caused it, and nothing later sees a stale combat. An attacker stays blocked when its blockers go
     * (CR 509.1h); a blocker whose attackers went stays blocking (CR 510.1d).
     */
    fun removeWhatLeft(recorder: Recorder) {
        val s = recorder.state
        val combat = s.combat ?: return
        if (combat.attackers.isEmpty() && combat.blockers.isEmpty()) return
        val snap = Snapshot(s)
        fun stays(id: ObjectId, controller: PlayerId?): Boolean {
            if (!onBattlefield(s, id)) return false
            val c = snap.view[id]?.characteristics ?: return false
            if (!c.isType(CardType.Creature) || c.isType(CardType.Battle)) return false
            return controller == null || snap.board.controller(id) == controller
        }
        val attackers = combat.attackers.filter { stays(it.id, s.turn.active) }
        val gone = combat.attackers.map { it.id }.toSet() - attackers.map { it.id }.toSet()
        val blockers = combat.blockers.filter { stays(it.id, it.player) }
            .map { b -> if (b.attackers.any { it in gone }) b.copy(attackers = b.attackers.filter { it !in gone }) else b }
        if (gone.isEmpty() && blockers == combat.blockers) return
        recorder.record(Change.Combat(combat, combat.copy(attackers = attackers, blockers = blockers), listOf(Meaning.RemovedFromCombat)))
    }

    // ---- Combat damage (CR 510)

    /** As a combat damage step begins: who deals damage in it (CR 510.4), their assignments, decisions where there is a
     * choice, then all the damage at once (CR 510.2). */
    fun damageStep(recorder: Recorder): Progress {
        val s = recorder.state
        val combat = s.combat ?: return Progress.Done
        val snap = Snapshot(s)
        val inCombat = (combat.attackers.map { it.id } + combat.blockers.map { it.id }).filter { onBattlefield(s, it) }
        val firstStep = when {
            // The second of two combat damage steps
            combat.firstStrikeStep -> false
            combat.firstStrikers == null && inCombat.any { hasFirstStrike(snap, it) || hasDoubleStrike(snap, it) } -> true
            else -> false
        }
        // CR 510.4: who assigns damage in this step
        val dealers = inCombat.filter { id ->
            when {
                firstStep -> hasFirstStrike(snap, id) || hasDoubleStrike(snap, id)
                combat.firstStrikers != null -> id !in combat.firstStrikers!! || hasDoubleStrike(snap, id)
                else -> true
            }
        }
        val updated = when {
            combat.firstStrikeStep -> combat.copy(firstStrikeStep = false)
            firstStep -> combat.copy(firstStrikeStep = true, firstStrikers = dealers)
            else -> combat
        }
        if (updated != combat) recorder.record(Change.Combat(combat, updated))
        // CR 510.1: the active player's attackers first, then the blockers
        val ordered = dealers.filter { id -> updated.attackers.any { it.id == id } } +
            dealers.filter { id -> updated.blockers.any { it.id == id } && updated.attackers.none { it.id == id } }
        val assigned = mutableListOf<Assignment>()
        val toDecide = mutableListOf<ObjectId>()
        ordered.forEach { id ->
            when (val plan = assignmentOf(snap, id, assigned)) {
                is Plan.Fixed -> assigned += plan.assignments
                is Plan.Choice -> toDecide += id
            }
        }
        return continueAssigning(recorder, TurnAction.CombatDamage(firstStep, assigned, toDecide))
    }

    private fun onBattlefield(s: GameState, id: ObjectId) = s.obj(id)?.let { it.zone == Zone.Battlefield && !it.phasedOut } == true

    private fun hasFirstStrike(snap: Snapshot, id: ObjectId) = snap.view[id]?.has { it == Mechanic.FirstStrike } == true
    private fun hasDoubleStrike(snap: Snapshot, id: ObjectId) = snap.view[id]?.has { it == Mechanic.DoubleStrike } == true
    private fun has(snap: Snapshot, id: ObjectId, m: Mechanic) = snap.view[id]?.has { it == m } == true
    private fun trample(snap: Snapshot, id: ObjectId) = snap.view[id]?.has { it is Mechanic.Trample } == true

    /** How a creature's combat damage is assigned: fixed, or a choice among [Plan.Choice.among]. */
    private sealed interface Plan {
        data class Fixed(val assignments: List<Assignment>) : Plan
        data class Choice(
            val amount: Int, val among: List<TargetRef>, val lethal: List<Share>, val trampleTo: TargetRef?, val suggested: List<Share>,
        ) : Plan
    }

    /** CR 510.1a: power (or toughness) of damage; 0 or less assigns none. */
    private fun amount(snap: Snapshot, id: ObjectId): Int {
        val c = snap.view[id]?.characteristics ?: return 0
        if (has(snap, id, Mechanic.AssignsNoCombatDamage)) return 0
        return (if (has(snap, id, Mechanic.AssignsDamageByToughness)) c.toughness else c.power)?.coerceAtLeast(0) ?: 0
    }

    /** Lethal damage for [target] from [source] (CR 120.4a, 702.19b): toughness less damage already marked and damage
     * other creatures assign to it in this step ([assigned]); any damage from a deathtouch source is lethal (CR 702.2c). */
    private fun lethal(snap: Snapshot, source: ObjectId, target: ObjectId, assigned: List<Assignment>): Int {
        val other = assigned.filter { it.source != source && it.target == TargetRef.Object(target) }.sumOf { it.amount }
        val remaining = ((snap.view[target]?.characteristics?.toughness ?: 0) - (snap.state.obj(target)?.damage ?: 0) - other).coerceAtLeast(0)
        return if (remaining > 0 && has(snap, source, Mechanic.Deathtouch)) 1 else remaining
    }

    /** How [id] assigns its combat damage, given what others have assigned in this step so far ([assigned]). */
    private fun assignmentOf(snap: Snapshot, id: ObjectId, assigned: List<Assignment>): Plan {
        val s = snap.state
        val combat = s.combat!!
        val n = amount(snap, id)
        if (n <= 0) return Plan.Fixed(emptyList())
        val attack = combat.attackers.firstOrNull { it.id == id }
        if (attack != null) {
            val target = attack.target.takeIf { stillAttackable(snap, it) }
            // CR 510.1b: unblocked, to what it attacks
            if (!attack.blocked) return Plan.Fixed(listOfNotNull(target?.let { Assignment(id, it, n) }))
            val blockers = combat.blockers.filter { id in it.attackers && onBattlefield(s, it.id) }.map { it.id }
            val tramples = trample(snap, id)
            return when {
                // CR 510.1c, 702.19d: blocked with no blockers left: no damage, unless it has trample
                blockers.isEmpty() -> Plan.Fixed(if (tramples && target != null) listOf(Assignment(id, target, n)) else emptyList())
                blockers.size == 1 && !tramples -> Plan.Fixed(listOf(Assignment(id, TargetRef.Object(blockers.single()), n)))
                else -> divide(snap, id, n, blockers, if (tramples) target else null, assigned)
            }
        }
        // CR 510.1d: a blocker's damage goes to what it blocks
        val blocks = combat.blockers.firstOrNull { it.id == id }?.attackers.orEmpty().filter { onBattlefield(s, it) }
        return when (blocks.size) {
            0 -> Plan.Fixed(emptyList())
            1 -> Plan.Fixed(listOf(Assignment(id, TargetRef.Object(blocks.single()), n)))
            else -> divide(snap, id, n, blocks, null, assigned)
        }
    }

    /** Damage among several creatures, or among a blocker and the attacked player or planeswalker with trample: divided
     * as its controller chooses (CR 510.1c–d, 702.19b), even when there is enough for lethal damage to each; fixed only
     * when one division is legal (one blocker and no more than lethal damage for it). The suggestion gives lethal damage
     * to each in turn, then the rest to the defender with trample, else to the first; short of that, the rest to the
     * next. */
    private fun divide(snap: Snapshot, source: ObjectId, n: Int, creatures: List<ObjectId>, trampleTo: TargetRef?, assigned: List<Assignment>): Plan {
        val lethal = creatures.map { Share(TargetRef.Object(it), lethal(snap, source, it, assigned)) }
        if (creatures.size == 1 && (trampleTo == null || n <= lethal.single().amount)) {
            return Plan.Fixed(listOf(Assignment(source, TargetRef.Object(creatures.single()), n)))
        }
        var left = n
        val suggested = lethal.map { l -> Share(l.target, minOf(left, l.amount).also { left -= it }) }.toMutableList()
        if (left > 0) {
            if (trampleTo != null) suggested += Share(trampleTo, left)
            else suggested[0] = suggested[0].copy(amount = suggested[0].amount + left)
        }
        return Plan.Choice(n, lethal.map { it.target } + listOfNotNull(trampleTo), lethal, trampleTo, suggested)
    }

    private fun merge(list: List<Assignment>): List<Assignment> =
        list.groupBy { it.source to it.target }.map { (k, v) -> Assignment(k.first, k.second, v.sumOf { it.amount }) }

    private fun stillAttackable(snap: Snapshot, target: TargetRef): Boolean = when (target) {
        is TargetRef.Player -> !snap.state.player(target.id).lost
        // CR 506.4c, 510.1b: a planeswalker that left the battlefield or stopped being one takes no damage
        is TargetRef.Object -> onBattlefield(snap.state, target.id) &&
            snap.view[target.id]?.characteristics?.let { it.isType(CardType.Planeswalker) || it.isType(CardType.Battle) } == true
    }

    /** Asks for the next assignment with a choice, or deals all the damage. */
    private fun continueAssigning(recorder: Recorder, action: TurnAction.CombatDamage): Progress {
        val next = action.attackers.firstOrNull() ?: return deal(recorder, action)
        val snap = Snapshot(recorder.state)
        val plan = when (val p = assignmentOf(snap, next, action.assigned)) {
            is Plan.Choice -> p
            is Plan.Fixed -> return continueAssigning(recorder, action.copy(assigned = action.assigned + p.assignments, attackers = action.attackers.drop(1)))
        }
        val player = snap.board.controller(next) ?: recorder.state.turn.active
        val decision = Decision.Divide(decisionId(recorder.state, "combat-damage:$next"), player, next, plan.amount, plan.among,
            minEach = 0, lethal = plan.lethal, suggested = plan.suggested)
        return Progress.Waiting(Pending(decision, Continuation.TurnBased(action)))
    }

    /** The answer to a damage assignment (CR 510.1c–d, 702.19b): it must use all the damage, only on those it may go
     * to, and with trample the defender gets damage only once each blocker has lethal damage. */
    fun resume(recorder: Recorder, continuation: Continuation.TurnBased, answer: Answer): Progress {
        val action = continuation.action as TurnAction.CombatDamage
        val source = action.attackers.first()
        val snap = Snapshot(recorder.state)
        val plan = assignmentOf(snap, source, action.assigned) as? Plan.Choice ?: throw IllegalStateException("No choice for $source")
        val shares = (answer as? Answer.Division)?.shares ?: throw IllegalArgumentException("Expected a division of damage")
        require(shares.all { it.target in plan.among && it.amount >= 0 }) { "Damage goes only to ${plan.among}" }
        require(shares.map { it.target }.distinct().size == shares.size) { "Each one once" }
        require(shares.sumOf { it.amount } == plan.amount) { "Assign all ${plan.amount} damage" }
        plan.trampleTo?.let { defender ->
            val toDefender = shares.filter { it.target == defender }.sumOf { it.amount }
            val allLethal = plan.lethal.all { l -> shares.filter { it.target == l.target }.sumOf { it.amount } >= l.amount }
            require(toDefender == 0 || allLethal) { "Trample: the defender gets damage only after lethal damage to each blocker (CR 702.19b)" }
        }
        val assignments = shares.filter { it.amount > 0 }.map { Assignment(source, it.target, it.amount) }
        return continueAssigning(recorder, action.copy(assigned = action.assigned + assignments, attackers = action.attackers.drop(1)))
    }

    /** CR 510.2, 120.3: all combat damage at once, as one damage event through the event pipeline (prevention,
     * replacements, results, lifelink: [arcanum.engine.action.Actions.damage]); a replacement order to choose waits
     * ([Continuation.Event]). */
    private fun deal(recorder: Recorder, action: TurnAction.CombatDamage): Progress {
        val list = action.assigned.filter { it.amount > 0 }
        if (list.isEmpty()) return Progress.Done
        val dealt = list.map { DamageDealt(it.source, it.target, it.amount, combat = true) }
        return arcanum.engine.action.Events.run(recorder, arcanum.engine.action.PlannedEvent.Damage(dealt, recorder.state.turn.active))
    }
}
