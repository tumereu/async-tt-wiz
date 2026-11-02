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
            arcanum.engine.casting.Casting.castByEffect(act, caster, id, effect.withoutPaying, effect.cost)?.let { cast += it }
        }
        scope.bind(effect.bind, NamedValue.Objects(cast))
    }

    /**
     * Modes (CR 700.2) and targets (CR 601.2c) for [instructions] of a triggered ability controlled by [controller];
     * with [required], null when a target slot that needs a target has none (it is then removed from the Stack,
     * CR 603.3d).
     */
    fun announceOrNull(
        act: Act, controller: PlayerId, source: ObjectId?, instructions: Instructions, names: Bindings, self: ObjectId?, required: Boolean,
        event: arcanum.engine.state.TriggerEvent? = null,
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
        // A triggered ability's targets may depend on its event ("with lesser mana value", CR 603.3d)
        val e = act.evaluator(arcanum.engine.query.Context(controller, source, names, event = event))
        for (slot in slots) {
            val options = Targeting.options(act, slot, controller, source, names, targets, self, event = event)
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
