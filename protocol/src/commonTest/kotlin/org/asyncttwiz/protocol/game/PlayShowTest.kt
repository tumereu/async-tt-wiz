package org.asyncttwiz.protocol.game

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
 * HIS-112 (owner 2026-11-07, MTG Arena as the reference): the other player's casts, activations and triggers, and the
 * player's own triggers (never their own casts), are shown on the player's table for a moment before they move to the
 * Stack; while one shows only the player's view holds (the later events wait, their device makes none of its own), live
 * and in catch-up playback alike, one after another, after any reveal panel (HIS-110), and gone when taken back.
 */
class PlayShowTest {
    private val warden = Cards.invent("Hearth Warden", "{0}", "Creature — Elemental",
        "Whenever another creature you control enters, you gain 1 life.", "1/1")
    private val muster = Cards.invent("Ember Muster", "{0}", "Sorcery",
        "Reveal the top card of your library and put that card into your hand. Create a 1/1 red Elemental creature token.")

    /** Bob's deck of free Hearth Wardens and Ember Musters, Alice's of Forests. */
    private fun sim() = DeviceSim(EngineGames(3, List(40) { "Forest" } to List(40) { if (it % 2 == 0) warden else muster }))

    private fun hand(model: GameModel, player: PlayerId, name: String): ObjectId =
        model.engine.player(player).hand.first { model.engine.obj(it)?.definition?.faces?.first()?.name == name }

    private fun name(model: GameModel, id: ObjectId?): String? = id?.let { model.engine.obj(it)?.definition?.faces?.first()?.name }

    /** The name of the card [card] (wherever it is now). */
    private fun card(model: GameModel, card: arcanum.engine.state.CardId?): String? =
        model.engine.objects.values.firstOrNull { it.card == card }?.definition?.faces?.first()?.name

    /** Bob starts and keeps; Alice keeps and is away, so his device assumes her passes up to his main phase. */
    private fun DeviceSim.bobToMain() {
        start(bob)
        keepWhileAway(bob)
    }

    /** Alice's table live at the head, both phones showing plays. */
    private fun DeviceSim.aliceLive() {
        device(alice).play()
        settle()
        assertEquals(server.head, device(alice).session.localHead, "Alice live")
        showing()
    }

    private fun DeviceSim.showing() { device(alice).showPlays = true; device(bob).showPlays = true }

    /** Bob casts the hand card [card] (his device passes after it by his mode). */
    private fun DeviceSim.bobCasts(card: String) {
        val bob = bob
        val cast = device(bob).act(Input.Cast(bob, hand(device(bob).model, bob, card))) { m ->
            val d = m.engine.pending?.decision
            if (d == null || d is Decision.Random || d.player != bob) null
            else Engine.options(m.engine, bob).filterIsInstance<Input.Answer>().first()
        }
        assertIs<GameDevice.Acted.Sent>(cast)
    }

    // HIS-112: live, Bob's cast shows on Alice's table at its event, the Warden still on the Stack; the game goes on for
    // Bob (his phone assumes her pass and it resolves on the server) while her view and her device wait; done, her table
    // plays on. Bob's own cast shows nothing on his
    @Test
    fun theOtherPlayersCastHoldsTheViewUntilDone() {
        val sim = sim()
        sim.bobToMain()
        sim.aliceLive()
        sim.bobCasts(warden)
        sim.settle()
        val alices = sim.device(sim.alice)
        val show = assertNotNull(alices.show, "Bob's cast shown on Alice's table")
        assertEquals(GameDevice.PlayKind.Cast, show.kind)
        assertEquals(sim.bob, show.by)
        assertEquals(warden, card(alices.model, show.card))
        assertEquals(show.event, alices.session.localHead, "her table at the cast")
        assertTrue(show.entry in alices.model.engine.stack, "the Warden still on the Stack on her table")
        assertNull(sim.device(sim.bob).show, "his own cast shows nothing")
        assertTrue(sim.server.line().indexOfFirst { it.id == show.event } < sim.server.line().size - 1, "the game went on")
        assertTrue(sim.device(sim.bob).model.engine.stack.isEmpty(), "the Warden resolved on Bob's phone")
        assertTrue(alices.session.behind, "his later events wait on her phone")
        assertNull(alices.step(), "playback waits while it shows")
        alices.advance(sim.now)
        assertEquals(show.event, alices.session.localHead, "her device makes nothing of its own meanwhile")
        assertTrue(alices.session.pending.isEmpty())
        assertTrue(alices.dismissShow(show))
        assertEquals(show.event, alices.showDone)
        sim.settle()
        assertNull(alices.show)
        assertEquals(sim.server.head, alices.session.localHead, "on to the head once done")
        assertTrue(alices.model.engine.battlefield.any { name(alices.model, it) == warden })
    }

