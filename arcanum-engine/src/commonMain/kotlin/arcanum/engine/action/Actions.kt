package arcanum.engine.action

import arcanum.api.Affected
import arcanum.api.CardDefinition
import arcanum.api.CardType
import arcanum.api.CopyException
import arcanum.api.CounterKind
import arcanum.api.Duration
import arcanum.api.EventRole
import arcanum.api.FaceDefinition
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.Objects
import arcanum.api.Zone
import arcanum.engine.change.Change
import arcanum.engine.change.Changes
import arcanum.engine.change.DamageDealt
import arcanum.engine.change.Meaning
import arcanum.engine.change.Move
import arcanum.engine.change.Placed
import arcanum.engine.decision.Answer
import arcanum.engine.decision.RandomKind
import arcanum.engine.decision.WaitingTrigger
import arcanum.engine.query.Context
import arcanum.engine.query.Evaluator
import arcanum.engine.resolution.Scope
import arcanum.engine.resolution.Steps
import arcanum.engine.state.Bindings
import arcanum.engine.state.ControlSince
import arcanum.engine.state.CounterEntry
import arcanum.engine.state.EffectId
import arcanum.engine.state.EffectObject
import arcanum.engine.state.GameObject
import arcanum.engine.state.NamedValue
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.RoleObjects
import arcanum.engine.state.StatusFlag
import arcanum.engine.state.TargetRef
import arcanum.engine.state.TokenIds
import arcanum.engine.state.TriggerEvent

/**
 * One object's planned zone change before replacements (CR 400.7, 614): where it goes, at which [position], under whose
 * [controller] onto the battlefield (CR 110.2a), tapped, face down, with [counters] (CR 122.6), with its [meanings]
 * (D33), attached to [attachTo] (an Aura, CR 303.4f), with its back face up ([transformed]) or attacking.
 */
internal data class PlannedMove(
    val id: ObjectId,
    val to: Zone,
    val position: Changes.Position? = null,
    val controller: PlayerId? = null,
    val tapped: Boolean = false,
    val faceDown: Boolean = false,
    val counters: List<Pair<CounterKind, Int>> = emptyList(),
    val meanings: List<Meaning> = emptyList(),
    val attachTo: TargetRef? = null,
    val transformed: Boolean = false,
    val attacking: TargetRef? = null,
    /** Set by "as enters" steps and modifications (CR 614.12): card memory, copy, extra abilities. */
    val memory: Bindings = Bindings.EMPTY,
    val copyOf: Pair<ObjectId, List<CopyException>>? = null,
    val abilities: List<arcanum.api.Ability> = emptyList(),
)

/**
 * The event pipeline (spec §7.3): every game action an effect, a state-based action or a turn-based action performs
 * goes through here, so replacement and prevention effects (CR 614–616) apply and every result is a reversible change
 * with its Magic meaning (D33, D59). Each function returns what actually happened.
 */
internal object Actions {
    // ---- Zone changes (CR 400.7)

    /**
     * Moves objects as one event (one tagged zone change, D44), each after its replacements (CR 614): a different
     * destination, entering tapped or with counters, "as enters" steps (CR 614.12), regeneration shields replacing
     * destruction (CR 701.19a), "instead" effects. Stale ids are skipped (CR 400.7). Returns old id → new id of each
     * object that moved.
     */
    fun move(act: Act, cause: Cause?, planned: List<PlannedMove>, noRegeneration: Boolean = false): Map<ObjectId, ObjectId> {
        val linked = mutableListOf<WaitingTrigger>()
        val final = planned.mapNotNull { pm ->
            val o = act.obj(pm.id) ?: return@mapNotNull null
            Replacements.apply(
                act, cause, pm, affected = act.controllerOf(pm.id) ?: o.owner, about = pm.id,
                proposed = { m -> Changes.move(act.state, listOf(m.id), m.to, meanings = m.meanings) },
                modify = { m, r, _ -> modifyMove(act, m, r) },
                instead = { r, instance -> runInstead(act, r, instance) },
                linked = linked,
                // CR 701.19c: "can't be regenerated"
                skip = { r -> isRegeneration(r) && (noRegeneration || act.view()[pm.id]?.has { it == Mechanic.CantBeRegenerated } == true) },
            )
        }
        val result = commitMoves(act, final)
        Replacements.addLinked(act, linked)
        return result
    }

    /** Names a regeneration shield keeps its creature under (D26). */
    const val REGENERATED = "\$regenerated"

