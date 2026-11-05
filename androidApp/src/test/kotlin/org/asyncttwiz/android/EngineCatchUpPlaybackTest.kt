package org.asyncttwiz.android

import arcanum.engine.Engine
import arcanum.engine.change.Change
import arcanum.engine.input.Attack
import arcanum.engine.input.Input
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.GameEvent
import org.asyncttwiz.protocol.game.ResponseMode
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Owner playtest decisions 2026-11-04 evening (async-play §29.1, §29.3, §29.6) on two app engine games and the simulated
 * server ([EngineAppSim]): coming back, playback waits paused for Play with no dialog; the opening (mulligans) is never
 * played back; an empty combat goes by at once; an attack plays back in the combat area.
 */
class EngineCatchUpPlaybackTest {
    private val f = EngineGameFixture()
    private val a = PlayerId(f.a)

    /** Alice's device on its own while Bob is away: what the server sent played back, her device's own steps sent. */
    private fun alone(sim: EngineAppSim, alice: EngineGame) {
        sim.deliver(f.a)
        alice.playToStop()
        (alice.device.status as? GameDevice.Status.Stopped)?.let { alice.act(it.assumed) }
        alice.tick(false, sim.now)
        sim.flush(f.a)
        sim.now += 1_000
    }

    /** Bob keeps and goes away; Alice takes a mulligan, keeps with a card to the bottom and plays on to her first main
     * phase, where she plays a Mountain. */
    private fun aliceMulligansAndPlaysOn(sim: EngineAppSim): EngineGame {
        val alice = sim.load(f.a)
        val bob = sim.load(f.b)
        assertIs<GameDevice.Acted.Sent>(bob.keep(emptyList()))
        sim.flush(f.b)
        sim.deliver(f.a)
        alice.playToStop()
        assertIs<GameDevice.Acted.Sent>(alice.mulligan())
        sim.flush(f.a)
        val bottom = (alice.device.model.engine.player(a).hand.first() as ObjectId.Card).card.value
        assertIs<GameDevice.Acted.Sent>(alice.keep(listOf(bottom)))
        sim.flush(f.a)
        repeat(5) { alone(sim, alice) }
        assertEquals(GameDevice.Status.YourMove, alice.device.status)
        val land = assertNotNull(Engine.options(alice.device.model.engine, a).filterIsInstance<Input.PlayLand>().firstOrNull())
        assertIs<GameDevice.Acted.Sent>(alice.send(listOf(land)))
        sim.flush(f.a)
        return alice
    }

    // HIS-033, HIS-094 (§29.1): coming back during the other player's opening, there is no playback of it: Alice's
    // mulligan and keep with its bottom card show at once (one board); past the opening the catch-up starts paused, no dialog,
    // nothing played until Play
    @Test fun theOpeningShowsAtOnceThenTheCatchUpWaitsPausedForPlay() {
        val sim = EngineAppSim(f)
        aliceMulligansAndPlaysOn(sim)
        val bob = sim.load(f.b)
        assertEquals(0, bob.shown!!.engine.turn.number, "Bob's local head is his keep")
        assertIs<GameDevice.Status.Playing>(bob.device.status, "no paused catch-up in the opening")
        assertNotNull(bob.step())
        val opening = bob.shown!!.transition!!.forward
        assertTrue(opening.size >= 2, "Alice's mulligan and keep in one board: ${opening.size}")
        assertTrue(opening.any { e -> e.engine?.steps.orEmpty().any { (it.input as? Input.Answer)?.answer == arcanum.engine.decision.Answer.Option("Mulligan") ||
            (it.input as? Input.SetupAnswer)?.answer == arcanum.engine.decision.Answer.Option("Mulligan") } }, "her mulligan among them")
        assertEquals(1, bob.shown!!.engine.turn.number, "the first turn reached at once")
        // Past the opening: paused until Play
        assertEquals(GameDevice.Status.CatchUp, bob.shown!!.status)
        val head = bob.device.session.localHead
        repeat(3) { assertNull(bob.step()) }
        assertEquals(head, bob.device.session.localHead, "nothing played while paused")
        assertTrue(bob.device.session.behind)
        bob.startPlayback()
        assertTrue(bob.shown!!.status.let { it is GameDevice.Status.Playing || it is GameDevice.Status.Stopped })
        var guard = 0
        while (bob.device.status is GameDevice.Status.Playing && guard++ < 100) bob.step()
        assertTrue(bob.device.session.localHead != head, "Play played Alice's turn back")
        assertTrue(sim.leaks.isEmpty())
    }

