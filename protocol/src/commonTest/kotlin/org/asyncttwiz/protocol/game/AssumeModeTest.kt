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
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Control mode and assumptions (owner, 2026-11-06): where a device assumes the other player's pass (GameDevice's point):
 * the player's Assume setting (Never, Normal, When they have no mana, Always, defined from the response modes) or the
 * other player's own response mode, learned from the server, would pass it (their Quick judged on their real hand); and
 * automation is button presses, so a late live move of the player an assumption is about branches.
 */
class AssumeModeTest {
    private val glimmer = arcanum.engine.cards.Cards.invent("Idle Glimmer", "{0}", "Instant", "Draw a card.")

    private fun hand(model: GameModel, p: PlayerId, name: String): ObjectId =
        model.engine.player(p).hand.first { model.engine.obj(it)?.definition?.faces?.first()?.name == name }

    private fun DeviceSim.atMain(p: PlayerId) = device(p).model.engine.let {
        it.turn.active == p && it.turn.step == TurnStep.PrecombatMain && it.stack.isEmpty() && it.priority.holder == p
    }

    /**
     * Bob starts, keeps and plays on to his main phase while Alice is away; then Bob's Assume setting is [setting] and
     * Alice's response mode [mode] (her device tells the server, which tells his). Alice's deck is all Forests, or with
     * [glimmers] Forests and free instants (she can respond whenever she gets priority).
     */
    private fun bobAtMain(setting: AssumeResolves, mode: ResponseMode, glimmers: Boolean = false): DeviceSim {
        val alices = if (glimmers) List(10) { "Forest" } + List(30) { glimmer } else List(40) { "Forest" }
        val sim = DeviceSim(EngineGames(3, alices to (List(20) { "Island" } + List(20) { "Glance Ahead" })))
        sim.start(sim.bob, alwaysAssume = false)
        sim.keepWhileAway(sim.bob)
        assertTrue(sim.atMain(sim.bob), "Bob is at his main phase")
        if (glimmers) assertTrue(sim.device(sim.alice).model.engine.player(sim.alice).hand.any {
            sim.device(sim.alice).model.engine.obj(it)?.definition?.faces?.first()?.name == glimmer }, "Alice holds a free instant")
        sim.device(sim.bob).settings(PlaySettings().assuming(setting))
        sim.device(sim.alice).settings(PlaySettings(mode = mode))
        sim.flush(sim.bob)
        sim.flush(sim.alice)
        sim.deliver(sim.bob)
        assertEquals(mode, sim.device(sim.bob).opponentMode, "Bob's device knows Alice's mode")
        return sim
    }

    /** Whether Bob's device assumed Alice's pass at once at the point [to] leads to (else it waits there with Assume). */
    private fun assumedAt(sim: DeviceSim, to: DeviceSim.() -> Unit): Boolean {
        val before = sim.server.line().size
        sim.to()
        sim.settle(only = listOf(sim.bob))
        val assumed = sim.server.line().drop(before).any { it.assumed?.assumedBy == sim.bob }
        if (!assumed) {
            val wait = assertIs<GameDevice.Status.Waiting>(sim.device(sim.bob).status)
            assertEquals(GameDevice.Offer.AssumeResolves, wait.offer, "a wait with Assume")
        }
        return assumed
    }

    /** Bob plays an Island and casts Glance Ahead: Alice's pause (his spell on top), his own pass folded in. */
    private val bobCasts: DeviceSim.() -> Unit = {
        val bobs = device(bob)
        assertIs<GameDevice.Acted.Sent>(bobs.act(Input.PlayLand(bob, hand(bobs.model, bob, "Island"))))
        assertIs<GameDevice.Acted.Sent>(bobs.act(Input.Cast(bob, hand(bobs.model, bob, "Glance Ahead"))) { m ->
            val d = m.engine.pending?.decision
            if (d == null || d is Decision.Random || d.player != bob) null
            else Engine.options(m.engine, bob).filterIsInstance<Input.Answer>().let { answers ->
                if (d is Decision.Targets) answers.first { (it.answer as Answer.Targets).chosen == listOf(TargetRef.Player(alice)) } else answers.first()
            }
        })
    }

    /** Bob passes in his main phase with nothing on the Stack: Alice's priority there is no pause of hers. */
    private val bobPasses: DeviceSim.() -> Unit = { assertIs<GameDevice.Acted.Sent>(device(bob).act(Input.PassPriority(bob))) }

    private val modes = listOf(ResponseMode.FullControl, ResponseMode.Normal, ResponseMode.AutoSkip)

    // HIS-096, TRN-070, STK-036 (owner 2026-11-06): at the other player's pause (your spell on top) where their real hand
    // can't respond: Never and Normal wait unless their mode would pass (Quick does: nothing to respond with); When they
    // have no mana and Always assume at once (no mana shown)
    @Test
    fun atTheirPauseWithNothingToRespondWith() {
        for (setting in AssumeResolves.all) for (mode in modes) {
            val expected = when (setting) {
                AssumeResolves.Never, AssumeResolves.Normal -> mode == ResponseMode.AutoSkip
                AssumeResolves.WhenNoMana, AssumeResolves.Always -> true
            }
            assertEquals(expected, assumedAt(bobAtMain(setting, mode), bobCasts), "$setting against $mode")
        }
    }