    private fun isRegeneration(r: Replacement): Boolean = r.names[REGENERATED] != null

    private fun modifyMove(act: Act, m: PlannedMove, r: Replacement): PlannedMove? {
        var move = m
        val e = Evaluator(act.board(), Context(you = r.controller, self = r.source ?: m.id, names = r.names))
        for (mod in r.mechanic.modify) {
            move = when (mod) {
                Modification.Skip -> return null
                is Modification.ChangeDestination -> move.copy(
                    to = mod.to.zone,
                    position = mod.to.libraryPosition?.let { libraryPosition(it) },
                    tapped = move.tapped || mod.to.tapped, faceDown = mod.to.faceDown, transformed = mod.to.transformed,
                    controller = mod.to.controller?.let { e.players(it).firstOrNull() } ?: move.controller,
                )
                Modification.EntersTapped -> if (move.to == Zone.Battlefield) move.copy(tapped = true) else move
                is Modification.EntersWithCounters -> if (move.to == Zone.Battlefield)
                    move.copy(counters = move.counters + (mod.kind to e.value(mod.amount))) else move
                is Modification.EntersUnderControlOf -> move.copy(controller = e.players(mod.players).firstOrNull() ?: move.controller)
                is Modification.EntersWithAbilities -> move.copy(abilities = move.abilities + mod.abilities)
                is Modification.EntersAsCopy -> {
                    val options = e.each(mod.of).filter { it != move.id }
                    val chooser = r.controller
                    val chosen = act.chooseObjects(chooser, move.id, options, if (mod.optional) 0 else 1, 1, "Enter as a copy of")
                    chosen.firstOrNull()?.let { move.copy(copyOf = it to mod.except) } ?: move
                }
                is Modification.AsEnters -> Steps.asEnters(act, r, move, mod.steps) ?: return null
                // Amounts, recipients and extra tokens don't apply to a zone change.
                is Modification.MultiplyAmount, is Modification.AddAmount, is Modification.ChangeRecipient,
                is Modification.AlsoCreate -> move
            }
        }
        return move
    }

    fun libraryPosition(position: Int): Changes.Position = when {
        position < 0 -> Changes.Position.Bottom
        position == 0 -> Changes.Position.Top
        else -> Changes.Position.At(position)
    }

    /** Runs a replacement's "instead" instructions with the replaced event's objects (CR 614.1a). */
    fun runInstead(act: Act, r: Replacement, instance: TriggerEvent) {
        val cause = Cause(r.controller, r.source, key = "instead:${act.state.nextTimestamp()}")
        Steps.run(Scope(act, cause, r.names, instance), r.mechanic.instead!!.steps)
    }

    /** Makes one zone-change event of [moves] (stamped in order, D34), with what everyone saw of each object. */
    private fun commitMoves(act: Act, moves: List<PlannedMove>): Map<ObjectId, ObjectId> {
        if (moves.isEmpty()) return emptyMap()
        val scratch = act.state.deepCopy()
        val all = mutableListOf<Move>()
        // Copy effects and extra abilities exist as the object enters (CR 614.12, 707.4), so its own enters triggers see them.
        moves.forEach { m ->
            val next = (m.id as ObjectId.Card).next()
            m.copyOf?.let { (of, except) ->
                act.record(Changes.effect(act.state) { ts -> lockedEffect("E$ts", listOf(Mechanic.CopyOf(Objects.Bound(COPIED), except)), next, m.controller, ts, act,
                    Bindings.EMPTY.with(COPIED, NamedValue.Objects(listOf(of))), copied = arcanum.engine.view.Copiable(act.state).faces(of)?.firstOrNull()?.face) })
            }
            if (m.abilities.isNotEmpty()) act.record(Changes.effect(act.state) { ts ->
                lockedEffect("E$ts", listOf(Mechanic.GainAbilities(m.abilities)), next, m.controller, ts, act)
            })
        }
        val snapshots = moves.associate { it.id to act.snapshot(it.id) }
        moves.forEach { m ->
            val before = scratch.objOrThrow(m.id)
            val toBattlefield = m.to == Zone.Battlefield
            val zc = Changes.move(
                scratch, listOf(m.id), m.to, position = m.position ?: if (m.to == Zone.Library) Changes.Position.Top else Changes.Position.Bottom,
                controller = m.controller, tapped = m.tapped && toBattlefield, faceDown = m.faceDown, meanings = m.meanings,
                counters = if (toBattlefield || m.to == Zone.Exile) m.counters.filter { it.second > 0 } else emptyList(),
            )
            val moved = zc.moves.single().let { mv ->
                var after = mv.after
                if (m.memory.values.isNotEmpty()) after = after.copy(memory = m.memory)
                if (toBattlefield && m.attachTo != null) after = after.copy(attachedTo = m.attachTo)
                if (toBattlefield && m.transformed && before.definition?.layout == Layout.Transform) after = after.copy(face = 1)
                if (!toBattlefield && m.to != Zone.Stack) after = after.copy(controller = null)
                mv.copy(after = after, seen = snapshots[m.id])
            }
            scratch.apply(Change.ZoneChange(listOf(moved)))
            all += moved
        }
        act.record(Change.ZoneChange(all))
        // Put onto the battlefield attacking (CR 508.4): not declared as an attacker.
        moves.filter { it.attacking != null && it.to == Zone.Battlefield }.forEach { m ->
            val combat = act.state.combat ?: return@forEach
            val after = all.first { it.before.id == m.id }.after.id
            act.record(Change.Combat(combat, combat.copy(attackers = combat.attackers + arcanum.engine.state.Attacker(after, m.attacking!!))))
        }
        return all.associate { it.before.id to it.after.id }
    }

