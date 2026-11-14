package arcanum.engine.turn

import arcanum.api.Ability
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
import arcanum.engine.decision.WaitingTrigger
import arcanum.engine.query.Context
import arcanum.engine.query.Evaluator
import arcanum.engine.query.ViewBoard
import arcanum.engine.state.GameState
import arcanum.engine.state.PlayerId
import arcanum.engine.state.StackItemKind
import arcanum.engine.state.playerStatus
import arcanum.engine.trigger.Triggers
import arcanum.engine.view.AbilityOrigin
import arcanum.engine.view.View

/**
 * Speed (CR 702.179): the player's [arcanum.engine.state.PlayerStatus.Speed] (none: no speed). A player who controls a permanent with start your
 * engines! and has no speed gets speed 1 (a state-based action, 702.179a); having speed gives them the inherent triggered ability
 * "Whenever one or more opponents lose life during your turn, if your speed is less than 4, your speed increases by 1. This ability
 * triggers only once each turn" (702.179d), found by [inherent] with no source and [RULE] as its origin; max speed is 4 (702.179e).
 */
internal object Speed {
    /** Max speed (CR 702.179e). */
    const val MAX: Int = 4

    /** The origin of the inherent speed ability on the Stack (CR 702.179d): a rule's, with no source. */
    val RULE: AbilityOrigin.Rule = AbilityOrigin.Rule("speed")

    /** [player]'s speed, 0 when they have none (CR 702.179f). */
    fun of(state: GameState, player: PlayerId): Int = state.player(player).statuses.playerStatus<arcanum.engine.state.PlayerStatus.Speed>()?.value ?: 0

    /** [player]'s speed becomes [value] (at most [MAX]). */
    fun set(act: Act, player: PlayerId, value: Int) {
        val before = of(act.state, player)
        val after = value.coerceIn(0, MAX)
        if (after == before) return
        act.record(Change.PlayerStatusChanged(player, arcanum.engine.state.PlayerStatus.Speed(before).takeIf { before > 0 }, arcanum.engine.state.PlayerStatus.Speed(after).takeIf { after > 0 }))
    }

    /** CR 702.179a: players with no speed who control a permanent with start your engines! get speed 1. */
    fun check(act: Act, view: View) {
        act.state.players.filter { !it.lost && of(act.state, it.id) == 0 }.forEach { p ->
            val engines = act.state.battlefield.any { id -> view[id]?.controller == p.id &&
                view[id]?.characteristics?.abilities.orEmpty().any { it.ability == Keyword.StartYourEngines } }
            if (engines) set(act, p.id, 1)
        }
    }

    /**
     * CR 702.179d: the inherent speed ability [change] triggers. Only the active player's can ("during your turn"), only while they have
     * speed, only below max speed (the intervening "if", CR 603.4: checked as the event happens and again on resolution, [ABILITY]'s
     * [Ability.Triggered.interveningIf]) and only once each turn: not when it already triggered this turn (waiting, or put on the Stack).
     */
    fun inherent(state: GameState, now: View, change: Change): List<WaitingTrigger> {
        val player = state.players.firstOrNull { it.id == state.turn.active && !it.lost }?.id ?: return emptyList()
        val speed = of(state, player)
        if (speed == 0 || speed >= MAX) return emptyList()
        val e = Evaluator(ViewBoard(state, now), Context(player, null))
        val instance = Triggers.instancesOf(e, state, TRIGGER, change).firstOrNull() ?: return emptyList()
        if (triggeredThisTurn(state, player)) return emptyList()
        return listOf(WaitingTrigger(player, ABILITY, RULE, null, event = instance))
    }

    /** Whether [player]'s speed ability triggered this turn: it waits to be put on the Stack, or was put there this turn. */
    private fun triggeredThisTurn(state: GameState, player: PlayerId): Boolean =
        state.triggered.any { it.controller == player && it.origin == RULE } ||
            state.eventsOf(state.turn.number).any { it is Change.StackItemAdded && it.item.kind == StackItemKind.Triggered &&
                it.item.origin == RULE && it.item.controller == player }

    /** "Whenever one or more opponents lose life" (during your turn: [inherent] asks only the active player's). */
    private val TRIGGER = Trigger.On(listOf(EventPattern.LoseLife(PlayerFilter.Opponent)), oneOrMore = true)

    /** The inherent ability (CR 702.179d) with its text, as it goes on the Stack (CR 603.3: it has the text of the ability). */
    val ABILITY: Ability.Triggered = Ability.Triggered(TRIGGER, Instructions(steps = listOf(Step.Do(Effect.IncreaseSpeed(Players.You, Value.Fixed(1))))),
        interveningIf = Condition.Not(Condition.MaxSpeed), perTurn = 1,
        text = "Whenever one or more opponents lose life during your turn, if your speed is less than 4, your speed increases by 1. " +
            "This ability triggers only once each turn.")
}
