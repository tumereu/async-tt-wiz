package arcanum.engine.turn

import arcanum.api.Condition
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.engine.action.Act
import arcanum.engine.change.Change
import arcanum.engine.change.Changes
import arcanum.engine.state.DelayedTrigger
import arcanum.engine.state.EffectId
import arcanum.engine.state.GameState
import arcanum.engine.state.PlayerId
import arcanum.engine.view.View

/**
 * Speed (CR 702.179): kept in the player's ledger under [KEY] (0: no speed). A player who controls a permanent with start your
 * engines! and has no speed gets speed 1 (a state-based action, 702.179a); having speed gives them the inherent triggered ability
 * "Whenever one or more opponents lose life during your turn, if your speed is less than 4, your speed increases by 1. This ability
 * triggers only once each turn" (702.179d), kept as a repeating delayed trigger with no source; max speed is 4 (702.179e).
 */
internal object Speed {
    const val KEY: String = "speed"
    const val MAX: Int = 4

    fun of(state: GameState, player: PlayerId): Int = state.player(player).ledger[KEY] ?: 0

    /** [player]'s speed becomes [value] (at most [MAX]); getting speed gives them the inherent trigger. */
    fun set(act: Act, player: PlayerId, value: Int) {
        val before = of(act.state, player)
        val after = value.coerceIn(0, MAX)
        if (after == before) return
        act.record(Change.Ledger(player, KEY, before, after))
        if (before == 0 && after > 0) act.record(Change.DelayedTriggerAdded(act.state.nextTimestamp().let { ts ->
            DelayedTrigger(EffectId("D$ts"), INHERENT, Instructions(steps = listOf(Step.If(Condition.And(listOf(Condition.YourTurn,
                Condition.Not(Condition.MaxSpeed))), listOf(Step.Do(Effect.IncreaseSpeed(Players.You, Value.Fixed(1))))))),
                repeats = true, source = null, controller = player, timestamp = ts, createdTurn = act.state.turn.number)
        }))
    }

    /** CR 702.179a: players with no speed who control a permanent with start your engines! get speed 1. */
    fun check(act: Act, view: View) {
        act.state.players.filter { !it.lost && of(act.state, it.id) == 0 }.forEach { p ->
            val engines = act.state.battlefield.any { id -> view[id]?.controller == p.id &&
                view[id]?.characteristics?.abilities.orEmpty().any { it.ability == Keyword.StartYourEngines } }
            if (engines) set(act, p.id, 1)
        }
    }

    /** "Whenever one or more opponents lose life during your turn … This ability triggers only once each turn." */
    private val INHERENT = Trigger.On(listOf(EventPattern.LoseLife(PlayerFilter.Opponent)), oneOrMore = true, nthThisTurn = 1)
}
