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
import kotlin.test.assertTrue

/**
 * Owner playtest 2026-11-07 (TRN-017, STK-029, TRN-070): holding Resolve or a Stack wait's Assume is the whole chain:
 * the device presses Resolve at each of the player's priority points and Assume at each of the other player's, whatever
 * the player's response mode, until the Stack is empty ([GameDevice.startChain]); the other player's real input is never
 * passed over: when it reaches the server before the chain's presses they aren't taken, and the player gets priority
 * with it on top.
 */
class ResolveChainTest {
    private val altar = arcanum.engine.cards.Cards.invent("Ember Altar", "{0}", "Artifact",
        "{T}, Sacrifice another creature: Each opponent loses 2 life and you gain 2 life.")
    private val martyr = arcanum.engine.cards.Cards.invent("Ember Martyr", "{0}", "Creature — Elemental",
        "When this creature dies, you gain 2 life.", "1/1")
    private val glimmer = arcanum.engine.cards.Cards.invent("Idle Glimmer", "{0}", "Instant", "Draw a card.")

    private fun hand(model: GameModel, p: PlayerId, name: String): ObjectId =
        model.engine.player(p).hand.first { model.engine.obj(it)?.definition?.faces?.first()?.name == name }

    /** Answers [player]'s own decisions of a flow with their first listed answer. */
    private fun first(player: PlayerId): (GameModel) -> Input? = { m ->
        val d = m.engine.pending?.decision
        if (d == null || d is Decision.Random || d.player != player) null
        else Engine.options(m.engine, player, org.asyncttwiz.protocol.game.EnginePreferences.of(m.engine)).filterIsInstance<Input.Answer>().first { it.decision == d.id }
    }

    private fun inputOf(e: GameEvent): Input? = e.engine?.steps?.firstNotNullOfOrNull { it.input }

    /**
     * Alice ([mode]) starts and plays on alone while Bob is away in Normal with Idle Glimmers in hand (his pause at her
     * spells waits for him, with Assume): she casts an Ember Altar and an Ember Martyr, pressing Assume for each, then
     * activates the Altar sacrificing the Martyr: its death trigger on the Stack above her ability, and her phone waits
     * for Bob over the trigger, with Assume.
     */
    private fun twoOfHersOnTheStack(mode: ResponseMode = ResponseMode.AutoSkip): DeviceSim {
        val sim = DeviceSim(EngineGames(3, (List(10) { "Mountain" } + List(15) { altar } + List(15) { martyr }) to
            (List(20) { "Island" } + List(20) { glimmer })))
        sim.start(sim.alice, alwaysAssume = false)
        sim.keepWhileAway(sim.alice)
        val alice = sim.alice
        val a = sim.device(alice)
        sim.device(sim.bob).settings(PlaySettings(mode = ResponseMode.Normal))
        sim.flush(sim.bob)
        sim.deliver(alice)
        for (name in listOf(altar, martyr)) {
            assertIs<GameDevice.Acted.Sent>(a.act(Input.Cast(alice, hand(a.model, alice, name)), follow = first(alice)))
            a.advance(sim.now)
            assertIs<GameDevice.Acted.Sent>(a.assume())
            a.advance(sim.now)
        }
        a.settings(PlaySettings(mode = mode))
        val activate = Engine.options(a.model.engine, alice, org.asyncttwiz.protocol.game.EnginePreferences.of(a.model.engine)).filterIsInstance<Input.Activate>().single()
        assertIs<GameDevice.Acted.Sent>(a.act(activate, follow = first(alice)))
        a.advance(sim.now)
        assertEquals(2, a.model.engine.stack.size, "her trigger above her ability")
        // Control keeps her priority after her own activation (STK-036)
        if (mode == ResponseMode.FullControl) assertEquals(GameDevice.Status.YourMove, a.status, "her priority over her trigger")
        else assertEquals(GameDevice.Offer.AssumeResolves, assertIs<GameDevice.Status.Waiting>(a.status).offer, "waiting for Bob over her trigger")
        return sim
    }

