package org.asyncttwiz.protocol.game

import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A matching answer that crossed the other device's assumption of it on the wire (owner 2026-11-07, SYS-077, HIS-109):
 * the other player's event reaches the server first and is the player's own pending event but for who made it, so the
 * server drops the player's event as its duplicate (a confirmation, no new event, nothing refused) and takes what they
 * made on it on that event; the player's phone puts the other player's event in its place with the board, the later
 * pending events and an open choice untouched (no take-back, nothing made again: SYS-009).
 */
class ConfirmRaceTest {
    private val demand = arcanum.engine.cards.Cards.invent("Idle Demand", "{0}", "Sorcery", "Target player discards a card.")
    private val glimmer = arcanum.engine.cards.Cards.invent("Idle Glimmer", "{0}", "Instant", "Draw a card.")

    private fun hand(model: GameModel, p: PlayerId, name: String): ObjectId =
        model.engine.player(p).hand.first { model.engine.obj(it)?.definition?.faces?.first()?.name == name }

    /** Both kept live, Bob ([bobStarts]) or Alice at their main phase; each passing by hand at their pauses (Normal),
     * Assume: Never. */
    private fun atMain(bobStarts: Boolean, decks: Pair<List<String>, List<String>>): DeviceSim {
        val sim = DeviceSim(EngineGames(3, decks))
        val first = if (bobStarts) sim.bob else sim.alice
        sim.start(first, mode = ResponseMode.Normal, alwaysAssume = false)
        for (p in listOf(sim.alice, sim.bob)) sim.device(p).settings(PlaySettings(mode = ResponseMode.Normal).assuming(AssumeResolves.Never))
        sim.settle()
        for (p in listOf(first, sim.other(first))) { assertIs<GameDevice.Acted.Sent>(sim.device(p).act(sim.keep(p))); sim.settle() }
        sim.device(first).model.engine.let {
            assertTrue(it.turn.active == first && it.turn.step == TurnStep.PrecombatMain && it.priority.holder == first, "at the main phase")
        }
        return sim
    }

    /**
     * Bob casts Idle Demand at Alice in his main phase (her pause: her phone waits for her, his waits with Assume). Alice
     * passes by hand on it, which opens her discard, and with [answer] also discards; meanwhile Bob presses Assume (her
     * pass) and his assumption reaches the server first; her events are still on their way when it reaches her phone.
     */
    private fun crossed(answer: Boolean): Crossed {
        val sim = atMain(bobStarts = true, decks = List(40) { "Forest" } to (List(20) { "Island" } + List(20) { demand }))
        val alice = sim.alice; val bob = sim.bob
        val bobs = sim.device(bob); val alices = sim.device(alice)
        assertIs<GameDevice.Acted.Sent>(bobs.act(Input.Cast(bob, hand(bobs.model, bob, demand))) { m ->
            val d = m.engine.pending?.decision
            if (d == null || d is Decision.Random || d.player != bob) null
            else Engine.options(m.engine, bob).filterIsInstance<Input.Answer>().let { answers ->
                if (d is Decision.Targets) answers.first { (it.answer as Answer.Targets).chosen == listOf(TargetRef.Player(alice)) } else answers.first()
            }
        })
        sim.flush(bob)
        sim.deliver(alice)
        alices.playToStop()
        alices.advance(sim.now)
        assertEquals(GameDevice.Status.YourMove, alices.status, "Alice's pause at his spell")
        assertIs<GameDevice.Acted.Sent>(alices.act(Input.PassPriority(alice)))
        val discard = assertNotNull(alices.model.engine.pending?.decision, "her discard is open")
        assertEquals(alice, discard.player)
        if (answer) assertIs<GameDevice.Acted.Sent>(alices.act(Engine.options(alices.model.engine, alice).filterIsInstance<Input.Answer>().first()))
        val mine = alices.session.pending.toList()
        // Bob's phone waits for her with Assume; he presses it
        sim.deliver(bob)
        bobs.advance(sim.now)
        assertEquals(GameDevice.Offer.AssumeResolves, assertIs<GameDevice.Status.Waiting>(bobs.status).offer)
        assertIs<GameDevice.Acted.Sent>(bobs.assume())
        sim.flush(bob)
        val assumption = sim.server.line().last()
        assertEquals(Input.PassPriority(alice), assumption.assumed?.input, "Bob assumed her pass")
        return Crossed(sim, mine, assumption)
    }

    private class Crossed(val sim: DeviceSim, val mine: List<GameEvent>, val assumption: GameEvent)

