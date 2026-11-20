package org.asyncttwiz.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import arcanum.engine.state.StackItemKind
import org.asyncttwiz.android.HistoryPlaybackPreferences
import org.asyncttwiz.android.PLAY_SHOW_TAG
import org.asyncttwiz.appui.AppPlatform
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.PlaySettings
import org.asyncttwiz.protocol.game.ResponseMode
import org.junit.Test
import kotlin.test.assertTrue

/**
 * Owner-approved change 2026-11-19: "Bob cast Cinder Charm · you gain 2 life" read as if it meant the viewer. Bob casts an Ember
 * Glint ("You gain 2 life."): on Alice's phone the play shown and then the band under his hand say "Bob gains 2 life".
 */
class OtherPlayersYouTest : E2e() {
    private val glints = TestDeck("Glints", listOf(TestCards.mountain to 20, TestCards.glint to 20))

    private fun description(player: E2ePlayer, tag: String): String? = player.nodes(hasTestTag(tag)).fetchSemanticsNodes().firstOrNull()
        ?.config?.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()

    // STK-220, STK-057, HIS-135: the other player's "you" reads as their name with the verb agreeing, in the play shown and on the band
    @Test fun theOtherPlayersYouReadsAsTheirName() {
        startGame(TestDeck.thirds(), glints, bobStarts = true, aliceSettings = PlaySettings(mode = ResponseMode.Normal),
            deal = { _, b -> "Ember Glint" in hand(b) })
        // The longest time (3 s), so the play shown is there to be read
        HistoryPlaybackPreferences(AppPlatform.current, alice.id).showPlaysMillis = HistoryPlaybackPreferences.MAX_SHOW_PLAYS_MILLIS
        bothOpen()
        alice.tap("Keep")
        bob.tap("Keep")
        waitUntil("Bob's Main 1", along = listOf(alice)) { bob.inOwnMain() }
        val line = "${bob.name} cast Ember Glint · ${bob.name} gains 2 life"
        val life = alice.state.player(bob.me).life
        bob.playFromHand("Ember Glint", this)
        waitUntil("the Glint shown on Alice's phone, naming Bob where it says \"you\"") {
            alice.shows(hasTestTag(PLAY_SHOW_TAG) and hasContentDescription(line, substring = true)) }
        waitUntil("Alice holding priority over Bob's Glint") {
            alice.status == GameDevice.Status.YourMove && alice.state.let { s ->
                s.stack.isNotEmpty() && s.priority.holder == alice.me && s.stackItems[s.stack.last()]?.kind == StackItemKind.Spell }
        }
        waitUntil("the band on Alice's table") { description(alice, "stack-line") == line }
        // Bob's own spell: no line on his phone
        assertTrue(!bob.shows(hasTestTag("stack-line")), bob.screen())
        alice.tap("Resolve")
        waitUntil("the Glint resolved", along = listOf(bob)) { alice.state.stack.isEmpty() && alice.state.player(bob.me).life == life + 2 }
        assertTrue(alice.leaks.leaks.isEmpty() && bob.leaks.leaks.isEmpty(), "${alice.leaks.leaks} ${bob.leaks.leaks}")
    }
}