    // HIS-112, HIS-110: Bob's Muster with a Warden out: on Alice's table the cast shows, then the Muster's reveal panel
    // comes first, then the Warden's trigger from the same event; on Bob's own table his cast shows nothing, his trigger does
    @Test
    fun revealFirstThenTheTriggerAndYourOwnTriggerToo() {
        val sim = sim()
        sim.bobToMain()
        sim.device(sim.alice).play()
        sim.settle()
        sim.bobCasts(warden)
        sim.settle()
        sim.showing()
        sim.bobCasts(muster)
        sim.settle()
        val alices = sim.device(sim.alice)
        val cast = assertNotNull(alices.show)
        assertEquals(GameDevice.PlayKind.Cast, cast.kind)
        assertEquals(muster, card(alices.model, cast.card))
        alices.dismissShow()
        sim.settle()
        val reveal = assertNotNull(alices.reveal, "the Muster's reveal panel")
        assertNull(alices.show, "the reveal comes first")
        val trigger = alices.openShows.single()
        assertEquals(GameDevice.PlayKind.Triggered, trigger.kind)
        assertEquals(sim.bob, trigger.by)
        assertEquals(warden, card(alices.model, trigger.card))
        assertEquals(reveal.event, trigger.event, "in the reveal's event")
        assertNull(alices.step())
        alices.dismissReveal()
        assertEquals(trigger, alices.show, "then the trigger")
        assertNull(alices.step(), "still held")
        alices.dismissShow()
        assertNull(alices.show)
        // Bob's own table: his trigger shows, his casts never did
        val bobs = sim.device(sim.bob)
        assertNotNull(bobs.reveal, "his own reveal first (HIS-110)")
        bobs.dismissReveal()
        val own = bobs.openShows.single()
        assertEquals(GameDevice.PlayKind.Triggered, own.kind)
        assertEquals(warden, card(bobs.model, own.card))
        assertTrue(bobs.dismissShow(own))
        sim.settle()
        assertTrue(alices.model.engine.stack.isEmpty(), "the trigger resolved")
        assertEquals(sim.server.head, alices.session.localHead)
    }

    // HIS-112: two Wardens' triggers going on the Stack at once (Bob orders them, STK-030) show one after another, in
    // order, each holding the view in turn
    @Test
    fun severalShowOneAfterAnother() {
        val sim = sim()
        sim.bobToMain()
        sim.device(sim.alice).play()
        sim.settle()
        repeat(2) { sim.bobCasts(warden); sim.settle() }
        sim.bobCasts(muster)
        sim.settle()
        val bobs = sim.device(sim.bob)
        val order = assertIs<Decision.OrderTriggers>(bobs.model.engine.pending?.decision, "Bob orders his triggers")
        sim.showing()
        assertIs<GameDevice.Acted.Sent>(bobs.act(Engine.options(bobs.model.engine, sim.bob).filterIsInstance<Input.Answer>()
            .first { it.decision == order.id }))
        sim.settle()
        val alices = sim.device(sim.alice)
        // The Muster's reveal held her view before Bob's order: dismissed, his ordered triggers are shown
        alices.dismissReveal()
        sim.settle()
        val shows = alices.openShows
        assertEquals(2, shows.size, "both triggers: $shows")
        assertTrue(shows.all { it.kind == GameDevice.PlayKind.Triggered && card(alices.model, it.card) == warden })
        assertEquals(shows.map { it.entry }, shows.map { it.entry }.sortedBy { alices.model.engine.stack.indexOf(it) },
            "in the order they went on the Stack")
        assertEquals(shows[0], alices.show)
        assertNull(alices.step())
        assertTrue(alices.dismissShow(shows[0]))
        assertEquals(shows[1], alices.show)
        assertTrue(!alices.dismissShow(shows[0]), "the first is done already")
        assertTrue(alices.dismissShow(shows[1]))
        assertNull(alices.show)
    }