    // SYS-077, HIS-109, SYS-009 (owner 2026-11-07): Bob's assumption of Alice's pass reaches her phone while her own pass,
    // which opened her discard, is still on its way: her board and the open discard stay exactly as they are (no take-back,
    // nothing made again); on the server her pass is the assumption's confirmation: no new event, nothing refused
    @Test
    fun aMatchingAnswerCrossingTheAssumptionKeepsTheBoardAndTheOpenChoice() {
        val (sim, mine, assumption) = crossed(answer = false).let { Triple(it.sim, it.mine, it.assumption) }
        val alice = sim.alice; val bob = sim.bob
        val alices = sim.device(alice)
        val pending = alices.model.engine.pending
        val state = alices.model.engine
        var shown = 0
        alices.session.beforeShow = { _, _ -> shown++ }
        sim.deliver(alice)
        assertSame(state, alices.model.engine, "the same board")
        assertSame(pending, alices.model.engine.pending, "the open discard untouched")
        assertEquals(0, shown, "nothing played back")
        assertEquals(assumption.id, alices.session.localHead, "Bob's event in place of hers")
        assertTrue(alices.session.pending.isEmpty())
        alices.advance(sim.now)
        assertSame(pending, alices.model.engine.pending, "still open after her phone's own steps")
        // Her pass reaches the server after his assumption: its confirmation, no new event
        val line = sim.server.line().size
        sim.flush(alice)
        assertEquals(line, sim.server.line().size, "no new event")
        assertEquals(1, sim.confirmations)
        assertEquals(assumption.id, sim.server.heads[alice], "her local head past the assumption: confirmed")
        sim.deliver(alice); sim.deliver(bob)
        assertTrue(alices.notes().none { it is GameDevice.Note.Refused || it is GameDevice.Note.Branched }, "no note")
        assertFalse(alices.needsReload)
        assertSame(pending, alices.model.engine.pending, "the discard still open")
        assertTrue(sim.device(bob).confirmed(assumption), "Bob's phone sees the assumption confirmed")
        assertTrue(mine.none { e -> sim.server.line().any { it.id == e.id } })
        // She discards on the board she had all along
        assertIs<GameDevice.Acted.Sent>(alices.act(Engine.options(alices.model.engine, alice).filterIsInstance<Input.Answer>().first()))
        sim.settle()
        assertEquals(0, sim.branches)
        assertEquals(sim.server.head, alices.session.head)
        assertEquals(1, sim.server.state().engine.player(alice).graveyard.size, "her discard taken")
        assertTrue(sim.leaks.isEmpty())
        assertTrue(sim.equivalence().isEmpty())
    }

    // SYS-077, SYS-009 (owner 2026-11-07): the same with her discard made on her pass before his assumption reached her: her
    // discard stays on her board, made on his event instead of her pass, and the server takes it there (not stale)
    @Test
    fun aMoveMadeOnTheMatchingAnswerIsTakenOnTheAssumption() {
        val crossed = crossed(answer = true)
        val sim = crossed.sim; val alice = sim.alice
        val alices = sim.device(alice)
        assertEquals(2, crossed.mine.size, "her pass and her discard on their way")
        val state = alices.model.engine
        val graveyard = state.player(alice).graveyard.toList()
        sim.deliver(alice)
        assertSame(state, alices.model.engine)
        assertEquals(graveyard, alices.model.engine.player(alice).graveyard, "her discard still made")
        val discard = alices.session.pending.single()
        assertEquals(crossed.mine[1].id, discard.id)
        assertEquals(crossed.assumption.id, discard.parentId, "made on Bob's event now")
        sim.flush(alice)
        assertEquals(discard.id, sim.server.head, "her discard taken on the assumption")
        assertEquals(crossed.assumption.id, sim.server.line().last().parentId)
        sim.deliver(alice)
        assertTrue(alices.session.pending.isEmpty())
        assertFalse(alices.needsReload)
        assertTrue(alices.notes().none { it is GameDevice.Note.Refused || it is GameDevice.Note.Branched }, "no note")
        sim.settle()
        assertEquals(0, sim.branches)
        assertEquals(sim.server.head, alices.session.head)
        assertTrue(sim.leaks.isEmpty())
        assertTrue(sim.equivalence().isEmpty())
    }

    // SYS-077, SYS-012 (owner 2026-11-07): the other way round: Alice presses Assume on Bob's pause at her spell while his
    // own pass by hand reaches the server first: her assumption is its duplicate, dropped quietly (nothing refused), and
    // her phone keeps its board with his pass in place of her assumption
    @Test
    fun anAssumptionCrossingTheSameRealAnswerIsItsDuplicate() {
        val sim = atMain(bobStarts = false, decks = (List(20) { "Forest" } + List(20) { glimmer }) to List(40) { "Island" })
        val alice = sim.alice; val bob = sim.bob
        val alices = sim.device(alice); val bobs = sim.device(bob)
        assertIs<GameDevice.Acted.Sent>(alices.act(Input.Cast(alice, hand(alices.model, alice, glimmer))))
        sim.flush(alice)
        sim.deliver(bob)
        bobs.playToStop()
        bobs.advance(sim.now)
        assertEquals(GameDevice.Status.YourMove, bobs.status, "Bob's pause at her spell")
        assertIs<GameDevice.Acted.Sent>(bobs.act(Input.PassPriority(bob)))
        sim.deliver(alice)
        alices.advance(sim.now)
        assertIs<GameDevice.Status.Waiting>(alices.status)
        assertIs<GameDevice.Acted.Sent>(alices.assume())
        val state = alices.model.engine
        val hand = state.player(alice).hand.toList()
        sim.flush(bob)
        val pass = sim.server.line().last()
        assertNull(pass.assumed, "his own pass first")
        sim.deliver(alice)
        assertSame(state, alices.model.engine)
        assertEquals(hand, alices.model.engine.player(alice).hand, "the Glimmer's draw stays")
        assertEquals(pass.id, alices.session.localHead)
        val line = sim.server.line().size
        sim.flush(alice)
        assertEquals(line, sim.server.line().size, "no new event")
        assertEquals(pass.id, sim.server.heads[alice])
        sim.deliver(alice)
        assertTrue(alices.notes().none { it is GameDevice.Note.Refused }, "nothing refused")
        sim.settle()
        assertEquals(0, sim.branches)
        assertEquals(sim.server.head, alices.session.head)
        assertTrue(sim.equivalence().isEmpty())
    }
}
