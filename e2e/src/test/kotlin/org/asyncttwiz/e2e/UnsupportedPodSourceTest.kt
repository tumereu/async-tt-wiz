package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.runBlocking
import org.asyncttwiz.android.DeckApi
import org.asyncttwiz.android.PodApi
import org.asyncttwiz.android.PodFailure
import org.asyncttwiz.protocol.*
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Owner gallery review, 2026-11-06: a cube or set with cards Automatum can't play can't be drafted. Alice has a cube
 * holding an Odd Relic (rules text Automatum can't do), its result kept as her phone works it out: the warning icon says
 * "Automatum can't play 1 card yet: Odd Relic", New pod from it greys Create with the reason, and the server refuses the
 * pod.
 */
class UnsupportedPodSourceTest : E2e() {
    // DRF-161, DRF-162, LOB-115: the cube's warning names the card; New pod's Create is greyed; the server refuses the pod
    @Test fun aCubeWithACardAutomatumCantPlayCantBeDrafted() {
        val (a, _) = ServerSetup.accounts()
        val cards = listOf(TestCards.mountain, TestCards.spark, TestCards.relic)
        val cube = DeckApi(E2eStack.origin).use { api -> runBlocking {
            api.submit(a.token, SubmitDefinitions(UUID.randomUUID().toString(), cards.map { it.submitted() }))
            val saved = api.create(a.token, CreateCube(UUID.randomUUID().toString(), "E2E odd cube", cards.map { SavedCubeEntry(it.digest, 4) }))
            // As Alice's phone keeps the result when she saves the cube (LOB-124)
            val support = CardListSupport(org.asyncttwiz.storage.CardDefinitions.parserVersion, listOf("Odd Relic"), arcanum.engine.Engine.SUPPORT_VERSION)
            assertTrue(api.setCubeSupport(a.token, saved.cube.id, SetCardListSupport(saved.cube.revision, support)).stored)
            saved.cube
        } }
        launchPlayers()
        waitUntil("Alice's menu") { alice.shows(hasTestTag("menu-PODS")) }
        alice.tagged("menu-PODS").performClick()
        waitUntil("Alice's Pods") { alice.shows(hasTestTag("new-pod")) }
        alice.pods.newPod(cube.id)
        waitUntil("New pod from the cube, its warning shown") {
            alice.shows(hasTestTag("automatum-blocked")) && alice.shows(hasContentDescription("Automatum can't play 1 card yet: Odd Relic"))
        }
        val create = alice.nodes(hasClickAction() and hasAnyDescendant(hasText("Create"))).fetchSemanticsNodes()
        assertEquals(1, create.size, alice.screen())
        assertTrue(isNotEnabled().matches(create.single()), "Create greyed: ${alice.screen()}")
        // The server refuses it too
        PodApi(E2eStack.origin).use { api -> runBlocking {
            try {
                api.create(a.token, CreatePod(UUID.randomUUID().toString(), "E2E odd pod", cube.id))
                fail("the server made a pod from the cube")
            } catch (refused: PodFailure) {
                assertEquals("automatum_unsupported", refused.code)
            }
        } }
    }
}