    private const val COPIED = "\$copied"

    private fun lockedEffect(
        id: String, mechanics: List<Mechanic>, on: ObjectId, controller: PlayerId?, ts: Long, act: Act,
        names: Bindings = Bindings.EMPTY, copied: FaceDefinition? = null,
    ) = EffectObject(
        id = EffectId(id), mechanics = mechanics, affects = Affected.Objects(Objects.Self), locked = listOf(on), source = on,
        controller = controller ?: act.state.turn.active, timestamp = ts, createdTurn = act.state.turn.number,
        duration = Duration.Indefinitely, names = names, copied = copied,
    )

    /** Destroys [ids] (CR 701.8): indestructible permanents aren't (CR 702.12b); regeneration shields replace it (CR
     * 701.19a) unless [noRegeneration] (CR 701.19c). */
    fun destroy(act: Act, cause: Cause?, ids: List<ObjectId>, noRegeneration: Boolean = false, extra: List<Meaning> = emptyList()): Map<ObjectId, ObjectId> {
        val view = act.view()
        val destroyable = ids.distinct().filter { id ->
            act.obj(id)?.zone == Zone.Battlefield && view[id]?.has { it == Mechanic.Indestructible } != true
        }
        return move(act, cause, destroyable.map { PlannedMove(it, Zone.Graveyard, meanings = listOf(Meaning.Destroyed) + extra) }, noRegeneration)
    }

    /** Sacrifices [ids] (CR 701.21): only permanents, and not those that can't be sacrificed. */
    fun sacrifice(act: Act, cause: Cause?, ids: List<ObjectId>): Map<ObjectId, ObjectId> {
        val view = act.view()
        val ok = ids.distinct().filter { act.obj(it)?.zone == Zone.Battlefield && view[it]?.has { m -> m == Mechanic.CantBeSacrificed } != true }
        return moveTo(act, cause, ok, Zone.Graveyard, listOf(Meaning.Sacrificed))
    }

    /** Moves [ids] to their owners' [to] (CR 400.3). */
    fun moveTo(
        act: Act, cause: Cause?, ids: List<ObjectId>, to: Zone, meanings: List<Meaning> = emptyList(),
        controller: PlayerId? = null, position: Changes.Position? = null,
    ): Map<ObjectId, ObjectId> = move(act, cause, ids.map { PlannedMove(it, to, position, controller, meanings = meanings) })

    // ---- Damage (CR 120)

    /** What a damage event did: the damage each part actually dealt after prevention and replacement, and the excess
     * over lethal per recipient (CR 120.4a). */
    data class DamageResult(val dealt: List<DamageDealt>, val excess: Int) {
        val total: Int get() = dealt.sumOf { it.amount }
    }

