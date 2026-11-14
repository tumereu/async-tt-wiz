package arcanum.engine.resolution

import arcanum.engine.state.castOrNone
import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.Effect
import arcanum.api.Instructions
import arcanum.api.PlayKind
import arcanum.api.Zone
import arcanum.engine.action.Act
import arcanum.engine.action.Actions
import arcanum.engine.action.PlannedMove
import arcanum.engine.action.Targeting
import arcanum.engine.change.Change
import arcanum.engine.change.Meaning
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.ModeOption
import arcanum.engine.state.Bindings
import arcanum.engine.state.NamedValue
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.StackItem
import arcanum.engine.state.StackItemKind
import arcanum.engine.state.TargetRef
import arcanum.engine.state.TurnCount
import arcanum.engine.view.AbilityOrigin

/**
 * Casting from an effect ([Effect.Cast]: cascade, rebound, suspend, madness, "you may cast it", CR 608.2g): each card
 * is cast with the casting flow's own plan ([arcanum.engine.casting.Casting.castByEffect]) on the effect's terms; one
 * that can't be cast (no legal targets, a cost it can't pay) isn't, and is left out of [Effect.Cast.bind] so the
 * effect's "if you don't" part applies to it. A land is played instead when the effect says play (CR 305.1).
 * [announceOrNull] chooses a triggered ability's modes and targets as it is put on the Stack (CR 603.3d).
 */
internal object Casts {
    fun castByEffect(scope: Scope, effect: Effect.Cast) {
        val act = scope.act
        val caster = scope.players(effect.caster).firstOrNull() ?: scope.actor
        val cast = mutableListOf<ObjectId>()
        for (id in scope.objects(effect.objects).filter { act.state.obj(it) != null }) {
            // woe chunk 04: a card that can't be cast this way isn't shown (a face-down card goes on hidden; showing it would also count as
            // something done for "if you don't cast it", CR 608.2c)
            if (effect.resultingSpell != null && !resultingSpellMatches(scope, effect.resultingSpell!!, id)) continue
            act.show(caster, listOf(id), public = true)
            val c = act.view()[id]?.characteristics ?: continue
            if (c.isType(CardType.Land)) {
                // CR 305.1: a land is played, not cast
                if (effect.how != PlayKind.Play) continue
                // CR 305.2a-b, 305.3: playing a land during a resolution counts as a land play; ignored on another player's turn or with no
                // land plays left; bro sweep: nor when an effect says the player can't play that land ("can't play land cards from exile",
                // CR 101.2), as for any land play
                val turn = act.state.turn
                val played = turn.landsPlayed[caster] ?: 0
                val snap = arcanum.engine.casting.Snapshot(act.state, viewOf = { act.view() })
                if (turn.active != caster || played >= arcanum.engine.casting.Legality.landPlays(snap, caster)) continue
                if (arcanum.engine.casting.Legality.cantPlayLandBy(snap, caster, id)) continue
                act.record(arcanum.engine.change.Changes.step(act.state, turn.copy(landsPlayed = turn.landsPlayed + (caster to played + 1))))
                Actions.move(act, scope.cause, listOf(PlannedMove(id, Zone.Battlefield, controller = caster, meanings = listOf(Meaning.PlayedLand))))
                continue
            }
            // mh3 chunk 02: the cost's amounts are the resolving ability's ("an amount of {E} equal to its mana value": the exiled card's), fixed
            // now, as the casting plan doesn't know the ability's names (CR 608.2h)
            val e = scope.eval()
            val cost = effect.cost?.let { c -> c.copy(parts = c.parts.map { p -> when (p) {
                is arcanum.api.CostPart.PayEnergy -> p.copy(amount = arcanum.api.Value.Fixed(e.value(p.amount)))
                is arcanum.api.CostPart.PayLife -> p.copy(amount = arcanum.api.Value.Fixed(e.value(p.amount)))
                else -> p
            } }) }
            arcanum.engine.casting.Casting.castByEffect(act, caster, id, effect.withoutPaying, cost, effect.transformed, effect.manaAsAnyType)?.let { cast += it }
        }
        scope.bind(effect.bind, NamedValue.Objects(cast))
    }

