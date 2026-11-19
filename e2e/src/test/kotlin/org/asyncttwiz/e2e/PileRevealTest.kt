package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import arcanum.engine.Engine
import arcanum.engine.Host
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.GameState
import arcanum.engine.state.TargetRef
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.PlaySettings
import org.junit.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Owner playtest 2026-11-09: when the other player moves a card from their graveyard or exile to a hidden zone, your table
 * shows it as a reveal does: "Bob returned Storm Spark from their graveyard to their hand", the reveal panel closed only by
 * your own OK. Not for moves to the battlefield. Bob starts with free Ember Trinkets ("Sacrifice this artifact: Draw a
 * card."), Ember Revivals (an artifact card from his graveyard to the battlefield) and Ember Recalls (a card from his
 * graveyard to his hand).
 */
class PileRevealTest : E2e() {
    private val inPanel = hasAnyAncestor(hasContentDescription("Reveal"))
    private val bobs = TestDeck("Recalls", listOf(TestCards.trinket to 14, TestCards.revival to 13, TestCards.recall to 13))

    /** Bob's own [first] input and his answers to what it asks (a target: his Trinket; else the first listed, a payment's
     * suggestion), as his cast panel makes them; his phone sends them. */
    private fun bobDoes(first: (GameState) -> Input) {
        waitUntil("Bob's game settled") { bob.settled(this) && bob.inOwnMain() }
        val s = bob.state
        val copy = s.deepCopy()
        val inputs = mutableListOf(first(s))
        var input = inputs.first()
        for (i in 0 until 10) {
            Engine.perform(copy, input, org.asyncttwiz.protocol.game.EnginePreferences.of(copy)).changes.forEach(copy::apply)
            val d = copy.pending?.decision ?: break
            if (d is Decision.Random || Host.decider(copy, d.player) != bob.me) break
            // A target: the Trinket where it may be
            val answers = Engine.options(copy, bob.me, org.asyncttwiz.protocol.game.EnginePreferences.of(copy)).filterIsInstance<Input.Answer>().filter { it.decision == d.id }
            input = if (d is Decision.ManaPayment) Input.Answer(d.id, Answer.ManaPayment(d.suggestion))
                else answers.firstOrNull { a -> (a.answer as? Answer.Targets)?.chosen.orEmpty()
                    .any { t -> (t as? TargetRef.Object)?.let { cardName(copy, it.id) } == "Ember Trinket" } } ?: answers.first()
            inputs += input
        }
        assertFalse(bob.game!!.send(inputs) is GameDevice.Acted.Refused)
        waitUntil("Bob's action resolved") { bob.settled(this) && bob.state.stack.isEmpty() && bob.inOwnMain() }
    }

    private fun cast(name: String): (GameState) -> Input = { Input.Cast(bob.me, bob.hand(name).first()) }

    /** Bob sacrifices his Trinket: it goes to his graveyard. */
    private fun sacrifice() {
        bobDoes { s -> Engine.options(s, bob.me, org.asyncttwiz.protocol.game.EnginePreferences.of(s)).filterIsInstance<Input.Activate>().first { it.source == bob.field("Ember Trinket").single() } }
        assertTrue(bob.state.player(bob.me).graveyard.any { cardName(bob.state, it) == "Ember Trinket" }, "the Trinket in Bob's graveyard")
    }

    // HIS-233, HIS-234, HIS-110 (owner 2026-11-19, N18, problem report MDLPM7): Bob's Ember Delve exiles the top two cards of his library:
    // both phones open the reveal panel, "Bob exiled 2 cards from their library" on Alice's and "You exiled 2 cards from your library" on
    // his, each closed by its own OK, and the timeline's text names the exiled cards
    @Test fun cardsBobExilesFromHisLibraryShowOnBothTables() {
        startGame(TestDeck.thirds(), TestDeck("Delves", listOf(TestCards.delve to 40)), bobStarts = true,
            bobSettings = PlaySettings(assume = org.asyncttwiz.protocol.game.AssumeResolves.Always), deal = { _, b -> "Ember Delve" in hand(b) })
        bothOpen()
        alice.tap("Keep")
        bob.tap("Keep")
        waitUntil("Bob's Main 1, his game settled") { bob.inOwnMain() && bob.settled(this) }
        // The Delve asks nothing (no target, nothing to pay): its cast alone
        assertFalse(bob.game!!.send(listOf(Input.Cast(bob.me, bob.hand("Ember Delve").first()))) is GameDevice.Acted.Refused)
        waitUntil("the panel on both phones") {
            alice.showsText("${bob.name} exiled 2 cards from their library") && bob.showsText("You exiled 2 cards from your library")
        }
        assertEquals(bob.me, assertNotNull(alice.shown.reveal?.exiled).owner)
        val exiled = bob.state.objects.values.filter { it.zone == arcanum.api.Zone.Exile && it.owner == bob.me }
            .map { cardName(bob.state, it.id) }
        assertEquals(listOf("Ember Delve", "Ember Delve"), exiled)
        assertTrue(alice.showsRevealOk() && bob.showsRevealOk(), "OK on both main buttons")
        val event = bob.engine { device.session.events.last { e -> org.asyncttwiz.protocol.game.GameDevice.libraryExileTexts(e, bob.id, emptyMap()).isNotEmpty() } }
        assertEquals(listOf("Exiled from ${bob.name}'s library: Ember Delve, Ember Delve"),
            org.asyncttwiz.protocol.game.GameDevice.libraryExileTexts(event, alice.id, mapOf(bob.id to bob.name)))
        bob.tap("OK")
        waitUntil("Bob's panel closed, Alice's still open") { bob.shown.reveal == null && alice.shown.reveal != null }
        alice.tap("OK")
        waitUntil("Alice's panel closed") { alice.shown.reveal == null }
    }

