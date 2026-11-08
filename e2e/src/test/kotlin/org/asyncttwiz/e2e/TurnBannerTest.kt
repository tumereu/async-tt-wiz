package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasTestTag
import org.asyncttwiz.android.TURN_BANNER_TAG
import org.junit.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner, 2026-11-08: "I keep missing that my turn starts": at a turn change the screen's centre says whose turn it is for a
 * moment, live and in catch-up playback, never in timeline mode or on opening a game.
 */
class TurnBannerTest : E2e() {
    private fun E2ePlayer.banners(text: String) = shownTexts.count { it == text }

    /** This player's land for the turn (else a turn's draw makes them discard at its end, which waits for them). */
    private fun E2ePlayer.playsAMountain() {
        val before = field("Mountain").size
        playFromHand("Mountain", this@TurnBannerTest)
        waitUntil("$name's Mountain") { field("Mountain").size == before + 1 && inOwnMain() }
    }

    // TRN-074: the first turn, then an End turn: live, the name on one table and "Your turn" on the other;
    // gone by itself; Alice in timeline mode gets none for Bob's turn beginning; Bob away gets his in the catch-up's playback;
    // a game opened anew shows none
    @Test fun whoseTurnShowsLiveAndInPlaybackNeverInTimelineModeOrOnOpening() {
        startGame(deal = { a, b -> hand(a).count { it == "Mountain" } >= 2 && "Mountain" in hand(b) })
        bothOpen()
        bothKeep(bobAway = false)
        waitUntil("Alice's first turn told") { alice.told("Your turn") }
        // No touches taken: it goes by itself
        waitUntil("the banners gone") { !alice.shows(hasTestTag(TURN_BANNER_TAG)) && !bob.shows(hasTestTag(TURN_BANNER_TAG)) }

        // Live: her End turn tells both
        val hers = alice.banners("Your turn")
        alice.playsAMountain()
        alice.hold("To combat")
        waitUntil("Bob's turn told on both tables", along = listOf()) { alice.told("${bob.name}'s turn") && bob.told("Your turn") }
        assertEquals(hers, alice.banners("Your turn"))
        waitUntil("Bob's Main 1") { bob.inOwnMain() }

        // Alice in timeline mode: Bob's End turn brings her turn; no banner, then or when she leaves
        bob.playsAMountain()
        alice.openTimeline(this)
        waitUntil("Alice in timeline mode") { alice.showsTimelineLook() }
        bob.hold("To combat")
        waitUntil("Alice's turn on the head of her phone") { alice.engine { device.session.head } == bob.engine { device.session.head } && bob.state.turn.active == alice.me }
        run(skip = Duration.ofSeconds(1))
        assertEquals(hers, alice.banners("Your turn"), "none in timeline mode")
        assertTrue(!alice.shows(hasTestTag(TURN_BANNER_TAG)))
        alice.tap("Exit timeline")
        // Out of timeline mode her playback goes on (her answer at Bob's end step): the turn change is shown to her only now
        waitUntil("Alice live in her Main 1", along = listOf(bob)) {
            if (alice.status is org.asyncttwiz.protocol.game.GameDevice.Status.Stopped) alice.once { alice.stepArrow()?.let(alice::tap) }
            alice.inOwnMain()
        }
        waitUntil("her turn told once shown") { alice.banners("Your turn") == hers + 1 }

        // Bob away: her End turn reaches him in the catch-up's playback
        waitUntil("both settled") { alice.settled(this) && bob.settled(this) }
        away(bob, alice)
        val bobs = bob.banners("Your turn")
        alice.playsAMountain()
        alice.hold("To combat")
        waitUntil("Alice's turn over") { alice.state.turn.active == bob.me }
        bob.back()
        waitUntil("Bob's centre text") { bob.showsWatch() }
        assertEquals(bobs, bob.banners("Your turn"), "nothing played yet")
        bob.tapToWatch()
        waitUntil("Bob's turn told in his playback") {
            if (bob.status is org.asyncttwiz.protocol.game.GameDevice.Status.Stopped) bob.once { bob.stepArrow()?.let(bob::tap) }
            bob.banners("Your turn") == bobs + 1
        }

        // Opened anew: none
        waitUntil("Bob's Main 1 again") { bob.inOwnMain() }
        waitUntil("both settled") { alice.settled(this) && bob.settled(this) }
        bob = restart(bob)
        bob.openGame(game, this)
        waitUntil("Bob's table") { bob.inOwnMain() }
        run(skip = Duration.ofSeconds(1))
        assertTrue(bob.shownTexts.none { it == "Your turn" }, "none on opening: ${bob.shownTexts}")
    }
}
