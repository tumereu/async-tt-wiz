package org.asyncttwiz.e2e

import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performMouseInput
import org.asyncttwiz.android.HistoryPlaybackPreferences
import org.asyncttwiz.android.PLAY_SHOW_TAG
import org.asyncttwiz.android.VIEW_PAUSED_TAG
import org.asyncttwiz.appui.AppPlatform
import org.asyncttwiz.protocol.game.AssumeResolves
import org.asyncttwiz.protocol.game.PlaySettings
import org.junit.Test
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Prod reports 96PB2X, 3LL6UZ and 3UQ9DY (owner, 2026-11-16, Firefox on Windows): Alice held Alt on the desktop table (the
 * finger held, WEB-025, WEB-087) and its key-up never reached the app (another window, the browser's menu, the chat field): the pause
 * stayed on, the other player's play shown stood in the middle of her table with no way to end it, and once she went on in
 * the game on her phone too, it stayed there over a board long past it. Alice plays in the desktop layout, Bob on his phone
 * casts Ember Glints assuming her pass, so his phone goes on.
 */
@Config(qualifiers = "w1440dp-h900dp-land-mdpi")
class FingerKeyLetGoTest : E2e() {
    private val glints = TestDeck("Glints", listOf(TestCards.mountain to 20, TestCards.glint to 20))

    private fun start() {
        startGame(TestDeck.thirds(), glints, bobStarts = true, aliceDesktop = true,
            bobSettings = PlaySettings(assume = AssumeResolves.Always),
            deal = { _, b -> hand(b).count { it == "Ember Glint" } >= 2 })
        HistoryPlaybackPreferences(AppPlatform.current, alice.id).showPlaysMillis = HistoryPlaybackPreferences.MAX_SHOW_PLAYS_MILLIS
        bothOpen()
        alice.control("Keep").performMouseInput { click() }
        bob.tap("Keep")
        waitUntil("Bob's Main 1", along = listOf(alice)) { bob.inOwnMain() }
    }

    private fun lifeOfBob(player: E2ePlayer) = player.state.player(bob.me).life

    /** Bob casts a Glint; Alice holds Alt (the pointer on no card) over its play shown, which stays twice its time. */
    private fun bobCastsAGlintWhileAliceHoldsAlt() {
        bob.playFromHand("Ember Glint", this)
        waitUntil("the Glint shown on Alice's table") { alice.shown.show != null && alice.shows(hasTestTag(PLAY_SHOW_TAG)) }
        val show = assertNotNull(alice.shown.show)
        assertTrue(alice.key("Alt", true), "the finger key taken")
        passWhileHeld(2 * HistoryPlaybackPreferences.MAX_SHOW_PLAYS_MILLIS)
        assertTrue(alice.shows(hasTestTag(VIEW_PAUSED_TAG)), "the pause icon while Alt is held: ${alice.screen()}")
        assertEquals(show, alice.engine { device.show }, "still shown while Alt is held")
    }

    /** The pause over: the play shown ends by itself and Alice's table plays on past the Glint (Bob gained 2). */
    private fun pauseOver(life: Int, why: String) {
        waitUntil("the show over by itself after $why, and Alice's table past the Glint") {
            alice.shown.show == null && lifeOfBob(alice) == life + 2
        }
        waitUntil("the pause icon gone") { !alice.shows(hasTestTag(VIEW_PAUSED_TAG)) }
    }

    // WEB-025, WEB-087, HIS-021, HIS-126 (prod report 96PB2X): Alt let go in another window (Alt+Tab): its key-up never comes, but the window
    // losing the focus lets it go, so the pause ends and the held play goes on
    @Test fun theWindowLosingTheFocusLetsAltGo() {
        start()
        clockInStep {
            val life = lifeOfBob(bob)
            bobCastsAGlintWhileAliceHoldsAlt()
            alice.keyUpLost("Alt")
            alice.windowLostFocus()
            pauseOver(life, "the window lost the focus")
        }
    }

    // WEB-025, WEB-087 (prod report 96PB2X: the chat opened, typed in): a text field taking the keyboard lets Alt go
    @Test fun typingInATextFieldLetsAltGo() {
        start()
        clockInStep {
            val life = lifeOfBob(bob)
            bobCastsAGlintWhileAliceHoldsAlt()
            alice.keyUpLost("Alt")
            alice.key("h", true, textField = true)
            alice.key("h", false, textField = true)
            pauseOver(life, "a key typed in a text field")
        }
    }

    // WEB-025, WEB-087: a later key or click whose modifiers no longer hold Alt says its key-up was lost: Alt is let go
    @Test fun aKeyWithoutAltLetsAltGo() {
        start()
        clockInStep {
            val life = lifeOfBob(bob)
            bobCastsAGlintWhileAliceHoldsAlt()
            alice.keyUpLost("Alt")
            alice.key("Shift", true)
            alice.key("Shift", false)
            pauseOver(life, "Shift pressed without Alt")
        }
    }

    // HIS-112, HIS-142, HIS-095 (prod reports 3LL6UZ, 3UQ9DY): with Alt still held on her desktop, Alice watches on her phone too (her
    // second device, the same account) and it plays on past the Glint: the server moves her on, and her desktop table follows
    // it with the play shown gone (its entry left the Stack), not standing in the middle over a board past it
    @Test fun anotherDeviceGoingOnEndsAPlayShownHeldHere() {
        start()
        clockInStep {
            val life = lifeOfBob(bob)
            bobCastsAGlintWhileAliceHoldsAlt()
            val phone = secondDevice(alice)
            phone.openGame(game, this)
            // HIS-094: opened behind, her phone waits for her tap on "Bob has made moves · Tap to watch"
            waitUntil("the catch-up's centre text on Alice's phone") { phone.showsWatch() || lifeOfBob(phone) == life + 2 }
            if (phone.showsWatch()) phone.tapToWatch()
            waitUntil("Alice's phone past the Glint") { lifeOfBob(phone) == life + 2 }
            waitUntil("her desktop table following her phone, the play shown gone though Alt is still held") {
                alice.shown.show == null && lifeOfBob(alice) == life + 2
            }
            waitUntil("no play shown left in the middle of her table") { !alice.shows(hasTestTag(PLAY_SHOW_TAG)) }
            assertFalse(alice.engine { device.pausing }, "no pause of playback left")
            alice.key("Alt", false)
        }
    }
}
