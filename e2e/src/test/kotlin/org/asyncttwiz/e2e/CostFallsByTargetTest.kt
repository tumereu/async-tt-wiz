package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performClick
import org.asyncttwiz.android.AppLog
import org.asyncttwiz.android.ProblemReportSender
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Problem report KX8GYS (owner, prod): a spell that costs less on a tapped creature (Banish from Edoras, four lands in play,
 * a tapped creature on the other side) couldn't be cast: its card didn't count as playable, since the check took the
 * cost before the target was chosen. And the report's log didn't say which cards were in play: LOB-089's plain game log.
 */
class CostFallsByTargetTest : E2e() {
    private val aliceDeck = TestDeck("Banishings", listOf(TestCards.mountain to 15, TestCards.banishing to 10, TestCards.pup to 10, TestCards.drowsy to 10, TestCards.glimmer to 15))

    private fun start() {
        startGame(aliceDeck, TestDeck.glimmers(), deal = { a, _ -> hand(a).let { h ->
            "Mountain" in h && "Ember Banishing" in h && "Ash Pup" in h && "Drowsy Pup" in h && "Idle Glimmer" in h } })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield", along = listOf(bob)) { alice.field("Mountain").size == 1 && alice.inOwnMain() }
        for (creature in listOf("Ash Pup", "Drowsy Pup")) {
            alice.playFromHand(creature, this)
            waitUntil("$creature on the battlefield", along = listOf(bob)) { alice.field(creature).size == 1 && alice.inOwnMain() }
        }
    }

    /** The app log's game lines of [player]'s seat. */
    private fun gameLog(player: E2ePlayer) = AppLog.text().lines().filter { "I/game: " in it && "seat ${player.me.value.take(8)}" in it }

    // TBL-117, CR 601.2f (KX8GYS): one Mountain, a tapped and an untapped creature: the {3}{R} spell at {R} on the tapped one
    // can be cast, only the tapped creature is offered as its target, and the Mountain pays it
    @Test fun aSpellCheaperOnATappedCreatureIsCastWithTheManaOfTheCheaperCost() {
        start()
        alice.playFromHand("Ember Banishing", this)
        waitUntil("the Banishing's target bar: ${alice.screen()}") { alice.shows(hasContentDescription("Choose targets")) }
        // The untapped creature isn't a target that can be paid for: a tap on it leaves the bar open
        alice.battlefieldCard("Ash Pup").performClick()
        run(skip = java.time.Duration.ofMillis(300))
        assertTrue(alice.shows(hasContentDescription("Choose targets")), "still choosing: ${alice.screen()}")
        assertEquals(1, alice.field("Ash Pup").size, "the untapped creature stays")
        alice.battlefieldCard("Drowsy Pup").performClick()
        waitUntil("the Pup exiled, on the server", along = listOf(bob)) {
            alice.field("Drowsy Pup").isEmpty() && alice.state.exile.any { cardName(alice.state, it) == "Drowsy Pup" } && alice.engine { device.session.pending.isEmpty() }
        }
        assertEquals(1, alice.field("Ash Pup").size, "the other creature stays")
    }

    // LOB-089 (KX8GYS): the log names the cards shown: the board at opening, what is played and drawn, and a report adds the
    // board with what can be done on it, without the other player's hand
    @Test fun theLogSaysWhichCardsWereInHandAndPlayed() {
        start()
        val opening = gameLog(alice).first { "hand [" in it }
        assertTrue("Ember Banishing" in opening && "Idle Glimmer" in opening, "the opening board names the hand: $opening")
        assertTrue("their hand [7 cards]" in opening, "the other player's hand only as a count: $opening")
        assertTrue(gameLog(alice).any { "hand -Mountain" in it && "battlefield +Mountain" in it }, "the land played: ${gameLog(alice)}")
        assertTrue(gameLog(alice).any { "battlefield +Drowsy Pup (tapped)" in it }, "the creature cast, tapped: ${gameLog(alice)}")

        val before = alice.state.player(alice.me).hand.size
        alice.playFromHand("Idle Glimmer", this)
        waitUntil("the Glimmer's card drawn", along = listOf(bob)) {
            alice.state.player(alice.me).library.size < 53 && alice.state.player(alice.me).hand.size == before && alice.inOwnMain()
        }
        assertTrue(gameLog(alice).any { "stack +Idle Glimmer" in it }, "the spell on the Stack: ${gameLog(alice)}")
        assertTrue(gameLog(alice).any { "hand +" in it && "library +52 cards, -53 cards" in it }, "the card drawn: ${gameLog(alice)}")

        var sent: String? = null
        val sender = ProblemReportSender(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO), upload = { report, _ -> sent = report.log; "TESTED" })
        sender.send("test")
        waitUntil("the report built") { sent != null }
        assertTrue(sent!!.lines().any { "I/game: " in it && "can do [" in it && "cast Ember Banishing" in it && "hand [" in it },
            "the report ends with the board and what can be done: ${sent!!.takeLast(1500)}")
    }
}
