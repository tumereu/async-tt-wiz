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
 * after the other player took the spell back and cast it again for them.
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
