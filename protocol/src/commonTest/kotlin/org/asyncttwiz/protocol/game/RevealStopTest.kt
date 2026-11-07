package org.asyncttwiz.protocol.game

import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.change.Change
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.CardId
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * HIS-110 (owner playtest 2026-11-07): the other player's reveals stop the player's view until dismissed: one panel per
 * reveal, in order, holding the later events like a playback stop (live and in catch-up playback), never for the player's
 * own reveals, and closed when the reveal is taken back. Only the player's view waits: the game goes on for the other
 * player.
 */
class RevealStopTest {
    private val insight = Cards.invent("Ember Insight", "{0}", "Sorcery", "Reveal the top card of your library and put that card into your hand.")

    /** Bob's deck of Islands and Ember Insights (a free reveal), Alice's of Forests. */
    private fun sim() = DeviceSim(EngineGames(3, List(40) { "Forest" } to (List(20) { "Island" } + List(20) { insight })))

    private fun hand(model: GameModel, player: PlayerId, name: String): ObjectId =
        model.engine.player(player).hand.first { model.engine.obj(it)?.definition?.faces?.first()?.name == name }

    /** Bob starts and keeps; Alice keeps and is away, so his device assumes her passes up to his main phase. */
    private fun DeviceSim.bobToMain() {
        start(bob)
        keepWhileAway(bob)
    }

    /** Bob casts an Ember Insight (his device passes after it; Alice's pass is assumed or made, and it resolves). */
    private fun DeviceSim.bobCastsInsight() {
        val bob = bob
        val cast = device(bob).act(Input.Cast(bob, hand(device(bob).model, bob, insight))) { m ->
            val d = m.engine.pending?.decision
            if (d == null || d is Decision.Random || d.player != bob) null
            else Engine.options(m.engine, bob).filterIsInstance<Input.Answer>().first()
        }
        assertIs<GameDevice.Acted.Sent>(cast)
    }

    /** The cards revealed in the server's main-line event [id]. */
    private fun DeviceSim.revealedIn(id: String): List<CardId> = server.line().first { it.id == id }.engine!!.changes
        .mapNotNull { ((it as? Change.Happened)?.event as? arcanum.engine.change.GameEvent.Revealed)?.ids }.flatten()
        .mapNotNull { (it as? ObjectId.Card)?.card }

    // HIS-110, HIS-093: in catch-up playback Bob's reveal is a playback stop: Alice's table stays at its event, with the
    // panel naming Bob, the revealed card and the Insight, the later events held, until she dismisses it; then playback
    // goes on to the head. Bob's own reveal opens nothing on his phone
    @Test
    fun anOpponentsRevealHoldsCatchUpPlaybackUntilDismissed() {
        val sim = sim()
        sim.bobToMain()
        sim.bobCastsInsight()
        sim.settle(only = listOf(sim.bob))
        assertNull(sim.device(sim.bob).reveal, "his own reveal opens nothing")
        // He ends his turn: events after the reveal
        sim.device(sim.bob).endTurn(sim.now)
        sim.settle(only = listOf(sim.bob))
        val alices = sim.load(sim.alice)
        assertNull(alices.reveal, "nothing shown yet")
        alices.play()
        alices.playToStop()
        val reveal = assertNotNull(alices.reveal, "stopped at Bob's reveal")
        assertEquals(sim.bob, reveal.by)
        assertEquals(alices.session.localHead, reveal.event, "her table at the reveal's event")
        assertEquals(sim.revealedIn(reveal.event), reveal.cards)
        assertEquals(1, reveal.cards.size)
        assertEquals(insight, reveal.sourceName)
        assertTrue(alices.session.behind, "the later events are held")
        assertIs<GameDevice.Status.Playing>(alices.status)
        assertNull(alices.step(), "playback waits for the dismissal")
        assertEquals(reveal.event, alices.session.localHead)
        assertTrue(alices.dismissReveal())
        assertNull(alices.reveal)
        alices.playToStop()
        assertEquals(sim.server.head, alices.session.localHead, "on to the head after the dismissal")
    }

    // HIS-110: live, Alice's view waits at Bob's reveal while the game goes on for Bob (his turn ends and his device assumes
    // her passes where her mode passes); her dismissal shows what followed
    @Test
    fun aLiveRevealHoldsTheViewWhileTheGameGoesOn() {
        val sim = sim()
        sim.bobToMain()
        sim.device(sim.alice).play()
        sim.settle()
        assertEquals(sim.server.head, sim.device(sim.alice).session.localHead, "Alice live")
        sim.bobCastsInsight()
        sim.settle()
        val alices = sim.device(sim.alice)
        val reveal = assertNotNull(alices.reveal, "the panel opens live")
        assertEquals(reveal.event, alices.session.localHead)
        val bobs = sim.device(sim.bob)
        assertNull(bobs.reveal)
        bobs.endTurn(sim.now)
        sim.settle()
        assertTrue(sim.server.line().indexOfFirst { it.id == reveal.event } < sim.server.line().size - 1, "the game went on")
        assertEquals(reveal.event, alices.session.localHead, "her view still at the reveal")
        assertEquals(reveal.event, sim.server.heads[sim.alice], "her local head stays there")
        alices.dismissReveal()
        sim.settle()
        assertNull(alices.reveal)
        assertTrue(sim.server.line().indexOfFirst { it.id == alices.session.localHead } > sim.server.line().indexOfFirst { it.id == reveal.event },
            "playback went on past the reveal")
    }

    // HIS-110: several reveals in a row open one panel each, in order
    @Test
    fun severalRevealsOpenOnePanelEachInOrder() {
        val sim = sim()
        sim.bobToMain()
        sim.bobCastsInsight()
        sim.settle(only = listOf(sim.bob))
        sim.bobCastsInsight()
        sim.settle(only = listOf(sim.bob))
        val alices = sim.load(sim.alice)
        alices.play()
        alices.playToStop()
        val first = assertNotNull(alices.reveal)
        alices.playToStop()
        assertEquals(first, alices.reveal, "still the first")
        alices.dismissReveal()
        alices.playToStop()
        val second = assertNotNull(alices.reveal, "the second reveal's panel")
        assertTrue(second.event != first.event)
        assertTrue(second.cards != first.cards)
        alices.dismissReveal()
        alices.playToStop()
        assertNull(alices.reveal)
        assertEquals(sim.server.head, alices.session.localHead)
    }

    // HIS-110, HIS-099: a reveal taken back before Alice dismissed it (Bob's undo of his Insight, which she approves)
    // closes its panel
    @Test
    fun aRevealTakenBackClosesItsPanel() {
        val sim = sim()
        sim.bobToMain()
        sim.device(sim.alice).play()
        sim.settle()
        val beforeCast = sim.server.head
        sim.bobCastsInsight()
        sim.settle()
        val alices = sim.device(sim.alice)
        assertNotNull(alices.reveal)
        val bobs = sim.device(sim.bob)
        val request = assertIs<GameDevice.Undo.Request>(bobs.undo(beforeCast))
        bobs.requestUndo(request.target, "")
        sim.settle()
        alices.answerRequest(accept = true)
        sim.settle()
        assertEquals(beforeCast, alices.session.localHead)
        assertNull(alices.reveal, "the panel closed with its reveal")
    }
}
