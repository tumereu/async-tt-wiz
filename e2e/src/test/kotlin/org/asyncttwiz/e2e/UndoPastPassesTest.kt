package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import arcanum.engine.input.Input
import arcanum.engine.state.PlayerId
import org.asyncttwiz.protocol.game.AssumeResolves
import org.asyncttwiz.protocol.game.GameEvent
import org.asyncttwiz.protocol.game.PlaySettings
import org.asyncttwiz.protocol.game.ResponseMode
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner problem report VXSGKH (2026-11-14: "undoing a pass should never need consent"): the other player's passes after
 * the point (tapped by them, made by their phone, or assumed by yours and confirmed) never make Undo a request; a real
 * input of theirs still does.
 */
class UndoPastPassesTest : E2e() {
    /** The inputs of [e]'s steps. */
    private fun inputs(e: GameEvent): List<Input> = e.engine?.steps.orEmpty().mapNotNull { it.input }

    /** Whether [e] is only a pass (or passes). */
    private fun passOnly(e: GameEvent): Boolean = inputs(e).let { it.isNotEmpty() && it.all { i -> i is Input.PassPriority } }

    /** Alice plays a Mountain and casts an Ember Herald with Bob at the table, Bob playing along; returns the Herald and
     * its cast event once it and its token are on her battlefield and everything is on the server. */
    private fun aliceCastsAHeraldWithBobThere(): Pair<String, GameEvent> {
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield", along = listOf(bob)) { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        val herald = atw(alice.hand("Ember Herald").first())
        alice.playFromHand("Ember Herald", this)
        waitUntil("the Herald and its token on Alice's battlefield, all on the server", along = listOf(bob)) {
            herald in alice.field("Ember Herald").map(::atw) && alice.state.battlefield.any { alice.state.obj(it)?.token == true } &&
                alice.inOwnMain() && quiet() && alice.game?.shown?.behind == false &&
                // Alice's phone knows Bob has seen it all (his passes, or hers assumed for him, confirmed)
                alice.engine { device.localHeads[PlayerId(bob.id)] == device.session.head }
        }
        val line = alice.engine { device.session.events }
        val cast = line.first { e -> inputs(e).any { (it as? Input.Cast)?.card?.let(::atw) == herald } }
        return herald to cast
    }

    /** Alice taps Undo: it is free (no Undo dialog ever opens) and both tables and the server go back to just before her
     * cast [cast], the [herald] in her hand. */
    private fun aliceUndoesFreely(herald: String, cast: GameEvent) {
        assertEquals(cast.parentId, alice.shown.undoTarget, "Undo goes back to just before her cast")
        var dialog = false
        everyRun += { if (alice.shows(hasTestTag("undo-scroller"))) dialog = true }
        alice.tap("Undo")
        waitUntil("the server back before her cast") { dialog || serverGame()?.headId == cast.parentId }
        assertFalse(dialog, "no Undo dialog: the undo is free")
        waitUntil("the Herald back in Alice's hand on both tables") {
            listOf(alice, bob).all { p -> where(p.shown.board, herald) == "HAND" } && quiet()
        }
        everyRun.clear()
        assertFalse(dialog, "no Undo dialog: the undo is free")
        assertFalse(alice.refusalSeen, "no refusal: ${alice.shownTexts}")
    }

