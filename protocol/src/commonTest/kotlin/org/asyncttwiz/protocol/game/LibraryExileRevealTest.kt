package org.asyncttwiz.protocol.game

import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.input.Input
import arcanum.engine.state.ObjectId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * HIS-233 (owner 2026-11-19, N18, problem report MDLPM7): cards exiled face up from a library show to every player as a reveal
 * (HIS-110), the player who exiled them too: one panel per library with those cards, closed only by each player's own OK, the
 * same stop. Cards also revealed by the same event show once, in its reveal.
 */
class LibraryExileRevealTest {
    private val delve = Cards.invent("Ember Delve", "{0}", "Sorcery",
        "Exile the top two cards of your library. Until the end of your next turn, you may play those cards.")

    // HIS-233, HIS-110, HIS-154: Bob's Ember Delve exiles the top two cards of his library: Alice's phone and his own each open one
    // panel with those two cards, as exiled by Bob from his library with the Delve as its source; each closes it with their own OK
    @Test
    fun cardsExiledFromALibraryOpenTheRevealPanelForBothPlayers() {
        val sim = DeviceSim(EngineGames(3, List(40) { "Forest" } to List(40) { delve }))
        val alice = sim.alice; val bob = sim.bob
        sim.start(bob)
        sim.keepWhileAway(bob)
        sim.device(alice).play()
        sim.settle()
        val bobs = sim.device(bob)
        val top = bobs.model.engine.player(bob).library.take(2).map { (it as ObjectId.Card).card }
        val card = bobs.model.engine.player(bob).hand.first()
        kotlin.test.assertIs<GameDevice.Acted.Sent>(bobs.act(Input.Cast(bob, card)))
        sim.settle()
        assertEquals(2, bobs.model.engine.objects.values.count { it.zone == Zone.Exile && it.owner == bob }, "two cards exiled")
        for (p in listOf(alice, bob)) {
            val panel = assertNotNull(sim.device(p).reveal, "the panel on ${p.value}'s phone")
            assertEquals(bob, panel.by)
            assertEquals(GameDevice.LibraryExile(bob), panel.exiled)
            assertEquals(top.toSet(), panel.cards.toSet(), "the two cards from the top of his library")
            assertEquals(delve, panel.sourceName)
            assertNull(panel.left)
        }
        assertTrue(sim.device(alice).dismissReveal())
        assertNull(sim.device(alice).reveal)
        assertNotNull(bobs.reveal, "Bob's own panel stays until his OK")
        assertTrue(bobs.dismissReveal())
        assertNull(bobs.reveal)
    }

    // HIS-234: the event's text adds the cards it exiled from a library, named for both players
    @Test
    fun theEventTextNamesTheCardsExiledFromALibrary() {
        val sim = DeviceSim(EngineGames(3, List(40) { "Forest" } to List(40) { delve }))
        val alice = sim.alice; val bob = sim.bob
        sim.start(bob)
        sim.keepWhileAway(bob)
        sim.settle(only = listOf(bob))
        val bobs = sim.device(bob)
        kotlin.test.assertIs<GameDevice.Acted.Sent>(bobs.act(Input.Cast(bob, bobs.model.engine.player(bob).hand.first())))
        sim.settle(only = listOf(bob))
        val event = sim.server.line().last { e -> e.engine?.changes.orEmpty().any { c ->
            c is arcanum.engine.change.Change.ZoneChange && c.moves.any { it.from == Zone.Library && it.to == Zone.Exile } } }
        val names = mapOf(alice.value to "Alice", bob.value to "Bob")
        assertEquals(listOf("Exiled from Bob's library: Ember Delve, Ember Delve"), GameDevice.libraryExileTexts(event, alice.value, names))
        assertEquals(listOf("Exiled from your library: Ember Delve, Ember Delve"), GameDevice.libraryExileTexts(event, bob.value, names))
    }
}
