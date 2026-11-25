package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlinx.coroutines.runBlocking
import org.asyncttwiz.android.DraftBotPreferences
import org.asyncttwiz.android.ShellDestination
import org.asyncttwiz.appui.AppPlatform
import org.asyncttwiz.protocol.*
import org.junit.Test
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Draft bots' card ratings (owner, 2026-11-25): bots in a pod of a set with ratings pick by them; elsewhere they carry a warning. */
class DraftBotRatingsTest : E2e() {
    // DRF-314, DRF-312, DRF-292: Alice's cube pod with a bot shows the warning sign on the bot's row and beside Add bot in setup,
    // its hold telling that cubes have no card ratings, and on the bot's avatar once the draft starts. Her pod of Test Set (the
    // test cards' invented ratings) shows none, its bot drafts by the ratings, and with Show bot picks on her pick's breakdown
    // starts with the card's rating
    @Test fun botsPickByRatingsWhereASetHasThemAndWarnElsewhere() {
        val preferences = DraftBotPreferences(AppPlatform.current)
        try {
            launchPlayers(aliceCatalog = true)
            waitUntil("Alice's card records installed") { alice.catalog!!.state.ready }
            val cubeTip = "Bots pick less well here: there are no card ratings for cubes."
            val cube = ServerSetup.setupPod(alice.account, "E2E cube bots ${UUID.randomUUID().toString().take(6)}",
                settings = DraftSettings(packsPerPlayer = 1, cardsPerPack = 3, bots = 1))
            alice.auth.navigate(ShellDestination.PODS)
            alice.pods.open(cube)
            waitUntil("the warning on the cube pod's bot and beside Add bot") {
                alice.shows(hasTestTag("pod-bot-warning-1") and hasContentDescription(cubeTip)) &&
                    alice.shows(hasTestTag("pod-add-bot-warning") and hasContentDescription(cubeTip))
            }
            ServerSetup.startPod(alice.account, cube)
            alice.pods.refresh()
            waitUntil("the warning on the started pod's bot avatar") {
                alice.pods.state.detail?.phase == PodPhase.DRAFTING &&
                    alice.shows(hasTestTag("pod-bot-warning-avatar") and hasContentDescription(cubeTip))
            }

            preferences.enabled = true
            val set = runBlocking {
                org.asyncttwiz.android.PodApi(E2eStack.origin).use { api ->
                    api.create(alice.account.token, CreatePod(UUID.randomUUID().toString(), "E2E rated bots", setCode = "TST",
                        settings = DraftSettings(packsPerPlayer = 1, cardsPerPack = 4, bots = 1))).id
                }
            }
            alice.pods.open(set)
            waitUntil("the rated set pod's setup") {
                alice.pods.state.detail?.id == set && alice.pods.state.detail?.source?.botRatings == true && alice.shows(hasTestTag("pod-add-bot"))
            }
            assertFalse(alice.shows(hasTestTag("pod-bot-warning-1")) || alice.shows(hasTestTag("pod-add-bot-warning")), alice.screen())
            ServerSetup.startPod(alice.account, set)
            alice.pods.refresh()
            waitUntil("Alice's pack in the rated pod") { alice.pods.state.detail?.takeIf { it.id == set }?.draft?.head != null }
            assertFalse(alice.shows(hasTestTag("pod-bot-warning-avatar")), alice.screen())
            val detail = alice.pods.state.detail!!
            val names = detail.definitions.associate { it.digest to it.definition.name }
            val rated = detail.draft!!.head!!.cards.first { names[it.definition] in setOf("Test Bear", "Test Bolt", "Test Caller", "Test Knight") }
            waitUntil("the bot's score on ${names[rated.definition]}") { alice.shows(hasTestTag("draft-bot-${rated.id}")) }
            alice.tagged("draft-bot-${rated.id}").performScrollTo().performClick()
            waitUntil("the breakdown starting with the card's rating") {
                alice.shows(hasTestTag("draft-bot-breakdown")) && alice.showsText("Rating (GIH WR ", substring = true)
            }
            // The bot drafts by the ratings to the end
            ServerSetup.pickUntil(listOf(alice.account), set) { ServerSetup.pod(alice.account, set).phase != PodPhase.DRAFTING }
            val done = ServerSetup.pod(alice.account, set)
            assertTrue(done.phase == PodPhase.DRAFTED && done.members.single { it.bot }.picksMade > 0, "${done.members}")
        } finally {
            preferences.enabled = false
        }
    }
}