    // HIS-096, TRN-070 (owner 2026-11-06): at their pause while their real hand holds a free instant: their Quick stops (it
    // is judged on their real hand), so Never and Normal wait in every mode; When they have no mana ignores the hand
    @Test
    fun atTheirPauseWithAnInstantInHand() {
        for (setting in AssumeResolves.all) for (mode in modes) {
            val expected = setting == AssumeResolves.WhenNoMana || setting == AssumeResolves.Always
            assertEquals(expected, assumedAt(bobAtMain(setting, mode, glimmers = true), bobCasts), "$setting against $mode")
        }
    }

    // HIS-096, STK-036 (owner 2026-11-06): at their priority that is no pause of theirs (your main phase, empty Stack):
    // Never waits only against Control (Normal and Quick pass there); every other setting assumes it, as Normal mode passes
    @Test
    fun atTheirPriorityThatIsNoPause() {
        for (setting in AssumeResolves.all) for (mode in modes) {
            val expected = setting != AssumeResolves.Never || mode != ResponseMode.FullControl
            assertEquals(expected, assumedAt(bobAtMain(setting, mode), bobPasses), "$setting against $mode")
        }
    }

    // HIS-096 (owner 2026-11-06): their pause at your end step is assumed with Normal whatever their mode; Never respects
    // their mode only
    @Test
    fun atTheirPauseInYourEndStep() {
        for (setting in listOf(AssumeResolves.Never, AssumeResolves.Normal)) for (mode in modes) {
            val sim = bobAtMain(setting, mode)
            val bobs = sim.device(sim.bob)
            val turn = bobs.model.engine.turn.number
            fun atEndStep() = bobs.model.engine.let { it.turn.step == TurnStep.End && it.priority.holder == sim.alice }
            // On to his end step: his own passes, and Alice's priority on the way assumed by hand where it waits
            var guard = 0
            while (!atEndStep() && bobs.model.engine.turn.number == turn && guard++ < 20) {
                when (bobs.status) {
                    GameDevice.Status.YourMove -> bobs.act(Input.PassPriority(sim.bob))
                    is GameDevice.Status.Waiting -> bobs.assume()
                    else -> {}
                }
                sim.settle(only = listOf(sim.bob))
            }
            val assumed = sim.server.line().any { e ->
                val before = sim.server.stateAt(e.parentId).engine
                e.assumed?.assumedBy == sim.bob && before.turn.number == turn && before.turn.step == TurnStep.End
            }
            if (!assumed) assertTrue(atEndStep(), "a wait at Alice's priority in Bob's end step")
            val expected = setting == AssumeResolves.Normal || mode == ResponseMode.AutoSkip
            assertEquals(expected, assumed, "$setting against $mode")
        }
    }

    // STK-036, HIS-096, SYS-077 (owner 2026-11-06, spec rule 6): Alice in Control casts a spell and keeps priority; Bob's
    // device ("Always") assumes her pass; her second spell, sent before that assumption reached her, is her answer at it:
    // not refused, it branches (HIS-097), and Bob's device goes back with the branch note
    @Test
    fun aLateSecondSpellWhileHoldingPriorityBranches() {
        val sim = DeviceSim(EngineGames(3, (List(10) { "Forest" } + List(30) { glimmer }) to List(40) { "Forest" }))
        val alice = sim.alice; val bob = sim.bob
        sim.start(alice, alwaysAssume = true)
        for (p in listOf(alice, bob)) { sim.device(p).act(sim.keep(p)); sim.settle() }
        assertTrue(sim.atMain(alice), "Alice at her main phase")
        val alices = sim.device(alice)
        alices.settings(PlaySettings(mode = ResponseMode.FullControl, alwaysAssumeResolves = true))
        sim.settle()
        assertEquals(ResponseMode.FullControl, sim.device(bob).opponentMode)
        // Her first spell: she keeps priority
        assertIs<GameDevice.Acted.Sent>(alices.act(Input.Cast(alice, hand(alices.model, alice, glimmer))))
        assertEquals(alice, alices.model.engine.priority.holder, "Control keeps priority after her own spell")
        sim.flush(alice)
        assertEquals(ResponseMode.FullControl, sim.server.line().last().mode, "the event records her mode")
        sim.deliver(bob)
        sim.device(bob).playToStop()
        sim.device(bob).advance(sim.now)
        sim.flush(bob)
        val assumption = sim.server.line().last { it.assumed?.assumedBy == bob }
        assertEquals(Input.PassPriority(alice), assumption.assumed?.input, "Bob's device assumed her pass")
        assertEquals(ResponseMode.FullControl, assumption.mode, "the assumption records her mode")
        // Her second spell, made before the assumption reached her
        assertIs<GameDevice.Acted.Sent>(alices.act(Input.Cast(alice, hand(alices.model, alice, glimmer))))
        sim.flush(alice)
        sim.deliver(alice)
        alices.advance(sim.now)
        assertTrue(alices.notes().none { it is GameDevice.Note.Refused }, "not refused")
        sim.flush(alice)
        assertEquals(1, sim.branches, "her second spell branched")
        val line = sim.server.line()
        assertTrue(line.none { it.id == assumption.id }, "the assumption is gone from the main line")
        assertEquals(2, sim.server.state().engine.stack.size, "both her spells on the Stack")
        sim.deliver(bob)
        assertEquals(alice, sim.device(bob).notes().filterIsInstance<GameDevice.Note.Branched>().single().by)
        sim.settle()
        assertEquals(sim.server.head, sim.device(bob).session.head)
        assertTrue(sim.leaks.isEmpty())
    }
}
