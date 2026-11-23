package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.performClick
import org.asyncttwiz.android.AppLog
import org.asyncttwiz.android.AutomatumWork
import org.asyncttwiz.android.CastController
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner problem report 4DKZET ("wtf is 'table changed, cast again'"): casting an X spell, each tap on + previews the payment
 * with the X set (STK-024) where the cast's flow runs. Each preview made a new version of the flow and only four were kept, so
 * after a burst of taps faster than the previews were worked out the X question's own version was gone: every later preview
 * and Cast was refused with "The table changed: cast it again." though nothing on the table changed, and the cast couldn't be
 * finished. Alice has six Ember Shards out and casts Ember Burst ({X}); she raises X six times while the Automatum worker is
 * busy (held here, as a slow phone's is), then Cast: the Burst goes on with X = 6 and Bob takes 6.
 */
class XRaisedManyTimesTest : E2e() {
    /** The cast panel's Cast button: the smallest clickable node holding the word "Cast". */
    private fun E2ePlayer.castButton(): androidx.compose.ui.semantics.SemanticsNode =
        nodes(hasClickAction() and hasAnyDescendant(hasText("Cast"))).fetchSemanticsNodes().minBy { it.size.width * it.size.height }

    // STK-138, STK-024, STK-023 (owner problem report 4DKZET): X raised six times faster than the previews are worked out, then
    // Cast: the cast goes on to its target with that X, never "The table changed: cast it again."
    @Test fun xRaisedSixTimesStillCasts() {
        startGame(TestDeck("Shard bursts", listOf(TestCards.shard to 34, TestCards.burst to 6)),
            deal = { a, _ -> hand(a).count { it == "Ember Shard" } >= 6 && "Ember Burst" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        repeat(6) { n ->
            alice.playFromHand("Ember Shard", this)
            waitUntil("${n + 1} Shards out", along = listOf(bob)) {
                alice.field("Ember Shard").size == n + 1 && alice.inOwnMain() && alice.engine { device.session.pending.isEmpty() }
            }
        }
        // The Burst is her only card left: raised, it can't be told from a row of others (playFromHand), so its chips show it
        waitUntil("Alice's game settled") { alice.settled(this) }
        alice.handCard("Ember Burst").performClick()
        waitUntil("the Burst raised with its Cast chip") { alice.shows(hasContentDescription("Way Cast")) && alice.settled(this) }
        alice.handCard("Ember Burst").performClick()
        waitUntil("Alice's cast panel with X at 0") { alice.shows(hasContentDescription("Cast panel")) && alice.shows(hasContentDescription("Value 0")) }
        val logFrom = AppLog.text().length
        // The Automatum worker busy while the taps come: each tap's preview waits behind it, all made on the version shown
        val held = CountDownLatch(1)
        AutomatumWork.Background.run({ held.await(30, TimeUnit.SECONDS) }) { }
        try {
            repeat(6) { alice.tap("One more") }
        } finally {
            held.countDown()
        }
        val drained = AtomicBoolean(false)
        AutomatumWork.Background.run({ }) { drained.set(true) }
        waitUntil("the previews worked out with X at 6") { drained.get() && alice.shows(hasContentDescription("Value 6")) }
        assertFalse(alice.showsText(CastController.TABLE_CHANGED), "no stale refusal in the panel")
        val cast = alice.castButton()
        alice.node(androidx.compose.ui.test.SemanticsMatcher("Cast button") { it.id == cast.id }).performClick()
        waitUntil("Alice's target bar") { alice.shows(hasText("Choose any target") and isHeading()) }
        alice.node(hasContentDescription("${bob.name}, life 20", substring = true)).performClick()
        waitUntil("the Burst dealing 6 to Bob, on the server", along = listOf(bob)) {
            alice.state.player(bob.me).life == 14 && alice.engine { device.session.pending.isEmpty() }
        }
        val log = AppLog.text().substring(logFrom.coerceAtMost(AppLog.text().length))
        assertTrue("refused as stale" !in log, "no step refused as stale: ${log.takeLast(3_000)}")
        assertFalse(alice.refusalSeen, "no refusal: ${alice.shownTexts}")
    }
}
