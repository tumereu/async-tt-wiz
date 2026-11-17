package org.asyncttwiz.e2e

import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performMouseInput
import org.asyncttwiz.android.AutomatumStep
import org.asyncttwiz.android.BOTTOM_BAR
import org.asyncttwiz.android.EngineShownProbe
import org.asyncttwiz.android.HistoryPlaybackPreferences
import org.asyncttwiz.android.PLAY_SHOW_TAG
import org.asyncttwiz.android.QUIET_WAIT_TAG
import org.asyncttwiz.android.words
import org.asyncttwiz.appui.AppPlatform
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.ResponseMode
import org.junit.After
import org.junit.Test
import org.robolectric.annotation.Config
import java.util.Collections
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
 *
 * Owner, 2026-11-16: the cast's own board, shown at once (SYS-025) before her phone folds its own pass into the cast's
 * event, still said "Resolve" for a few ms. The main button is looked at from the moment of the cast: on every frame drawn
 * and on every board her table is given, the cast's own board included, whether a frame draws it or not.
 */
class MainButtonQuietTest : E2e() {
    @After fun noProbe() { EngineShownProbe.all = null }

    // TRN-063, TRN-119, STK-029, HIS-112, HIS-140, TRN-004, TRN-015, SYS-025: no "Resolve" on Alice's main button from her cast on (its own board
    // too) while her own trigger shows and her phone is about to pass by itself; then the quiet waiting status in its place
    // while Bob acts in his main phase
    @Test fun noPressTheDeviceMakesItselfAndAQuietWait() {
        startGame(heralds, mountains, deal = { a, _ -> "Mountain" in hand(a) && "Ember Herald" in hand(a) })
        HistoryPlaybackPreferences(AppPlatform.current, alice.id).showPlaysMillis = HistoryPlaybackPreferences.DEFAULT_SHOW_PLAYS_MILLIS
        bothOpen()
        bothKeep(bobAway = false)
        assertEquals(ResponseMode.AutoSkip, alice.shown.settings.mode)
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield", along = listOf(bob)) { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        castTheHeraldWithNoPress { alice.playFromHand("Ember Herald", this) }
        // TRN-017, TRN-106: holding the main button in her main phase ends her turn
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

    // TRN-063, TRN-119, STK-029, SYS-025, WEB-018: the same on the desktop table: no "Resolve" on Alice's main button from the click
    // that casts her Herald on, its own board too
    @Config(qualifiers = "w1440dp-h900dp-land-mdpi")
    @Test fun noPressTheDeviceMakesItselfOnTheDesktopTable() {
        startGame(heralds, mountains, aliceDesktop = true, deal = { a, _ -> "Mountain" in hand(a) && "Ember Herald" in hand(a) })
        HistoryPlaybackPreferences(AppPlatform.current, alice.id).showPlaysMillis = HistoryPlaybackPreferences.DEFAULT_SHOW_PLAYS_MILLIS
        bothOpen()
        assertTrue(alice.shows(hasTestTag("phase-track")), "Alice's table in the desktop layout: ${alice.screen()}")
        alice.control("Keep").performMouseInput { click() }
        waitUntil("Alice's Main 1", along = listOf(bob)) { alice.inOwnMain() }
        clickFromHand("Mountain")
        waitUntil("the Mountain on the battlefield", along = listOf(bob)) { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        castTheHeraldWithNoPress { clickFromHand("Ember Herald") }
        assertFalse(alice.refusalSeen, "no refusal: ${alice.shownTexts}")
    }

    /**
     * Alice casts her Ember Herald ([cast]); its trigger shows on her table and her phone passes over it by itself once the
     * show is done. From the cast on no frame drawn and no board her table is given ([EngineShownProbe.all]: the cast's own
     * board, shown before her phone's pass is folded into its event, too) puts "Resolve" on her main button.
     */
    private fun castTheHeraldWithNoPress(cast: () -> Unit) {
        // What the main button says on each board: a step press only on her move with no step of her own held (TRN-063, TRN-119)
        val boards = Collections.synchronizedList(mutableListOf<String>())
        clockInStep {
            val from = alice.shownTexts.size
            EngineShownProbe.all = { me, shown, _ ->
                if (me == alice.id) boards += "v${shown.version}${if (shown.ownActionShown) " (the cast's own board)" else ""}: " +
                    (if (shown.status == GameDevice.Status.YourMove && !shown.ownStepHeld) AutomatumStep.of(shown.board).words(shown.board) else "no press")
            }
            cast()
            waitUntil("the Herald's trigger shown on Alice's table", along = listOf(bob)) {
                alice.shown.show?.kind == GameDevice.PlayKind.Triggered && alice.shows(hasTestTag(PLAY_SHOW_TAG))
            }
            assertEquals(GameDevice.Status.YourMove, alice.status, "she holds priority over her trigger")
            assertTrue(alice.shown.ownStepHeld, "her phone passes by itself once the show is done")
            assertEquals(null, alice.stepArrow(), "no press on the main button meanwhile: ${alice.screen()}")
            waitUntil("the trigger resolved on Alice's table, her phone having passed", along = listOf(bob)) {
                alice.shown.show == null && alice.state.stack.isEmpty() && alice.state.battlefield.any { alice.state.obj(it)?.token == true } && alice.inOwnMain()
            }
            EngineShownProbe.all = null
            val bars = alice.shownTexts.drop(from).filter { it.startsWith(BOTTOM_BAR) }
            assertFalse(bars.any { "Resolve" in it }, "no Resolve on her main button: $bars")
            val given = boards.toList()
            assertTrue(given.any { "the cast's own board" in it }, "the cast's own board given to her table: $given")
            assertFalse(given.any { "Resolve" in it }, "no Resolve on any board her table was given: $given")
        }
    }

    /** WEB-023, ZON-079, ZON-102: a click raises Alice's hand card [name], a click on the raised card plays it. */
    private fun clickFromHand(name: String) {
        waitUntil("Alice's game settled") { alice.settled(this) }
        alice.handCard(name).performMouseInput { click() }
        waitUntil("$name raised in Alice's hand") { alice.raisedInHand() == name && alice.settled(this) }
        alice.handCard(name).performMouseInput { click() }
    }

    private companion object {
        val heralds = TestDeck("Heralds", listOf(TestCards.mountain to 20, TestCards.herald to 20))
        val mountains = TestDeck("Mountains", listOf(TestCards.mountain to 40))
    }
}
