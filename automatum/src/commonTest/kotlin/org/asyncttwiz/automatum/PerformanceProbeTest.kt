package org.asyncttwiz.automatum

import arcanum.api.TurnStep
import arcanum.engine.input.Input
import arcanum.engine.state.PlayerId
import org.asyncttwiz.protocol.CommandIds
import org.asyncttwiz.protocol.GameAction
import org.asyncttwiz.protocol.GameCommand
import org.asyncttwiz.protocol.GameScope
import org.asyncttwiz.protocol.GameplayRules
import org.asyncttwiz.protocol.PlayAction
import org.asyncttwiz.protocol.PlayBatches
import org.asyncttwiz.protocol.PlayState
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.TurnPosition
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * A printed probe of what one engine call costs on a busy board (SYS-025: a local apply in under 20 ms). It prints
 * numbers and asserts only the outcome, never elapsed time (no timing tests): speed is watched by the in-app slow-action
 * reports. About 180 cards: 90 permanents, 80 library cards, 7 in hand.
 */
class PerformanceProbeTest {
    private val table = AtwTable("probe")
    private val alice get() = table.alice
    private val bob get() = table.bob

    init {
        table.put(alice, PlayZone.BATTLEFIELD, *Array(25) { "Mountain" }, tapped = true)
        table.put(alice, PlayZone.BATTLEFIELD, *Array(20) { "Tinkered Mite" }, tapped = true)
        table.put(bob, PlayZone.BATTLEFIELD, *Array(25) { "Island" }, tapped = true)
        table.put(bob, PlayZone.BATTLEFIELD, *Array(20) { "Tinkered Mite" }, tapped = true)
        table.put(alice, PlayZone.HAND, *Array(4) { "Storm Spark" })
        table.put(bob, PlayZone.HAND, *Array(3) { "Island" })
        table.put(alice, PlayZone.LIBRARY, *Array(40) { "Mountain" })
        table.put(bob, PlayZone.LIBRARY, *Array(40) { "Island" })
    }

    /** The busy board in Alice's turn 1 at [position], Alice holding priority. */
    private fun board(position: TurnPosition): PlayState {
        table.turn = table.turn.copy(phase = position.phase, step = position.step)
        return table.state()
    }

    private class Timing(var build: Duration = Duration.ZERO, var perform: Duration = Duration.ZERO,
        var plan: Duration = Duration.ZERO, var apply: Duration = Duration.ZERO, var members: Int = 0)

    /** One engine call as the acting client makes it, then its batch as the server applies it. */
    private fun call(state: PlayState, input: Input, actor: String, n: Int, timing: Timing): PlayState {
        val clock = TimeSource.Monotonic
        var mark = clock.markNow()
        val call = EngineCall(state, table.definitions, seeds = { CommandIds.uuidV5(AtwTable.NAMESPACE, "seed/$n") })
        timing.build += mark.elapsedNow(); mark = clock.markNow()
        call.perform(input)
        timing.perform += mark.elapsedNow(); mark = clock.markNow()
        val command = GameCommand(CommandIds.uuidV5(AtwTable.NAMESPACE, "probe/$n"), GameScope(state.gameId, playId = state.id),
            GameAction.PlayBatch(listOf(PlayAction.PassAttention)), CommandIds.uuidV5(AtwTable.NAMESPACE, "stream"), "1")
        val plan = EngineBatch.plan(call, actor, command)
        timing.plan += mark.elapsedNow(); mark = clock.markNow()
        val batch = command.copy(action = plan.action)
        var next = state
        for ((i, action) in plan.actions.withIndex()) next = GameplayRules.change(next, actor, PlayBatches.member(batch, i, action)).state!!
        timing.apply += mark.elapsedNow()
        timing.members = plan.actions.size
        return next
    }

    @Test fun probeOneCallOnABusyBoard() {
        val main = board(TurnPosition.PRECOMBAT_MAIN)
        val end = board(TurnPosition.END_STEP)
        val rounds = 5 // the first ones warm up the JVM
        repeat(rounds) { round ->
            val pass = Timing()
            val step = Timing()
            val turn = Timing()
            // Main phase: Alice passes, Bob passes (the game moves to the beginning of combat)
            var state = call(main, Input.PassPriority(PlayerId(alice)), alice, 0, pass)
            state = call(state, Input.PassPriority(PlayerId(bob)), bob, 1, step)
            check(EngineState.from(state, table.definitions).turn.step != TurnStep.PrecombatMain) { "the step didn't move on" }
            // End step: both pass; the turn moves on (cleanup, Bob's untap of 45 permanents, upkeep)
            state = call(end, Input.PassPriority(PlayerId(alice)), alice, 2, Timing())
            state = call(state, Input.PassPriority(PlayerId(bob)), bob, 3, turn)
            check(EngineState.from(state, table.definitions).turn.number == 2) { "the turn didn't move on" }
            if (round == rounds - 1) for ((what, t) in listOf("pass priority" to pass, "step change" to step, "turn change" to turn))
                println("probe $what (${t.members} members): build ${t.build}, perform ${t.perform}, plan ${t.plan}, apply as the server ${t.apply}")
        }
    }
}
