package arcanum.engine.resolution

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
 * Casting from an effect ([Effect.Cast]: cascade, rebound, suspend, "you may cast it without paying its mana cost",
 * CR 608.2g): a spell cast without paying its mana cost is put on the Stack here with its modes and targets (CR 601.2,
 * 118.9; X is 0, CR 107.3b). Casting for a mana cost (madness) announces the spell, then pays the mana; casting for
 * other costs, or for its mana cost, is done by hand until the casting flow ([arcanum.engine.casting.Casting]) offers an
 * entry point for effects.
 */
internal object Casts {
    fun castByEffect(scope: Scope, effect: Effect.Cast) {
        val act = scope.act
        val caster = scope.players(effect.caster).firstOrNull() ?: scope.actor
        val cast = mutableListOf<ObjectId>()
        for (id in scope.objects(effect.objects).filter { act.state.obj(it) != null }) {
            act.fill(caster, scope.self, listOf(id), "cast")
            val e = scope.eval()
            val c = act.view()[id]?.characteristics ?: continue
            if (effect.resultingSpell != null && !e.matches(effect.resultingSpell!!, id)) continue
            if (c.isType(CardType.Land)) {
                // CR 305.1: a land is played, not cast
                if (effect.how == PlayKind.Play) Actions.move(act, scope.cause, listOf(PlannedMove(id, Zone.Battlefield, controller = caster,
                    meanings = listOf(Meaning.PlayedLand))))
                continue
            }
            // A mana cost instead of its mana cost (madness, CR 702.35a): announced, then paid as when casting (CR 601.2g–h)
            val cost = effect.cost
            if (cost != null && cost.parts.all { it is arcanum.api.CostPart.Mana }) {
                putOnStack(act, scope.cause.key, caster, id)?.let { spell ->
                    Costs.pay(scope, caster, cost)
                    cast += spell
                }
                continue
            }
            if (!effect.withoutPaying || effect.cost != null) {
                act.manual(caster, id, "Cast ${c.name ?: "it"}" + (effect.cost?.let { " for ${Costs.describe(it)}" } ?: ""))
                continue
            }
            putOnStack(act, scope.cause.key, caster, id)?.let { cast += it }
        }
        scope.bind(effect.bind, NamedValue.Objects(cast))
    }

    /** Puts the card [id] on the Stack as a spell cast by [caster] without paying its mana cost (CR 601.2, 118.9),
     * choosing its modes and targets; returns the spell's id. */
    fun putOnStack(act: Act, key: String, caster: PlayerId, id: ObjectId): ObjectId? {
        val from = act.state.obj(id)?.zone ?: return null
        val face = act.state.obj(id)?.definition?.faces?.firstOrNull() ?: return null
        val spellIndex = face.abilities.indexOfFirst { it is Ability.Spell }
        val ability = face.abilities.getOrNull(spellIndex) as? Ability.Spell
        val instructions = ability?.instructions ?: Instructions()
        val choices = announce(act, caster, id, instructions)
        val moved = Actions.move(act, null, listOf(PlannedMove(id, Zone.Stack, controller = caster)))
        val spell = moved[id] ?: return null
        val o = act.state.objOrThrow(spell)
        act.record(Change.Facts(spell, o.facts, o.facts.copy(castFrom = from, x = 0, modesChosen = choices.first)))
        val item = StackItem(
            id = spell, kind = StackItemKind.Spell, controller = caster, source = spell, ability = ability,
            origin = if (spellIndex >= 0) AbilityOrigin.Printed(0, spellIndex) else null,
            targets = choices.second, modes = choices.first, announced = mapOf("X" to 0), timestamp = act.state.nextTimestamp(),
        )
        act.record(Change.StackItemAdded(item, null, listOf(Meaning.Cast)))
        return spell
    }

    /** Modes (CR 700.2) and targets (CR 601.2c) for [instructions] of a spell or ability controlled by [controller]. */
    fun announce(
        act: Act, controller: PlayerId, source: ObjectId?, instructions: Instructions, names: Bindings = Bindings.EMPTY,
        self: ObjectId? = null,
    ): Pair<List<Int>, Map<String, List<TargetRef>>> = announceOrNull(act, controller, source, instructions, names, self, required = false)!!

    /**
     * Like [announce]; with [required], null when a target slot that needs a target has none (a triggered ability is then
     * removed from the Stack, CR 603.3d).
     */
    fun announceOrNull(
        act: Act, controller: PlayerId, source: ObjectId?, instructions: Instructions, names: Bindings, self: ObjectId?, required: Boolean,
    ): Pair<List<Int>, Map<String, List<TargetRef>>>? {
        val modes = instructions.modes?.let { m ->
            // "Choose one that hasn't been chosen (this turn)": modes this object chose before aren't available
            val o = source?.let { act.state.obj(it) }
            val turn = act.state.turn.number
            val used = buildSet {
                if (m.onlyUnchosen) addAll(o?.facts?.modesChosen.orEmpty())
                if (m.onlyUnchosenThisTurn) o?.facts?.modeTurns.orEmpty().filter { it.turn == turn }.forEach { add(it.key.toInt()) }
            }
            if (m.options.size - used.size < m.min) return null
            val options = m.options.mapIndexed { i, mode -> ModeOption(i, mode.text, available = i !in used) }
            val chosen = if (used.isEmpty() && m.min == m.max && m.max == m.options.size && !m.repeatable) options.map { it.index }
                else (act.asker.ask { Decision.Modes(it, controller, source, options, m.min, m.max, m.repeatable) } as Answer.Modes).chosen
            require(chosen.none { it in used }) { "Mode already chosen: $chosen" }
            if (o != null && (m.onlyUnchosen || m.onlyUnchosenThisTurn) && chosen.isNotEmpty()) {
                act.record(Change.Facts(o.id, o.facts, o.facts.copy(
                    modesChosen = (o.facts.modesChosen + chosen).distinct(),
                    modeTurns = o.facts.modeTurns.filter { it.turn == turn } + chosen.distinct().map { TurnCount(it.toString(), turn, 1) },
                )))
            }
            chosen
        }.orEmpty()
        val slots = instructions.targets + modes.flatMap { instructions.modes?.options?.getOrNull(it)?.instructions?.targets.orEmpty() }
        val targets = LinkedHashMap<String, List<TargetRef>>()
        val e = act.evaluator(arcanum.engine.query.Context(controller, source, names))
        for (slot in slots) {
            val options = Targeting.options(act, slot, controller, source, names, targets, self)
            val min = e.value(slot.min)
            val max = slot.max?.let { e.value(it) }
            if (options.size < min && required) return null
            // P5: no decision when every legal target must be chosen
            val chosen = if (options.size <= min) options else act.chooseTargets(controller, source, slot.name, options, min, max)
            if (chosen.isNotEmpty()) targets[slot.name] = chosen
        }
        return modes to targets
    }
}