    /** Alice's hold of Assume (or, [resolve], of Resolve): its tap, then the chain. The events her device made from it on. */
    private fun aliceHoldsAssume(sim: DeviceSim, resolve: Boolean = false, steps: Boolean = true): List<GameEvent> {
        val a = sim.device(sim.alice)
        val from = a.session.tip
        val since = a.opponentInputs
        assertIs<GameDevice.Acted.Sent>(if (resolve) a.act(Input.PassPriority(sim.alice)) else a.assume())
        assertTrue(a.startChain(since), "the chain begins")
        if (steps) a.advance(sim.now)
        return a.session.pending.dropWhile { it.parentId != from }
    }

    /** Bob comes back to the head (Alice's events so far sent): his playback stops at Alice's assumptions of his passes
     * over her spells (he could respond), which he confirms, until his priority over her trigger. */
    private fun bobAtHisPriorityOverTheTrigger(sim: DeviceSim): GameDevice {
        val bob = sim.bob
        sim.flush(sim.alice)
        sim.deliver(bob)
        val b = sim.device(bob)
        b.play()
        repeat(10) {
            b.playToStop()
            if (b.status is GameDevice.Status.Stopped) assertIs<GameDevice.Acted.Confirmed>(b.act(Input.PassPriority(bob)))
        }
        assertEquals(GameDevice.Status.YourMove, b.status, "Bob may respond over her trigger")
        assertEquals(2, b.model.engine.stack.size)
        assertTrue(b.model.engine.player(bob).hand.any { b.model.engine.obj(it)?.definition?.faces?.first()?.name == glimmer }, "a Glimmer in hand")
        return b
    }

    // TRN-017, TRN-070, STK-029 (owner 2026-11-07): Alice holds Assume over her trigger: her phone assumes Bob's pass (the
    // trigger resolves), presses Resolve for her over her ability and assumes Bob's pass again, where a tap would have
    // stopped at Bob's next wait: the whole Stack resolves and the chain ends with it
    @Test
    fun holdingAssumeResolvesTheWholeStack() {
        val sim = twoOfHersOnTheStack()
        val alice = sim.alice; val bob = sim.bob
        val a = sim.device(alice)
        val made = aliceHoldsAssume(sim)
        assertEquals(listOf<Input?>(Input.PassPriority(bob), Input.PassPriority(alice), Input.PassPriority(bob)), made.map(::inputOf))
        assertEquals(listOf(alice, null, alice), made.map { it.assumed?.assumedBy }, "Assume, Resolve, Assume")
        assertTrue(a.model.engine.stack.isEmpty(), "the whole Stack resolved")
        assertFalse(a.chaining, "the chain ended with the Stack")
        assertEquals(GameDevice.Status.YourMove, a.status)
        assertEquals(TurnStep.PrecombatMain, a.model.engine.turn.step, "her main phase, where her mode stops")
        sim.flush(alice)
        sim.deliver(alice)
        assertTrue(a.session.pending.isEmpty(), "all taken")
    }

    // TRN-017, STK-029, STK-036 (owner 2026-11-07): whatever her response mode: in Control she holds Resolve over her
    // trigger; where her own priority over her ability stops for her, the chain presses Resolve there too
    @Test
    fun holdingResolvePressesResolveWhereHerModeWouldStop() {
        val sim = twoOfHersOnTheStack(ResponseMode.FullControl)
        val alice = sim.alice; val bob = sim.bob
        val made = aliceHoldsAssume(sim, resolve = true)
        assertEquals(listOf<Input?>(Input.PassPriority(alice), Input.PassPriority(bob), Input.PassPriority(alice), Input.PassPriority(bob)),
            made.map(::inputOf))
        assertTrue(sim.device(alice).model.engine.stack.isEmpty(), "the whole Stack resolved")
    }

