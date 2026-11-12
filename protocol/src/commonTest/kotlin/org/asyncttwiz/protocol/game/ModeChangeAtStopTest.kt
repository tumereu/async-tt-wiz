package org.asyncttwiz.protocol.game

import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Owner problem report WDKHSB (2026-11-07): catch-up playback stopped at the other player's spell where the player could
 * not respond; toggling playback's Control on and off made it go on by itself. The data showed his response mode was
 * Normal (which stops at the other player's spells and abilities, STK-036), while the toggle's off set Quick. Owner
 * decisions: a new response mode applies only from the next point on (the stop the player stands at stays until they
 * answer it, live and in playback alike), and playback's mode cell is live play's (TRN-059).
 *
 * The game mirrors the report's shapes with invented cards: Alice holds only lands (nothing she could do); Bob casts a
 * free artifact creature and then activates its "Sacrifice this creature: Draw a card." ability.
 */
class ModeChangeAtStopTest {
    private val drone = arcanum.engine.cards.Cards.invent("Satchel Drone", "{0}", "Artifact Creature — Drone",
        "Sacrifice this creature: Draw a card.", "1/1")

    private fun sim() = DeviceSim(EngineGames(3, List(40) { "Forest" } to (List(20) { "Island" } + List(20) { drone })))

    private fun DeviceSim.atMain(p: PlayerId) = server.state().engine.let {
        it.turn.active == p && it.turn.step == TurnStep.PrecombatMain && it.stack.isEmpty() && it.priority.holder == p
    }

    private fun card(model: GameModel, zone: List<ObjectId>, name: String): ObjectId =
        zone.first { model.engine.obj(it)?.definition?.faces?.first()?.name == name }

    /** Bob casts a Satchel Drone from his hand (answering his own questions with the first answer listed). */
    private fun DeviceSim.bobCastsDrone() {
        val bobs = device(bob)
        assertIs<GameDevice.Acted.Sent>(bobs.act(Input.Cast(bob, card(bobs.model, bobs.model.engine.player(bob).hand, drone))) { m ->
            val d = m.engine.pending?.decision
            if (d == null || d is Decision.Random || d.player != bob) null
            else Engine.options(m.engine, bob, org.asyncttwiz.protocol.game.EnginePreferences.of(m.engine)).filterIsInstance<Input.Answer>().first { it.decision == d.id }
        })
    }

    /** Bob activates his Satchel Drone's ability. */
    private fun DeviceSim.bobActivatesDrone() {
        val bobs = device(bob)
        val source = card(bobs.model, bobs.model.engine.battlefield, drone)
        val activate = Engine.options(bobs.model.engine, bob, org.asyncttwiz.protocol.game.EnginePreferences.of(bobs.model.engine)).filterIsInstance<Input.Activate>().first { it.source == source }
        assertIs<GameDevice.Acted.Sent>(bobs.act(activate))
    }

    /** Both keep live (Bob starts); each device then plays on to Bob's main phase. Alice's mode is [mode]; Bob's
     * "Assume resolves" Normal, so at his points his phone waits for her where her Normal stops. */
    private fun liveAtBobsMain(mode: ResponseMode): DeviceSim {
        val sim = sim()
        sim.start(sim.bob, alwaysAssume = false)
        sim.settle()
        for (p in listOf(sim.bob, sim.alice)) { assertIs<GameDevice.Acted.Sent>(sim.device(p).act(sim.keep(p))); sim.settle() }
        sim.device(sim.alice).settings(PlaySettings(mode = mode))
        sim.settle()
        assertTrue(sim.atMain(sim.bob), "Bob at his main phase")
        assertEquals(mode, sim.device(sim.bob).opponentMode)
        return sim
    }

    // STK-036, HIS-095, SYS-063 (report WDKHSB): at the other player's spell and at their activated ability on top of the
    // Stack, a player holding only lands (no card they could cast, no ability that does anything) is stopped by Normal
    // (their pause) and not by Quick (judged on their own hand: nothing they could respond with)
    @Test
    fun normalStopsAtTheOtherPlayersSpellAndAbilityWhereQuickPasses() {
        val sim = liveAtBobsMain(ResponseMode.Normal)
        val alice = sim.alice
        fun judged() = sim.server.state().engine.let { s ->
            assertTrue(s.stack.isNotEmpty() && s.priority.holder == alice, "Alice holds priority over Bob's item")
            NextStop.stopsAt(s, Input.PassPriority(alice), alice, ResponseMode.Normal) to
                NextStop.stopsAt(s, Input.PassPriority(alice), alice, ResponseMode.AutoSkip)
        }
        sim.bobCastsDrone()
        sim.settle()
        assertEquals(true to false, judged(), "Bob's spell: Normal stops, Quick passes")
        assertEquals(GameDevice.Status.YourMove, sim.device(alice).status)
        assertIs<GameDevice.Acted.Sent>(sim.device(alice).act(Input.PassPriority(alice)))
        sim.settle()
        assertTrue(sim.atMain(sim.bob), "the Drone resolved")
        sim.bobActivatesDrone()
        sim.settle()
        assertEquals(true to false, judged(), "Bob's ability: Normal stops, Quick passes")
    }

    // STK-036, TRN-059 (owner 2026-11-07, report WDKHSB): in live play Alice, in Normal, stands at her stop at Bob's spell;
    // she switches to Quick, which would pass there: the stop stays until she answers it (her phone passes nothing by
    // itself, and Bob's assumption of her pass, made now that her Quick would pass, stops her table as well); the next
    // point, Bob's ability, follows Quick: her phone passes by itself
    @Test
    fun aModeSwitchLiveLeavesTheCurrentStopForTheNextPoint() {
        val sim = liveAtBobsMain(ResponseMode.Normal)
        val alice = sim.alice
        val alices = sim.device(alice)
        sim.bobCastsDrone()
        sim.settle()
        assertEquals(GameDevice.Status.YourMove, alices.status, "Normal stops at Bob's spell")
        val before = sim.server.line().size
        alices.settings(PlaySettings(mode = ResponseMode.AutoSkip))
        sim.settle()
        assertEquals(ResponseMode.AutoSkip, sim.device(sim.bob).opponentMode)
        assertTrue(alices.model.engine.stack.isNotEmpty(), "the Drone still on her Stack")
        assertTrue(sim.server.line().drop(before).none { it.author == alice.value && it.engine != null }, "her phone passed nothing by itself")
        when (val status = alices.status) {
            GameDevice.Status.YourMove -> assertIs<GameDevice.Acted.Sent>(alices.act(Input.PassPriority(alice)))
            is GameDevice.Status.Stopped -> assertEquals(GameDevice.Acted.Confirmed, alices.act(status.assumed))
            else -> error("Alice's stop gone: $status")
        }
        sim.settle()
        assertTrue(sim.atMain(sim.bob), "the Drone resolved on her answer")
        val hand = sim.server.state().engine.player(sim.bob).hand.size
        sim.bobActivatesDrone()
        sim.settle()
        assertTrue(sim.atMain(sim.bob), "Quick passed for her at Bob's ability: it resolved")
        assertEquals(hand + 1, sim.server.state().engine.player(sim.bob).hand.size, "Bob drew a card")
        assertTrue(sim.leaks.isEmpty())
    }

    // HIS-095, TRN-059 (owner 2026-11-07, report WDKHSB): Alice, in Normal, catches up on Bob's turn played while she was
    // away; her playback stops at the pass assumed for her at his spell although she can't respond (Normal stops at his
    // spells); switching to Quick there leaves the stop until she answers it (it went on by itself before), and the next
    // point, his ability, follows Quick: playback goes on past it
    @Test
    fun aModeSwitchAtAPlaybackStopLeavesItForTheNextPoint() {
        val sim = sim()
        val alice = sim.alice; val bob = sim.bob
        sim.start(bob)
        sim.keepWhileAway(bob)
        val alices = sim.device(alice)
        alices.settings(PlaySettings(mode = ResponseMode.Normal))
        sim.flush(alice)
        sim.bobCastsDrone()
        sim.settle(only = listOf(bob))
        sim.bobActivatesDrone()
        sim.settle(only = listOf(bob))
        assertTrue(sim.atMain(bob), "Bob's Drone and its ability resolved on Alice's assumed passes")
        sim.deliver(alice)
        alices.play()
        alices.playToStop()
        val stop = assertIs<GameDevice.Status.Stopped>(alices.status, "Normal stops at Bob's spell")
        assertTrue(!stop.paused)
        assertEquals(Input.PassPriority(alice), stop.assumed)
        alices.settings(PlaySettings(mode = ResponseMode.AutoSkip))
        assertNull(alices.step(), "playback stays at the stop")
        assertEquals(stop, alices.status)
        assertEquals(GameDevice.Acted.Confirmed, alices.act(stop.assumed))
        alices.playToStop()
        assertTrue(!alices.session.behind, "Quick went on past Bob's ability: ${alices.status}")
        assertTrue(sim.leaks.isEmpty())
    }
}
