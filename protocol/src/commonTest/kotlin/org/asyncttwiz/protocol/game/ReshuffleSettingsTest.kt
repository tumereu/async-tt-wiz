package org.asyncttwiz.protocol.game

import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import org.asyncttwiz.protocol.AssumeReveals
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * HIS-042 (owner 2026-11-26, playtest F): the game settings "Reshuffle libraries on undo" and "Reshuffle libraries on branch"
 * (both on by default): off, that kind of new line keeps the libraries' unknown order instead of reshuffling it. The server
 * and the devices make the same events either way.
 */
class ReshuffleSettingsTest {
    private fun hand(model: GameModel, player: PlayerId, name: String): ObjectId =
        model.engine.player(player).hand.first { model.engine.obj(it)?.definition?.faces?.first()?.name == name }

    /** Every player's library, in order. */
    private fun libraries(model: GameModel): List<List<ObjectId>> = model.engine.let { e -> e.players.map { e.player(it.id).library.toList() } }

    // HIS-042: Bob plays an Island, undoes it (free) and plays it again: the event starting the undo's line reshuffles the
    // libraries with "Reshuffle libraries on undo" on (whatever "on branch" says) and keeps their order with it off; the server
    // makes the same event as the device ([AsyncServer.act] checks it)
    @Test
    fun anUndosLineFollowsTheUndoSwitch() {
        fun replayed(reveals: AssumeReveals): Pair<GameEvent, Boolean> {
            val server = AsyncServer(EngineGames(3, List(40) { "Forest" } to List(40) { "Island" }))
            server.reveals = reveals
            val bob = server.bob
            server.start(bob)
            server.keepAndAssumeKeep(bob)
            var passes = 0
            while (true) {
                val s = server.state().engine
                if (s.turn.active == bob && s.turn.step == TurnStep.PrecombatMain && s.priority.holder == bob) break
                check(passes++ < 60) { "Bob's main phase not reached" }
                val holder = s.priority.holder!!
                server.act(bob, Input.PassPriority(holder), assumed = holder != bob).getOrThrow()
            }
            val before = server.head
            val libraries = libraries(server.state())
            server.act(bob, Input.PlayLand(bob, hand(server.state(), bob, "Island"))).getOrThrow()
            server.rewind(bob, before)
            assertEquals(1, server.lines)
            assertFalse(server.lineBranch, "an undo's line")
            assertEquals(libraries, libraries(server.state()), "the undo itself moves nothing")
            server.act(bob, Input.PlayLand(bob, hand(server.state(), bob, "Island"))).getOrThrow()
            val event = server.history.store.node(server.head)!!.event
            assertEquals(1, event.startsLine)
            return event to (libraries(server.state()) != libraries)
        }
        val (kept, moved) = replayed(AssumeReveals(reshuffleOnUndo = false))
        assertTrue(kept.engine!!.steps.none { it.input == null }, "no reshuffle step")
        assertFalse(moved, "the libraries keep their order")
        val (shuffled, reordered) = replayed(AssumeReveals(reshuffleOnBranch = false))
        assertEquals(null, shuffled.engine!!.steps.first().input, "the line's reshuffle first")
        assertTrue(reordered)
    }

    // HIS-042, HIS-192: a line started without its reshuffle counts the line's random answers as with it, so a coin flip or a
    // shuffle later on the line comes out the same either way; only the libraries' order differs
    @Test
    fun aLineWithoutItsReshuffleKeepsTheLinesRandomAnswers() {
        val server = AsyncServer(EngineGames(3, List(40) { "Forest" } to List(40) { "Island" }))
        server.start()
        fun started(reshuffle: Boolean): EventBuilder =
            EventBuilder(server.state(), "e", server.head, server.alice.value).also { it.startLine(4, reshuffle) }
        val with = started(true)
        val without = started(false)
        assertTrue(with.engineSteps.isNotEmpty())
        assertTrue(without.engineSteps.isEmpty())
        val withLine = (with.build().table.single() as TableChange.LineMoved).after
        val withoutLine = (without.build().table.single() as TableChange.LineMoved).after
        assertEquals(withLine, withoutLine)
    }