    /**
     * Deals [parts] as one damage event (CR 120, D44): replacements first (redirection, doubling, CR 614.9), then
     * prevention (protection CR 702.16e, prevention effects and shields CR 615) unless it can't be prevented (CR 615.12).
     * Results (CR 120.3): life loss or poison (infect, CR 702.90b) for players; damage or −1/−1 counters (wither, infect)
     * for creatures; loyalty and defense counters removed from planeswalkers and battles; lifelink life gain (CR 702.15b);
     * toxic poison counters (CR 702.164c).
     */
    fun damage(act: Act, cause: Cause?, parts: List<DamageDealt>, cantBePrevented: Boolean = false): DamageResult {
        val linked = mutableListOf<WaitingTrigger>()
        val after = parts.filter { it.amount > 0 }.mapNotNull { part ->
            val affected = when (val r = part.recipient) {
                is TargetRef.Player -> r.id
                is TargetRef.Object -> act.controllerOf(r.id) ?: act.state.turn.active
            }
            Replacements.apply(
                act, cause, part, affected, part.source,
                proposed = { d -> Change.Damage(listOf(d)) },
                modify = { d, r, _ -> modifyDamage(act, d, r) },
                instead = { r, instance -> runInstead(act, r, instance) },
                linked = linked,
            )
        }.filter { recipientExists(act, it.recipient) }
            .map { prevent(act, it, cantBePrevented) }.filter { it.amount > 0 }
        if (after.isEmpty()) return DamageResult(emptyList(), 0)
        val view = act.view()
        val board = act.board()
        val dealt = after.map { it.copy(sourceSeen = it.sourceSeen ?: act.snapshot(it.source)) }
        act.record(Change.Damage(dealt))
        var excess = 0
        val lifelink = mutableMapOf<PlayerId, Int>()
        dealt.forEach { d ->
            val source = board.characteristics(d.source)
            val keywords = source?.keywords.orEmpty().map { it.kind }.toSet()
            when (val r = d.recipient) {
                is TargetRef.Player -> {
                    if (Keyword.Infect in keywords) act.record(Changes.counters(act.state, r, CounterKind.Poison, d.amount))
                    else act.record(Changes.life(act.state, r.id, -d.amount, listOf(Meaning.LifeLost)))
                    val toxic = source?.keywords.orEmpty().filterIsInstance<Keyword.Toxic>().sumOf { it.amount }
                    if (d.combat && toxic > 0) act.record(Changes.counters(act.state, r, CounterKind.Poison, toxic))
                }
                is TargetRef.Object -> {
                    val o = act.obj(r.id) ?: return@forEach
                    val c = view[r.id]?.characteristics
                    if (c?.isType(CardType.Creature) == true) {
                        val lethal = (c.toughness ?: 0) - o.damage
                        excess += (d.amount - (if (Keyword.Deathtouch in keywords) minOf(1, lethal) else lethal).coerceAtLeast(0)).coerceAtLeast(0)
                        if (Keyword.Wither in keywords || Keyword.Infect in keywords) {
                            act.record(Changes.counters(act.state, r, CounterKind.PowerToughness(-1, -1), d.amount))
                            if (Keyword.Deathtouch in keywords) {
                                val now = act.obj(r.id)!!
                                act.record(Change.DamageMarked(r.id, now.damage, now.damage, now.deathtouchDamage, true))
                            }
                        } else act.record(Changes.damageMarked(act.state, r.id, d.amount, deathtouch = Keyword.Deathtouch in keywords))
                    }
                    if (c?.isType(CardType.Planeswalker) == true) {
                        val loyalty = o.counters(CounterKind.Loyalty)
                        if (c.isType(CardType.Creature).not()) excess += (d.amount - loyalty).coerceAtLeast(0)
                        act.record(Changes.counters(act.state, r, CounterKind.Loyalty, -minOf(d.amount, loyalty)))
                    }
                    if (c?.isType(CardType.Battle) == true) {
                        val defense = o.counters(CounterKind.Defense)
                        act.record(Changes.counters(act.state, r, CounterKind.Defense, -minOf(d.amount, defense)))
                    }
                }
            }
            if (Keyword.Lifelink in keywords) {
                val controller = board.controller(d.source) ?: return@forEach
                lifelink[controller] = (lifelink[controller] ?: 0) + d.amount
            }
        }
        lifelink.forEach { (player, amount) -> gainLife(act, cause, player, amount) }
        Replacements.addLinked(act, linked)
        return DamageResult(dealt, excess)
    }

    private fun recipientExists(act: Act, ref: TargetRef): Boolean = when (ref) {
        is TargetRef.Player -> !act.state.player(ref.id).lost
        is TargetRef.Object -> act.obj(ref.id)?.zone == Zone.Battlefield
    }

