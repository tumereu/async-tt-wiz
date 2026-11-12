package org.asyncttwiz.protocol.game

import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Owner problem report E48RDE (HIS-109, SYS-012, SYS-011): a tap made on a board the device has moved past is the same
 * press as the device's own step right after that board when the device made that step by itself with the same input
 * (its pass for the player by their mode, its assumption of the other player's answer): [GameDevice.pressedByItself].
 * A tap with another input, at a later point, or after a step the player's own tap made, isn't.
 */
class SamePressTest {
    private val altar = arcanum.engine.cards.Cards.invent("Ember Altar", "{0}", "Artifact",
        "{T}, Sacrifice another creature: Each opponent loses 2 life and you gain 2 life.")
    private val martyr = arcanum.engine.cards.Cards.invent("Ember Martyr", "{0}", "Creature — Elemental",
        "When this creature dies, you gain 2 life.", "1/1")

    private fun hand(model: GameModel, p: PlayerId, name: String): ObjectId =
        model.engine.player(p).hand.first { model.engine.obj(it)?.definition?.faces?.first()?.name == name }

    /** Answers [player]'s own decisions of a flow with their first listed answer. */
    private fun first(player: PlayerId): (GameModel) -> Input? = { m ->
        val d = m.engine.pending?.decision
        if (d == null || d is Decision.Random || d.player != player) null
        else Engine.options(m.engine, player, org.asyncttwiz.protocol.game.EnginePreferences.of(m.engine)).filterIsInstance<Input.Answer>().first { it.decision == d.id }
    }

    /**
     * Alice (Quick, her setting Assume resolves: [assume]) starts and plays on alone while Bob is away in Normal (his pause
     * at her spells waits for him, with Assume): she casts an Ember Altar and an Ember Martyr, pressing Assume for each,
     * then activates the Altar sacrificing the Martyr: its death trigger on the Stack above her ability, her pass folded in.
     */
    private fun twoOfHersOnTheStack(assume: AssumeResolves = AssumeResolves.Normal): DeviceSim {
        val sim = DeviceSim(EngineGames(3, (List(10) { "Mountain" } + List(15) { altar } + List(15) { martyr }) to List(40) { "Island" }))
        sim.start(sim.alice, alwaysAssume = false)
        sim.keepWhileAway(sim.alice)
        val alice = sim.alice
        val a = sim.device(alice)
        sim.device(sim.bob).settings(PlaySettings(mode = ResponseMode.Normal))
        sim.flush(sim.bob)
        sim.deliver(alice)
        assertEquals(ResponseMode.Normal, a.opponentMode, "Alice's device knows Bob's mode")
        for (name in listOf(altar, martyr)) {
            assertIs<GameDevice.Acted.Sent>(a.act(Input.Cast(alice, hand(a.model, alice, name)), follow = first(alice)))
            a.advance(sim.now)
            assertEquals(GameDevice.Offer.AssumeResolves, assertIs<GameDevice.Status.Waiting>(a.status).offer, "waiting for Bob at $name")
            assertIs<GameDevice.Acted.Sent>(a.assume())
            a.advance(sim.now)
            assertEquals(GameDevice.Status.YourMove, a.status, "back in her main phase after $name")
        }
        a.settings(PlaySettings().assuming(assume))
        val activate = Engine.options(a.model.engine, alice, org.asyncttwiz.protocol.game.EnginePreferences.of(a.model.engine)).filterIsInstance<Input.Activate>().single()
        assertIs<GameDevice.Acted.Sent>(a.act(activate, follow = first(alice)))
        return sim
    }

    // HIS-109, SYS-012 (E48RDE): Alice presses Assume over her trigger (it resolves), then her phone passes for her by
    // itself over her ability (Quick): her tap of Resolve made on the board in between is that very press; any other input
    // there, her Assume made by her own tap, and anything at the later point (her ability's own wait) are not
    @Test
    fun theDevicesOwnPassIsTheSamePressAsATapOfResolveThere() {
        val sim = twoOfHersOnTheStack()
        val alice = sim.alice; val bob = sim.bob
        val a = sim.device(alice)
        a.advance(sim.now)
        assertEquals(2, a.model.engine.stack.size, "her trigger above her ability")
        assertEquals(GameDevice.Offer.AssumeResolves, assertIs<GameDevice.Status.Waiting>(a.status).offer)
        val activated = a.session.tip
        assertIs<GameDevice.Acted.Sent>(a.assume())
        val assumed = a.session.tip
        a.model.engine.let { assertTrue(it.stack.size == 1 && it.priority.holder == alice, "her priority over her ability") }
        // Her phone's own steps: its pass for her
        a.advance(sim.now)
        val passed = a.session.tip
        assertNotEquals(assumed, passed, "her phone passed for her by itself")
        a.model.engine.let { assertTrue(it.stack.size == 1 && it.priority.holder == bob, "Bob's priority over her ability") }

        assertTrue(a.pressedByItself(assumed, Input.PassPriority(alice)), "Resolve there: the same press")
        assertFalse(a.pressedByItself(assumed, Input.PassPriority(bob)), "another input")
        assertFalse(a.pressedByItself(assumed, Input.Cast(alice, hand(a.model, alice, martyr))), "another input: a cast")
        assertFalse(a.pressedByItself(activated, Input.PassPriority(bob)), "her Assume was her own tap (SYS-011: a second tap)")
        assertFalse(a.pressedByItself(passed, Input.PassPriority(bob)), "nothing pressed at the later point: her ability waits")
        // Once the server has them too
        sim.flush(alice)
        sim.deliver(alice)
        assertTrue(a.session.pending.isEmpty())
        assertTrue(a.pressedByItself(assumed, Input.PassPriority(alice)), "still the same press on the main line")
    }

    // HIS-109 (E48RDE): the other order: Alice's tap of Resolve comes first; her phone's own steps then make no second pass
    // of hers there, and her ability still waits for Bob. That pass was her tap's, so a second tap made on the same board
    // is not the same press (SYS-011: a quick second tap is told)
    @Test
    fun aTapOfResolveFirstLeavesNothingMoreForTheDeviceToPress() {
        val sim = twoOfHersOnTheStack()
        val alice = sim.alice; val bob = sim.bob
        val a = sim.device(alice)
        a.advance(sim.now)
        assertIs<GameDevice.Acted.Sent>(a.assume())
        val assumed = a.session.tip
        assertIs<GameDevice.Acted.Sent>(a.act(Input.PassPriority(alice)))
        val tapped = a.session.tip
        a.advance(sim.now)
        assertEquals(tapped, a.session.tip, "no step of her phone's own after her tap")
        a.model.engine.let { assertTrue(it.stack.size == 1 && it.priority.holder == bob, "her ability waits for Bob") }
        assertEquals(GameDevice.Offer.AssumeResolves, assertIs<GameDevice.Status.Waiting>(a.status).offer)
        assertEquals(1, (a.session.pending).count { e -> e.engine?.steps?.firstNotNullOfOrNull { it.input } == Input.PassPriority(alice) &&
            e.parentId == assumed }, "one pass of hers there")
        assertFalse(a.pressedByItself(assumed, Input.PassPriority(alice)), "her own tap's pass")
    }

    // HIS-109, TRN-070 (E48RDE): with Assume resolves Always her phone assumes Bob's passes by itself: a tap of Assume
    // made on the board before such an assumption is that very press, and never the press at the next Stack item
    @Test
    fun theDevicesOwnAssumptionIsTheSamePressAsATapOfAssumeThere() {
        val sim = twoOfHersOnTheStack(AssumeResolves.Always)
        val alice = sim.alice; val bob = sim.bob
        val a = sim.device(alice)
        val activated = a.session.tip
        a.advance(sim.now)
        val line = a.session.pending.dropWhile { it.id != activated }.drop(1)
        val inputs = line.map { e -> e.engine?.steps?.firstNotNullOfOrNull { it.input } }
        assertEquals(listOf<Input?>(Input.PassPriority(bob), Input.PassPriority(alice), Input.PassPriority(bob)), inputs,
            "her phone assumed, passed for her and assumed again: both resolved")
        assertTrue(a.model.engine.stack.isEmpty())
        assertEquals(TurnStep.PrecombatMain, a.model.engine.turn.step)
        assertTrue(a.pressedByItself(activated, Input.PassPriority(bob)), "Assume over the trigger: the same press")
        assertTrue(a.pressedByItself(line[0].id, Input.PassPriority(alice)), "Resolve over her ability: the same press")
        assertTrue(a.pressedByItself(line[1].id, Input.PassPriority(bob)), "Assume over her ability: the same press")
        assertFalse(a.pressedByItself(activated, Input.PassPriority(alice)), "another input")
    }
}