    /** Whether casting [id] would make a spell matching [filter] ("if that spell's mana value is 4 or less"): a card exiled face down is cast
     * face up (CR 601.2a, 708.2: face down it has no mana cost), so it is judged face up. */
    private fun resultingSpellMatches(scope: Scope, filter: arcanum.api.ObjectFilter, id: ObjectId): Boolean {
        val act = scope.act
        val e = scope.eval()
        if (act.state.obj(id)?.faceDown != true) return e.matches(filter, id)
        val faceUp = act.state.deepCopy().also { it.apply(Change.Status(id, arcanum.engine.state.StatusFlag.FaceDown, before = true, after = false)) }
        return arcanum.engine.query.Evaluator(arcanum.engine.query.ViewBoard(faceUp, arcanum.engine.Engine.view(faceUp)), e.ctx).matches(filter, id)
    }

    /**
     * Modes (CR 700.2) and targets (CR 601.2c) for [instructions] of a triggered ability controlled by [controller];
     * with [required], null when a target slot that needs a target has none (it is then removed from the Stack,
     * CR 603.3d). [watch] is given the chosen modes and the targets map, filled in as each slot is chosen, before any
     * target is asked: what was chosen so far when a decision interrupts it.
     */
    fun announceOrNull(
        act: Act, controller: PlayerId, source: ObjectId?, instructions: Instructions, names: Bindings, self: ObjectId?, required: Boolean,
        event: arcanum.engine.state.TriggerEvent? = null,
        watch: ((List<Int>, Map<String, List<TargetRef>>) -> Unit)? = null,
        // mh3: the kind of the ability being put on the Stack (a trigger, CR 603.3d)
        pendingKind: arcanum.api.AbilityKind? = null,
    ): Pair<List<Int>, Map<String, List<TargetRef>>>? {
        val modes = instructions.modes?.let { m ->
            // "Choose one that hasn't been chosen (this turn)": modes this object chose before aren't available
            val o = source?.let { act.state.obj(it) }
            val turn = act.state.turn.number
            val used = buildSet {
                if (m.onlyUnchosen) addAll(o?.castOrNone?.modesChosen.orEmpty())
                if (m.onlyUnchosenThisTurn) o?.uses?.modeTurns.orEmpty().filter { it.turn == turn }.forEach { add(it.key.toInt()) }
            }
            val ev = act.evaluator(arcanum.engine.query.Context(controller, source, names, event = event))
            // CR 700.2b: a mode that couldn't choose its targets can't be chosen; with too few left, the ability is removed from the Stack
            val illegal = if (!required) emptySet() else m.options.indices.filterTo(mutableSetOf()) { i ->
                m.options[i].instructions.targets.any { slot ->
                    slot.perPlayer == null && !Targeting.possible(slot, Targeting.options(act, slot, controller, source, names, emptyMap(), self, event = event,
                        pendingKind = pendingKind), ev.value(slot.min), ev)
                }
            }
            if (m.options.size - (used + illegal).size < m.min) return null
            val options = m.options.mapIndexed { i, mode -> ModeOption(i, mode.text, available = i !in used && i !in illegal, pawprints = mode.pawprints) }
            // mh2: "choose one. If …, choose both instead" on a triggered ability, judged as it is put on the Stack (CR 700.2, 603.3c)
            val raised = m.maxIf?.let { ev.holds(it) } == true
            val printed = if (raised) m.maxIfCount ?: m.max else m.max
            // msh: "choose up to X": at most that many
            val max = m.maxCount?.let { minOf(printed, maxOf(m.min, ev.value(it))) } ?: printed
            val min = if (raised && m.min == m.max && !m.upToMaxIf) max else m.min
            val chosen = if (used.isEmpty() && min == max && max == m.options.size && !m.repeatable) options.map { it.index }
                else (act.ask { Decision.Modes(it, controller, source, options, min, max, m.repeatable, m.pawprintLimit) } as Answer.Modes).chosen
            require(chosen.size <= max) { "At most $max modes" }
            require(chosen.none { it in used }) { "Mode already chosen: $chosen" }
            require(chosen.none { it in illegal }) { "Mode without legal targets: $chosen" }
            if (o != null && (m.onlyUnchosen || m.onlyUnchosenThisTurn) && chosen.isNotEmpty()) {
                arcanum.engine.change.Changes.cast(act.state, o.id, o.castOrNone.copy(modesChosen = (o.castOrNone.modesChosen + chosen).distinct()))?.let(act::record)
                act.record(arcanum.engine.change.Changes.uses(act.state, o.id, o.uses.copy(
                    modeTurns = o.uses.modeTurns.filter { it.turn == turn } + chosen.distinct().map { TurnCount(it.toString(), turn, 1) })))
            }
            chosen
        }.orEmpty()
        val slots = instructions.targets + modes.flatMap { instructions.modes?.options?.getOrNull(it)?.instructions?.targets.orEmpty() }
        val targets = LinkedHashMap<String, List<TargetRef>>()
        watch?.invoke(modes, targets)
        // A triggered ability's targets may depend on its event ("with lesser mana value", CR 603.3d)
        val e = act.evaluator(arcanum.engine.query.Context(controller, source, names, event = event))
        // inr: a later slot sees the targets chosen for the earlier ones ("up to one target creature that player or that
        // planeswalker's controller controls", CR 601.2c, 603.3d)
        fun named() = targets.entries.fold(names) { b, (k, v) -> b.with(k, arcanum.engine.state.NamedValue.Targets(v)) }
        for (slot in slots) {
            // fra: one choice for each player of a per-player slot ("for each opponent, up to one target creature that player
            // controls", CR 601.2c), all under the slot's name
            slot.perPlayer?.let { f ->
                val chosenAll = mutableListOf<TargetRef>()
                for (p in act.state.playersFrom(controller).filter { e.matches(f, it) }) {
                    val options = Targeting.options(act, slot, controller, source, named(), targets, self, event = event, slotPlayer = p, pendingKind = pendingKind)
                    val pe = e.with(e.ctx.copy(slotPlayer = p))
                    val min = pe.value(slot.min)
                    val max = slot.max?.let { pe.value(it) }
                    if (!Targeting.possible(slot, options, min, pe) && required) return null
                    chosenAll += if (options.size <= min) options else Targeting.choose(act, controller, source, slot, options, min, max, pe)
                }
                if (chosenAll.isNotEmpty()) targets[slot.name] = chosenAll
                continue
            }
            val options = Targeting.options(act, slot, controller, source, named(), targets, self, event = event, pendingKind = pendingKind)
            val min = e.value(slot.min)
            val max = slot.max?.let { e.value(it) }
            // CR 603.3d: too few legal targets, or no group of them within the slot's limits on each other (CR 601.2c)
            if (!Targeting.possible(slot, options, min, e) && required) return null
            // P5: no decision when every legal target must be chosen
            val chosen = when {
                options.size <= min -> options
                // dgm: "target … chosen at random": the host picks one (a die with a side for each legal target)
                slot.atRandom -> listOfNotNull(options.getOrNull((act.random(controller, source,
                    arcanum.engine.decision.RandomKind.Die(options.size, 1)) as Answer.DieRolls).results.single() - 1))
                // rvr fixes: "target … of an opponent's choice": the controller picks which opponent, who chooses it (CR 601.2c; 801.5a's example)
                slot.chosenBy != null -> act.chooseTargets(act.choosePlayer(controller, source, act.state.playersFrom(controller).filter { e.matches(slot.chosenBy!!, it) })
                    ?: controller, source, slot.name, options, min, max)
                else -> act.chooseTargets(controller, source, slot.name, options, min, max)
            }
            // mkm fixes: one controller or owner, shared or different qualities, a total: as for a spell (CR 603.3d, 601.2c)
            Targeting.groupRefusal(slot, chosen, e)?.let { throw IllegalArgumentException(it) }
            if (chosen.isNotEmpty()) targets[slot.name] = chosen
        }
        return modes to targets
    }
}
