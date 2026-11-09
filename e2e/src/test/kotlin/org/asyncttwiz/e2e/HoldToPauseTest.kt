package org.asyncttwiz.e2e

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.hasTestTag
import androidx.test.core.app.ApplicationProvider
import org.asyncttwiz.android.HistoryPlaybackPreferences
import org.asyncttwiz.android.PLAY_SHOW_TAG
import org.asyncttwiz.android.VIEW_PAUSED_TAG
import org.asyncttwiz.protocol.game.AssumeResolves
import org.asyncttwiz.protocol.game.PlaySettings
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Owner, 2026-11-08: a finger held on the table pauses whatever the view is waiting on. It goes on when lifted, with the time
 * left; only the view waits, never the game. A pause icon shows in a corner meanwhile, and holding a shown card also inspects
 * it. A pan or a drag isn't a pause. Bob casts Ember Glints with his phone assuming Alice's pass, so his phone goes on.
 */
class HoldToPauseTest : E2e() {
    private val glints = TestDeck("Glints", listOf(TestCards.mountain to 20, TestCards.glint to 20))

    private fun start() {
        startGame(TestDeck.thirds(), glints, bobStarts = true, bobSettings = PlaySettings(assume = AssumeResolves.Always),
            deal = { _, b -> hand(b).count { it == "Ember Glint" } >= 2 })
        HistoryPlaybackPreferences(ApplicationProvider.getApplicationContext<Context>(), alice.id).showPlaysMillis =
            HistoryPlaybackPreferences.MAX_SHOW_PLAYS_MILLIS
        bothOpen()
        alice.tap("Keep")
        bob.tap("Keep")
        waitUntil("Bob's Main 1", along = listOf(alice)) { bob.inOwnMain() }
    }

    private fun bobCastsAGlint() {
        bob.playFromHand("Ember Glint", this)
        waitUntil("the Glint shown on Alice's phone") { alice.shown.show != null && alice.shows(hasTestTag(PLAY_SHOW_TAG)) }
    }

    /** A point of the battlefield clear of the play shown in the board's middle and its line. */
    private val besideTheShow: TouchInjectionScope.() -> Offset = { Offset(width * .08f, height * .6f) }

    private fun lifeOfBob(player: E2ePlayer) = player.state.player(bob.me).life

    // HIS-112, HIS-021: held on the table a play shown stays however long the finger does, the icon in the corner and Alice's
    // table at the cast, while Bob's phone goes on past it; lifted, it ends by itself and her table plays on
    @Test fun aFingerHeldOnTheTablePausesAPlayShown() {
        start()
        val life = lifeOfBob(bob)
        bobCastsAGlint()
        val show = assertNotNull(alice.shown.show)
        assertFalse(alice.shows(hasTestTag(VIEW_PAUSED_TAG)), "no icon before the hold")
        holding(alice.table(), at = besideTheShow) {
            assertTrue(alice.shows(hasTestTag(VIEW_PAUSED_TAG)), "the pause icon: ${alice.screen()}")
            passWhileHeld(2 * HistoryPlaybackPreferences.MAX_SHOW_PLAYS_MILLIS)
            assertEquals(show, alice.engine { device.show }, "still shown")
            assertEquals(show.event, alice.engine { device.session.tip }, "her phone makes nothing of its own meanwhile")
        }
        // Never the game: Bob's phone resolved it
        waitUntil("the Glint resolved on Bob's phone") { lifeOfBob(bob) == life + 2 && bob.inOwnMain() }
        waitUntil("the show over by itself once lifted and her table past the Glint") {
            alice.shown.show == null && lifeOfBob(alice) == life + 2
        }
        waitUntil("the pause icon gone") { !alice.shows(hasTestTag(VIEW_PAUSED_TAG)) }
        assertTrue(alice.leaks.leaks.isEmpty() && bob.leaks.leaks.isEmpty())
    }

    // HIS-112, TBL-104: holding the shown card inspects it and pauses it; lifted, the Inspect closes and it goes on
    @Test fun holdingTheShownCardInspectsItAndPausesIt() {
        start()
        val life = lifeOfBob(bob)
        bobCastsAGlint()
        val show = assertNotNull(alice.shown.show)
        holding(alice.tagged(PLAY_SHOW_TAG)) {
            assertTrue(alice.shows(hasContentDescription("Card inspection content")), "the shown card inspected: ${alice.screen()}")
            passWhileHeld(2 * HistoryPlaybackPreferences.MAX_SHOW_PLAYS_MILLIS)
            assertEquals(show, alice.engine { device.show }, "still shown")
        }
        waitUntil("the Inspect closed") { !alice.shows(hasContentDescription("Card inspection content")) }
        waitUntil("the show over by itself once lifted") { alice.shown.show == null && lifeOfBob(alice) == life + 2 }
    }

    // HIS-021, TBL-011: a pan is no pause: the finger stays down moving the table and the show ends by its time meanwhile
    @Test fun aPanIsNoPause() {
        start()
        bobCastsAGlint()
        val show = assertNotNull(alice.shown.show)
        holding(alice.table(), millis = 2 * HistoryPlaybackPreferences.MAX_SHOW_PLAYS_MILLIS, moveFirst = Offset(0f, 60f), at = besideTheShow) {
            assertFalse(alice.shows(hasTestTag(VIEW_PAUSED_TAG)), "no pause icon: ${alice.screen()}")
            assertNull(alice.engine { device.show?.takeIf { it == show } }, "the show ended under the pan")
        }
    }
}
