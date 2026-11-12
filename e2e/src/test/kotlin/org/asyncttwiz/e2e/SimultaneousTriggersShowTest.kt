package org.asyncttwiz.e2e

import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.performClick
import arcanum.engine.decision.Decision
import org.asyncttwiz.android.HistoryPlaybackPreferences
import org.asyncttwiz.android.PLAY_SHOW_TAG
import org.asyncttwiz.android.PLAY_SHOW_WAITING_TAG
import org.asyncttwiz.android.PlayShowProbe
import org.asyncttwiz.appui.AppPlatform
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.After
import org.junit.Test
import java.util.Collections
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner playtest 2026-11-12: two triggers put on the Stack by one event weren't both shown large (one showed, then the
 * other). Alice casts an Ember
 * Twinherald ("you gain 1 life" and "draw a card" as it enters) and orders its two triggers: on her table (her own
 * triggers) and on Bob's (the other player's) both fly in and pile up large in the middle, then show one at a time, top
 * first, the other waiting large under it, each for the whole "Show plays and combat" time, and fly to the Stack.
 */
class SimultaneousTriggersShowTest : E2e() {
    private class Seen(val me: String, val show: GameDevice.PlayShow, val millis: Long)

    private val seen = Collections.synchronizedList(ArrayList<Seen>())

    @After fun forgetProbe() { PlayShowProbe.listener = null }

    private fun androidx.compose.ui.semantics.SemanticsNode.words(): String =
        config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription)?.joinToString().orEmpty()

    /** [player]'s table: the trigger shown large, and the other waiting large in the middle under it. */
    private fun bothLarge(player: E2ePlayer): Boolean {
        val shown = player.nodes(hasTestTag(PLAY_SHOW_TAG)).fetchSemanticsNodes()
        val waiting = player.nodes(hasTestTag(PLAY_SHOW_WAITING_TAG)).fetchSemanticsNodes()
        return shown.size == 1 && waiting.size == 1 && shown.single().words() != waiting.single().words() &&
            listOf(shown.single(), waiting.single()).all { it.words().isNotBlank() }
    }

    // HIS-112, HIS-021, CR 603.3b: both Twinherald triggers show large on both tables, piled in the middle, then one at a
    // time for the whole time each (different abilities: neither is a repeat), and both resolve
    @Test fun twoTriggersFromOneEventBothShowLarge() {
        startGame(TestDeck.thirds(TestCards.twinherald), TestDeck("Mountains", listOf(TestCards.mountain to 40)),
            deal = { a, _ -> "Mountain" in hand(a) && "Ember Twinherald" in hand(a) })
        for (player in listOf(alice, bob))
            HistoryPlaybackPreferences(AppPlatform.current, player.id).showPlaysMillis = HistoryPlaybackPreferences.DEFAULT_SHOW_PLAYS_MILLIS
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield", along = listOf(bob)) { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        val life = alice.state.player(alice.me).life
        PlayShowProbe.listener = { me, show, _, millis, _ -> if (show.kind == GameDevice.PlayKind.Triggered) seen += Seen(me, show, millis) }
        alice.playFromHand("Ember Twinherald", this)
        waitUntil("Alice ordering the Twinherald's triggers") {
            alice.state.pending?.decision is Decision.OrderTriggers && alice.shows(hasText("Order triggers") and isHeading())
        }
        clockInStep {
            alice.text("OK").performClick()
            // Hers at once; his after her Twinherald's cast has shown on his table
            waitUntil("both triggers large on Alice's table") { bothLarge(alice) }
            waitUntil("both triggers large on Bob's table") { bothLarge(bob) }
            waitUntil("both triggers resolved on both tables", timeoutSeconds = 90) {
                listOf(alice, bob).all { p -> p.shown.show == null && p.state.stack.isEmpty() && p.state.player(alice.me).life == life + 1 }
            }
        }
        val setting = HistoryPlaybackPreferences.DEFAULT_SHOW_PLAYS_MILLIS
        for (player in listOf(alice, bob)) {
            val mine = seen.filter { it.me == player.id }
            assertEquals(2, mine.map { it.show.entry }.distinct().size, "${player.name}: both triggers shown")
            assertTrue(mine.all { it.millis == setting }, "${player.name}: each for the whole time")
            assertEquals(1, mine.map { it.show.event }.distinct().size, "${player.name}: put on the Stack in one go")
        }
        assertFalse(alice.refusalSeen || bob.refusalSeen, "no refusal: ${alice.shownTexts} ${bob.shownTexts}")
    }
}
