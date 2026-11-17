package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.performClick
import org.asyncttwiz.android.HistoryPlaybackPreferences
import org.asyncttwiz.android.PLAY_SHOW_TAG
import org.asyncttwiz.android.StackAnimationProbe
import org.asyncttwiz.appui.AppPlatform
import org.asyncttwiz.protocol.game.AssumeResolves
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.PlaySettings
import org.junit.After
import org.junit.Test
import java.util.Collections
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Stack animation (owner spec 2026-11-14; owner playtest 2026-11-14: "a play flew to the middle and, at the same time,
 * straight to the Stack, so it looked as if two entries went on the Stack", in both layouts). Bob casts Ember Flick at
 * Alice's Ash Pup, his phone assuming her pass at once. On Alice's phone the Flick flies in, holds in the middle and flies
 * to the Stack, and while it shows her Stack has nothing of it (no second entry, nothing moving there); it lands as her
 * Stack's one entry, its arrow grows to the Pup, and only then does Bob's assumed resolution play. On Bob's phone his own
 * cast makes one move to his Stack, its arrow grows, then the resolution he assumed plays.
 */
class StackAnimationTest : E2e() {
    private val aliceDeck = TestDeck("Pups", listOf(TestCards.mountain to 20, TestCards.pup to 20))
    private val bobDeck = TestDeck("Flicks", listOf(TestCards.mountain to 20, TestCards.flick to 20))

    @After fun forgetProbes() {
        StackAnimationProbe.targeting = null
        StackAnimationProbe.resolving = null
    }

    // HIS-112, HIS-137, STK-058, HIS-230, TBL-125, TBL-150 (owner playtest 2026-11-14)
    @Test fun aPlayGoesOnTheStackOnceAndItsArrowsComeBeforeItsAssumedResolution() {
        startGame(aliceDeck, bobDeck, bobSettings = PlaySettings(assume = AssumeResolves.Always),
            deal = { a, b -> "Ash Pup" in hand(a) && "Mountain" in hand(b) && "Ember Flick" in hand(b) })
        // A longer show than the default: the checks while it shows have time to look
        HistoryPlaybackPreferences(AppPlatform.current, alice.id).showPlaysMillis = 2_500
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Ash Pup", this)
        waitUntil("the Pup on Alice's battlefield", along = listOf(bob)) { alice.field("Ash Pup").isNotEmpty() && alice.inOwnMain() }
        alice.hold(requireNotNull(alice.stepArrow()))
        // Bob plays Alice's turn back and answers its stops up to his Main 1
        waitUntil("Bob's Main 1") {
            val inBobsMain = bob.state.turn.active == bob.me && bob.state.turn.step == arcanum.api.TurnStep.PrecombatMain
            when (bob.status) {
                GameDevice.Status.CatchUp -> bob.once { bob.tapToWatch() }
                is GameDevice.Status.Stopped -> if (!inBobsMain) bob.stepArrow()?.takeIf { it != "To combat" }?.let { label -> bob.once { bob.tap(label) } }
                else -> {}
            }
            bob.inOwnMain()
        }
        waitUntil("Alice's table caught up with Bob's Main 1") {
            alice.state.turn.active == bob.me && !alice.shown.behind && alice.shown.show == null
        }

        // What each table's Stack animation does, in order: its arrows (step 4) and its resolutions (step 5)
        val steps = Collections.synchronizedList(ArrayList<String>())
        StackAnimationProbe.targeting = { me, entry, targets, _ -> steps += "${who(me)} arrows $entry ($targets)" }
        StackAnimationProbe.resolving = { me, entries -> for (e in entries) steps += "${who(me)} resolves $e" }

        clockInStep {
            bob.playFromHand("Ember Flick", this)
            waitUntil("Bob's target bar") { bob.shows(hasText("Choose any target") and isHeading()) }
            bob.battlefieldCard("Ash Pup", owner = alice).performClick()
            // While it shows on Alice's table her Stack has none of it, though her device has it there; Bob's own cast never
            // puts a second entry on his
            var seenOnAlice = false
            val wrong = ArrayList<String>()
            waitUntil("the Flick shown on Alice's table and landed there") {
                if (bob.shows(hasContentDescription("Stack, 2 entries"))) wrong += "two entries on Bob's Stack: ${bob.screen()}"
                if (alice.shows(hasContentDescription("Stack, 2 entries"))) wrong += "two entries on Alice's Stack: ${alice.screen()}"
                val showing = alice.shows(hasTestTag(PLAY_SHOW_TAG))
                if (showing) {
                    seenOnAlice = true
                    if (!alice.shows(hasContentDescription("Stack, 0 entries"))) wrong += "the Flick on Alice's Stack while it shows: ${alice.screen()}"
                    if (alice.shows(hasTestTag("stack-target-arrows"))) wrong += "arrows while it shows: ${alice.screen()}"
                }
                seenOnAlice && !showing
            }
            assertTrue(wrong.isEmpty(), wrong.joinToString("\n"))
            waitUntil("the Flick resolved on both phones, the Pup gone", along = listOf(bob)) {
                alice.field("Ash Pup").isEmpty() && bob.field("Ash Pup").isEmpty() && alice.state.stack.isEmpty() &&
                    steps.count { "resolves" in it } >= 2
            }
        }
        val log = steps.toList()
        for (name in listOf(alice.name, bob.name)) {
            val own = log.filter { it.startsWith("$name ") }
            assertEquals(1, own.count { "arrows" in it }, "one step 4 on $name's table: $log")
            val arrows = own.indexOfFirst { "arrows" in it }
            val resolves = own.indexOfFirst { "resolves" in it }
            assertTrue(arrows in 0 until resolves, "on $name's table the arrows before the resolution: $log")
            assertTrue(own[arrows].endsWith("(1)"), "one target, the Pup: $log")
        }
        assertTrue(alice.leaks.leaks.isEmpty() && bob.leaks.leaks.isEmpty(), "${alice.leaks.leaks} ${bob.leaks.leaks}")
    }

    private fun who(account: String) = if (account == alice.id) alice.name else bob.name
}
