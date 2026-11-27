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
import kotlin.test.assertTrue

/**
 * TRN-168, TRN-057, HIS-058 (owner problem report UJP7DE, 2026-11-26): End turn passes the player's priority until the turn ends, but
 * only over what the turn was when they pressed it: once the other player casts a spell or activates an ability in it (CR
 * 733.2b, 733.2c: they shortened the shortcut there), the End turn is over and the player answers it themselves. Before, the
 * device passed their priority over the other player's spell at their end step, so they could never respond to it, even
 * after the other player took the spell back and cast it again for them. Owner 2026-11-27: anything the other player does but
 * pass at their priority ends it, whoever's turn it is: a land played and an attack too.
 */
class EndTurnInterruptedTest {
    private val glimmer: String by lazy { arcanum.engine.cards.Cards.invent("Idle Glimmer", "{0}", "Instant", "Draw a card.") }

    /** Both decks Forests and a free instant, so either player can cast at the other's end step. */
    private fun sim() = DeviceSim(EngineGames(3, (List(10) { "Forest" } + List(30) { glimmer }).let { it to it }))

    private fun hand(model: GameModel, player: PlayerId, name: String): ObjectId =
        model.engine.player(player).hand.first { model.engine.obj(it)?.definition?.faces?.first()?.name == name }

    private fun answering(p: PlayerId): (GameModel) -> Input? = { m ->
        m.engine.pending?.decision?.takeIf { it.player == p && it !is Decision.Random }?.let { open ->
            Engine.options(m.engine, p, EnginePreferences.of(m.engine)).filterIsInstance<Input.Answer>().first { it.decision == open.id }
        }
    }

    /** [p] plays back to the head, confirming each assumption their playback stops at. */
    private fun DeviceSim.catchUp(p: PlayerId) {
        val d = device(p)
        d.play()
        while (d.session.behind) {
            d.playToStop()
            (d.status as? GameDevice.Status.Stopped)?.let { d.act(it.assumed) }
        }
        settle()
    }

    /** Both keep, live; Alice at her first main phase holding priority. */
    private fun DeviceSim.aliceAtMain() {
        start(alice, ResponseMode.Normal, alwaysAssume = false)
        for (p in listOf(alice, bob)) { device(p).act(keep(p)); settle() }
        catchUp(alice)
        catchUp(bob)
        val s = device(alice).model.engine
        assertTrue(s.turn.active == alice && s.turn.step == TurnStep.PrecombatMain && s.priority.holder == alice, "${s.turn}")
    }

    /** Bob, at his playback's stop at Alice's end step (her device assumed his pass there), casts his Glimmer instead. */
    private fun DeviceSim.bobCastsAtHerEndStep() {
        val bobs = device(bob)
        bobs.play()
        while (true) {
            bobs.playToStop()
            val s = bobs.model.engine
            if (s.turn.active == alice && s.turn.step == TurnStep.End && s.priority.holder == bob) break
            val stop = assertIs<GameDevice.Status.Stopped>(bobs.status, "Bob stops at Alice's end step: ${s.turn} ${bobs.status}")
            bobs.act(stop.assumed)
        }
        assertIs<GameDevice.Acted.Sent>(bobs.act(Input.Cast(bob, hand(bobs.model, bob, glimmer)), follow = answering(bob)))
        settle()
    }

    // TRN-168, TRN-057, HIS-058, CR 733.2b, CR 733.2c (report UJP7DE): Alice ends her turn; Bob casts an instant at her end step: her
    // device stops there with his spell on the Stack and her priority (her move, "Resolve" on her main button), never passing
    // it for her; once she lets it resolve, her turn goes on by her response mode as without an End turn
    @Test
    fun theOtherPlayersSpellEndsYourEndTurn() {
        val sim = sim()
        sim.aliceAtMain()
        val alice = sim.alice; val bob = sim.bob
        val alices = sim.device(alice)
        alices.endTurn(sim.now)
        sim.settle()
        sim.bobCastsAtHerEndStep()
        val s = alices.model.engine
        assertEquals(TurnStep.End to alice, s.turn.step to s.turn.active, "still her end step: ${s.turn}")
        assertEquals(1, s.stack.size, "Bob's Glimmer on the Stack")
        assertEquals(bob, s.stackItems[s.stack.last()]?.controller ?: s.obj(s.stack.last())?.controller)
        assertEquals(alice, s.priority.holder, "her priority, not passed for her")
        assertEquals(GameDevice.Status.YourMove, alices.status)
        assertTrue(sim.server.line().none { e -> e.author == alice.value && e.assumed == null &&
            sim.server.stateAt(e.parentId).engine.stack.isNotEmpty() }, "no pass of hers over his spell on the server")
        // She lets it resolve: her End turn is over and her mode passes her own end step (no pause of hers); the game then
        // waits for Bob there (after his own spell his mode passes nothing for him in this step, STK-213)
        assertIs<GameDevice.Acted.Sent>(alices.act(Input.PassPriority(alice)))
        sim.settle()
        val after = alices.model.engine
        assertTrue(after.stack.isEmpty() && after.turn.step == TurnStep.End && after.priority.holder == bob, "${after.turn}, ${after.priority}")
        assertIs<GameDevice.Status.Waiting>(alices.status)
    }