    // HIS-104 (§29.6): Alice's combat with no attackers changes nothing Bob can see: its events go by at once in his
    // playback; under Full control they keep the playback delay
    @Test fun anEmptyCombatIsSkipped() {
        val sim = EngineAppSim(f)
        val alice = aliceMulligansAndPlaysOn(sim)
        alice.endTurn()
        repeat(3) { alone(sim, alice) }
        val bob = sim.load(f.b)
        bob.step()
        assertEquals(GameDevice.Status.CatchUp, bob.device.status)
        bob.startPlayback()
        val shown = mutableListOf<GameEvent>()
        var guard = 0
        while (guard++ < 200) {
            when (val status = bob.device.status) {
                is GameDevice.Status.Playing -> shown += assertNotNull(bob.step())
                is GameDevice.Status.Stopped -> bob.act(status.assumed)
                else -> break
            }
        }
        val combat = shown.filter { e -> e.engine?.changes.orEmpty().any { it is Change.Combat } ||
            e.engine?.steps.orEmpty().any { it.input is Input.DeclareAttackers } }
        // Alice has no creatures: her combat begins and ends with no attackers
        assertTrue(combat.any { e -> e.engine?.changes.orEmpty().any { it is Change.Combat && it.after?.attackers?.isEmpty() == true } },
            "Alice's empty combat shown: ${shown.map { e -> e.engine?.changes?.filterIsInstance<Change.Combat>() }}")
        for (event in combat) {
            assertTrue(EnginePlaybackPace.quiet(event), "an empty combat shows nothing: ${event.engine?.changes}")
            assertEquals(0L, playbackWait(event, playbackDelay(1f), ResponseMode.AutoSkip))
            assertEquals(playbackDelay(1f), playbackWait(event, playbackDelay(1f), ResponseMode.FullControl))
        }
    }

    // HIS-021, CMB-007 (§29.6): Alice attacks while Bob is away; his catch-up shows the attack in the combat area (the
    // panel in the middle of the table, as in live combat) and keeps the playback delay after it
    @Test fun anAttackPlaysBackInTheCombatArea() {
        val scenario = EngineCombatScenario()
        val sf = scenario.f
        scenario.until(scenario::bothHaveCubs)
        val alice = scenario.alice
        val me = scenario.me
        val attacker = scenario.creatures(alice.device.model.engine, me, ready = true).first()
        // Bob is away from here on
        assertIs<GameDevice.Acted.Sent>(alice.send(listOf(Input.DeclareAttackers(me, listOf(Attack(attacker, TargetRef.Player(scenario.other)))))))
        alice.tick(false, scenario.sim.now)
        if ((alice.device.status as? GameDevice.Status.Waiting)?.offer == GameDevice.Offer.AssumeBlockers) alice.assume()
        scenario.sim.flush(sf.a)
        val bob = scenario.sim.load(sf.b)
        assertEquals(GameDevice.Status.CatchUp, bob.device.status)
        bob.startPlayback()
        var attack: GameEvent? = null
        var guard = 0
        while (attack == null && bob.device.status is GameDevice.Status.Playing && guard++ < 100)
            bob.step()?.takeIf { e -> e.engine?.steps.orEmpty().any { (it.input as? Input.DeclareAttackers)?.attacks?.isNotEmpty() == true } }
                ?.let { attack = it }
        val shownAttack = assertNotNull(attack, "the attack played back")
        assertTrue(!EnginePlaybackPace.quiet(shownAttack))
        assertEquals(playbackDelay(1f), playbackWait(shownAttack, playbackDelay(1f), ResponseMode.AutoSkip))
        val play = bob.play()
        val id = (attacker as ObjectId.Card).card.value
        val pair = assertNotNull(play.board.combat).pairs.single()
        assertEquals(id, pair.attackers.single().sourceId)
        val scene = TablePresentation.scene(sf.game, play, sf.b, 412f, 892f, false, reviewing = true)
        val copy = assertNotNull(scene.items.firstOrNull { it.viewport == "combat" && it.sourceId == id }, "the attacker in the combat area")
        assertTrue(copy.visible.isNotEmpty(), "the attacker's copy shows")
        assertNotNull(scene.combatPanel, "the combat area's panel floats over the middle of the table")
    }
}