    // TRN-017, SYS-077, HIS-109 (owner 2026-11-07): Bob, live in Normal, casts an Idle Glimmer over her trigger while her
    // phone makes the chain; his cast reaches the server first: none of her chain's presses is taken, the chain is over,
    // and Alice (Normal) has priority with his Glimmer on top: nothing of hers passed over it
    @Test
    fun theirInputReachingTheServerFirstIsNeverPassedOver() {
        val sim = twoOfHersOnTheStack(ResponseMode.Normal)
        val alice = sim.alice; val bob = sim.bob
        val a = sim.device(alice)
        val b = bobAtHisPriorityOverTheTrigger(sim)
        val since = a.opponentInputs
        // Her hold pressed Assume; his cast reaches the server before her phone's next press
        aliceHoldsAssume(sim, steps = false)
        assertTrue(a.chaining)
        assertIs<GameDevice.Acted.Sent>(b.act(Input.Cast(bob, hand(b.model, bob, glimmer)), follow = first(bob)))
        sim.flush(bob)
        sim.flush(alice)
        sim.deliver(alice)
        // His cast plays back on her table (it shows as live play shows it), then her device goes on
        a.playToStop()
        a.advance(sim.now)
        assertFalse(a.chaining, "his input ended the chain")
        assertFalse(a.startChain(since), "no chain begins over it")
        assertTrue(a.session.pending.isEmpty(), "none of her chain's presses taken")
        assertGlimmerOnTopForAlice(sim)
    }

    // TRN-017, HIS-109 (owner 2026-11-07): the other order: her chain's presses reach the server first, then Bob's Glimmer
    // made where her phone assumed his pass: his answer there branches; her table goes back and she has priority with his
    // Glimmer on top, nothing of hers passed over it
    @Test
    fun theirInputAfterTheChainBranchesAndIsNeverPassedOver() {
        val sim = twoOfHersOnTheStack(ResponseMode.Normal)
        val alice = sim.alice; val bob = sim.bob
        val a = sim.device(alice)
        val b = bobAtHisPriorityOverTheTrigger(sim)
        aliceHoldsAssume(sim)
        sim.flush(alice)
        assertIs<GameDevice.Acted.Sent>(b.act(Input.Cast(bob, hand(b.model, bob, glimmer)), follow = first(bob)))
        // His cast made before her assumption reached him: refused as made on the old line, then made again as his answer
        // at her assumption (SYS-077)
        sim.flush(bob)
        sim.deliver(bob)
        b.advance(sim.now)
        sim.flush(bob)
        assertEquals(1, sim.branches, "his answer where she assumed his pass: ${b.recentRecord(20)}")
        sim.deliver(alice)
        a.playToStop()
        a.advance(sim.now)
        assertFalse(a.chaining)
        assertGlimmerOnTopForAlice(sim)
    }

    /** Alice's priority with Bob's Glimmer on top of the Stack, her phone waiting for her, and nothing of hers on the
     * server after his cast. */
    private fun assertGlimmerOnTopForAlice(sim: DeviceSim) {
        val alice = sim.alice; val bob = sim.bob
        val a = sim.device(alice)
        val s = a.model.engine
        val top = s.stack.last()
        assertEquals(glimmer, s.obj(top)?.definition?.faces?.first()?.name,
            "his Glimmer on top: ${s.stack.map { s.obj(it)?.definition?.faces?.first()?.name ?: it.toString() }}; ${a.recentRecord(30)}")
        assertEquals(alice, s.priority.holder)
        assertEquals(GameDevice.Status.YourMove, a.status, "her priority over it (Normal stops there)")
        val line = a.session.events
        val cast = line.indexOfLast { e -> e.author == bob.value && inputOf(e) is Input.Cast }
        assertTrue(cast >= 0 && line.drop(cast + 1).none { it.author == alice.value }, "nothing of hers after his cast")
        assertTrue(sim.leaks.isEmpty())
    }

    // TRN-017: the player's own action ends a chain going on
    @Test
    fun anOwnActionEndsTheChain() {
        val sim = twoOfHersOnTheStack()
        val a = sim.device(sim.alice)
        assertTrue(a.startChain(a.opponentInputs))
        assertTrue(a.chaining)
        assertIs<GameDevice.Acted.Sent>(a.assume())
        assertFalse(a.chaining, "her own press of Assume ended it")
    }
}
