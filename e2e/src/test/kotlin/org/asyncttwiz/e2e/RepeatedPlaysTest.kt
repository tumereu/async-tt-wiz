package org.asyncttwiz.e2e

import org.asyncttwiz.android.HistoryPlaybackPreferences
import org.asyncttwiz.android.PLAY_SHOW_FLIGHT_MILLIS
import org.asyncttwiz.android.PlayShowProbe
import org.asyncttwiz.android.playShowFlightMillis
import org.asyncttwiz.android.playShowMillis
import org.asyncttwiz.appui.AppPlatform
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.PlaySettings
import org.junit.After
import org.junit.Test
import java.util.Collections
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Owner playtest 2026-11-11: a board wipe's "whenever a creature dies" triggers each showed for the whole "Show plays and
 * combat" time, so 30 of them took half a minute. Now the same ability from the same kind of source shown again in one
 * chain shows faster each time, the first keeping the whole time. Bob wipes his three Ember Martyrs: on Alice's table his
 * wipe shows for its time, then the three Martyr triggers, each shorter than the one before.
 */
class RepeatedPlaysTest : E2e() {
    private class Seen(val kind: GameDevice.PlayKind, val repeats: Int, val millis: Long, val flight: Int)

    private val seen = Collections.synchronizedList(ArrayList<Seen>())

    @After fun forgetProbe() { PlayShowProbe.listener = null }

    // HIS-112, HIS-021: the first Martyr trigger shows for the whole 1.2 s, the second 35% shorter, the third shorter again,
    // their flights likewise; the wipe (a spell) keeps its whole time; and the three resolve on her table as on his
    @Test fun repeatedTriggersShowFasterEachTime() {
        startGame(TestDeck.glimmers(), TestDeck("Martyrs", listOf(TestCards.martyr to 30, TestCards.reckoning to 10)), bobStarts = true,
            bobSettings = PlaySettings(assume = org.asyncttwiz.protocol.game.AssumeResolves.Always),
            deal = { _, b -> hand(b).count { it == "Ember Martyr" } >= 3 && "Ember Reckoning" in hand(b) })
        HistoryPlaybackPreferences(AppPlatform.current, alice.id).showPlaysMillis = HistoryPlaybackPreferences.DEFAULT_SHOW_PLAYS_MILLIS
        bothOpen()
        alice.tap("Keep")
        bob.tap("Keep")
        waitUntil("Bob's Main 1", along = listOf(alice)) { bob.inOwnMain() }
        repeat(3) { i ->
            bob.playFromHand("Ember Martyr", this)
            waitUntil("Martyr ${i + 1} on Bob's battlefield on both tables") {
                bob.field("Ember Martyr", bob.me).size == i + 1 && alice.field("Ember Martyr", bob.me).size == i + 1 &&
                    alice.shown.show == null && bob.inOwnMain()
            }
        }
        val life = bob.state.player(bob.me).life
        PlayShowProbe.listener = { me, show, repeats, millis, flight -> if (me == alice.id) seen += Seen(show.kind, repeats, millis, flight) }
        bob.playFromHand("Ember Reckoning", this)
        waitUntil("the Martyrs' triggers resolved on Alice's table", timeoutSeconds = 90) {
            alice.state.player(bob.me).life == life + 6 && alice.state.stack.isEmpty() && alice.shown.show == null
        }
        val setting = HistoryPlaybackPreferences.DEFAULT_SHOW_PLAYS_MILLIS
        val shows = seen.toList()
        assertEquals(listOf(GameDevice.PlayKind.Cast to 0), shows.filter { it.kind == GameDevice.PlayKind.Cast }.map { it.kind to it.repeats },
            "the wipe once, a spell: never sped up")
        assertEquals(setting, shows.first { it.kind == GameDevice.PlayKind.Cast }.millis)
        val triggers = shows.filter { it.kind == GameDevice.PlayKind.Triggered }
        assertEquals(listOf(0, 1, 2), triggers.map { it.repeats }, "the Martyrs' triggers, one chain")
        assertEquals(listOf(setting, playShowMillis(setting, 1), playShowMillis(setting, 2)), triggers.map { it.millis })
        assertEquals(listOf(1_200L, 780L, 507L), triggers.map { it.millis }, "each 35% shorter than the one before")
        assertEquals(listOf(PLAY_SHOW_FLIGHT_MILLIS, playShowFlightMillis(1f, 1), playShowFlightMillis(1f, 2)), triggers.map { it.flight })
        assertFalse(alice.refusalSeen, "no refusal: ${alice.shownTexts}")
    }
}
