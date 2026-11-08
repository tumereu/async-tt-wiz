package org.asyncttwiz.android

import arcanum.engine.input.Input
import arcanum.engine.state.PlayerId
import org.asyncttwiz.protocol.game.EnginePart
import org.asyncttwiz.protocol.game.EngineStep
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.GameEvent
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** TRN-075, TRN-058, TRN-063 (owner, 2026-11-08): when the move passes to you from the other player: one cue, once. */
class YourMoveCueTest {
    private val me = "a"
    private val other = "b"
    private fun input(id: String, author: String, assumedBy: String? = null) = GameEvent(id, "p", author,
        EnginePart(0, listOf(EngineStep(Input.PassPriority(PlayerId(author)), emptyList(), assumedBy?.let(::PlayerId)))))
    private val waiting = GameDevice.Status.Waiting(GameDevice.Wait.TheirTurn)
    private val playing = GameDevice.Status.Playing(input("n", other))
    private val line = mutableListOf(input("0", me))
    private var clock = 10_000L
    private val cue = YourMoveCue()

    /** The board after [events], [status], [wait] ms after the last one; whether the cue came. */
    private fun board(status: GameDevice.Status, vararg events: GameEvent, wait: Long = 5_000, held: Boolean = false): Boolean {
        clock += wait
        line += events
        return cue.see(status, line.toList(), line.last().id, held, me, clock)
    }

    private fun yourMove() = assertFalse(board(GameDevice.Status.YourMove), "the first board: opening a game brings no cue")

    // TRN-075: their End turn reaches you: one cue, and no more while it stays your move
    @Test fun theirActionPassingTheMoveCues() {
        board(waiting)
        assertTrue(board(GameDevice.Status.YourMove, input("1", other)))
        assertFalse(board(GameDevice.Status.YourMove), "once per passing")
        assertFalse(board(GameDevice.Status.YourMove, input("2", me)), "your own step")
    }

    // TRN-075: opening the game at your move brings none
    @Test fun openingAGameDoesNot() { yourMove() }

    // TRN-075: your own chain of steps, your assumptions of their passes on the way, brings none
    @Test fun yourOwnChainDoesNot() {
        yourMove()
        assertFalse(board(GameDevice.Status.Waiting(GameDevice.Wait.Sending), input("1", me)))
        assertFalse(board(GameDevice.Status.YourMove, input("2", other, assumedBy = other).copy(author = me), input("3", me)))
    }

    // TRN-075: their answer just after your own action: you have just acted; a while later it counts
    @Test fun notWhenYouHaveJustActed() {
        yourMove()
        assertFalse(board(waiting, input("1", me)))
        assertFalse(board(GameDevice.Status.YourMove, input("2", other), wait = 100), "just acted")
        assertFalse(board(waiting, input("3", me)))
        assertTrue(board(GameDevice.Status.YourMove, input("4", other), wait = YourMoveCue.JUST_ACTED_MILLIS), "they took their time")
    }

    // TRN-075, HIS-095: in playback only when you stop at a decision: never while it runs, never at a stop your pause made
    @Test fun inPlaybackOnlyAtADecision() {
        board(GameDevice.Status.CatchUp)
        assertFalse(board(playing, input("1", other)))
        val assumed = input("2", other, assumedBy = other)
        assertFalse(board(GameDevice.Status.Stopped(assumed, Input.PassPriority(PlayerId(me)), paused = true)), "your own pause")
        assertFalse(board(playing))
        assertTrue(board(GameDevice.Status.Stopped(assumed, Input.PassPriority(PlayerId(me)))), "a decision of yours")
    }

    // TRN-075: while a play or a combat holds your view it waits, and comes when that is done
    @Test fun itWaitsForWhatHoldsTheView() {
        board(waiting)
        assertFalse(board(GameDevice.Status.YourMove, input("1", other), held = true))
        assertTrue(board(GameDevice.Status.YourMove))
    }

    // TRN-075: what was taken back no longer counts
    @Test fun aTakeBackForgetsTheirInput() {
        board(waiting)
        board(playing, input("1", other))
        line.clear(); line += input("0", me)
        assertFalse(board(GameDevice.Status.YourMove))
        assertEquals(1, line.size)
    }
}
