package arcanum.engine.casting

import arcanum.api.Affected
import arcanum.engine.change.Change
import arcanum.engine.change.Recorder
import arcanum.engine.query.Evaluator
import arcanum.engine.state.EffectId
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId

/**
 * Effects on "the next [count] spells" ([Affected.NextSpells]: "the next instant or sorcery spell you cast this turn
 * costs {2} less", CR 611.2f): they apply to a matching spell as it is cast and are used up by it, one spell each
 * ([arcanum.engine.state.EffectObject.usesLeft] counts the spells left).
 */
internal object NextSpells {
    /** Whether the mechanic of [effect] applies to [spell]: always unless [effect] waits for the next spells, then when
     * [spell] is one of them. */
    fun appliesTo(state: GameState, effect: EffectId?, e: Evaluator, spell: ObjectId): Boolean {
        val o = effect?.let { state.effects[it] } ?: return true
        val next = o.affects as? Affected.NextSpells ?: return true
        return (o.usesLeft ?: next.count) > 0 && e.matches(next.spells, spell)
    }

    /** [spell] became cast (CR 601.2i): each effect waiting for it is used up by one spell, and ends with its last. */
    fun useUp(recorder: Recorder, spell: ObjectId) {
        val state = recorder.state
        val waiting = state.effects.values.filter { it.affects is Affected.NextSpells }.sortedBy { it.timestamp }
        if (waiting.isEmpty()) return
        val board = recorder.snapshot()
        waiting.forEach { o ->
            val next = o.affects as Affected.NextSpells
            val e = board.evaluator(o.controller, self = o.source, names = o.names)
            if (!e.matches(next.spells, spell)) return@forEach
            // fra: what the effect does to the spell beyond its cost ("can't be countered") now applies to it and the permanent
            // it becomes (CR 611.2f, 400.7d)
            val gets = o.mechanics.filter { it !is arcanum.api.Mechanic.CostChange }
            if (gets.isNotEmpty()) recorder.record(arcanum.engine.change.Changes.effect(state) { t ->
                val after = (spell as? ObjectId.Card)?.next()
                arcanum.engine.state.EffectObject(EffectId("next:${o.id.value}:$spell"), gets, affects = null, locked = listOfNotNull(spell, after),
                    source = o.source, controller = o.controller, timestamp = t, createdTurn = state.turn.number, duration = arcanum.api.Duration.Indefinitely,
                    names = arcanum.engine.state.Bindings(mapOf(arcanum.engine.resolution.Effects.FOLLOWS_SPELL to arcanum.engine.state.NamedValue.Objects(listOfNotNull(spell, after)))))
            })
            val left = (o.usesLeft ?: next.count) - 1
            if (left <= 0) recorder.record(Change.EffectRemoved(o))
            else recorder.record(Change.EffectUpdated(o, o.copy(usesLeft = left)))
        }
    }
}