    private fun modifyDamage(act: Act, d: DamageDealt, r: Replacement): DamageDealt? {
        var damage = d
        val e = Evaluator(act.board(), Context(you = r.controller, self = r.source, names = r.names))
        for (mod in r.mechanic.modify) {
            damage = when (mod) {
                Modification.Skip -> return null
                is Modification.MultiplyAmount -> damage.copy(amount = damage.amount * mod.factor)
                is Modification.AddAmount -> damage.copy(amount = damage.amount + e.value(mod.amount))
                is Modification.ChangeRecipient -> recipientsOf(e, mod.to).firstOrNull()?.let { damage.copy(recipient = it) } ?: damage
                else -> damage
            }
        }
        return damage
    }

    /** The objects and players of [recipients]. */
    fun recipientsOf(e: Evaluator, recipients: arcanum.api.Recipients): List<TargetRef> =
        recipients.target?.let { slot ->
            when (val v = e.ctx.names[slot]) {
                is NamedValue.Targets -> v.refs
                is NamedValue.Objects -> v.ids.map { TargetRef.Object(it) }
                is NamedValue.Players -> v.ids.map { TargetRef.Player(it) }
                else -> emptyList()
            }
        }.orEmpty() + recipients.objects?.let { e.objects(it).map { id -> TargetRef.Object(id) } }.orEmpty() +
            recipients.players?.let { e.players(it).map { p -> TargetRef.Player(p) } }.orEmpty()

    /** CR 615: protection, prevention effects, then shields with an amount, each reducing what is left. */
    private fun prevent(act: Act, d: DamageDealt, cantBePrevented: Boolean): DamageDealt {
        if (cantBePrevented) return d
        val view = act.view()
        val board = act.board()
        val proposed = Change.Damage(listOf(d))
        val unpreventable = view.rules.any { applied ->
            val m = applied.mechanic as? Mechanic.DamageCantBePrevented ?: return@any false
            arcanum.engine.trigger.EventMatcher.instances(Evaluator(board, Context(applied.controller, applied.source, applied.names)),
                m.event, proposed, proposed = true).isNotEmpty()
        }
        if (unpreventable) return d
        // CR 702.16e: damage from a source with the quality protection is from is prevented
        (d.recipient as? TargetRef.Object)?.let { r ->
            val protections = view[r.id]?.mechanics.orEmpty().mapNotNull { a -> (a.mechanic as? Mechanic.Protection)?.let { it to a } }
            if (protections.any { (p, a) -> Evaluator(board, Context(a.controller, r.id)).matches(p.from, d.source) }) return d.copy(amount = 0)
        }
        var amount = d.amount
        view.rules.forEach { applied ->
            if (amount <= 0) return@forEach
            val m = applied.mechanic as? Mechanic.Prevention ?: return@forEach
            if (applied.effect != null && act.state.effects[applied.effect]?.amountLeft != null) return@forEach
            val e = Evaluator(board, Context(applied.controller, applied.source, applied.names))
            if (arcanum.engine.trigger.EventMatcher.instances(e, m.event, Change.Damage(listOf(d.copy(amount = amount))), proposed = true).isEmpty()) return@forEach
            amount = if (m.each == null) 0 else (amount - e.value(m.each!!)).coerceAtLeast(0)
        }
        // Shields (CR 615.7, D26), oldest first
        act.state.effects.values.filter { it.amountLeft != null }.sortedBy { it.timestamp }.forEach { shield ->
            if (amount <= 0) return@forEach
            val m = shield.mechanics.filterIsInstance<Mechanic.Prevention>().firstOrNull() ?: return@forEach
            val e = Evaluator(board, Context(shield.controller, shield.source, shield.names))
            if (arcanum.engine.trigger.EventMatcher.instances(e, m.event, Change.Damage(listOf(d.copy(amount = amount))), proposed = true).isEmpty()) return@forEach
            val left = shield.amountLeft!!
            val prevented = minOf(left, amount)
            amount -= prevented
            if (left - prevented <= 0) act.record(Change.EffectRemoved(shield, listOf(Meaning.ShieldUsed)))
            else act.record(Change.EffectUpdated(shield, shield.copy(amountLeft = left - prevented)))
        }
        return d.copy(amount = amount)
    }

    // ---- Life (CR 119)

    /** [player] gains [amount] life (CR 119.3) unless they can't (CR 119.7); returns the life actually gained. */
    fun gainLife(act: Act, cause: Cause?, player: PlayerId, amount: Int): Int {
        if (amount <= 0 || act.state.player(player).lost) return 0
        if (act.view().players[player]?.mechanics.orEmpty().any { it.mechanic == Mechanic.CantGainLife }) return 0
        val linked = mutableListOf<WaitingTrigger>()
        val final = Replacements.apply(act, cause, amount, player, null,
            proposed = { n -> Changes.life(act.state, player, n, listOf(Meaning.LifeGained)) },
            modify = { n, r, _ -> modifyAmount(act, n, r) },
            instead = { r, instance -> runInstead(act, r, instance) }, linked = linked) ?: 0
        if (final > 0) act.record(Changes.life(act.state, player, final, listOf(Meaning.LifeGained)))
        Replacements.addLinked(act, linked)
        return final.coerceAtLeast(0)
    }