    // HIS-042, HIS-097: Alice answers Bob's spell with her own instead of the assumed pass: a branch. With "Reshuffle libraries on
    // branch" off its new line keeps the libraries' order (whatever "on undo" says), on it reshuffles them; her device and the
    // server agree (her device would reload otherwise)
    @Test
    fun aBranchsLineFollowsTheBranchSwitch() {
        val glimmer = arcanum.engine.cards.Cards.invent("Idle Glimmer", "{0}", "Instant", "Draw a card.")
        fun branched(reveals: AssumeReveals): GameEvent {
            val sim = DeviceSim(EngineGames(3, (List(10) { "Forest" } + List(30) { glimmer }) to (List(20) { "Mountain" } + List(20) { "Storm Spark" })))
            sim.reveals = reveals
            val alice = sim.alice; val bob = sim.bob
            sim.start(bob)
            sim.keepWhileAway(bob)
            val bobs = sim.device(bob)
            assertIs<GameDevice.Acted.Sent>(bobs.act(Input.PlayLand(bob, hand(bobs.model, bob, "Mountain"))))
            assertIs<GameDevice.Acted.Sent>(bobs.act(Input.Cast(bob, hand(bobs.model, bob, "Storm Spark"))) { m ->
                val d = m.engine.pending?.decision
                if (d == null || d is Decision.Random || d.player != bob) null
                else Engine.options(m.engine, bob, EnginePreferences.of(m.engine)).filterIsInstance<Input.Answer>().let { answers ->
                    if (d is Decision.Targets) answers.first { (it.answer as Answer.Targets).chosen == listOf(TargetRef.Player(alice)) } else answers.first()
                }
            })
            sim.settle(only = listOf(bob))
            val device = sim.load(alice)
            device.play()
            device.playToStop()
            assertIs<GameDevice.Status.Stopped>(device.status)
            assertIs<GameDevice.Acted.Sent>(device.act(Input.Cast(alice, hand(device.model, alice, glimmer))) { m ->
                m.engine.pending?.decision?.takeIf { it.player == alice && it !is Decision.Random }?.let { open ->
                    Engine.options(m.engine, alice, EnginePreferences.of(m.engine)).filterIsInstance<Input.Answer>().first { it.decision == open.id } }
            })
            sim.flush(alice)
            assertEquals(1, sim.branches)
            sim.settle()
            assertFalse(device.needsReload, "her device made the server's event")
            assertTrue(device.model.sameAs(sim.server.stateAt(device.session.localHead)))
            return sim.server.line().single { it.startsLine == 1 }
        }
        // (her cast's event may go on to its resolution and draw: the reshuffle is the step without an input, first)
        assertTrue(branched(AssumeReveals(reshuffleOnBranch = false)).engine!!.steps.none { it.input == null }, "no reshuffle step")
        val shuffled = branched(AssumeReveals(reshuffleOnUndo = false)).engine!!.steps.first()
        assertEquals(null, shuffled.input, "the line's reshuffle first")
        assertTrue(shuffled.changes.isNotEmpty())
    }

    // HIS-042: the device's own free undo, then its next action there: the line it starts follows "Reshuffle libraries on undo"
    // on the device as on the server (the device's board stays the server's, no reload)
    @Test
    fun aDevicesOwnUndoStartsTheLineAsTheServerDoes() {
        fun undone(reveals: AssumeReveals): GameEvent {
            val sim = DeviceSim(EngineGames(3, List(40) { "Forest" } to List(40) { "Island" }))
            sim.reveals = reveals
            val bob = sim.bob
            sim.start(bob)
            sim.keepWhileAway(bob)
            val bobs = sim.device(bob)
            val before = sim.server.head
            assertIs<GameDevice.Acted.Sent>(bobs.act(Input.PlayLand(bob, hand(bobs.model, bob, "Island"))))
            sim.settle()
            assertEquals(GameDevice.Undo.Free(before), bobs.undo())
            sim.settle()
            assertIs<GameDevice.Acted.Sent>(bobs.act(Input.PlayLand(bob, hand(bobs.model, bob, "Island"))))
            sim.settle()
            assertFalse(bobs.needsReload, "his device made the server's event")
            assertTrue(bobs.model.sameAs(sim.server.stateAt(bobs.session.localHead)))
            val event = sim.server.line().single { it.parentId == before }
            assertEquals(1, event.startsLine)
            return event
        }
        assertTrue(undone(AssumeReveals(reshuffleOnUndo = false)).engine!!.steps.none { it.input == null }, "no reshuffle step")
        val shuffled = undone(AssumeReveals(reshuffleOnBranch = false)).engine!!.steps.first()
        assertEquals(null, shuffled.input, "the line's reshuffle first")
        assertTrue(shuffled.changes.isNotEmpty())
    }
}