    private val hound: String by lazy { arcanum.engine.cards.Cards.invent("Swift Hound", "{0}", "Creature — Dog", "Haste", "2/2") }

    /** Alice's deck Forests and Glimmers, Bob's Forests and hasty Hounds; Bob starts, Alice on Control (she stops at every
     * point of hers), both live at Bob's first main phase. */
    private fun atBobsMain(): DeviceSim {
        val sim = DeviceSim(EngineGames(3, (List(10) { "Forest" } + List(30) { glimmer }) to (List(10) { "Forest" } + List(30) { hound })))
        sim.start(sim.bob, alwaysAssume = false)
        sim.settle()
        for (p in listOf(sim.bob, sim.alice)) { assertIs<GameDevice.Acted.Sent>(sim.device(p).act(sim.keep(p))); sim.settle() }
        sim.device(sim.alice).settings(PlaySettings(mode = ResponseMode.FullControl))
        sim.settle()
        val s = sim.server.state().engine
        assertTrue(s.turn.active == sim.bob && s.turn.step == TurnStep.PrecombatMain && s.priority.holder == sim.bob && s.stack.isEmpty(), "${s.turn}")
        return sim
    }

    /** Where [p]'s device is: the turn's player, its step and who holds priority. */
    private fun DeviceSim.at(p: PlayerId) = device(p).model.engine.let { Triple(it.turn.active, it.turn.step, it.priority.holder) }

    /** Whether [d]'s player answers now: their move at the head, or their playback stopped at the other player's assumption
     * of their answer there (HIS-095). */
    private fun answers(d: GameDevice) = d.status == GameDevice.Status.YourMove || d.status is GameDevice.Status.Stopped

    // TRN-168, TRN-057 (owner 2026-11-27): Alice ends the turn in Bob's turn; Bob plays a land, a special action that isn't a pass:
    // her End turn is over, so when Bob then passes in his main phase her device stops for her there (Control), not passing on
    @Test
    fun theOtherPlayersLandEndsYourEndTurn() {
        val sim = atBobsMain()
        val alice = sim.alice; val bob = sim.bob
        val alices = sim.device(alice); val bobs = sim.device(bob)
        val said = mutableListOf<String>()
        alices.endTurnSaid = { said += it }
        alices.endTurn(sim.now)
        sim.settle()
        assertIs<GameDevice.Acted.Sent>(bobs.act(Input.PlayLand(bob, hand(bobs.model, bob, "Forest"))))
        sim.settle()
        assertTrue(said.any { "ends: the other player played the land" in it }, "her log says why: $said")
        assertIs<GameDevice.Acted.Sent>(bobs.act(Input.PassPriority(bob)))
        sim.settle()
        assertEquals(Triple(bob, TurnStep.PrecombatMain, alice), sim.at(alice), "her priority in Bob's main phase, not passed for her")
        assertTrue(answers(alices), "hers to answer: ${alices.status}")
    }

