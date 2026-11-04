package org.asyncttwiz.protocol.game

import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Attack
import arcanum.engine.input.Input
import arcanum.engine.setup.GameResult
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * TRN-058, LOB-022, LOB-024 (I-D57, async-play.md §24): whose move the game is, on each device ([GameDevice.move]: the move
 * pill and bar colours), is the player the game really waits for, the same as the server's per-player record the game
 * list and notifications read ([DeviceSim.serverMove], `engine_seat.waited`): the acting player while they play on assumptions
 * about the other, whatever decision stop the other player's playback is at.
 */
class MoveTest {
    private fun hand(model: GameModel, player: PlayerId, name: String): ObjectId =
        model.engine.player(player).hand.first { model.engine.obj(it)?.definition?.faces?.first()?.name == name }

    /** Bob's deck of Islands and [spell], Alice's of Forests. */
    private fun spells(spell: String) = DeviceSim(EngineGames(3, List(40) { "Forest" } to (List(20) { "Island" } + List(20) { spell })))

    /** Both devices (everything delivered) show [expected]'s move for each viewer, as the server's record does. */
    private fun DeviceSim.assertMoves(expected: Map<PlayerId, PlayerId?>, what: String) {
        for (p in listOf(alice, bob)) {
            assertEquals(expected[p], device(p).move, "$what: ${p.value}'s device (${device(p).status})")
            assertEquals(expected[p], serverMove(p), "$what: ${p.value}'s game list")
        }
    }

    private fun DeviceSim.bobToMain() {
        start(bob)
        // TRN-027: both openings are open at once: each player's own mulligan is their move
        assertMoves(mapOf(alice to alice, bob to bob), "the opening")
        assertIs<GameDevice.Acted.Sent>(device(bob).act(keep(bob)))
        settle()
    }

    // TRN-058 (I-D57): Bob plays on an assumed keep of Alice's (she is away) up to his main phase: the game waits for Bob
    // only, so it is Bob's move for both; Alice's catch-up playback stopping at the assumed keep (her decision stop) is
    // still Bob's move, and stays his once she confirms and reaches the head
    @Test
    fun anAssumedLineIsTheMoveOfThePlayerWhoPlaysOn() {
        val sim = spells("Storm Spark")
        sim.bobToMain()
        val alice = sim.alice; val bob = sim.bob
        assertEquals(GameDevice.Status.YourMove, sim.device(bob).status)
        sim.assertMoves(mapOf(alice to bob, bob to bob), "Bob plays on")
        val device = sim.load(alice)
        // HIS-033 (§29.1): coming back in the opening there is no paused catch-up: it shows at once up to the assumed keep
        assertIs<GameDevice.Status.Stopped>(device.status, "the assumed keep at once, no paused catch-up")
        assertEquals(bob, device.move)
        device.playToStop()
        val stop = assertIs<GameDevice.Status.Stopped>(device.status)
        assertEquals(bob, device.move, "a decision stop behind the head while Bob plays on is Bob's move")
        assertEquals(GameDevice.Acted.Confirmed, device.act(stop.assumed))
        device.playToStop()
        sim.settle()
        sim.assertMoves(mapOf(alice to bob, bob to bob), "Alice caught up")
    }

    // TRN-058, LOB-073 (I-D57): the information rule leaves Bob really waiting for Alice's pass: her move for both, also
    // while her playback is still behind the head
    @Test
    fun aRealWaitIsTheMoveOfThePlayerWaitedFor() {
        val sim = spells("Cerebral Scan")
        sim.bobToMain()
        val alice = sim.alice; val bob = sim.bob
        val d = sim.device(bob)
        assertIs<GameDevice.Acted.Sent>(d.act(Input.PlayLand(bob, hand(d.model, bob, "Island"))))
        assertIs<GameDevice.Acted.Sent>(d.act(Input.Cast(bob, hand(d.model, bob, "Cerebral Scan"))) { m ->
            val open = m.engine.pending?.decision
            if (open == null || open is Decision.Random || open.player != bob) null
            else Engine.options(m.engine, bob).filterIsInstance<Input.Answer>().let { answers ->
                if (open is Decision.Targets) answers.first { (it.answer as Answer.Targets).chosen == listOf(TargetRef.Player(alice)) } else answers.first()
            }
        })
        sim.settle()
        assertEquals(GameDevice.Status.Waiting(GameDevice.Wait.HiddenCards), sim.device(bob).status)
        sim.assertMoves(mapOf(alice to alice, bob to alice), "Bob waits for Alice")
        val device = sim.load(alice)
        device.play()
        device.playToStop()
        assertIs<GameDevice.Status.Stopped>(device.status)
        assertEquals(alice, device.move, "the game waits for her at the head")
    }