    // HIS-087, HIS-202, HIS-084 (owner problem report VXSGKH): Alice casts an Ember Herald while Bob is at the table; he lets it and
    // its trigger resolve by tapping his passes himself (Control: his phone passes nothing for him, Alice's assumes
    // nothing). Her Undo is free: no dialog, and both tables go back to before her cast
    @Test fun undoPastTheOtherPlayersTappedPassesIsFree() {
        startGame(TestDeck.thirds(TestCards.herald), TestDeck.thirds(TestCards.herald),
            aliceSettings = PlaySettings(assume = AssumeResolves.Never), bobSettings = PlaySettings(mode = ResponseMode.FullControl),
            deal = { a, _ -> "Mountain" in hand(a) && "Ember Herald" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        val (herald, cast) = aliceCastsAHeraldWithBobThere()
        val after = alice.engine { device.session.after(cast.id).orEmpty() }
        val bobs = after.filter { it.author == bob.id }
        assertTrue(bobs.isNotEmpty() && bobs.all(::passOnly), "Bob's events after her cast are his passes: ${bobs.map(::inputs)}")
        aliceUndoesFreely(herald, cast)
    }

    // HIS-087, HIS-202, HIS-084 (owner problem report VXSGKH): the same with Bob's passes made without his taps (Auto-skip, nothing
    // he could respond with: his phone passes for him, or Alice's assumes it as his mode would), and Bob having seen them:
    // no request either
    @Test fun undoPastTheOtherPlayersAutomaticPassesIsFree() {
        startGame(TestDeck.thirds(TestCards.herald), TestDeck.thirds(),
            aliceSettings = PlaySettings(assume = AssumeResolves.Never), bobSettings = PlaySettings(mode = ResponseMode.AutoSkip),
            deal = { a, _ -> "Mountain" in hand(a) && "Ember Herald" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        val (herald, cast) = aliceCastsAHeraldWithBobThere()
        val after = alice.engine { device.session.after(cast.id).orEmpty() }
        val bobs = after.filter { it.author == bob.id || it.assumed?.input == Input.PassPriority(bob.me) }
        assertTrue(bobs.isNotEmpty() && bobs.all(::passOnly), "Bob's passes after her cast: ${bobs.map(::inputs)}")
        aliceUndoesFreely(herald, cast)
    }

    // HIS-087, HIS-202, HIS-084 (owner problem report VXSGKH): Alice casts an Ember Herald while Bob is away, her phone assuming his
    // passes; Bob comes back and his phone confirms them (he watches it all). Her Undo is still free
    @Test fun undoPastAssumedPassesTheOtherPlayerConfirmedIsFree() {
        val cast = aliceCastsAHeraldWhileBobIsAway(endTurn = false, bobCanRespond = false)
        bob.back()
        val head = alice.engine { device.session.head }
        waitUntil("Bob caught up, Alice's phone knowing it", along = listOf(bob)) {
            bob.game?.shown?.let { !it.behind && it.localHead == head } == true && bob.engine { device.session.pending.isEmpty() } &&
                alice.engine { device.localHeads[PlayerId(bob.id)] } == head
        }
        aliceUndoesFreely(cast.herald, cast.cast)
    }

    // HIS-232, LOB-135, HIS-099, HIS-209 (owner 2026-11-19, N15): Alice, the host, turns "Undo past the other player's passes needs a
    // request" on from Table actions → Game settings; then Bob's own passes over her Ember Herald make her Undo a request: the
    // Undo dialog opens asking him and nothing is taken back without him
    @Test fun undoPastTheOtherPlayersPassesNeedsARequestWithTheGameSetting() {
        startGame(TestDeck.thirds(TestCards.herald), TestDeck.thirds(TestCards.herald),
            aliceSettings = PlaySettings(assume = AssumeResolves.Never), bobSettings = PlaySettings(mode = ResponseMode.FullControl),
            deal = { a, _ -> "Mountain" in hand(a) && "Ember Herald" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.tap("Table actions")
        waitUntil("Alice's table actions") { alice.showsText("Game settings") }
        alice.text("Game settings").performClick()
        waitUntil("the game settings") { alice.shows(hasTestTag("undo-past-passes")) }
        alice.tagged("undo-past-passes").performClick()
        waitUntil("the setting on the server, both phones told") {
            listOf(alice, bob).all { p -> p.engine { device.assumeReveals.undoPastPasses } } &&
                ServerSetup.head(aliceAccount, game.id).summary.assumeReveals.undoPastPasses
        }
        alice.tap("Close")
        run()
        val (_, cast) = aliceCastsAHeraldWithBobThere()
        val after = alice.engine { device.session.after(cast.id).orEmpty() }
        assertTrue(after.any { it.author == bob.id && passOnly(it) }, "Bob's passes after her cast: ${after.map(::inputs)}")
        val head = serverGame()?.headId
        alice.tap("Undo")
        waitUntil("Alice's Undo dialog asking Bob") { alice.shows(hasTestTag("undo-scroller")) && alice.showsText("Ask ${bob.name}") }
        assertEquals(head, serverGame()?.headId, "nothing taken back without Bob")
        alice.text("Cancel").performClick()
        run()
    }

    // HIS-087, HIS-099, HIS-209 (owner problem report VXSGKH): a real input of the other player's after the point still makes Undo
    // a request: Alice plays a Mountain and ends her turn, Bob plays his Mountain; her Undo opens the Undo dialog asking him
    @Test fun undoPastTheOtherPlayersRealInputIsStillARequest() {
        startGame(deal = { a, b -> "Mountain" in hand(a) && "Mountain" in hand(b) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Mountain", this)
        waitUntil("Alice's Mountain", along = listOf(bob)) { alice.field("Mountain").size == 1 && alice.inOwnMain() }
        alice.hold("To combat")
        // Alice's phone stops at Bob's passes assumed for her in his turn: she answers as assumed
        waitUntil("Bob's Mountain on Alice's table, all on the server", along = listOf(alice, bob)) {
            alice.field("Mountain", owner = bob.me).size == 1 && quiet() && alice.game?.shown?.behind == false
        }
        val head = serverGame()?.headId
        alice.tap("Undo")
        waitUntil("Alice's Undo dialog asking Bob") { alice.shows(hasTestTag("undo-scroller")) && alice.showsText("Ask ${bob.name}") }
        assertEquals(head, serverGame()?.headId, "nothing taken back without Bob")
        alice.text("Cancel").performClick()
        run()
    }
}
