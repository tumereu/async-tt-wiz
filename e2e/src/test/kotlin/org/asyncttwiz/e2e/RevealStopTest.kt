package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import org.asyncttwiz.android.REVEAL_OK_TAG
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.PlaySettings
import org.junit.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Owner prod playtest 2026-11-07: "Opponent reveal effects must stop for you and wait for dismissal at least locally,
 * otherwise you don't see the reveals?" Bob starts and casts an Ember Insight ("Reveal the top card of your library and
 * put that card into your hand."): Alice's phone opens the reveal panel with the card, her table stays at that event
 * while Bob plays on (his Mountain, his turn's end), and plays on once she taps OK; the same in catch-up playback.
 */
class RevealStopTest : E2e() {
    private val inPanel = hasAnyAncestor(hasContentDescription("Reveal"))
    private val title get() = "${bob.name} revealed · Ember Insight"

    private fun start() {
        startGame(TestDeck.thirds(), TestDeck.insights(), bobStarts = true, bobSettings = PlaySettings(assume = org.asyncttwiz.protocol.game.AssumeResolves.Always),
            deal = { _, b -> "Ember Insight" in hand(b) && "Mountain" in hand(b) })
        bothOpen()
        alice.tap("Keep")
        bob.tap("Keep")
        waitUntil("Bob's Main 1", along = listOf(alice)) { bob.inOwnMain() }
    }

    /** The card Bob's Insight revealed, by name, from the server's line. */
    private fun revealedName(): String {
        val reveal = assertNotNull(alice.shown.reveal)
        return bob.engine { device.model.engine }.let { s -> s.objects.values.first { it.card == reveal.cards.single() }.definition!!.faces.first().name }
    }

    /** Bob plays a Mountain and ends his turn, all of it reaching the server; Alice's table stays where it is meanwhile. */
    private fun bobPlaysOn() {
        bob.playFromHand("Mountain", this)
        waitUntil("Bob's Mountain on his battlefield") { bob.field("Mountain").isNotEmpty() && bob.inOwnMain() }
        bob.hold("To combat")
        waitUntil("Bob's turn over, all of it on the server") {
            bob.state.turn.active == alice.me && bob.engine { device.session.pending.isEmpty() }
        }
    }

    // HIS-110, STK-044, TBL-104 (owner problem, prod playtest 2026-11-07): live, Alice's phone shows Bob's reveal in its
    // panel and her table doesn't move past that event until she taps OK, while Bob's phone plays on unblocked (a Mountain,
    // the end of his turn, his phone assuming her passes); after OK her table plays on to his Mountain and her own turn.
    // Bob's own reveal opens nothing on his phone
    @Test fun aLiveRevealWaitsForAlicesOkWhileBobPlaysOn() {
        start()
        bob.playFromHand("Ember Insight", this)
        waitUntil("the reveal panel on Alice's phone") { alice.showsText(title) && alice.shown.reveal != null }
        val name = revealedName()
        assertTrue(alice.shows(hasContentDescription(name) and inPanel), "the revealed card in the panel: ${alice.screen()}")
        assertTrue(alice.shows(hasTestTag(REVEAL_OK_TAG)))
        val at = alice.shown.tip
        assertEquals(at, alice.shown.reveal!!.event, "her table at the reveal's event")
        assertFalse(bob.shows(hasTestTag(REVEAL_OK_TAG)), "no panel for Bob's own reveal")
        bobPlaysOn()
        repeat(3) { run(skip = Duration.ofSeconds(2)) }
        // SYS-077: when her phone passed on the Insight itself (her own pass resolving it, the panel on her pending event)
        // and Bob's assumption of that pass reached the server first, his same event took her event's place: the same board
        val held = alice.engine { device.adoptedAs(at) } ?: at
        assertEquals(held, alice.shown.tip, "Alice's table still at the reveal")
        assertEquals(held, alice.shown.reveal?.event, "the panel still at the reveal")
        assertTrue(alice.shown.behind, "Bob's later events wait on her phone")
        assertFalse(alice.showsOnBattlefield("Mountain", bob), "his Mountain not shown to her yet")
        assertTrue(alice.showsText(title))
        alice.tagged(REVEAL_OK_TAG).performClick()
        waitUntil("Alice's table playing on to her own turn", along = listOf(alice)) {
            !alice.showsText(title) && alice.showsOnBattlefield("Mountain", bob) && alice.state.turn.active == alice.me
        }
    }

    // HIS-110, HIS-093: in catch-up playback Bob's reveal is a stop: Alice, back after he played his turn, watches it; her
    // playback stops right after the reveal with its panel, stays there, and goes on to her own turn after OK
    @Test fun aRevealStopsCatchUpPlaybackUntilOk() {
        start()
        away(alice, bob)
        bob.playFromHand("Ember Insight", this)
        waitUntil("the Insight resolved on Bob's table") {
            bob.state.player(bob.me).graveyard.isNotEmpty() && bob.state.stack.isEmpty() && bob.inOwnMain()
        }
        bobPlaysOn()
        alice.back()
        // She watches as a player does: the centre text's tap, and at a stop of hers (HIS-095) the main button
        waitUntil("Alice's playback stopped at the reveal") {
            if (alice.shown.reveal == null) when (alice.status) {
                GameDevice.Status.CatchUp -> alice.once { alice.tapToWatch() }
                is GameDevice.Status.Stopped -> alice.once { alice.stepArrow()?.let(alice::tap) }
                else -> {}
            }
            alice.showsText(title) && alice.shown.reveal != null
        }
        assertIs<GameDevice.Status.Playing>(alice.status, "playback held at the reveal")
        val name = revealedName()
        assertTrue(alice.shows(hasContentDescription(name) and inPanel), "the revealed card in the panel: ${alice.screen()}")
        val at = alice.shown.tip
        repeat(3) { run(skip = Duration.ofSeconds(2)) }
        assertEquals(at, alice.shown.tip, "her playback waits at the reveal")
        assertTrue(alice.shown.behind)
        assertFalse(alice.showsOnBattlefield("Mountain", bob))
        alice.tagged(REVEAL_OK_TAG).performClick()
        waitUntil("Alice's playback going on to her own turn", along = listOf(alice)) {
            !alice.showsText(title) && alice.showsOnBattlefield("Mountain", bob) && alice.state.turn.active == alice.me
        }
        assertTrue(alice.leaks.leaks.isEmpty() && bob.leaks.leaks.isEmpty())
    }
}