    // TRN-058, HIS-099: an open undo request is the other player's move for both; the answer ends it
    @Test
    fun anUndoRequestIsTheOtherPlayersMove() {
        val sim = spells("Glance Ahead")
        sim.bobToMain()
        val alice = sim.alice; val bob = sim.bob
        val bobs = sim.device(bob)
        val beforeCast = sim.server.head
        bobs.act(Input.PlayLand(bob, hand(bobs.model, bob, "Island")))
        bobs.act(Input.Cast(bob, hand(bobs.model, bob, "Glance Ahead"))) { m ->
            m.engine.pending?.decision?.takeIf { it.player == bob && it !is Decision.Random }?.let { open ->
                Engine.options(m.engine, bob).filterIsInstance<Input.Answer>().first { it.decision == open.id } }
        }
        sim.settle()
        while (bobs.status == GameDevice.Status.YourMove && bobs.model.engine.pending != null) {
            val open = bobs.model.engine.pending!!.decision
            bobs.act(Engine.options(bobs.model.engine, bob).filterIsInstance<Input.Answer>().first { it.decision == open.id })
            sim.settle()
        }
        // Before the request (all delivered to Bob), as after a declined one
        val before = sim.serverMove(bob)
        assertEquals(before, bobs.move)
        val request = assertIs<GameDevice.Undo.Request>(bobs.undo(beforeCast))
        bobs.requestUndo(request.target, "misclick")
        sim.settle()
        sim.load(alice)
        sim.assertMoves(mapOf(alice to alice, bob to alice), "Alice answers Bob's request")
        sim.device(alice).answerRequest(accept = false)
        sim.settle()
        sim.assertMoves(mapOf(alice to before, bob to before), "declined: as before the request")
    }

    // TRN-058, TRN-047, TRN-049: a waiting result suggestion locks the game for both: answering it is the other player's move
    @Test
    fun aResultSuggestionIsTheOtherPlayersMove() {
        val sim = spells("Storm Spark")
        sim.bobToMain()
        val alice = sim.alice; val bob = sim.bob
        sim.catchUp(alice)
        assertIs<GameDevice.Acted.Sent>(sim.device(bob).suggest(GameResult.Draw))
        sim.settle()
        sim.assertMoves(mapOf(alice to alice, bob to alice), "Alice answers Bob's suggestion")
        assertIs<GameDevice.Acted.Sent>(sim.device(alice).answerSuggestion(accept = false))
        sim.settle()
        sim.assertMoves(mapOf(alice to bob, bob to bob), "declined: Bob's main phase")
    }

    // TRN-058, HIS-097 (I-D56 §8): Alice answers the assumed keep differently (a mulligan): the main line branches and
    // whose move it is follows the new line on both devices and the server alike
    @Test
    fun aBranchMovesTheMoveWithTheNewLine() {
        val sim = spells("Storm Spark")
        sim.bobToMain()
        val alice = sim.alice; val bob = sim.bob
        val device = sim.load(alice)
        device.play()
        device.playToStop()
        assertIs<GameDevice.Status.Stopped>(device.status)
        val mulligan = Engine.options(device.model.engine, alice).first {
            (it as? Input.Answer)?.answer == Answer.Option("Mulligan") || (it as? Input.SetupAnswer)?.answer == Answer.Option("Mulligan")
        }
        assertIs<GameDevice.Acted.Sent>(device.act(mulligan))
        sim.settle()
        assertEquals(1, sim.branches)
        val expected = sim.serverMove(alice)
        assertEquals(expected, sim.serverMove(bob))
        sim.assertMoves(mapOf(alice to expected, bob to expected), "after the branch")
    }

