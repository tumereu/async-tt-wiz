package arcanum.engine.epic

import arcanum.api.Affected
import arcanum.api.CopyException
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.engine.action.Act
import arcanum.engine.change.Change
import arcanum.engine.change.Changes
import arcanum.engine.state.Bindings
import arcanum.engine.state.DelayedTrigger
import arcanum.engine.state.EffectId
import arcanum.engine.state.EffectObject
import arcanum.engine.state.NamedValue
import arcanum.engine.state.StackItem
import arcanum.engine.state.StackItemKind

/**
 * Epic (CR 702.50): "For the rest of the game, you can't cast spells," and "At the beginning of each of your upkeeps for the rest of
 * the game, copy this spell except for its epic ability. If the spell has any targets, you may choose new targets for the copy."
 * Both are done as a spell with epic finishes resolving ([resolves]): an effect object, and a repeating delayed trigger that keeps
 * the spell as it last was on the Stack ([NamedValue.Spell]), its choices and characteristics, to copy it from (CR 707.10).
 */
internal object Epic {
    const val SPELL: String = "\$epic-spell"

    /** CR 702.50a, b: [item], a spell or a copy of one with epic, resolves. */
    fun resolves(act: Act, item: StackItem) {
        if (item.kind != StackItemKind.Spell && item.kind !is StackItemKind.SpellCopy) return
        if (act.view()[item.id]?.characteristics?.hasKeyword(Keyword.Epic) != true) return
        val player = item.controller
        act.record(Changes.effect(act.state) { ts ->
            EffectObject(id = EffectId("E$ts"), mechanics = listOf(Mechanic.CantCast(ObjectFilter.Any)), affects = Affected.Players(Players.You),
                lockedPlayers = listOf(player), source = item.id, controller = player, timestamp = ts, createdTurn = act.state.turn.number,
                duration = Duration.Indefinitely)
        })
        val frozen = item.copy(facts = item.facts ?: act.state.obj(item.id)?.facts,
            snapshots = item.snapshots + listOfNotNull(act.snapshot(item.id)))
        val copy = Effect.CopySpell(Objects.Bound(SPELL), Players.You, newTargets = true, except = listOf(CopyException.WithoutKeyword(Keyword.Epic)))
        act.record(Change.DelayedTriggerAdded(act.state.nextTimestamp().let { ts ->
            DelayedTrigger(EffectId("D$ts"), Trigger.On(EventPattern.StepBegins(TurnStep.Upkeep, PlayerFilter.You)),
                Instructions(steps = listOf(Step.Do(copy))), repeats = true, source = item.id, controller = player, timestamp = ts,
                createdTurn = act.state.turn.number, names = Bindings.EMPTY.with(SPELL, NamedValue.Spell(frozen)))
        }))
    }
}
