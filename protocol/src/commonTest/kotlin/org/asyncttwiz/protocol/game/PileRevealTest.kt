package org.asyncttwiz.protocol.game

import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * HIS-114 (owner playtest 2026-11-09): when the other player moves a card out of a public pile (their graveyard, exile)
 * into a hidden zone (a hand, a library), the player's table shows it as a reveal (HIS-110): one panel with the card that
 * left, closed only by their own OK, the same stop. Not for moves to the battlefield or another public pile, nor for the
 * player's own moves.
 */
class PileRevealTest {
    private val study = Cards.invent("Ember Study", "{0}", "Sorcery", "Draw a card.")
    private val recall = Cards.invent("Ember Recall", "{0}", "Sorcery", "Return target card from your graveyard to your hand.")
    private val trinket = Cards.invent("Ember Trinket", "{0}", "Artifact", "Sacrifice this artifact: Draw a card.")
    private val revival = Cards.invent("Ember Revival", "{0}", "Sorcery", "Return target artifact card from your graveyard to the battlefield.")

    /** Bob's deck of [cards], Alice's of Forests; Bob starts and keeps, Alice keeps and plays along live. */
    private fun sim(vararg cards: String): DeviceSim {
        val sim = DeviceSim(EngineGames(3, List(40) { "Forest" } to cards.flatMap { c -> List(40 / cards.size) { c } }))
        sim.start(sim.bob)
        sim.keepWhileAway(sim.bob)
        sim.device(sim.alice).play()
        sim.settle()
        return sim
    }

    private fun DeviceSim.inHand(name: String): ObjectId = device(bob).model.engine.let { s ->
        s.player(bob).hand.first { s.obj(it)?.definition?.faces?.first()?.name == name }
    }

    /** Bob makes [input] (a cast or an activation), answering his own decisions with their first listed answer, and both
     * phones go on. */
    private fun DeviceSim.bobDoes(input: Input) {
        val acted = device(bob).act(input) { m ->
            val d = m.engine.pending?.decision
            if (d == null || d is Decision.Random || d.player != bob) null
            else Engine.options(m.engine, bob).filterIsInstance<Input.Answer>().first { it.decision == d.id }
        }
        assertIs<GameDevice.Acted.Sent>(acted)
        settle()
    }

    private fun DeviceSim.graveyard(player: PlayerId) = device(bob).model.engine.player(player).graveyard

    // HIS-114, HIS-110: Bob's Recall returns his Study from his graveyard to his hand: Alice's phone opens one panel with that
    // card only, named as moved by Bob from his graveyard to his hand with the Recall as its source; her view waits there
    // until her OK. Bob's own phone opens none
    @Test
    fun aCardReturnedFromTheOtherPlayersGraveyardToTheirHandOpensThePanel() {
        val sim = sim(study, recall)
        sim.bobDoes(Input.Cast(sim.bob, sim.inHand(study)))
        val studied = sim.graveyard(sim.bob).single()
        val alices = sim.device(sim.alice)
        assertNull(alices.reveal, "a spell resolving into a graveyard shows nothing")
        sim.bobDoes(Input.Cast(sim.bob, sim.inHand(recall)))
        val panel = assertNotNull(alices.reveal, "the panel on Alice's phone")
        assertEquals(sim.bob, panel.by)
        assertEquals(listOf((studied as ObjectId.Card).card), panel.cards, "only the card that left the pile")
        assertEquals(GameDevice.LeftPile(Zone.Graveyard, Zone.Hand, sim.bob), panel.left)
        assertEquals(recall, panel.sourceName)
        assertEquals(panel.event, alices.session.localHead, "her table at the move")
        assertNull(sim.device(sim.bob).reveal, "his own move opens nothing of his")
        // Bob plays on; her view waits for her OK
        sim.device(sim.bob).endTurn(sim.now)
        sim.settle()
        assertEquals(panel.event, alices.session.localHead, "her view still at the move")
        assertTrue(alices.session.behind)
        assertTrue(alices.dismissReveal())
        sim.settle()
        assertNull(alices.reveal)
        assertTrue(alices.session.localHead != panel.event, "on past it after her OK")
    }

    // HIS-114: a card moved from the graveyard to the battlefield, or a permanent sacrificed into it, is no move into a hidden
    // zone: no panel
    @Test
    fun aCardReturnedToTheBattlefieldOpensNoPanel() {
        val sim = sim(trinket, revival)
        sim.bobDoes(Input.Cast(sim.bob, sim.inHand(trinket)))
        val s = sim.device(sim.bob).model.engine
        val sacrifice = Engine.options(s, sim.bob).filterIsInstance<Input.Activate>().first()
        sim.bobDoes(sacrifice)
        assertEquals(1, sim.graveyard(sim.bob).size, "the Trinket in his graveyard")
        sim.bobDoes(Input.Cast(sim.bob, sim.inHand(revival)))
        assertTrue(sim.device(sim.bob).model.engine.battlefield.isNotEmpty(), "the Trinket back")
        assertNull(sim.device(sim.alice).reveal, "no panel for a move to the battlefield")
    }
}