    // TRN-050, HIS-097 (I-D56 §8): Bob suggests a draw on the line where Alice's keep was assumed; Alice answers the assumed
    // keep differently (a mulligan): the main line branches, and the suggestion left behind on the discarded line locks
    // nothing for either player
    @Test
    fun aSuggestionLeftOnADiscardedLineLocksNothing() {
        val sim = spells("Storm Spark")
        sim.bobToMain()
        val alice = sim.alice; val bob = sim.bob
        assertIs<GameDevice.Acted.Sent>(sim.device(bob).suggest(GameResult.Draw))
        sim.settle(only = listOf(bob))
        assertTrue(sim.device(bob).locked, "the suggestion waits on Bob's line")
        val device = sim.load(alice)
        device.play()
        device.playToStop()
        assertIs<GameDevice.Status.Stopped>(device.status)
        val mulligan = Engine.options(device.model.engine, alice).first {
            (it as? Input.Answer)?.answer == Answer.Option("Mulligan") || (it as? Input.SetupAnswer)?.answer == Answer.Option("Mulligan")
        }
        assertIs<GameDevice.Acted.Sent>(device.act(mulligan))
        sim.settle()
        assertEquals(1, sim.branches)
        for (p in listOf(alice, bob)) {
            assertNull(sim.device(p).model.table.suggestion, "${p.value}: gone with its line")
            assertFalse(sim.device(p).locked, "${p.value}: nothing locked")
        }
        assertNull(sim.server.state().table.suggestion)
    }

    /** Forests and Elvish Seers (1/1) for both, at 1 life: Alice's turn-3 attack is lethal unless Bob blocks. */
    private fun seers() = DeviceSim(EngineGames(5, List(40) { if (it % 2 == 0) "Forest" else "Elvish Seer" }.let { it to it }, life = 1))

    private fun DeviceSim.atMain(p: PlayerId) = device(p).model.engine.let {
        it.turn.active == p && it.turn.step == TurnStep.PrecombatMain && it.stack.isEmpty() && it.priority.holder == p
    }

    /** Both play a Forest and an Elvish Seer in their first turns; in Alice's third, with Bob away (nothing delivered to
     * him), she attacks with her Seer, assumes Bob's "No blocks" and passes on to the lethal damage. */
    private fun DeviceSim.seersToResult() {
        val alice = alice; val bob = bob
        start(alice, ResponseMode.Normal)
        for (p in listOf(alice, bob)) { device(p).act(keep(p)); settle() }
        for (p in listOf(alice, bob)) {
            catchUp(p)
            assertTrue(atMain(p), "${p.value} at their main phase: ${device(p).status}")
            val d = device(p)
            assertIs<GameDevice.Acted.Sent>(d.act(Input.PlayLand(p, hand(d.model, p, "Forest"))))
            assertIs<GameDevice.Acted.Sent>(d.act(Input.Cast(p, hand(d.model, p, "Elvish Seer")), follow = { m ->
                m.engine.pending?.decision?.takeIf { it.player == p && it !is Decision.Random }?.let { open ->
                    Engine.options(m.engine, p).filterIsInstance<Input.Answer>().first { it.decision == open.id } }
            }))
            settle()
            d.endTurn(now)
            settle()
        }
        catchUp(alice)
        val m = device(alice).model.engine
        val seer = m.battlefield.first { m.obj(it)?.controller == alice && m.obj(it)?.definition?.faces?.first()?.name == "Elvish Seer" }
        device(alice).act(Input.PassPriority(alice))
        settle(only = listOf(alice))
        device(alice).act(Input.DeclareAttackers(alice, listOf(Attack(seer, TargetRef.Player(bob)))))
        settle(only = listOf(alice))
        assertIs<GameDevice.Acted.Sent>(device(alice).assume())
        settle(only = listOf(alice))
        while (device(alice).status == GameDevice.Status.YourMove && device(alice).model.engine.priority.holder == alice) {
            device(alice).act(Input.PassPriority(alice))
            settle(only = listOf(alice))
        }
    }

    // TRN-058, TRN-065: the result of Alice's line, reached while Bob is away, waits for Bob (his move for both) until he
    // has been shown it; then the game waits for nobody
    @Test
    fun aResultNotSeenYetIsTheMoveOfThePlayerWhoHasntSeenIt() {
        val sim = seers()
        sim.seersToResult()
        val alice = sim.alice; val bob = sim.bob
        assertEquals(GameResult.Won(alice), assertIs<GameDevice.Status.Over>(sim.device(alice).status).result)
        val bobs = sim.load(bob)
        assertTrue(bobs.session.behind)
        sim.assertMoves(mapOf(alice to bob, bob to bob), "Bob hasn't been shown the result")
        sim.catchUp(bob)
        sim.assertMoves(mapOf(alice to null, bob to null), "both have seen the result")
    }

    private fun DeviceSim.catchUp(p: PlayerId) {
        val d = device(p)
        d.play()
        while (d.session.behind) {
            d.playToStop()
            (d.status as? GameDevice.Status.Stopped)?.let { d.act(it.assumed) }
        }
        settle()
    }
}
