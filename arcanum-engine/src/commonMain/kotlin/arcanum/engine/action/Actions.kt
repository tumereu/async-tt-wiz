package arcanum.engine.action

import arcanum.engine.state.castOrNone
import arcanum.engine.state.dealtCombatDamage
import arcanum.engine.state.dealtDamage
import arcanum.engine.state.deathtouchDamage
import arcanum.engine.state.flippedStatus
import arcanum.engine.state.gaining
import arcanum.engine.state.withStatus
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
import arcanum.engine.change.causedBy
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
    /** Put into a library in a random order together with the other moves so marked: their places are known as a group
     * only ([LibraryPlaces.group]). */
    val randomOrder: Boolean = false,
    // dsk gaps
    /** What the instruction says it is as it enters the battlefield (CR 611.2e): applied as it enters. */
    val entersAs: List<Mechanic> = emptyList(),
    // fra
    /** Marks it enters with ("enters prepared", CR 722.3a). */
    val marks: List<arcanum.api.ObjectMark> = emptyList(),
    // fin
    /** "Lands you control enter untapped": it enters untapped whatever else says (applied last). */
    val untapped: Boolean = false,
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
        // CR 707.10a: a copy of a spell put into another zone ceases to exist (no card moves)
        planned.forEach { pm ->
            val copy = act.state.stackItems[pm.id]?.takeIf { it.kind is arcanum.engine.state.StackItemKind.SpellCopy && act.obj(pm.id) == null }
            if (copy != null && pm.to != Zone.Stack) {
                act.record(Change.StackItemRemoved(copy, act.state.stack.indexOf(pm.id), pm.meanings + Meaning.CeasedToExist))
            }
        }
        val replaced = planned.mapNotNull { pm ->
            val o = act.obj(pm.id) ?: return@mapNotNull null
            Replacements.apply(
                act, cause, pm, affected = act.controllerOf(pm.id) ?: o.owner, about = pm.id,
                // rtr chunk 06: the proposed move names its cause, for "a spell or ability an opponent controls causes you to discard"
                proposed = { m -> Changes.move(act.state, listOf(m.id), m.to, meanings = m.meanings).let { c ->
                    if (cause == null) c else c.causedBy(arcanum.engine.change.CausedBy(cause.source, null, cause.controller, cause.item?.id)) } },
                modify = { m, r, _ -> modifyMove(act, m, r, linked) },
                instead = { r, instance -> runInstead(act, r, instance) },
                linked = linked,
                // CR 701.19c: "can't be regenerated"
                skip = { r, _ -> isRegeneration(r) && (noRegeneration || act.view()[pm.id]?.has { it == Mechanic.CantBeRegenerated } == true) },
            )
        }
        // CR 303.4f, g, i: what each Aura put onto the battlefield (not resolving as an Aura spell) will enchant
        val final = replaced.mapNotNull { auraEntering(act, it) }
        val result = commitMoves(act, cause, final, linked)
        final.filter { it.randomOrder }.mapNotNull { result[it.id] }.filter { act.state.obj(it)?.zone == Zone.Library }
            .takeIf { it.size > 1 }?.let { LibraryPlaces.group(act.recorder, it) }
        // CR 714.3a: as a Saga enters, its controller puts a lore counter on it
        final.filter { it.to == Zone.Battlefield }.mapNotNull { result[it.id] }
            .filter { act.state.obj(it)?.zone == Zone.Battlefield && act.view()[it]?.characteristics?.subtypes?.any { s -> s.name == "Saga" } == true }
            .forEach { saga ->
                // CR 714.3b, 702.155b: with read ahead, the number of lore counters chosen between one and its final chapter number
                val chars = act.view()[saga]?.characteristics
                val final = chars?.abilities.orEmpty().mapNotNull { ((it.ability as? arcanum.api.Ability.Triggered)?.trigger as? arcanum.api.Trigger.Chapter)
                    ?.chapters?.maxOrNull() }.maxOrNull()
                val readAhead = chars?.hasKeyword(arcanum.api.Keyword.ReadAhead) == true && final != null && final > 1
                val lore = if (readAhead) act.chooseNumber(act.controllerOf(saga) ?: act.state.objOrThrow(saga).owner, saga, 1, final) else 1
                // CR 714.3a, 614.1c: an effect (a replacement effect) puts it, so "if an effect would put counters" replacements apply
                val controller = act.controllerOf(saga) ?: act.state.objOrThrow(saga).owner
                putCounters(act, cause ?: Cause(controller, saga, key = "enters:$saga"), TargetRef.Object(saga), CounterKind.Lore, lore)
            }
        Replacements.addLinked(act, linked)
        // CR 610.3: "until" returns happen right after the event, without the Stack
        if (result.isNotEmpty()) arcanum.engine.trigger.Triggers.runOneShots(act)
        return result
    }

    /** What a permanent entering the battlefield ends up attached to ([host], null = unattached); [enters] false = it doesn't enter. */
    internal class Entering(val enters: Boolean, val host: TargetRef?)

    /**
     * CR 303.4f, g, h, i, 301.5e, 301.6: the attachment of a permanent about to enter the battlefield, [named] being what the
     * effect putting it there attaches it to. An Aura (other than a resolving Aura spell) is attached to what the effect names when
     * it can legally enchant that, else to the object or player its controller chooses as it enters, without targeting, among those
     * it can legally enchant (its enchant abilities, protection, not what enters with it); with nothing legal it doesn't enter. An
     * Equipment or Fortification that is not an Aura enters unattached unless it can legally be attached to what is named. Any other
     * permanent enters unattached. [board] sees the entering permanent when it is a token not yet created.
     */
    internal fun entering(
        act: Act, id: ObjectId, controller: PlayerId, face: arcanum.api.FaceDefinition, named: TargetRef?,
        board: arcanum.engine.query.Board = act.board(),
    ): Entering {
        val aura = CardType.Enchantment in face.types && face.subtypes.any { it.name == "Aura" }
        if (!aura) {
            val equipment = face.subtypes.any { it.name == "Equipment" }
            val fortification = face.subtypes.any { it.name == "Fortification" }
            if (named == null || !(equipment || fortification)) return Entering(true, null)
            val host = (named as? TargetRef.Object)?.id
            val hostChars = host?.let { act.view()[it]?.characteristics }
            val legal = host != null && host != id && act.state.obj(host)?.zone == Zone.Battlefield && when {
                equipment -> hostChars?.isType(CardType.Creature) == true || face.abilities.any { a ->
                    (a as? arcanum.api.Ability.Static)?.mechanics.orEmpty().any { m ->
                        m is Mechanic.CanAttachTo && Evaluator(board, Context(controller, id)).matches(m.what, host)
                    }
                }
                else -> hostChars?.isType(CardType.Land) == true
            } && !arcanum.engine.sba.StateBasedActions.protectedFrom(act, named, id, board)
            return Entering(true, if (legal) named else null)
        }
        val enchant = face.abilities.filterIsInstance<Keyword.Enchant>().map { it.what }
        fun legal(host: TargetRef) = arcanum.engine.sba.StateBasedActions.canEnchant(act, id, controller, enchant, host, board)
        if (named != null) return if (legal(named)) Entering(true, named) else Entering(false, null)
        val objects = act.state.battlefield.filter { legal(TargetRef.Object(it)) }
        val players = act.state.players.map { it.id }.filter { legal(TargetRef.Player(it)) }
        val chosen = when {
            objects.isEmpty() && players.isEmpty() -> null
            players.isEmpty() -> act.chooseObjects(controller, id, objects, 1, 1, "Enchant").firstOrNull()?.let { TargetRef.Object(it) }
            objects.isEmpty() -> act.choosePlayer(controller, id, players)?.let { TargetRef.Player(it) }
            else -> act.chooseTargets(controller, id, Keyword.Enchant.SLOT, objects.map { TargetRef.Object(it) } + players.map { TargetRef.Player(it) }, 1, 1)
                .firstOrNull()
        }
        return Entering(chosen != null, chosen)
    }

    /** CR 303.4f–i, 303.4h, 301.5e, 301.6 for a planned move onto the battlefield: see [entering]. An Aura that doesn't enter stays in
     * its zone (null), from the Stack it goes to its owner's graveyard instead; a resolving Aura spell already names what it targeted. */
    private fun auraEntering(act: Act, m: PlannedMove): PlannedMove? {
        if (m.to != Zone.Battlefield) return m
        val o = act.state.obj(m.id) ?: return m
        if (o.zone == Zone.Stack && m.attachTo != null) return m
        val face = enteringFace(act, o, m) ?: return m
        val r = entering(act, m.id, m.controller ?: o.owner, face, m.attachTo)
        if (r.enters) return if (r.host == m.attachTo) m else m.copy(attachTo = r.host)
        return if (o.zone == Zone.Stack) m.copy(to = Zone.Graveyard, attachTo = null, controller = null) else null
    }

    /** Names a regeneration shield keeps its creature under (D26). */
    const val REGENERATED = "\$regenerated"

    private fun isRegeneration(r: Replacement): Boolean = r.names[REGENERATED] != null

    private fun modifyMove(act: Act, m: PlannedMove, r: Replacement, linked: MutableList<WaitingTrigger>): PlannedMove? {
        var move = m
        // spm: the entering permanent's own replacements read the names its spell's keyword costs made ("the returned creature")
        val names = if (m.memory.values.isEmpty() || r.source != m.id) r.names else Bindings(m.memory.values + r.names.values)
        // fdn chunk 11b: "enters with a counter for each creature of the chosen type": the type was chosen as it enters, its memory is on the move
        val e = Evaluator(act.board(), Context(you = r.controller, self = r.source ?: m.id, names = names,
            enteringMemory = m.memory.takeIf { r.source == m.id && it.values.isNotEmpty() }))
        for (mod in r.mechanic.modify) {
            move = when (mod) {
                Modification.Skip -> return null
                is Modification.ChangeDestination -> move.copy(
                    to = mod.to.zone,
                    position = mod.to.libraryPosition?.let { libraryPosition(it) },
                    tapped = move.tapped || mod.to.tapped, faceDown = mod.to.faceDown, transformed = mod.to.transformed,
                    controller = mod.to.controller?.let { e.players(it).firstOrNull() } ?: move.controller,
                    // ecl: "exile that card with a dream counter on it" (CR 122.1)
                    counters = move.counters + mod.to.counters.map { it.kind to e.value(it.amount) },
                )
                Modification.EntersTapped -> if (move.to == Zone.Battlefield) move.copy(tapped = true) else move
                is Modification.EntersWithCounters -> if (move.to == Zone.Battlefield)
                    move.copy(counters = move.counters + (mod.kind to e.value(mod.amount))) else move
                is Modification.EntersUnderControlOf -> move.copy(controller = e.players(mod.players).firstOrNull() ?: move.controller)
                is Modification.EntersWithAbilities -> move.copy(abilities = move.abilities + mod.abilities)
                // gtc chunk 12: "enters … as a Mutant in addition to its other types" (CR 614.12)
                is Modification.EntersAs -> if (move.to == Zone.Battlefield) move.copy(entersAs = move.entersAs + mod.mechanics) else move
                is Modification.EntersAsCopy -> {
                    val options = e.each(mod.of).filter { it != move.id }
                    val chooser = r.controller
                    val chosen = act.chooseObjects(chooser, move.id, options, if (mod.optional) 0 else 1, 1, "Enter as a copy of")
                    chosen.firstOrNull()?.let { of ->
                        // spm: "When you do, exile that card": the linked trigger knows the object copied (CR 603.12)
                        val bind = mod.bind
                        val then = r.mechanic.whenApplied
                        if (bind != null && then != null) linked += WaitingTrigger(controller = r.controller,
                            ability = arcanum.api.Ability.Triggered(arcanum.api.Trigger.On(emptyList()), then), origin = null, source = r.source,
                            names = r.names.with(bind, NamedValue.Objects(listOf(of))))
                        move.copy(copyOf = of to mod.except)
                    } ?: move
                }
                is Modification.AsEnters -> Steps.asEnters(act, r, move, mod.steps) ?: return null
                // fra: "enters prepared" (CR 722.3a)
                is Modification.EntersMarked -> if (move.to == Zone.Battlefield) move.copy(marks = move.marks + mod.mark) else move
                // Amounts, recipients and extra tokens don't apply to a zone change.
                is Modification.MultiplyAmount, is Modification.AddAmount, is Modification.ChangeRecipient,
                is Modification.AlsoCreate, is Modification.CreateInstead, Modification.HealsOtherDamage -> move
                // fin: "Lands you control enter untapped": applied after the others ([untapped])
                Modification.EntersUntapped -> if (move.to == Zone.Battlefield) move.copy(untapped = true) else move
                // fin: transforming and coin flips aren't zone changes
                is Modification.AsTransforms, Modification.WinFlips -> move
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
        // CR 614.5: it doesn't apply again to the events its own instructions make
        val added = act.replacing.add(r.key)
        try {
            val scope = Scope(act, cause, r.names, instance)
            Steps.run(scope, r.mechanic.instead!!.steps)
            // hob: what the spell's own replacement named is the spell's to read afterwards
            if (r.own) scope.names.values.forEach { (name, value) -> if (r.names.values[name] != value) act.ownInsteadBinds[name] = value }
        } finally {
            if (added) act.replacing.remove(r.key)
        }
    }

    /** Makes one zone-change event of [moves] (stamped in order, D34), with what everyone saw of each object. */
    private fun commitMoves(act: Act, cause: Cause?, planned: List<PlannedMove>, linked: MutableList<WaitingTrigger>): Map<ObjectId, ObjectId> {
        if (planned.isEmpty()) return emptyMap()
        // The counters each permanent enters with, after the replacements on counters being put (CR 122.6)
        val moves = planned.map { m -> if (m.to == Zone.Battlefield) m.copy(counters = enteringCounters(act, cause, m, linked)) else m }
        // CR 310.9a, 310.12a: as a battle enters, its controller chooses its protector
        val protectors = moves.mapNotNull { m -> enteringFace(act, act.state.objOrThrow(m.id), m)?.takeIf { CardType.Battle in it.types }?.let { face ->
            arcanum.engine.combat.Battles.protectorAsEnters(act, m.id, m.controller ?: act.state.objOrThrow(m.id).owner, face.subtypes)?.let { m.id to it }
        } }.toMap()
        val scratch = act.state.deepCopy()
        val all = mutableListOf<Move>()
        // Copy effects and extra abilities exist as the object enters (CR 614.12, 707.4), so its own enters triggers see them.
        moves.forEach { m ->
            val next = (m.id as ObjectId.Card).next()
            m.copyOf?.let { (of, except) ->
                act.record(Changes.effect(act.state) { ts -> lockedEffect("E$ts", listOf(Mechanic.CopyOf(Objects.Bound(COPIED), except)), next, m.controller, ts, act,
                    Bindings.EMPTY.with(COPIED, NamedValue.Objects(listOf(of))),
                    // CR 707.2, 730.2a: a merged permanent's copiable values include every component's abilities
                    copied = arcanum.engine.view.Copiable(act.state).forCopyEffect(of)) })
            }
            if (m.abilities.isNotEmpty()) act.record(Changes.effect(act.state) { ts ->
                lockedEffect("E$ts", listOf(Mechanic.GainAbilities(m.abilities)), next, m.controller, ts, act)
            })
            // dsk gaps: "It's an enchantment." of the instruction that puts it there applies as it enters (CR 611.2e)
            if (m.entersAs.isNotEmpty() && m.to == Zone.Battlefield) act.record(Changes.effect(act.state) { ts ->
                lockedEffect("E$ts", m.entersAs, next, m.controller, ts, act)
            })
        }
        val snapshots = moves.associate { it.id to act.snapshot(it.id) }
        moves.forEach { m ->
            val before = scratch.objOrThrow(m.id)
            val toBattlefield = m.to == Zone.Battlefield
            val zc = Changes.move(
                scratch, listOf(m.id), m.to, position = m.position ?: if (m.to == Zone.Library) Changes.Position.Top else Changes.Position.Bottom,
                controller = m.controller, tapped = m.tapped && toBattlefield && !m.untapped, faceDown = m.faceDown, meanings = m.meanings,
                counters = if (toBattlefield || m.to == Zone.Exile) m.counters.filter { it.second > 0 } else emptyList(),
            )
            val moved = zc.moves.single().let { mv ->
                var after = mv.after
                if (m.memory.values.isNotEmpty()) after = after.copy(linked = m.memory)
                protectors[m.id]?.let { if (toBattlefield) after = after.withStatus(arcanum.engine.state.ObjectStatus.Protector(it).key, arcanum.engine.state.ObjectStatus.Protector(it)) }
                if (toBattlefield && m.attachTo != null) after = after.copy(attachedTo = m.attachTo)
                if (toBattlefield && m.transformed && (before.definition.layout == Layout.Transform || before.definition.layout is Layout.Meld)) after = after.copy(face = 1)
                // CR 702.145b: at night a daybound card enters transformed
                if (toBattlefield && !m.faceDown && arcanum.engine.turn.DayNight.entersTransformed(scratch, after)) after = after.copy(face = 1)
                // CR 709.5d: a Room enters with the half cast as a spell unlocked
                if (toBattlefield && before.zone == Zone.Stack && arcanum.engine.room.Rooms.isRoom(before.definition))
                    after = after.copy(marks = after.marks + arcanum.api.ObjectMark.Unlocked(arcanum.engine.room.Rooms.door(before.face)))
                if (!toBattlefield && m.to != Zone.Stack) after = after.copy(controller = null)
                // Marks it gets as it goes elsewhere than the battlefield (foretold: exiled face down, CR 702.143a)
                if (!toBattlefield && m.marks.isNotEmpty()) {
                    after = after.copy(marks = (after.marks + m.marks).distinct())
                    m.marks.forEach { mark -> gained(act, m.id, mark, emptyList())?.let { record ->
                        after = after.withStatus(arcanum.engine.state.ObjectStatus.MarksGained(emptyList()).key, after.gaining(record)) } }
                }
                mv.copy(after = after, seen = snapshots[m.id])
            }
            scratch.apply(Change.ZoneChange(listOf(moved)))
            all += moved
        }
        // Put onto the battlefield attacking (CR 508.4): not declared as an attacker. Recorded just before the zone change,
        // so it is attacking as it enters ("if it's attacking", CR 603.4).
        moves.filter { it.attacking != null && it.to == Zone.Battlefield }.forEach { m ->
            val combat = act.state.combat ?: return@forEach
            val after = all.first { it.before.id == m.id }.after.id
            act.record(Change.Combat(combat, combat.copy(attackers = combat.attackers + arcanum.engine.state.Attacker(after, m.attacking!!))))
        }
        act.record(Change.ZoneChange(all))
        arcanum.engine.room.Rooms.enteredUnlocked(act, all.filter { it.before.zone == Zone.Stack && it.after.zone == Zone.Battlefield }.map { it.after.id })
        // fra: marks it enters with; becoming prepared makes its prepare spell's copy (CR 722.3a, c)
        moves.filter { it.marks.isNotEmpty() && it.to == Zone.Battlefield }.forEach { m ->
            val after = all.first { it.before.id == m.id }.after.id
            m.marks.distinct().forEach { mark ->
                setMark(act, after, mark, true)
            }
        }
        // CR 400.7: a spell whose card left the Stack is no longer a spell (one resolving keeps its item in its
        // continuation, Continuation.Resolving.left)
        all.filter { it.before.zone == Zone.Stack && it.after.zone != Zone.Stack }.forEach { mv ->
            act.state.stackItems[mv.before.id]?.let { act.record(Change.StackItemRemoved(it, null)) }
        }
        return all.associate { it.before.id to it.after.id }
    }

    private const val COPIED = "\$copied"

    /**
     * CR 122.6, 614.1c: the counters a permanent enters with (its own "enters with", printed loyalty or defense, an effect's
     * "with a counter on it") are put on it as it enters, so replacements of counters being put apply, one event per
     * kind (CR 614.16), tested on the permanent as it would exist on the battlefield (CR 614.12).
     */
    private fun enteringCounters(act: Act, cause: Cause?, m: PlannedMove, linked: MutableList<WaitingTrigger>): List<Pair<CounterKind, Int>> {
        val o = act.state.objOrThrow(m.id)
        val all = m.counters.filter { it.second > 0 } + printedCounters(act, o, m)
        if (all.isEmpty()) return emptyList()
        val controller = m.controller ?: o.owner
        val entering = o.copy(id = (m.id as ObjectId.Card).next(), zone = Zone.Battlefield, controller = controller,
            counters = emptyList(), tapped = m.tapped, faceDown = m.faceDown)
        return countersAsPut(act, cause, entering, all, linked)
    }

    /** The [counters] (several of one kind add up) put on [entering] as it enters, after replacements (CR 122.6). */
    private fun countersAsPut(
        act: Act, cause: Cause?, entering: GameObject, counters: List<Pair<CounterKind, Int>>, linked: MutableList<WaitingTrigger>,
    ): List<Pair<CounterKind, Int>> {
        val board = ProposedBoard(act.board(), mapOf(entering.id to entering))
        val holder = TargetRef.Object(entering.id)
        return counters.groupBy({ it.first }, { it.second }).mapNotNull { (kind, amounts) ->
            val final = Replacements.apply(act, cause, amounts.sum(), entering.controller ?: entering.owner, entering.id,
                // CR 614.1c: "enters with counters" is itself an effect (a replacement effect), so "an effect would put counters" replacements apply
                proposed = { n -> if (n <= 0) null else Change.Counters(holder, kind, null, CounterEntry(kind, n, act.state.nextTimestamp())).causedBy(
                    arcanum.engine.change.CausedBy(cause?.source ?: entering.id, null, cause?.controller ?: entering.controller ?: entering.owner, cause?.item?.id)) },
                modify = { n, r, _ -> modifyAmount(act, n, r) },
                instead = { r, instance -> runInstead(act, r, instance) }, linked = linked, board = { board })
            final?.takeIf { it > 0 }?.let { kind to it }
        }
    }

    /** CR 306.5b, 310.4b: a planeswalker enters with loyalty counters equal to its printed loyalty, a battle with defense
     * counters equal to its printed defense (the face that enters, or what it enters as a copy of; X counts as 0). */
    private fun printedCounters(act: Act, o: GameObject, m: PlannedMove): List<Pair<CounterKind, Int>> {
        val face = enteringFace(act, o, m) ?: return emptyList()
        fun amount(n: arcanum.api.PrintedNumber?) = (n as? arcanum.api.PrintedNumber.Fixed)?.value ?: 0
        // CR 702.150a: compleated: two fewer for each Phyrexian symbol paid with life
        val compleated = if (face.abilities.any { it == arcanum.api.Keyword.Compleated }) 2 * o.castOrNone.phyrexianPaidWithLife else 0
        return listOfNotNull(
            (CounterKind.Loyalty to amount(face.loyalty) - compleated).takeIf { CardType.Planeswalker in face.types && it.second > 0 },
            (CounterKind.Defense to amount(face.defense)).takeIf { CardType.Battle in face.types && it.second > 0 },
        )
    }

    /** The face [o] enters the battlefield with in [m] (the one up, or what it enters as a copy of); null when it doesn't
     * enter face up. */
    private fun enteringFace(act: Act, o: GameObject, m: PlannedMove): arcanum.api.FaceDefinition? {
        if (m.to != Zone.Battlefield || m.faceDown) return null
        return m.copyOf?.let { (of, _) -> arcanum.engine.view.Copiable(act.state).faces(of)?.firstOrNull()?.face }
            ?: o.definition.faces.getOrNull(if (m.transformed && (o.definition.layout == Layout.Transform || o.definition.layout is Layout.Meld)) 1 else o.face)
    }

    private fun lockedEffect(
        id: String, mechanics: List<Mechanic>, on: ObjectId, controller: PlayerId?, ts: Long, act: Act,
        names: Bindings = Bindings.EMPTY, copied: arcanum.engine.state.CopiableValues? = null,
    ) = EffectObject(
        id = EffectId(id), mechanics = mechanics, affects = Affected.Objects(Objects.Self), locked = listOf(on), source = on,
        controller = controller ?: act.state.turn.active, timestamp = ts, createdTurn = act.state.turn.number,
        duration = Duration.Indefinitely, names = names, copiedValues = copied,
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
        val ok = ids.distinct().filter { act.obj(it)?.zone == Zone.Battlefield && !cantBeSacrificed(act, it, cause?.controller) }
        return moveTo(act, cause, ok, Zone.Graveyard, listOf(Meaning.Sacrificed))
    }

    /** Whether [id] can't be sacrificed (CR 701.21a): [Mechanic.CantBeSacrificed], or (inr chunk 09) [Mechanic.CantBeSacrificedBy] whose
     * players, seen from the controller of the ability giving it, include [by], the controller of the spell or ability sacrificing it. */
    fun cantBeSacrificed(act: Act, id: ObjectId, by: PlayerId?): Boolean {
        val view = act.view()[id] ?: return false
        if (view.has { it == Mechanic.CantBeSacrificed }) return true
        if (by == null) return false
        return view.mechanics.any { applied ->
            val m = applied.mechanic as? Mechanic.CantBeSacrificedBy ?: return@any false
            act.evaluator(Context(you = applied.controller)).matches(m.controllers, by)
        }
    }

    /** Moves [ids] to their owners' [to] (CR 400.3). */
    fun moveTo(
        act: Act, cause: Cause?, ids: List<ObjectId>, to: Zone, meanings: List<Meaning> = emptyList(),
        controller: PlayerId? = null, position: Changes.Position? = null,
    ): Map<ObjectId, ObjectId> = move(act, cause, ids.map { PlannedMove(it, to, position, controller, meanings = meanings) })

    // ---- Damage (CR 120)

    /** What a damage event did: the damage each part actually dealt after prevention and replacement, and the excess
     * over lethal per recipient (CR 120.4a). */
    data class DamageResult(
        val dealt: List<DamageDealt>, val excess: Int,
        // tla
        /** The excess damage dealt to each recipient (CR 120.4a). */
        val excessTo: Map<TargetRef, Int> = emptyMap(),
    ) {
        val total: Int get() = dealt.sumOf { it.amount }
    }

    /**
     * Deals [parts] as one damage event (CR 120, D44): replacement and prevention effects (redirection, doubling, CR
     * 614.9; protection CR 702.16e, prevention effects and shields CR 615) in the order the affected player chooses when
     * it matters (CR 616.1), prevention not when it can't be prevented (CR 615.12).
     * Results (CR 120.3): life loss or poison (infect, CR 702.90b) for players; damage or −1/−1 counters (wither, infect)
     * for creatures; loyalty and defense counters removed from planeswalkers and battles; lifelink life gain (CR 702.15b);
     * toxic poison counters (CR 702.164c).
     */
    fun damage(act: Act, cause: Cause?, parts: List<DamageDealt>, cantBePrevented: Boolean = false): DamageResult {
        val linked = mutableListOf<WaitingTrigger>()
        // dsk gaps: prevention effects' additional effects, done immediately after the damage event (CR 615.5)
        val afterwards = mutableListOf<() -> Unit>()
        val after = parts.filter { it.amount > 0 }.mapNotNull { part ->
            val affected = when (val r = part.recipient) {
                is TargetRef.Player -> r.id
                is TargetRef.Object -> act.controllerOf(r.id) ?: act.state.turn.active
            }
            Replacements.apply(
                act, cause, part, affected, part.source,
                proposed = { d -> Change.Damage(listOf(d)) },
                modify = { d, r, instance ->
                    if (r.prevention == null) modifyDamage(act, d, r)
                    else prevent(act, d, r, r.prevention, cantBePrevented || unpreventable(act, d), instance, afterwards)
                },
                instead = { r, instance -> runInstead(act, r, instance) },
                linked = linked,
                // CR 615.12: damage that can't be prevented isn't, but a prevention effect's additional effect still
                // applies to it, once (615.12a)
                skip = { r, d -> r.prevention != null && !r.prevention.hasAdditional && (cantBePrevented || unpreventable(act, d)) },
                extra = preventions(act),
            )
        }.filter { recipientExists(act, it.recipient) && it.amount > 0 }
        if (after.isEmpty()) {
            afterwards.forEach { it() }
            return DamageResult(emptyList(), 0)
        }
        val view = act.view()
        val board = act.board()
        // ltr chunk 07: the excess of each part (CR 120.4a), parts to the same permanent counted in order
        val marked = mutableMapOf<ObjectId, Int>()
        val dealt = after.map { d ->
            val excess = (d.recipient as? TargetRef.Object)?.let { r ->
                val c = view[r.id]?.characteristics ?: return@let 0
                val deathtouch = board.characteristics(d.source)?.keywords.orEmpty().any { it.kind == Keyword.Deathtouch }
                val already = marked[r.id] ?: (act.obj(r.id)?.damage ?: 0)
                marked[r.id] = already + d.amount
                when {
                    c.isType(CardType.Creature) -> {
                        val lethal = ((c.toughness ?: 0) - already).coerceAtLeast(0)
                        (d.amount - (if (deathtouch) minOf(1, lethal) else lethal)).coerceAtLeast(0)
                    }
                    c.isType(CardType.Planeswalker) -> (d.amount - (act.obj(r.id)?.counters(CounterKind.Loyalty) ?: 0)).coerceAtLeast(0)
                    else -> 0
                }
            } ?: 0
            d.copy(sourceSeen = d.sourceSeen ?: act.snapshot(d.source), excess = excess)
        }
        act.record(Change.Damage(dealt))
        // fra: what has dealt combat damage remembers it ("as long as they haven't dealt combat damage yet")
        // tdm: and what has dealt any damage ("as long as it hasn't dealt damage yet")
        dealt.map { it.source }.distinct().forEach { source ->
            val o = act.obj(source) ?: return@forEach
            val combat = dealt.any { it.source == source && it.combat }
            var after = o
            if (!o.dealtDamage) after = after.withStatus(arcanum.engine.state.ObjectStatus.DealtDamage.key, arcanum.engine.state.ObjectStatus.DealtDamage)
            if (combat && !o.dealtCombatDamage) after = after.withStatus(arcanum.engine.state.ObjectStatus.DealtCombatDamage.key, arcanum.engine.state.ObjectStatus.DealtCombatDamage)
            if (after != o) act.record(Change.ObjectUpdated(o, after))
        }
        var excess = 0
        val excessTo = mutableMapOf<TargetRef, Int>()
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
                        val over = (d.amount - (if (Keyword.Deathtouch in keywords) minOf(1, lethal) else lethal).coerceAtLeast(0)).coerceAtLeast(0)
                        excess += over
                        excessTo[r] = (excessTo[r] ?: 0) + over
                        if (Keyword.Wither in keywords || Keyword.Infect in keywords) {
                            // CR 122.6, 614.1: the counters are put on it, so effects that change counters being put apply
                            putCounters(act, cause, r, CounterKind.PowerToughness(-1, -1), d.amount)
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
        afterwards.forEach { it() }
        return DamageResult(dealt, excess, excessTo)
    }

    private fun recipientExists(act: Act, ref: TargetRef): Boolean = when (ref) {
        is TargetRef.Player -> !act.state.player(ref.id).lost
        is TargetRef.Object -> act.obj(ref.id)?.zone == Zone.Battlefield
    }

    /** CR 614.9: a redirection does nothing if one of its permanents is no longer on the battlefield or no longer a
     * battle, creature or planeswalker, or one of its players has left the game. */
    private fun redirectable(act: Act, ref: TargetRef): Boolean = recipientExists(act, ref) && when (ref) {
        is TargetRef.Player -> true
        is TargetRef.Object -> act.view()[ref.id]?.characteristics.let { c ->
            c != null && (c.isType(CardType.Creature) || c.isType(CardType.Planeswalker) || c.isType(CardType.Battle))
        }
    }

    private fun modifyDamage(act: Act, d: DamageDealt, r: Replacement): DamageDealt? {
        var damage = d
        val e = Evaluator(act.board(), Context(you = r.controller, self = r.source, names = r.names))
        for (mod in r.mechanic.modify) {
            damage = when (mod) {
                Modification.Skip -> return null
                is Modification.MultiplyAmount -> damage.copy(amount = damage.amount * mod.factor)
                is Modification.AddAmount -> damage.copy(amount = damage.amount + e.value(mod.amount))
                is Modification.ChangeRecipient -> recipientsOf(e, mod.to).firstOrNull()
                    ?.takeIf { redirectable(act, damage.recipient) && redirectable(act, it) }
                    ?.let { damage.copy(recipient = it) } ?: damage
                // msh: "instead that damage is dealt, but all other damage already dealt to him is healed" (CR 701.69a): the damage
                // marked before this event is removed as it is dealt
                Modification.HealsOtherDamage -> {
                    (damage.recipient as? TargetRef.Object)?.let { r -> act.obj(r.id) }?.let { o ->
                        if (o.damage > 0 || o.deathtouchDamage) act.record(Change.DamageMarked(o.id, o.damage, 0, o.deathtouchDamage, false))
                    }
                    damage
                }
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

    /** CR 615.12: damage an effect says can't be prevented. */
    private fun unpreventable(act: Act, d: DamageDealt): Boolean {
        val board = act.board()
        val proposed = Change.Damage(listOf(d))
        return act.view().game.rules.any { applied ->
            val m = applied.mechanic as? Mechanic.DamageCantBePrevented ?: return@any false
            arcanum.engine.trigger.EventMatcher.instances(Evaluator(board, Context(applied.controller, applied.source, applied.names)),
                m.event, proposed, proposed = true).isNotEmpty()
        }
    }

    /** The prevention effects in force (CR 615), as candidates among the replacements of a damage event (CR 616.1):
     * protection of each permanent (CR 702.16e), prevention effects, and shields with an amount, oldest first (D26). */
    private fun preventions(act: Act): List<Replacement> {
        val view = act.view()
        val anyDamage = arcanum.api.EventPattern.Damage(arcanum.api.ObjectFilter.Any, arcanum.api.RecipientFilter())
        val protection = view.objects.values.flatMap { o ->
            o.mechanics.mapIndexedNotNull { i, a ->
                val p = a.mechanic as? Mechanic.Protection ?: return@mapIndexedNotNull null
                Replacement(Mechanic.Replacement(anyDamage), a.controller, o.id, Bindings.EMPTY, null, own = false,
                    key = "protection:${o.id}:$i", prevention = Prevention.Protection(p.from, TargetRef.Object(o.id)))
            }
        } + view.players.values.flatMap { pv ->
            // fin: a player with protection (CR 702.16e)
            pv.mechanics.mapIndexedNotNull { i, a ->
                val p = a.mechanic as? Mechanic.Protection ?: return@mapIndexedNotNull null
                Replacement(Mechanic.Replacement(anyDamage), pv.id, null, Bindings.EMPTY, null, own = false,
                    key = "protection:${pv.id}:$i", prevention = Prevention.Protection(p.from, TargetRef.Player(pv.id)))
            }
        }
        val effects = view.game.rules.mapIndexedNotNull { i, applied ->
            val m = applied.mechanic as? Mechanic.Prevention ?: return@mapIndexedNotNull null
            if (applied.effect != null && act.state.effects[applied.effect]?.amountLeft != null) return@mapIndexedNotNull null
            Replacement(Mechanic.Replacement(m.event), applied.controller, applied.source, applied.names, applied.effect, own = false,
                key = "prevent:${applied.effect?.value ?: applied.source}:${applied.origin?.key}:$i", prevention = Prevention.Amount(m.each, m.additional),
                text = arcanum.engine.decision.Texts.of(null, applied.origin, applied.source?.let { view[it]?.characteristics }))
        }
        val shields = act.state.effects.values.filter { it.amountLeft != null }.sortedBy { it.timestamp }.mapNotNull { shield ->
            val m = shield.mechanics.filterIsInstance<Mechanic.Prevention>().firstOrNull() ?: return@mapNotNull null
            Replacement(Mechanic.Replacement(m.event), shield.controller, shield.source, shield.names, null, own = false,
                key = "shield:${shield.id.value}", prevention = Prevention.Shield(shield.id))
        }
        return protection + effects + shields
    }

    /** Applies prevention effect [r] to [d] (CR 615): what is left of the damage, or null when none is. Damage that
     * [cantBePrevented] is left as it is (CR 615.12). An additional effect is queued in [afterwards] (CR 615.5). */
    private fun prevent(
        act: Act, d: DamageDealt, r: Replacement, p: Prevention, cantBePrevented: Boolean, instance: TriggerEvent,
        afterwards: MutableList<() -> Unit>,
    ): DamageDealt? {
        if (p is Prevention.Amount && p.additional != null) {
            val steps = p.additional.steps
            afterwards += {
                val c = Cause(r.controller, r.source, key = "prevented:${act.state.nextTimestamp()}")
                arcanum.engine.resolution.Steps.run(arcanum.engine.resolution.Scope(act, c, r.names, instance.copy(amount = d.amount)), steps)
            }
        }
        if (cantBePrevented) return d
        val left = when (p) {
            is Prevention.Protection -> 0
            is Prevention.Amount -> p.each?.let { each ->
                d.amount - Evaluator(act.board(), Context(r.controller, r.source, r.names)).value(each)
            } ?: 0
            is Prevention.Shield -> {
                val shield = act.state.effects[p.effect] ?: return d
                val has = shield.amountLeft ?: return d
                val prevented = minOf(has, d.amount)
                if (has - prevented <= 0) act.record(Change.EffectRemoved(shield, listOf(Meaning.ShieldUsed)))
                else act.record(Change.EffectUpdated(shield, shield.copy(amountLeft = has - prevented)))
                d.amount - prevented
            }
        }
        return if (left > 0) d.copy(amount = left) else null
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
    fun loseLife(act: Act, cause: Cause?, player: PlayerId, amount: Int, meanings: List<Meaning> = listOf(Meaning.LifeLost)): Int {
        if (amount <= 0 || act.state.player(player).lost) return 0
        val linked = mutableListOf<WaitingTrigger>()
        val final = Replacements.apply(act, cause, amount, player, null,
            proposed = { n -> Changes.life(act.state, player, -n, meanings) },
            modify = { n, r, _ -> modifyAmount(act, n, r) },
            instead = { r, instance -> runInstead(act, r, instance) }, linked = linked) ?: 0
        if (final > 0) act.record(Changes.life(act.state, player, -final, meanings))
        Replacements.addLinked(act, linked)
        return final.coerceAtLeast(0)
    }

    /** [player] pays [amount] life (CR 119.4): they lose that much life, so replacements of life loss and "whenever you
     * lose life" apply; the payment is made whatever the replacements do to the loss. Returns the life actually lost. */
    fun payLife(act: Act, cause: Cause?, player: PlayerId, amount: Int): Int =
        loseLife(act, cause, player, amount, listOf(Meaning.LifePaid, Meaning.LifeLost))

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
    fun putCounters(act: Act, cause: Cause?, holder: TargetRef, kind: CounterKind, amount: Int, meanings: List<Meaning> = emptyList()): Int {
        if (amount <= 0) return 0
        if (holder is TargetRef.Object && act.obj(holder.id) == null) return 0
        val affected = when (holder) {
            is TargetRef.Player -> holder.id
            is TargetRef.Object -> act.controllerOf(holder.id) ?: act.state.turn.active
        }
        val linked = mutableListOf<WaitingTrigger>()
        val final = Replacements.apply(act, cause, amount, affected, (holder as? TargetRef.Object)?.id,
            // blb: the proposed change names its cause, for "counters you would put" (EventPattern.CountersPut.causedBy)
            // fdn: and whether it is paid as a cost (EventPattern.CountersPut.byEffect)
            proposed = { n -> Changes.counters(act.state, holder, kind, n, meanings.filter { it == arcanum.engine.change.Meaning.Paid }).let { c ->
                if (cause == null) c else c.causedBy(arcanum.engine.change.CausedBy(cause.source, null, cause.controller, cause.item?.id)) } },
            modify = { n, r, _ -> modifyAmount(act, n, r) },
            instead = { r, instance -> runInstead(act, r, instance) }, linked = linked) ?: 0
        // fra: the counters put name their cause too ("whenever you put one or more loyalty counters on a planeswalker")
        if (final > 0) act.record(Changes.counters(act.state, holder, kind, final, meanings).let { c ->
            if (cause == null) c else c.causedBy(arcanum.engine.change.CausedBy(cause.source, null, cause.controller, cause.item?.id)) })
        Replacements.addLinked(act, linked)
        return final.coerceAtLeast(0)
    }

    /** Removes up to [amount] counters of [kind] from [holder]; returns how many were removed. */
    fun removeCounters(act: Act, holder: TargetRef, kind: CounterKind, amount: Int, meanings: List<Meaning> = emptyList()): Int {
        val has = when (holder) {
            is TargetRef.Object -> act.obj(holder.id)?.counters(kind) ?: 0
            is TargetRef.Player -> act.state.player(holder.id).counters(kind)
        }
        val n = minOf(has, amount)
        if (n > 0) act.record(Changes.counters(act.state, holder, kind, -n, meanings))
        return n
    }

    /**
     * Removes [amount] counters from object [id]: of [kind], or, with [kind] null ("remove up to three counters from target
     * creature"), counters of any kinds [chooser] chooses one at a time as the effect applies (CR 608.2d, 609.3: no choice when
     * one kind is there or every counter goes). Returns the number removed.
     */
    fun removeChosenCounters(act: Act, chooser: PlayerId, source: ObjectId?, id: ObjectId, kind: CounterKind?, amount: Int,
                             meanings: List<Meaning> = emptyList()): Int {
        if (kind != null) return removeCounters(act, TargetRef.Object(id), kind, amount, meanings)
        val there = act.obj(id)?.counters?.filter { it.count > 0 }?.associate { it.kind to it.count }.orEmpty()
        val total = there.values.sum()
        val taken: Map<CounterKind, Int> = if (there.size <= 1 || amount >= total) there.mapValues { (_, c) -> minOf(c, amount) }
        else {
            val left = there.toMutableMap()
            val chosen = mutableMapOf<CounterKind, Int>()
            repeat(amount) {
                val labels = left.filterValues { it > 0 }.keys.associateBy { counterLabel(it) }
                val k = labels.getValue(act.chooseOption(chooser, source, labels.keys.toList(), text = "Choose a counter to remove"))
                left[k] = left.getValue(k) - 1
                chosen[k] = (chosen[k] ?: 0) + 1
            }
            chosen
        }
        return taken.entries.sumOf { (k, n) -> removeCounters(act, TargetRef.Object(id), k, n, meanings) }
    }

    /** A counter kind in a few words for a choice: "+1/+1", "stun", "flying". */
    private fun counterLabel(kind: CounterKind): String = when (kind) {
        is CounterKind.PowerToughness -> "${if (kind.power < 0) "${kind.power}" else "+${kind.power}"}/${if (kind.toughness < 0) "${kind.toughness}" else "+${kind.toughness}"}"
        is CounterKind.Named -> kind.name
        else -> (kotlinx.serialization.json.Json.encodeToJsonElement(CounterKind.serializer(), kind) as kotlinx.serialization.json.JsonObject)
            .let { o -> (o["keyword"] ?: o["type"])?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content ?: it.toString() } ?: kind.toString() }
    }

    // ---- Tapping (CR 701.26)

    fun tap(act: Act, ids: List<ObjectId>, tapped: Boolean, meanings: List<Meaning> = emptyList()): List<ObjectId> =
        ids.distinct().filter { act.obj(it)?.zone == Zone.Battlefield && act.obj(it)?.tapped != tapped }.filter {
            // msh: "can't become untapped"
            tapped || act.view()[it]?.has { m -> m == Mechanic.CantBecomeUntapped } != true
        }.filter {
            // CR 122.1d: a permanent with a stun counter that would become untapped loses a stun counter instead
            if (tapped || act.obj(it)!!.counters(CounterKind.Stun) == 0) true else {
                act.record(Changes.counters(act.state, TargetRef.Object(it), CounterKind.Stun, -1))
                false
            }
        }.onEach {
            act.record(Changes.tap(act.state, it, tapped, meanings))
        }

    // ---- Tokens (CR 111)

    /**
     * Creates [count] tokens with the copiable values [values] (one face, or a copy's layout and all its faces with the face
     * up, CR 707.8a, 709.5b) under [controller]'s control as one event (CR 111.2), after replacements on creating tokens
     * ("twice that many", "those tokens plus …", CR 614.1) and on entering. Their card ids are name-based from [cause]
     * (D35). Returns the new ids.
     *
     * [fromSpellCopy]: a copy of a permanent spell becoming a token as it resolves (CR 111.13, 608.3f, 707.10f), with
     * the copy's facts; it isn't "created", so replacements and triggers on creating tokens don't see it.
     *
     * [withCounters]: counters each token enters with by the effect itself ("an Incubator token that enters with N +1/+1 counters on it",
     * CR 701.53a), put on it as it enters with any others, so replacements on putting counters see them (CR 122.6).
     */
    fun createTokens(
        act: Act, cause: Cause, values: arcanum.engine.state.CopiableValues, count: Int, controller: PlayerId, tapped: Boolean = false,
        attachTo: TargetRef? = null, fromSpellCopy: arcanum.engine.state.CastFacts? = null, withCounters: List<Pair<CounterKind, Int>> = emptyList(),
    ): List<ObjectId> {
        if (count <= 0) return emptyList()
        val linked = mutableListOf<WaitingTrigger>()
        // D35: from the timestamp, not the cause's key
        val creator = TokenIds.created("token", act.state.nextTimestamp())
        fun planned(specs: List<Pair<arcanum.engine.state.CopiableValues, Int>>): List<GameObject> {
            var n = 0
            val first = act.state.nextTimestamp()
            return specs.flatMap { (f, k) -> List(k) { f } }.map { f ->
                val i = n++
                GameObject(
                    id = ObjectId.Card(TokenIds.token(creator, i), 0), owner = controller, controller = controller,
                    zone = Zone.Battlefield, definition = f.definition, face = f.up, token = true, tapped = tapped,
                    timestamp = first + i, controlSince = ControlSince(controller, act.state.turn.number), attachedTo = attachTo,
                    cast = arcanum.engine.state.castFacts(fromSpellCopy),
                    statuses = listOfNotNull(arcanum.engine.state.ObjectStatus.GainedInCopying(f.gained).takeIf { f.gained.isNotEmpty() },
                        // isd: what its abilities' "Gutter Grime" means: the object that created it, or the original's creator for a copy (CR 201.5a)
                        (f.createdBy ?: cause.source)?.let { arcanum.engine.state.ObjectStatus.CreatedBy(it) }),
                )
            }
        }
        val specs = if (fromSpellCopy != null) listOf(values to count) else Replacements.apply(act, cause, listOf(values to count), controller, null,
            proposed = { s -> Change.Created(planned(s).mapIndexed { i, o -> Placed(o, act.state.battlefield.size + i) }) },
            modify = { s, r, _ ->
                var result: List<Pair<arcanum.engine.state.CopiableValues, Int>>? = s
                // "That many" (CR 614.1a): the tokens this event would create so far
                val e = Evaluator(act.board(), Context(you = r.controller, self = r.source, names = r.names,
                    event = arcanum.engine.state.TriggerEvent(amount = s.sumOf { it.second })))
                r.mechanic.modify.forEach { mod ->
                    result = when (mod) {
                        Modification.Skip -> null
                        is Modification.MultiplyAmount -> result?.map { (f, k) -> f to k * mod.factor }
                        is Modification.AddAmount -> result?.map { (f, k) -> f to k + e.value(mod.amount) }
                        is Modification.AlsoCreate -> result?.plus(tokenValues(act, e, mod.token) to e.value(mod.count))
                        // fra: the matching tokens are created as another token instead, as many (CR 614.1a)
                        is Modification.CreateInstead -> result?.map { (f, k) ->
                            val probe = planned(listOf(f to 1)).single()
                            val pattern = r.mechanic.event as? arcanum.api.EventPattern.CreateToken
                            val matches = pattern == null || Evaluator(ProposedBoard(act.board(), mapOf(probe.id to probe)),
                                Context(you = r.controller, self = r.source, names = r.names)).matches(pattern.tokens, probe.id)
                            if (matches) tokenValues(act, e, mod.token) to k else f to k
                        }
                        is Modification.EntersTapped -> result
                        else -> result
                    }
                }
                result
            },
            instead = { r, instance -> runInstead(act, r, instance) },
            linked = linked,
            board = { s -> ProposedBoard(act.board(), planned(s).associateBy { it.id }) },
        ) ?: return emptyList()
        var tokens = planned(specs)
        // Replacements on entering (CR 614.12): "enters tapped", "with counters", tested on the tokens as they would be
        val proposedBoard = { ProposedBoard(act.board(), tokens.associateBy { it.id }) }
        // gtc: "enters … as a Mutant in addition to its other types" (CR 614.1c): an effect from as it enters
        val entersAs = LinkedHashMap<ObjectId, List<Mechanic>>()
        tokens = tokens.mapNotNull { t ->
            val enters = Replacements.candidates(act, cause).filter { r ->
                Replacements.matches(act, r, Change.Created(listOf(Placed(t, 0))), proposedBoard()).isNotEmpty()
            }
            var o = t.copy(counters = withCounters.filter { it.second > 0 }.map { (kind, n) -> CounterEntry(kind, n, t.timestamp) })
            enters.forEach { r ->
                // gtc: "this creature" of another permanent's replacement is that permanent (Master Biomancer's power)
                val e = Evaluator(proposedBoard(), Context(you = r.controller, self = r.source ?: t.id, names = r.names))
                r.mechanic.modify.forEach { mod ->
                    o = when (mod) {
                        Modification.EntersTapped -> o.copy(tapped = true)
                        is Modification.EntersWithCounters -> o.copy(counters = o.counters + CounterEntry(mod.kind, e.value(mod.amount), o.timestamp))
                        Modification.Skip -> return@mapNotNull null
                        is Modification.EntersAs -> o.also { entersAs[t.id] = entersAs[t.id].orEmpty() + mod.mechanics }
                        else -> o
                    }
                }
            }
            // CR 122.6: the counters it enters with are put on it (several of one kind add up)
            if (o.counters.isEmpty()) o else o.copy(counters = countersAsPut(act, cause, o.copy(counters = emptyList()),
                o.counters.map { it.kind to it.count }, linked).map { (kind, n) -> CounterEntry(kind, n, o.timestamp) })
        }
        // CR 303.4f–i, 303.4h, 301.5e: what each token is attached to as it is created; an Aura with nothing to enchant isn't created
        tokens = tokens.mapNotNull { t ->
            val face = t.definition.faces.getOrNull(t.face) ?: return@mapNotNull t
            val r = entering(act, t.id, t.controller ?: t.owner, face, t.attachedTo, proposedBoard())
            if (!r.enters) null else if (r.host == t.attachedTo) t else t.copy(attachedTo = r.host)
        }
        if (tokens.isEmpty()) return emptyList()
        val start = act.state.battlefield.size
        act.record(Change.Created(tokens.mapIndexed { i, o -> Placed(o, start + i) },
            if (fromSpellCopy != null) listOf(Meaning.Resolved, Meaning.Entered) else listOf(Meaning.TokenCreated, Meaning.Entered)))
        tokens.forEach { t ->
            val mechanics = entersAs[t.id] ?: return@forEach
            act.record(Changes.effect(act.state) { ts -> lockedEffect("E$ts", mechanics, t.id, t.controller ?: t.owner, ts, act) })
        }
        Replacements.addLinked(act, linked)
        return tokens.map { it.id }
    }

    /** The copiable values of a token spec (CR 111.4, 111.10, 707.2): a copy's are its layout and all its faces (CR 707.8a, 709.5b). */
    fun tokenValues(act: Act, e: Evaluator, spec: arcanum.api.TokenSpec): arcanum.engine.state.CopiableValues = when (spec) {
        is arcanum.api.TokenSpec.Defined -> arcanum.engine.state.CopiableValues.of(spec.face.copy(
            power = spec.power?.let { arcanum.api.PrintedNumber.Fixed(e.value(it)) } ?: spec.face.power,
            toughness = spec.toughness?.let { arcanum.api.PrintedNumber.Fixed(e.value(it)) } ?: spec.face.toughness,
        ))
        is arcanum.api.TokenSpec.Predefined -> {
            val front = arcanum.api.PredefinedTokens.face(spec.name) ?: FaceDefinition("${spec.name} Token")
            // mom chunk 03: a double-faced token (CR 111.10i), front face up
            val back = arcanum.api.PredefinedTokens.back(spec.name)
            if (back == null) arcanum.engine.state.CopiableValues.of(front) else arcanum.engine.state.CopiableValues(arcanum.api.Layout.Transform, listOf(front, back))
        }
        is arcanum.api.TokenSpec.CopyOf -> {
            val of = e.objects(spec.objects).firstOrNull()
            val values = of?.let { arcanum.engine.view.Copiable(act.state).forToken(it)?.takeIf { v -> v.faces.isNotEmpty() } }
                ?: arcanum.engine.state.CopiableValues.of(of?.let { e.characteristics(it) }?.let { c -> FaceDefinition(c.name ?: "Copy", c.manaCost, c.colors, c.supertypes, c.types,
                    c.subtypes, c.power?.let { arcanum.api.PrintedNumber.Fixed(it) }, c.toughness?.let { arcanum.api.PrintedNumber.Fixed(it) }) }
                    ?: FaceDefinition("Copy"))
            arcanum.engine.view.Copiable.exceptions(values, spec.except)
        }
    }

    // ---- Library, hand and graveyard

    /**
     * [player] draws [count] cards, one at a time (CR 121.2), each after replacements ("skip that draw", dredge, CR
     * 121.6). Drawing from an empty library is remembered for the state-based action (CR 704.5b). Returns the cards drawn.
     */
    fun draw(act: Act, cause: Cause?, player: PlayerId, count: Int): Map<ObjectId, ObjectId> {
        val drawn = LinkedHashMap<ObjectId, ObjectId>()
        repeat(drawCount(act, cause, player, count)) {
            val max = act.view().players[player]?.mechanics.orEmpty().mapNotNull { (it.mechanic as? Mechanic.MaxDrawsPerTurn)?.count }.minOrNull()
            if (max != null && drawsThisTurn(act, player) >= max) return drawn
            val top = act.state.player(player).library.firstOrNull()
            // CR 614.11: an "instead" replacement of the draw applies with no card to draw ("you win the game instead")
            if (top == null && Replacements.replaceEmptyDraw(act, cause, player) { r, instance -> runInstead(act, r, instance) }) return@repeat
            if (top == null) {
                // CR 121.4, 704.5b
                if (player !in act.state.emptyDraws) act.record(Change.EmptyLibraryDraw(player, false, true))
                return drawn
            }
            drawn += move(act, cause, listOf(PlannedMove(top, Zone.Hand, meanings = listOf(Meaning.Drew))))
        }
        return drawn
    }

    /** eoe: replacements that refer to the number of cards drawn ("you draw that many cards plus one instead") modify an instruction to
     * draw before its individual draws (CR 121.2a, 616.1g). */
    private fun drawCount(act: Act, cause: Cause?, player: PlayerId, count: Int): Int {
        if (count <= 0) return count
        var n = count
        Replacements.candidates(act, cause).forEach { r ->
            val pattern = r.mechanic.event as? arcanum.api.EventPattern.Draw ?: return@forEach
            if (r.mechanic.modify.isEmpty() || r.mechanic.instead != null ||
                r.mechanic.modify.any { it !is Modification.AddAmount && it !is Modification.MultiplyAmount }) return@forEach
            val e = Evaluator(act.board(), Context(you = r.controller, self = r.source, names = r.names))
            if (!e.matches(pattern.players, player)) return@forEach
            n = modifyAmount(act, n, r) ?: n
        }
        return n
    }

    private fun drawsThisTurn(act: Act, player: PlayerId): Int = act.state.eventsOf(act.state.turn.number).count { c ->
        c is Change.ZoneChange && c.moves.any { Meaning.Drew in it.meanings && it.before.owner == player }
    }

    /** [player] mills [count] (CR 701.17): the top cards into the graveyard as one event. */
    fun mill(act: Act, cause: Cause?, player: PlayerId, count: Int): Map<ObjectId, ObjectId> {
        // fin: replacements of the mill itself ("they mill that many cards plus four instead", CR 614.1a)
        val linked = mutableListOf<WaitingTrigger>()
        val n = if (count <= 0) 0 else Replacements.apply(act, cause, count, player, null,
            proposed = { k -> Change.Happened(arcanum.engine.change.GameEvent.Milling(player, k)) },
            modify = { k, r, _ -> modifyAmount(act, k, r) },
            instead = { r, instance -> runInstead(act, r, instance) }, linked = linked) ?: 0
        Replacements.addLinked(act, linked)
        val cards = act.state.player(player).library.take(n.coerceAtLeast(0))
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
        val order = if (shuffled.size <= 1) shuffled else act.randomObjects(player, source, shuffled, shuffled.size, library = library)
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

    /** [given]: costs of the mark's keyword the effect gives as the mark is gained (CR 702.143d), kept with the others it has. */
    fun tapStatus(act: Act, id: ObjectId, flag: StatusFlag, on: Boolean, meanings: List<Meaning> = emptyList(), given: List<arcanum.api.Cost> = emptyList()) {
        val o = act.obj(id) ?: return
        val now = when (flag) {
            StatusFlag.Tapped -> o.tapped
            StatusFlag.FaceDown -> o.faceDown
            StatusFlag.PhasedOut -> o.phasedOut
            is StatusFlag.Marked -> flag.mark in o.marks
            StatusFlag.Flipped -> o.flippedStatus
        }
        if (now != on) act.record(Change.Status(id, flag, now, on, meanings))
        // A mark whose turn is kept (CR 702.143d, 702.170d: a card that became foretold or plotted is castable on later turns)
        if (on && now != on && flag is StatusFlag.Marked) gained(act, id, flag.mark, given)?.let { record ->
            val marked = act.state.objOrThrow(id)
            arcanum.engine.change.Changes.status(act.state, id, arcanum.engine.state.ObjectStatus.MarksGained(emptyList()).key, marked.gaining(record))?.let(act::record)
        }
    }

    /** [id] gets or loses [mark]: a mark that gives a copy to cast does that as it is gained and lost (prepared, CR 722.3a–c). */
    fun setMark(act: Act, id: ObjectId, mark: arcanum.api.ObjectMark, on: Boolean, given: List<arcanum.api.Cost> = emptyList()) {
        if (arcanum.api.MarkRules.of(mark).permission == arcanum.api.MarkPermission.CastCopy) {
            if (on) arcanum.engine.prepare.Prepare.becomePrepared(act, id) else arcanum.engine.prepare.Prepare.unprepare(act, id)
        } else tapStatus(act, id, StatusFlag.Marked(mark), on, given = given)
    }

    /** The record of [id] gaining [mark] now ([arcanum.api.MarkRule.recordsTurn]), with the costs it has now of the keyword the mark's
     * permission names and those the effect gives ([given], CR 702.143d); null for a mark whose turn isn't kept. */
    private fun gained(act: Act, id: ObjectId, mark: arcanum.api.ObjectMark, given: List<arcanum.api.Cost>): arcanum.engine.state.MarkGained? {
        val rule = arcanum.api.MarkRules.of(mark)
        if (!rule.recordsTurn) return null
        val keyword = ((rule.permission as? arcanum.api.MarkPermission.CastCard)?.cost as? arcanum.api.MarkCost.OfKeyword)?.keyword
        val costs = keyword?.let { kind -> act.view()[id]?.characteristics?.abilities.orEmpty().mapNotNull { (it.ability as? Keyword)?.takeIf { k -> k.kind == kind } as? arcanum.api.HasCost }.map { it.cost } }
        return arcanum.engine.state.MarkGained(mark, act.state.turn.number, (costs.orEmpty() + if (keyword != null) given else emptyList()).distinct())
    }
}