    // HIS-114, HIS-157, HIS-110, TRN-063, TRN-114: Bob returns his Trinket from his graveyard to the battlefield (no panel on Alice's phone),
    // sacrifices it again and returns it to his hand: Alice's phone opens the reveal panel, "Bob returned Ember Trinket from
    // their graveyard to their hand", the Trinket only, the Recall as its source, and her table waits there while Bob plays on
    // until her own OK on the main button. Bob's own phone opens no panel for his own move
    @Test fun aCardBobReturnsFromHisGraveyardToHisHandShowsOnAlicesTableUntilHerOk() {
        startGame(TestDeck.thirds(), bobs, bobStarts = true, bobSettings = PlaySettings(assume = org.asyncttwiz.protocol.game.AssumeResolves.Always),
            deal = { _, b -> listOf("Ember Trinket", "Ember Revival", "Ember Recall").all { it in hand(b) } })
        bothOpen()
        alice.tap("Keep")
        bob.tap("Keep")
        // Alice's phone passes by itself; no playing along here: that would tap her OK
        waitUntil("Bob's Main 1") { bob.inOwnMain() }
        var panels = 0
        everyRun += { if (alice.game?.shown?.reveal != null || bob.game?.shown?.reveal != null) panels++ }
        bob.playFromHand("Ember Trinket", this)
        waitUntil("Bob's Trinket") { bob.field("Ember Trinket").isNotEmpty() && bob.settled(this) && bob.inOwnMain() }
        sacrifice()
        bobDoes(cast("Ember Revival"))
        assertEquals(1, bob.field("Ember Trinket").size, "the Trinket back on the battlefield")
        waitUntil("Alice's table shows the Trinket back") { alice.showsOnBattlefield("Ember Trinket", bob) }
        run(skip = Duration.ofSeconds(1))
        assertEquals(0, panels, "no panel for moves to the battlefield or into a graveyard")
        everyRun.clear()
        sacrifice()
        bobDoes(cast("Ember Recall"))
        assertTrue(bob.hand("Ember Trinket").isNotEmpty(), "the Trinket in Bob's hand")
        val title = "${bob.name} returned Ember Trinket from their graveyard to their hand"
        waitUntil("the panel on Alice's phone") { alice.showsText(title) && alice.shown.reveal != null }
        val panel = assertNotNull(alice.shown.reveal)
        assertEquals(GameDevice.LeftPile(arcanum.api.Zone.Graveyard, arcanum.api.Zone.Hand, bob.me), panel.left)
        assertTrue(alice.shows(hasContentDescription("Ember Trinket") and inPanel), "the Trinket in the panel: ${alice.screen()}")
        assertTrue(alice.showsRevealOk(), "its OK on her main button")
        assertEquals(null, bob.shown.reveal, "nothing on Bob's own phone")
        // Bob plays on; her table stays at the move
        val at = alice.shown.tip
        bob.hold(requireNotNull(bob.stepArrow()))
        waitUntil("Bob's turn over, all of it on the server") {
            bob.state.turn.active == alice.me && bob.engine { device.session.pending.isEmpty() }
        }
        repeat(3) { run(skip = Duration.ofSeconds(2)) }
        val held = alice.engine { device.adoptedAs(at) } ?: at
        assertEquals(held, alice.shown.tip, "Alice's table still at the move")
        assertTrue(alice.showsText(title))
        alice.revealOk()
        waitUntil("Alice's table playing on to her own turn") { !alice.showsText(title) && alice.state.turn.active == alice.me }
        assertFalse(alice.showsRevealOk())
        assertTrue(alice.leaks.leaks.isEmpty() && bob.leaks.leaks.isEmpty(), "${alice.leaks.leaks} ${bob.leaks.leaks}")
    }
}
