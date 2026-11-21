package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.asyncttwiz.android.DraftBotPreferences
import org.asyncttwiz.android.ShellDestination
import org.asyncttwiz.appui.AppPlatform
import org.asyncttwiz.protocol.DraftSettings
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Draft bots' debug view (backlog: draft bots): a test build's "Show bot picks" shows the bot's scores and pick on the pick screen. */
class DraftBotPicksTest : E2e() {
    // LOB-263, DRF-292: Alice turns on Show bot picks in Settings; her Booster pack shows the bot's score on each card and marks
    // its pick, the card the heuristic rates best (Storm Spark's removal over Ember Cub over a Mountain); a tap on the score
    // lists its parts. Turned off, the scores are gone
    @Test fun showBotPicksMarksTheBotsPickOnThePack() {
        val preferences = DraftBotPreferences(AppPlatform.current)
        try {
            launchPlayers(aliceCatalog = true)
            waitUntil("Alice's card records installed") { alice.catalog!!.state.ready }
            waitUntil("Alice's menu") { alice.shows(hasTestTag("menu-SETTINGS")) }
            alice.tagged("menu-SETTINGS").performScrollTo().performClick()
            waitUntil("Show bot picks in Alice's Settings") { alice.shows(hasTestTag("draft-bot-setting")) }
            alice.tagged("draft-bot-setting").performScrollTo().performClick()
            waitUntil("Show bot picks on") { preferences.enabled }

            val name = "E2E bot picks ${UUID.randomUUID().toString().take(6)}"
            val pod = ServerSetup.draftingPod(alice.account, bob.account, name, settings = DraftSettings(packsPerPlayer = 3, cardsPerPack = 2))
            alice.auth.navigate(ShellDestination.PODS)
            alice.pods.open(pod)
            waitUntil("Alice's pack") { alice.pods.state.detail?.takeIf { it.id == pod }?.draft?.head?.cards?.size == 2 }
            val detail = alice.pods.state.detail!!
            val names = detail.definitions.associate { it.digest to it.definition.name }
            val pack = detail.draft!!.head!!.cards
            val rank = listOf("Storm Spark", "Ember Cub", "Mountain")
            val best = pack.minWith(compareBy({ rank.indexOf(names[it.definition]) }, { it.id }))
            val scores = mapOf("Storm Spark" to 52, "Ember Cub" to 40, "Mountain" to 0)
            for (copy in pack) {
                val score = scores.getValue(names.getValue(copy.definition))
                val text = if (copy == best) "Bot pick $score" else "Bot $score"
                waitUntil("the bot's score on ${names[copy.definition]}") { alice.shows(hasTestTag("draft-bot-${copy.id}") and hasText(text)) }
            }
            assertEquals(1, pack.count { alice.shows(hasTestTag("draft-bot-frame-${it.id}")) }, alice.screen())
            alice.tagged("draft-bot-${best.id}").performClick()
            waitUntil("the breakdown") { alice.shows(hasTestTag("draft-bot-breakdown")) && alice.showsText("Base 40") }

            preferences.enabled = false
            waitUntil("the scores gone") { pack.none { alice.shows(hasTestTag("draft-bot-${it.id}")) } }
            assertFalse(alice.shows(hasTestTag("draft-bot-frame-${best.id}")), alice.screen())
        } finally {
            preferences.enabled = false
        }
    }
}
