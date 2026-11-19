package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Drafted basic lands (owner, 2026-11-19): they go to the basics count in Build, never to a column as cards. */
class DraftedBasicsTest : E2e() {
    // DRF-093, DRF-103: Alice picks a Mountain on the phone; in Build no Mountain card shows and Main's basics dialog counts it
    // ("Basics 1 · Lands 1")
    @Test fun aDraftedMountainGoesToTheBasicsCount() {
        launchPlayers()
        val name = "E2E basics ${UUID.randomUUID().toString().take(6)}"
        val pod = ServerSetup.draftingPod(alice.account, bob.account, name, cards = listOf(TestCards.mountain))
        waitUntil("Alice's menu") { alice.shows(hasTestTag("menu-PODS")) }
        alice.tagged("menu-PODS").performClick()
        waitUntil("the pod in Alice's list") { alice.showsText(name) }
        alice.text(name).performScrollTo().performClick()
        waitUntil("Alice's pack") { alice.pods.state.detail?.takeIf { it.id == pod }?.draft?.head != null }
        val detail = alice.pods.state.detail!!
        val names = detail.definitions.associate { it.digest to it.definition.name }
        val mountain = detail.draft!!.head!!.cards.firstOrNull { names[it.definition] == "Mountain" }
            ?: error("Alice's first pack has no Mountain: ${detail.draft!!.head!!.cards.map { names[it.definition] }}")
        alice.pods.pick(listOf(mountain.id), emptyList())
        waitUntil("the Mountain in Alice's pool, accepted") {
            alice.pods.state.detail?.draft?.pool?.any { it.id == mountain.id } == true && !alice.pods.state.uncertain &&
                alice.pods.state.pending == 0
        }
        val build = alice.pods.state.detail!!.build!!
        assertEquals(listOf(mountain.id), build.landed)
        assertEquals(1, build.basics.mountain)
        assertTrue(build.copies.none { it.copyId == mountain.id }, "the Mountain is no card in Build")

        alice.text("Build").performClick()
        waitUntil("Alice's Build") { alice.shows(hasTestTag("pod-build-basics")) }
        // The Lands column (Main's first) holds no card: the Mountain is no card there
        waitUntil("Main's Lands column empty") { alice.shows(hasTestTag("pod-build-column-count-MAIN-0") and hasText("0")) }
        alice.tagged("pod-build-basics").performClick()
        waitUntil("the drafted Mountain in the basics count") {
            alice.shows(hasTestTag("pod-build-basics-count") and hasText("Basics 1 · Lands 1"))
        }
        alice.text("Done").performClick()
    }
}
