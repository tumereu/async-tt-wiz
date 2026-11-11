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
 * Control mode and assumptions (owner, 2026-11-06, 2026-11-07): where a device assumes the other player's pass
 * (GameDevice's point): where the other player's own response mode, learned from the server, would pass it (their Quick
 * judged on their real hand), or, at a point the player began (the last real input since the step began was theirs), where
 * their Assume setting (Never, Normal, When they have no mana, Always, defined from the response modes) assumes it; and
 * automation is button presses, so a late live move of the player an assumption is about branches. Bob's device is the
 * one assuming throughout: the first grids are at points he began, the later ones at points Alice began.
 */
class AssumeModeTest {
    private val glimmer = arcanum.engine.cards.Cards.invent("Idle Glimmer", "{0}", "Instant", "Draw a card.")
    private val pup = arcanum.engine.cards.Cards.invent("Idle Pup", "{0}", "Creature — Dog", "", "1/1")

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

    private val modes = listOf<ResponseMode>(ResponseMode.FullControl, ResponseMode.Normal, ResponseMode.AutoSkip)

    // HIS-096, TRN-070, STK-036 (owner 2026-11-06, 2026-11-07: Bob's spell, his point): at the other player's pause where their real hand
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
    /** Bob's turn played on by hand (his passes, Alice's passes assumed where his device waits) until [at] holds. */
    private fun bobPlaysOn(sim: DeviceSim, at: () -> Boolean) {
        val bobs = sim.device(sim.bob)
        var guard = 0
        while (!at() && guard++ < 30) {
            when (bobs.status) {
                GameDevice.Status.YourMove -> bobs.act(Input.PassPriority(sim.bob))
                is GameDevice.Status.Waiting -> bobs.assume()
                else -> {}
            }
            sim.settle(only = listOf(sim.bob))
        }
        assertTrue(at(), "reached the point")
    }

    // HIS-096 (owner 2026-11-07): Alice's upkeep, a point she began (nothing was input since the step began: the active
    // player's, as with a beginning-of-step trigger): only her response mode decides; Bob's Assume setting doesn't count,
    // so against Control even Always waits (at a point Bob began, Always assumes every pass)
    @Test
    fun atTheirStepWithNoInputOnlyTheirModeDecides() {
        for (setting in AssumeResolves.all) for (mode in modes) {
            val sim = bobAtMain(setting, mode)
            val bobs = sim.device(sim.bob)
            fun atHerUpkeep() = bobs.model.engine.let { it.turn.active == sim.alice && it.turn.step == TurnStep.Upkeep && it.priority.holder == sim.alice }
            fun assumedInHerUpkeep() = sim.server.line().any { e ->
                val before = sim.server.stateAt(e.parentId).engine
                e.assumed?.assumedBy == sim.bob && before.turn.active == sim.alice && before.turn.step == TurnStep.Upkeep
            }
            bobPlaysOn(sim) { atHerUpkeep() || assumedInHerUpkeep() }
            val assumed = assumedInHerUpkeep()
            if (!assumed) {
                assertEquals(sim.alice, GameDevice.pointOwner(bobs.session.events + bobs.session.pending, bobs.model.engine), "her point")
                assertEquals(GameDevice.Offer.AssumeResolves, assertIs<GameDevice.Status.Waiting>(bobs.status).offer, "a wait with Assume")
            }
            assertEquals(mode != ResponseMode.FullControl, assumed, "$setting against $mode")
        }
    }

    /**
     * Both players live, each passing by hand (Control, Assume Never), Bob first: Bob casts an Idle Pup in his turn 1, Alice
     * one in her turn 2, and in his turn 3 Bob attacks with his Pup. Then Bob's Assume setting becomes [setting] and Alice's
     * mode [mode]; with [aliceBlocks] Alice blocks with her Pup (her own input), else Bob assumes that block (his press),
     * and Bob passes his priority after the blocks: Alice's priority in the declare blockers step, as Bob's device sees it.
     */
    private fun afterBlocks(setting: AssumeResolves, mode: ResponseMode, aliceBlocks: Boolean): DeviceSim {
        val sim = DeviceSim(EngineGames(3, List(40) { pup } to List(40) { pup }))
        val alice = sim.alice; val bob = sim.bob
        sim.start(bob, mode = ResponseMode.FullControl, alwaysAssume = false)
        for (p in listOf(alice, bob)) sim.device(p).settings(PlaySettings(mode = ResponseMode.FullControl).assuming(AssumeResolves.Never))
        sim.settle()
        for (p in listOf(bob, alice)) { assertIs<GameDevice.Acted.Sent>(sim.device(p).act(sim.keep(p))); sim.settle() }
        fun s() = sim.server.state().engine
        var guard = 0
        // Each player's move by hand until Bob's attack is declared and the blockers are awaited
        while (guard++ < 300) {
            val state = s()
            if (state.turn.number == 3 && state.turn.step == TurnStep.DeclareBlockers && state.priority.holder == null) break
            val acting = listOf(alice, bob).first { p -> sim.device(p).status.let { it == GameDevice.Status.YourMove || it is GameDevice.Status.Stopped } }
            val d = sim.device(acting)
            val ds = d.model.engine
            val input = when {
                ds.priority.holder == acting && ds.turn.active == acting && ds.stack.isEmpty() && ds.turn.step == TurnStep.PrecombatMain &&
                    ds.turn.number <= 2 && ds.battlefield.none { ds.obj(it)?.controller == acting } -> Input.Cast(acting, hand(d.model, acting, pup))
                ds.priority.holder == acting -> Input.PassPriority(acting)
                else -> {
                    val options = Engine.options(ds, acting)
                    options.filterIsInstance<Input.DeclareAttackers>().let { attacks ->
                        if (ds.turn.number == 3) attacks.firstOrNull { it.attacks.size == 1 } else attacks.firstOrNull { it.attacks.isEmpty() }
                    } ?: options.filterIsInstance<Input.DeclareBlockers>().first { it.blocks.isEmpty() }
                }
            }
            assertIs<GameDevice.Acted.Sent>(d.act(input), "$acting: $input")
            sim.settle()
        }
        assertEquals(1, s().combat?.attackers?.size, "Bob attacks")
        sim.device(alice).settings(PlaySettings(mode = mode).assuming(AssumeResolves.Never))
        sim.device(bob).settings(PlaySettings(mode = ResponseMode.FullControl).assuming(setting))
        sim.flush(alice); sim.flush(bob)
        sim.deliver(bob)
        assertEquals(mode, sim.device(bob).opponentMode)
        val bobs = sim.device(bob)
        if (aliceBlocks) {
            val alices = sim.device(alice)
            val block = Engine.options(alices.model.engine, alice).filterIsInstance<Input.DeclareBlockers>().first { it.blocks.size == 1 }
            assertIs<GameDevice.Acted.Sent>(alices.act(block))
            sim.flush(alice)
            sim.deliver(bob)
        } else {
            val st = bobs.model.engine
            assertIs<GameDevice.Acted.Sent>(bobs.assume(listOf(arcanum.engine.input.Block(
                st.battlefield.first { st.obj(it)?.controller == alice }, st.combat!!.attackers.single().id))))
            sim.flush(bob)
        }
        sim.settle(only = listOf(bob))
        assertEquals(TurnStep.DeclareBlockers, bobs.model.engine.turn.step)
        assertEquals(bob, bobs.model.engine.priority.holder, "Bob's priority after the blocks")
        assertIs<GameDevice.Acted.Sent>(bobs.act(Input.PassPriority(bob)))
        sim.settle(only = listOf(bob))
        return sim
    }

    /** Whether Bob's device assumed Alice's pass after the blocks (else it waits there with Assume). */
    private fun assumedAfterBlocks(sim: DeviceSim): Boolean {
        val assumed = sim.server.line().any { e ->
            e.assumed?.assumedBy == sim.bob && e.assumed?.input == Input.PassPriority(sim.alice) &&
                sim.server.stateAt(e.parentId).engine.turn.step == TurnStep.DeclareBlockers
        }
        if (!assumed) {
            val bobs = sim.device(sim.bob)
            assertEquals(sim.alice, bobs.model.engine.priority.holder)
            assertEquals(GameDevice.Offer.AssumeResolves, assertIs<GameDevice.Status.Waiting>(bobs.status).offer)
        }
        return assumed
    }

    /** Who began the point at the head of [p]'s device. */
    private fun DeviceSim.owner(p: PlayerId) = device(p).let { GameDevice.pointOwner(it.session.events + it.session.pending, it.model.engine) }

    // HIS-096 (owner 2026-11-07): Bob attacks and Alice blocks: the point after it (her priority, a pause of hers) is hers,
    // the block being the last real input (Bob's pass after it doesn't count), so only her mode decides: her Quick passes
    // (her Pups can't be cast now), Normal and Control wait, whatever Bob's Assume setting
    @Test
    fun afterTheirBlocksOnlyTheirModeDecides() {
        for (setting in AssumeResolves.all) for (mode in modes) {
            val sim = afterBlocks(setting, mode, aliceBlocks = true)
            val assumed = assumedAfterBlocks(sim)
            if (!assumed) assertEquals(sim.alice, sim.owner(sim.bob), "her point")
            assertEquals(mode == ResponseMode.AutoSkip, assumed, "$setting against $mode")
        }
    }

    // HIS-096 (owner 2026-11-07): the same point after a block Bob assumed himself (Assume blockers): his press began it,
    // so his setting counts too: When they have no mana (she shows no mana source) and Always assume her pass in any mode
    @Test
    fun afterBlocksYouAssumedYourSettingCounts() {
        for (setting in AssumeResolves.all) for (mode in modes) {
            val sim = afterBlocks(setting, mode, aliceBlocks = false)
            val assumed = assumedAfterBlocks(sim)
            if (!assumed) assertEquals(sim.bob, sim.owner(sim.bob), "his point")
            val expected = mode == ResponseMode.AutoSkip || setting == AssumeResolves.WhenNoMana || setting == AssumeResolves.Always
            assertEquals(expected, assumed, "$setting against $mode")
        }
    }

    /** Alice and Bob kept, Alice at her main phase in Control holding free instants; Bob's Assume setting [setting]. */
    private fun aliceInControl(setting: AssumeResolves): DeviceSim {
        val sim = DeviceSim(EngineGames(3, (List(10) { "Forest" } + List(30) { glimmer }) to List(40) { "Forest" }))
        val alice = sim.alice; val bob = sim.bob
        sim.start(alice, alwaysAssume = false)
        for (p in listOf(alice, bob)) { sim.device(p).act(sim.keep(p)); sim.settle() }
        assertTrue(sim.atMain(alice), "Alice at her main phase")
        sim.device(alice).settings(PlaySettings(mode = ResponseMode.FullControl))
        sim.device(bob).settings(PlaySettings().assuming(setting))
        sim.settle()
        assertEquals(ResponseMode.FullControl, sim.device(bob).opponentMode)
        return sim
    }

    // STK-036, HIS-096 (owner 2026-11-07): Alice in Control casts a spell and keeps priority: a point she began, so Bob's
    // device waits for her whatever his setting (Always too); her second spell is simply taken: no branch, nothing assumed
    @Test
    fun aSecondSpellWhileHoldingPriorityIsSimplyTaken() {
        for (setting in AssumeResolves.all) {
            val sim = aliceInControl(setting)
            val alice = sim.alice; val bob = sim.bob
            val alices = sim.device(alice)
            val assumedBefore = sim.server.line().count { it.assumed?.assumedBy == bob }
            assertIs<GameDevice.Acted.Sent>(alices.act(Input.Cast(alice, hand(alices.model, alice, glimmer))))
            sim.settle()
            assertEquals(alice, alices.model.engine.priority.holder, "Control keeps priority after her own spell")
            assertEquals(alice, sim.owner(bob), "her point")
            assertEquals(GameDevice.Offer.AssumeResolves, assertIs<GameDevice.Status.Waiting>(sim.device(bob).status).offer, "$setting: a wait")
            assertIs<GameDevice.Acted.Sent>(alices.act(Input.Cast(alice, hand(alices.model, alice, glimmer))))
            sim.settle()
            assertEquals(0, sim.branches, "$setting: no branch")
            assertEquals(assumedBefore, sim.server.line().count { it.assumed?.assumedBy == bob }, "$setting: nothing assumed")
            assertEquals(2, sim.server.state().engine.stack.size, "$setting: both her spells on the Stack")
            assertTrue(sim.leaks.isEmpty())
        }
    }

    // STK-036, HIS-096, HIS-109, SYS-077 (owner 2026-11-06, 2026-11-07): Alice in Control casts a spell and keeps priority;
    // Bob presses Assume by hand there (still offered at her point); her second spell, sent before that assumption reached
    // her, is her answer at it: not refused, it branches (HIS-097), and Bob's device goes back with the branch note
    @Test
    fun aSecondSpellAfterAnAssumeByHandBranches() {
        val sim = aliceInControl(AssumeResolves.Always)
        val alice = sim.alice; val bob = sim.bob
        val alices = sim.device(alice)
        // Her first spell: she keeps priority
        assertIs<GameDevice.Acted.Sent>(alices.act(Input.Cast(alice, hand(alices.model, alice, glimmer))))
        assertEquals(alice, alices.model.engine.priority.holder, "Control keeps priority after her own spell")
        sim.flush(alice)
        assertEquals(ResponseMode.FullControl, sim.server.line().last().mode, "the event records her mode")
        sim.deliver(bob)
        sim.device(bob).playToStop()
        sim.device(bob).advance(sim.now)
        assertIs<GameDevice.Status.Waiting>(sim.device(bob).status, "a wait at her point")
        assertIs<GameDevice.Acted.Sent>(sim.device(bob).assume())
        sim.flush(bob)
        val assumption = sim.server.line().last { it.assumed?.assumedBy == bob }
        assertEquals(Input.PassPriority(alice), assumption.assumed?.input, "Bob assumed her pass")
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