    /** [player] loses [amount] life (CR 119.3); returns the life actually lost. */
    fun loseLife(act: Act, cause: Cause?, player: PlayerId, amount: Int): Int {
        if (amount <= 0 || act.state.player(player).lost) return 0
        val linked = mutableListOf<WaitingTrigger>()
        val final = Replacements.apply(act, cause, amount, player, null,
            proposed = { n -> Changes.life(act.state, player, -n, listOf(Meaning.LifeLost)) },
            modify = { n, r, _ -> modifyAmount(act, n, r) },
            instead = { r, instance -> runInstead(act, r, instance) }, linked = linked) ?: 0
        if (final > 0) act.record(Changes.life(act.state, player, -final, listOf(Meaning.LifeLost)))
        Replacements.addLinked(act, linked)
        return final.coerceAtLeast(0)
    }

    private fun modifyAmount(act: Act, n: Int, r: Replacement): Int? {
        var amount = n
        val e = Evaluator(act.board(), Context(you = r.controller, self = r.source, names = r.names))
        r.mechanic.modify.forEach { mod ->
            amount = when (mod) {
                Modification.Skip -> return null
                is Modification.MultiplyAmount -> amount * mod.factor
                is Modification.AddAmount -> amount + e.value(mod.amount)
                else -> amount
            }
        }
        return amount
    }

    // ---- Counters (CR 122)

    /** Puts [amount] counters of [kind] on [holder] (CR 122.6), after replacements ("one more", "twice that many");
     * returns how many were put. */
    fun putCounters(act: Act, cause: Cause?, holder: TargetRef, kind: CounterKind, amount: Int): Int {
        if (amount <= 0) return 0
        if (holder is TargetRef.Object && act.obj(holder.id) == null) return 0
        val affected = when (holder) {
            is TargetRef.Player -> holder.id
            is TargetRef.Object -> act.controllerOf(holder.id) ?: act.state.turn.active
        }
        val linked = mutableListOf<WaitingTrigger>()
        val final = Replacements.apply(act, cause, amount, affected, (holder as? TargetRef.Object)?.id,
            proposed = { n -> Changes.counters(act.state, holder, kind, n) },
            modify = { n, r, _ -> modifyAmount(act, n, r) },
            instead = { r, instance -> runInstead(act, r, instance) }, linked = linked) ?: 0
        if (final > 0) act.record(Changes.counters(act.state, holder, kind, final))
        Replacements.addLinked(act, linked)
        return final.coerceAtLeast(0)
    }

    /** Removes up to [amount] counters of [kind] from [holder]; returns how many were removed. */
    fun removeCounters(act: Act, holder: TargetRef, kind: CounterKind, amount: Int): Int {
        val has = when (holder) {
            is TargetRef.Object -> act.obj(holder.id)?.counters(kind) ?: 0
            is TargetRef.Player -> act.state.player(holder.id).counters(kind)
        }
        val n = minOf(has, amount)
        if (n > 0) act.record(Changes.counters(act.state, holder, kind, -n))
        return n
    }

    // ---- Tapping (CR 701.26)

    fun tap(act: Act, ids: List<ObjectId>, tapped: Boolean, meanings: List<Meaning> = emptyList()): List<ObjectId> =
        ids.distinct().filter { act.obj(it)?.zone == Zone.Battlefield && act.obj(it)?.tapped != tapped }.onEach {
            act.record(Changes.tap(act.state, it, tapped, meanings))
        }

    // ---- Tokens (CR 111)

