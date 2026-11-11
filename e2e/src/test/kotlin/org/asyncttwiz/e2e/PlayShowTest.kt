package org.asyncttwiz.e2e

import android.content.Context
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import org.asyncttwiz.android.HistoryPlaybackPreferences
import org.asyncttwiz.android.PLAY_SHOW_TAG
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.PlaySettings
import org.junit.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.asyncttwiz.appui.AppPlatform

/**
 * Owner-approved feature 2026-11-07 (MTG Arena as the reference): when Bob casts a spell while Alice is live, her phone
 * shows it large in the middle of her table for a moment, her table held at the cast meanwhile, and then it moves to the
 * Stack; a double tap ends it at once (owner, 2026-11-08: a single tap does nothing). Bob's phone is never held: his Ember Glint resolves there at once (his phone assumes her
 * pass) while hers still shows it.
 */
class PlayShowTest : E2e() {
    private val glints = TestDeck("Glints", listOf(TestCards.mountain to 20, TestCards.glint to 20))

    private fun start() {
        startGame(TestDeck.thirds(), glints, bobStarts = true, bobSettings = PlaySettings(assume = org.asyncttwiz.protocol.game.AssumeResolves.Always),
            deal = { _, b -> hand(b).count { it == "Ember Glint" } >= 2 })
        // The longest time (3 s): Bob's phone has long resolved the Glint while hers still shows it
        HistoryPlaybackPreferences(AppPlatform.current, alice.id).showPlaysMillis =
            HistoryPlaybackPreferences.MAX_SHOW_PLAYS_MILLIS
        bothOpen()
        alice.tap("Keep")
        bob.tap("Keep")
        waitUntil("Bob's Main 1", along = listOf(alice)) { bob.inOwnMain() }
    }

    private fun bobsLife(player: E2ePlayer) = player.state.player(bob.me).life

    /** Bob casts an Ember Glint; Alice's table shows it, held at its cast; returns the show. */
    private fun bobCastsAGlint(): GameDevice.PlayShow {
        bob.playFromHand("Ember Glint", this)
        waitUntil("the Glint shown on Alice's phone") { alice.shown.show != null && alice.shows(hasTestTag(PLAY_SHOW_TAG)) }
        val show = assertNotNull(alice.shown.show)
        assertEquals(GameDevice.PlayKind.Cast, show.kind)
        assertTrue(alice.shows(hasTestTag(PLAY_SHOW_TAG) and hasContentDescription("${bob.name} cast Ember Glint", substring = true)),
            "the shown card names it: ${alice.screen()}")
        return show
    }

    // HIS-112, HIS-021: live, Bob's Glint shows on Alice's table; Bob's phone shows nothing and resolves it at once, while
    // her table stays at the cast (the Glint on her Stack, his life before it): a single tap on the shown card does nothing,
    // a double tap ends it and it plays on; his second Glint, left alone, ends by itself after its time and her table plays on
    @Test fun bobsSpellShowsOnAlicesTableWhileBobPlaysOn() {
        start()
        // The frames' clock in step with the real one: a show can't end between two looks (E2e.clockInStep)
        clockInStep {
            val life = bobsLife(bob)
            val show = bobCastsAGlint()
            assertFalse(bob.shows(hasTestTag(PLAY_SHOW_TAG)), "nothing shown on Bob's phone for his own cast")
            assertNull(bob.shown.show)
            waitUntil("the Glint resolved on Bob's phone") { bobsLife(bob) == life + 2 && bob.state.stack.isEmpty() && bob.inOwnMain() }
            // His phone shows the resolve at once, as its own pending event: it reaches her phone only after the server took it
            waitUntil("his resolve reaching Alice's phone, his later events waiting there") { alice.shown.behind }
            assertNotNull(alice.shown.show, "still shown on Alice's phone")
            assertEquals(show.event, alice.shown.tip, "her table at the cast")
            assertTrue(alice.state.stack.isNotEmpty(), "the Glint still on her Stack")
            assertEquals(life, bobsLife(alice), "his life before the Glint on her table")
            alice.tagged(PLAY_SHOW_TAG).performClick()
            run()
            assertNotNull(alice.shown.show, "a single tap does nothing")
            assertEquals(show.event, alice.shown.tip, "her table still at the cast")
            run(skip = Duration.ofMillis(500)) // past the double-tap window: that tap was a single tap
            alice.tagged(PLAY_SHOW_TAG).performTouchInput { doubleClick() }
            waitUntil("Alice's table playing on past the Glint") {
                alice.shown.show == null && !alice.shows(hasTestTag(PLAY_SHOW_TAG)) && bobsLife(alice) == life + 2
            }
            bobCastsAGlint()
            val at = alice.shown.tip
            run(skip = Duration.ofSeconds(1))
            assertEquals(at, alice.shown.tip, "still held after 1 s")
            assertNotNull(alice.shown.show)
            run(skip = Duration.ofSeconds(3))
            waitUntil("the second show over by itself and Alice's table past the Glint") {
                alice.shown.show == null && bobsLife(alice) == life + 4
            }
            assertTrue(alice.leaks.leaks.isEmpty() && bob.leaks.leaks.isEmpty())
        }
    }
}