    // TRN-168, TRN-057 (owner 2026-11-27): Alice ends the turn in Bob's turn, after his hasty Hound resolved; Bob attacks her with it:
    // her End turn is over. Her device stops for her priority after the attack (Control), she blocks with nothing, and then gets
    // her priority in the declare blockers step as usual, nothing passed for her
    @Test
    fun theOtherPlayersAttackEndsYourEndTurn() {
        val sim = atBobsMain()
        val alice = sim.alice; val bob = sim.bob
        val alices = sim.device(alice); val bobs = sim.device(bob)
        assertIs<GameDevice.Acted.Sent>(bobs.act(Input.Cast(bob, hand(bobs.model, bob, hound)), follow = answering(bob)))
        sim.settle()
        assertEquals(1, alices.model.engine.stack.size, "the Hound on the Stack")
        assertEquals(GameDevice.Status.YourMove, alices.status)
        val said = mutableListOf<String>(); alices.endTurnSaid = { said += it }
        // Her End turn passes over the Hound: it began after his cast
        alices.endTurn(sim.now)
        sim.settle()
        assertTrue(bobs.model.engine.battlefield.any { bobs.model.engine.obj(it)?.definition?.faces?.first()?.name == hound }, "the Hound resolved")
        if (sim.at(bob) == Triple(bob, TurnStep.PrecombatMain, bob)) { assertIs<GameDevice.Acted.Sent>(bobs.act(Input.PassPriority(bob))); sim.settle() }
        val beginning = sim.at(bob)
        // HIS-058 (fixed along the way): before his attack her End turn passes for her, so her playback never stops at his
        // assumptions of her passes (on Control it stopped at each)
        assertTrue(alices.status !is GameDevice.Status.Stopped, "nothing for her to answer before his attack: ${alices.status} $said")
        if (beginning == Triple(bob, TurnStep.BeginningOfCombat, bob)) { assertIs<GameDevice.Acted.Sent>(bobs.act(Input.PassPriority(bob))); sim.settle() }
        val s = bobs.model.engine
        assertEquals(TurnStep.DeclareAttackers, s.turn.step, "${s.turn} ${s.priority}")
        val dog = s.battlefield.first { s.obj(it)?.definition?.faces?.first()?.name == hound }
        assertIs<GameDevice.Acted.Sent>(bobs.act(Input.DeclareAttackers(bob, listOf(arcanum.engine.input.Attack(dog,
            arcanum.engine.state.TargetRef.Player(alice))))))
        sim.settle()
        if (sim.at(bob) == Triple(bob, TurnStep.DeclareAttackers, bob)) { assertIs<GameDevice.Acted.Sent>(bobs.act(Input.PassPriority(bob))); sim.settle() }
        assertEquals(Triple(bob, TurnStep.DeclareAttackers, alice), sim.at(alice), "her priority after his attack, not passed for her: $said")
        assertTrue(answers(alices), "hers to answer: ${alices.status}")
        // Her answers: sent, or confirming Bob's assumption of them
        fun answered(acted: GameDevice.Acted) = assertTrue(acted is GameDevice.Acted.Sent || acted is GameDevice.Acted.Confirmed, "$acted")
        answered(alices.act(Input.PassPriority(alice)))
        sim.settle()
        // Without creatures she has no blocks to declare (the engine declares none for her)
        val blocks = alices.model.engine.combat
        assertTrue(blocks?.blocksDecided == true && blocks.blockers.isEmpty(), "$blocks")
        if (sim.at(bob) == Triple(bob, TurnStep.DeclareBlockers, bob)) { assertIs<GameDevice.Acted.Sent>(bobs.act(Input.PassPriority(bob))); sim.settle() }
        assertEquals(Triple(bob, TurnStep.DeclareBlockers, alice), sim.at(alice), "her priority after the blocks, as usual")
        assertTrue(answers(alices), "hers to answer: ${alices.status}")
    }

    // TRN-168, CR 733.2c (report UJP7DE): Bob takes his spell back (an undo request Alice accepts) to cast it again so she can
    // respond: her End turn ended with his first cast, so her device stops for his second as well
    @Test
    fun theOtherPlayersSpellCastAgainAfterTheirUndoWaitsForYouToo() {
        val sim = sim()
        sim.aliceAtMain()
        val alice = sim.alice; val bob = sim.bob
        val alices = sim.device(alice)
        alices.endTurn(sim.now)
        sim.settle()
        sim.bobCastsAtHerEndStep()
        val bobs = sim.device(bob)
        val cast = sim.server.line().last { e -> e.author == bob.value && e.engine?.steps?.any { it.input is Input.Cast } == true }
        val undo = bobs.undo()
        if (undo is GameDevice.Undo.Request) {
            bobs.requestUndo(undo.target, "")
            sim.settle()
            alices.answerRequest(true)
        }
        sim.settle()
        assertTrue(sim.server.line().none { it.id == cast.id }, "his cast taken back")
        // Her device assumes his pass at her end step again (HIS-096); he casts it again there instead
        sim.bobCastsAtHerEndStep()
        val s = alices.model.engine
        assertEquals(1, s.stack.size, "Bob's Glimmer on the Stack again")
        assertEquals(alice, s.priority.holder, "her priority, not passed for her")
        assertEquals(GameDevice.Status.YourMove, alices.status)
    }
}