    // HIS-112, HIS-021: in catch-up playback Bob's cast shows at its event, holding the later events until done
    @Test
    fun aCastShowsInCatchUpPlayback() {
        val sim = sim()
        sim.bobToMain()
        sim.bobCasts(warden)
        sim.settle(only = listOf(sim.bob))
        sim.device(sim.bob).endTurn(sim.now)
        sim.settle(only = listOf(sim.bob))
        val alices = sim.load(sim.alice)
        alices.showPlays = true
        assertNull(alices.show, "nothing shown yet")
        alices.play()
        alices.playToStop()
        val show = assertNotNull(alices.show, "playback at Bob's cast")
        assertEquals(show.event, alices.session.localHead)
        assertIs<GameDevice.Status.Playing>(alices.status)
        assertNull(alices.step())
        alices.dismissShow()
        alices.playToStop()
        assertNull(alices.show)
        assertEquals(sim.server.head, alices.session.localHead, "on to the head")
    }

    // HIS-112, LOB-082: at 0 s (off) nothing shows and playback plays straight on
    @Test
    fun offShowsNothing() {
        val sim = sim()
        sim.bobToMain()
        sim.bobCasts(warden)
        sim.settle(only = listOf(sim.bob))
        val alices = sim.load(sim.alice)
        alices.play()
        alices.playToStop()
        assertNull(alices.show)
        assertEquals(sim.server.head, alices.session.localHead)
        sim.settle(only = listOf(sim.alice))
        // A game opened again shows nothing of what was shown before
        val again = sim.load(sim.alice)
        again.showPlays = true
        again.playToStop()
        assertNull(again.show, "nothing of what was shown before the load")
    }

    // HIS-112, HIS-099: Bob's cast taken back before Alice's show was done (his undo: free, as it showed him nothing new,
    // or approved by her) ends it
    @Test
    fun aPlayTakenBackEndsItsShow() {
        val sim = sim()
        sim.bobToMain()
        sim.aliceLive()
        val beforeCast = sim.server.head
        sim.bobCasts(warden)
        sim.settle()
        val alices = sim.device(sim.alice)
        assertNotNull(alices.show)
        val bobs = sim.device(sim.bob)
        when (val undo = bobs.undo(beforeCast)) {
            is GameDevice.Undo.Free -> sim.settle()
            is GameDevice.Undo.Request -> {
                bobs.requestUndo(undo.target, "")
                sim.settle()
                alices.answerRequest(accept = true)
                sim.settle()
            }
            else -> error("no undo: $undo")
        }
        assertEquals(beforeCast, alices.session.localHead)
        assertNull(alices.show, "the show ended with its cast")
        assertTrue(alices.openShows.isEmpty())
    }

    // HIS-112: turning the shows off closes the open one
    @Test
    fun turnedOffTheOpenShowCloses() {
        val sim = sim()
        sim.bobToMain()
        sim.aliceLive()
        sim.bobCasts(warden)
        sim.settle()
        val alices = sim.device(sim.alice)
        assertNotNull(alices.show)
        alices.showPlays = false
        assertNull(alices.show)
        sim.settle()
        assertEquals(sim.server.head, alices.session.localHead)
    }
}
