package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import org.asyncttwiz.android.BOTTOM_BAR
import org.asyncttwiz.android.HistoryPlaybackPreferences
import org.asyncttwiz.android.PLAY_SHOW_TAG
import org.asyncttwiz.android.QUIET_WAIT_TAG
import org.asyncttwiz.appui.AppPlatform
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.ResponseMode
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Owner playtest 2026-11-12: while a play shown ran and Quick was going to pass by itself, the main button flashed
 * "Resolve" and then went on without a press; and while waiting on the other player its place was empty, so the pause read
 * as lag. Alice (Quick, nothing to answer with) casts her Ember Herald: its enters trigger, her own, shows large on her
 * table; meanwhile her main button holds no press, and once it has landed her phone passes by itself. Then she ends her
 * turn: in Bob's main phase her main button's place says "Bob's move…" quietly.
 */
class MainButtonQuietTest : E2e() {
    // TRN-063, STK-029, HIS-112, TRN-004, TRN-015: no "Resolve" on Alice's main button while her own trigger shows and her
    // phone is about to pass by itself; then the quiet waiting status in its place while Bob acts in his main phase
    @Test fun noPressTheDeviceMakesItselfAndAQuietWait() {
        startGame(TestDeck("Heralds", listOf(TestCards.mountain to 20, TestCards.herald to 20)), TestDeck("Mountains", listOf(TestCards.mountain to 40)),
            deal = { a, _ -> "Mountain" in hand(a) && "Ember Herald" in hand(a) })
        HistoryPlaybackPreferences(AppPlatform.current, alice.id).showPlaysMillis = HistoryPlaybackPreferences.DEFAULT_SHOW_PLAYS_MILLIS
        bothOpen()
        bothKeep(bobAway = false)
        assertEquals(ResponseMode.AutoSkip, alice.shown.settings.mode)
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield", along = listOf(bob)) { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        clockInStep {
            val from = alice.shownTexts.size
            alice.playFromHand("Ember Herald", this)
            waitUntil("the Herald's trigger shown on Alice's table", along = listOf(bob)) {
                alice.shown.show?.kind == GameDevice.PlayKind.Triggered && alice.shows(hasTestTag(PLAY_SHOW_TAG))
            }
            assertEquals(GameDevice.Status.YourMove, alice.status, "she holds priority over her trigger")
            assertTrue(alice.shown.ownStepHeld, "her phone passes by itself once the show is done")
            assertEquals(null, alice.stepArrow(), "no press on the main button meanwhile: ${alice.screen()}")
            waitUntil("the trigger resolved on Alice's table, her phone having passed", along = listOf(bob)) {
                alice.shown.show == null && alice.state.stack.isEmpty() && alice.state.battlefield.any { alice.state.obj(it)?.token == true } && alice.inOwnMain()
            }
            val bars = alice.shownTexts.drop(from).filter { it.startsWith(BOTTOM_BAR) }
            assertFalse(bars.any { "Resolve" in it }, "no Resolve on her main button: $bars")
        }
        // TRN-017: holding the main button in her main phase ends her turn
        alice.hold(assertNotNull(alice.stepArrow()))
        waitUntil("Bob in his main phase, Alice waiting on him") {
            bob.inOwnMain() && alice.status is GameDevice.Status.Waiting
        }
        waitUntil("the quiet waiting status in Alice's main button's place") {
            alice.shows(hasText("${bob.name}'s move…") and hasAnyAncestor(hasTestTag(QUIET_WAIT_TAG)))
        }
        assertEquals(null, alice.stepArrow())
        assertTrue(alice.shownTexts.last { it.startsWith(BOTTOM_BAR) }.contains("status ${bob.name}'s move…"))
        assertFalse(alice.refusalSeen, "no refusal: ${alice.shownTexts}")
    }
}