    /**
     * Creates [count] tokens of [face] under [controller]'s control as one event (CR 111.2), after replacements on
     * creating tokens ("twice that many", "those tokens plus …", CR 614.1) and on entering. Their card ids are
     * name-based from [cause] (D35). Returns the new ids.
     */
    fun createTokens(
        act: Act, cause: Cause, face: FaceDefinition, count: Int, controller: PlayerId, tapped: Boolean = false,
        attachTo: TargetRef? = null,
    ): List<ObjectId> {
        if (count <= 0) return emptyList()
        val linked = mutableListOf<WaitingTrigger>()
        val creator = "${cause.key}@${act.state.nextTimestamp()}"
        fun planned(specs: List<Pair<FaceDefinition, Int>>): List<GameObject> {
            var n = 0
            val first = act.state.nextTimestamp()
            return specs.flatMap { (f, k) -> List(k) { f } }.map { f ->
                val i = n++
                GameObject(
                    id = ObjectId.Card(TokenIds.token(creator, i), 0), owner = controller, controller = controller,
                    zone = Zone.Battlefield, definition = CardDefinition(Layout.Normal, listOf(f)), token = true, tapped = tapped,
                    timestamp = first + i, controlSince = ControlSince(controller, act.state.turn.number), attachedTo = attachTo,
                )
            }
        }
        val specs = Replacements.apply(act, cause, listOf(face to count), controller, null,
            proposed = { s -> Change.Created(planned(s).mapIndexed { i, o -> Placed(o, act.state.battlefield.size + i) }) },
            modify = { s, r, _ ->
                var result: List<Pair<FaceDefinition, Int>>? = s
                val e = Evaluator(act.board(), Context(you = r.controller, self = r.source, names = r.names))
                r.mechanic.modify.forEach { mod ->
                    result = when (mod) {
                        Modification.Skip -> null
                        is Modification.MultiplyAmount -> result?.map { (f, k) -> f to k * mod.factor }
                        is Modification.AddAmount -> result?.map { (f, k) -> f to k + e.value(mod.amount) }
                        is Modification.AlsoCreate -> result?.plus(tokenFace(act, e, mod.token) to e.value(mod.count))
                        is Modification.EntersTapped -> result
                        else -> result
                    }
                }
                result
            },
            instead = { r, instance -> runInstead(act, r, instance) },
            linked = linked,
            board = { ProposedBoard(act.board(), emptyMap()) },
        ) ?: return emptyList()
        var tokens = planned(specs)
        // Replacements on entering (CR 614.12): "enters tapped", "with counters", tested on the tokens as they would be
        val proposedBoard = { ProposedBoard(act.board(), tokens.associateBy { it.id }) }
        tokens = tokens.mapNotNull { t ->
            val enters = Replacements.candidates(act, cause).filter { r ->
                Replacements.matches(act, r, Change.Created(listOf(Placed(t, 0))), proposedBoard()).isNotEmpty()
            }
            var o = t
            enters.forEach { r ->
                val e = Evaluator(proposedBoard(), Context(you = r.controller, self = t.id, names = r.names))
                r.mechanic.modify.forEach { mod ->
                    o = when (mod) {
                        Modification.EntersTapped -> o.copy(tapped = true)
                        is Modification.EntersWithCounters -> o.copy(counters = o.counters + CounterEntry(mod.kind, e.value(mod.amount), o.timestamp))
                        Modification.Skip -> return@mapNotNull null
                        else -> o
                    }
                }
            }
            o
        }
        if (tokens.isEmpty()) return emptyList()
        val start = act.state.battlefield.size
        act.record(Change.Created(tokens.mapIndexed { i, o -> Placed(o, start + i) }, listOf(Meaning.TokenCreated, Meaning.Entered)))
        Replacements.addLinked(act, linked)
        return tokens.map { it.id }
    }

    /** The face of a token spec (CR 111.4, 111.10, 707.2). */
    fun tokenFace(act: Act, e: Evaluator, spec: arcanum.api.TokenSpec): FaceDefinition = when (spec) {
        is arcanum.api.TokenSpec.Defined -> spec.face.copy(
            power = spec.power?.let { arcanum.api.PrintedNumber.Fixed(e.value(it)) } ?: spec.face.power,
            toughness = spec.toughness?.let { arcanum.api.PrintedNumber.Fixed(e.value(it)) } ?: spec.face.toughness,
        )
        is arcanum.api.TokenSpec.Predefined -> arcanum.api.PredefinedTokens.face(spec.name)
            ?: FaceDefinition("${spec.name} Token")
        is arcanum.api.TokenSpec.CopyOf -> {
            val of = e.objects(spec.objects).firstOrNull()
            val face = of?.let { arcanum.engine.view.Copiable(act.state).faces(it)?.firstOrNull()?.face }
                ?: of?.let { e.characteristics(it) }?.let { c -> FaceDefinition(c.name ?: "Copy", c.manaCost, c.colors, c.supertypes, c.types,
                    c.subtypes, c.power?.let { arcanum.api.PrintedNumber.Fixed(it) }, c.toughness?.let { arcanum.api.PrintedNumber.Fixed(it) }) }
                ?: FaceDefinition("Copy")
            arcanum.engine.view.Copiable.exceptions(face, spec.except)
        }
    }

    // ---- Library, hand and graveyard

    /**
     * [player] draws [count] cards, one at a time (CR 121.2), each after replacements ("skip that draw", dredge, CR
     * 121.6). Drawing from an empty library is remembered for the state-based action (CR 704.5b). Returns the cards drawn.
     */
    fun draw(act: Act, cause: Cause?, player: PlayerId, count: Int): Map<ObjectId, ObjectId> {
        val drawn = LinkedHashMap<ObjectId, ObjectId>()
        repeat(count) {
            val max = act.view().players[player]?.mechanics.orEmpty().mapNotNull { (it.mechanic as? Mechanic.MaxDrawsPerTurn)?.count }.minOrNull()
            if (max != null && drawsThisTurn(act, player) >= max) return drawn
            val top = act.state.player(player).library.firstOrNull()
            if (top == null) {
                // CR 121.4, 704.5b
                val before = act.state.player(player).ledger[EMPTY_DRAW] ?: 0
                act.record(Change.Ledger(player, EMPTY_DRAW, before, before + 1))
                return drawn
            }
            drawn += move(act, cause, listOf(PlannedMove(top, Zone.Hand, meanings = listOf(Meaning.Drew))))
        }
        return drawn
    }

    private fun drawsThisTurn(act: Act, player: PlayerId): Int = act.state.eventsOf(act.state.turn.number).count { c ->
        c is Change.ZoneChange && c.moves.any { Meaning.Drew in it.meanings && it.before.owner == player }
    }

    /** Ledger key of a player who drew from an empty library since state-based actions were last checked. */
    const val EMPTY_DRAW = "\$drew-from-empty-library"

    /** [player] mills [count] (CR 701.17): the top cards into the graveyard as one event. */
    fun mill(act: Act, cause: Cause?, player: PlayerId, count: Int): Map<ObjectId, ObjectId> {
        val cards = act.state.player(player).library.take(count.coerceAtLeast(0))
        return moveTo(act, cause, cards, Zone.Graveyard, listOf(Meaning.Milled))
    }

    /** Discards [ids] (CR 701.9) as one event; replacements (madness) apply per card. */
    fun discard(act: Act, cause: Cause?, ids: List<ObjectId>): Map<ObjectId, ObjectId> =
        moveTo(act, cause, ids.filter { act.obj(it)?.zone == Zone.Hand }, Zone.Graveyard, listOf(Meaning.Discarded))

    /**
     * Shuffles [player]'s library (CR 701.24): the host decides the order (D25); the cards become new objects (D38),
     * except [kept], which stay on top as they were (CR 701.24b, D45).
     */
    fun shuffle(act: Act, player: PlayerId, kept: List<ObjectId> = emptyList(), source: ObjectId? = null) {
        val library = act.state.player(player).library
        val shuffled = library.filter { it !in kept }
        val order = if (shuffled.size <= 1) shuffled else
            (act.random(player, source, RandomKind.Objects(shuffled, shuffled.size)) as Answer.RandomObjects).chosen
        require(order.toSet() == shuffled.toSet()) { "A shuffle must keep the library's cards" }
        val before = library.map { act.state.objOrThrow(it) }
        val after = library.filter { it in kept }.map { act.state.objOrThrow(it) } +
            order.map { act.state.objOrThrow(it).let { o -> o.copy(id = o.id.next()) } }
        act.record(Change.Shuffle(player, before, after))
    }

    // ---- Attaching (CR 701.3)

    fun attach(act: Act, id: ObjectId, to: TargetRef?) {
        val o = act.obj(id) ?: return
        if (o.attachedTo == to) return
        act.record(Change.Attached(id, o.attachedTo, to, o.timestamp, if (to == null) o.timestamp else act.state.nextTimestamp()))
    }

    fun tapStatus(act: Act, id: ObjectId, flag: StatusFlag, on: Boolean, meanings: List<Meaning> = emptyList()) {
        val o = act.obj(id) ?: return
        val now = when (flag) {
            StatusFlag.Tapped -> o.tapped
            StatusFlag.FaceDown -> o.faceDown
            StatusFlag.PhasedOut -> o.phasedOut
            is StatusFlag.Marked -> flag.mark in o.marks
        }
        if (now != on) act.record(Change.Status(id, flag, now, on, meanings))
    }
}
