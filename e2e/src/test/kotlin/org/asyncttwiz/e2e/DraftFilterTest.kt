package org.asyncttwiz.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import org.asyncttwiz.android.PodTab
import org.asyncttwiz.protocol.DraftMode
import org.asyncttwiz.protocol.DraftSettings
import org.asyncttwiz.protocol.PodDeckCategory
import org.junit.Test
import java.util.UUID
import kotlin.test.assertTrue

/** Owner, 2026-11-19: a filter button in the corner of the Rotisserie pool and of Build opens a popup with a search field and quick filters. */
class DraftFilterTest : E2e() {
    private fun filterState(tag: String, state: String) =
        alice.shows(hasTestTag(tag) and SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, state))

    private fun openPod(name: String, pod: String) {
        waitUntil("Alice's menu") { alice.shows(hasTestTag("menu-PODS")) }
        alice.tagged("menu-PODS").performClick()
        waitUntil("the pod in Alice's list") { alice.showsText(name) }
        alice.text(name).performScrollTo().performClick()
        waitUntil("Alice's pod") { alice.pods.state.detail?.id == pod }
    }

    // DRF-250, DRF-251, DRF-252, DRF-253, DRF-255, DRF-153: on her phone Alice taps the Rotisserie pool's filter button; typing "instant" (a
    // type line's word) leaves the Storm Sparks; Rare (every test card is common) leaves none, Common the Sparks again; Clear brings
    // every card back
    @Test fun theRotisseriePoolFiltersByItsPopup() {
        launchPlayers(aliceCatalog = true)
        waitUntil("Alice's card records installed") { alice.catalog!!.state.ready }
        val name = "E2E draft filter ${UUID.randomUUID().toString().take(6)}"
        val pod = ServerSetup.draftingPod(alice.account, bob.account, name,
            settings = DraftSettings(mode = DraftMode.ROTISSERIE, packsPerPlayer = 3, cardsPerPack = 2, cardsPerPlayer = 5, poolPerPlayer = 6))
        openPod(name, pod)
        waitUntil("Alice's Rotisserie pool") { alice.pods.state.detail?.limited?.board?.size == 12 && alice.shows(hasTestTag("pod-rotisserie-filter")) }
        val detail = alice.pods.state.detail!!
        val names = detail.definitions.associate { it.digest to it.definition.name }
        val board = detail.limited!!.board.map { it.copy }
        fun shown() = board.filter { alice.shows(hasTestTag("pod-rotisserie-${it.id}")) }.map { names.getValue(it.definition) }.toSet()
        assertTrue(filterState("pod-rotisserie-filter", "No filter"), alice.screen())

        alice.tagged("pod-rotisserie-filter").performClick()
        waitUntil("the popup") { alice.shows(hasTestTag("pod-rotisserie-filter-field")) && alice.showsText("Filter cards") }
        alice.tagged("pod-rotisserie-filter-field").performTextInput("instant")
        waitUntil("only the Sparks") { shown() == setOf("Storm Spark") }
        assertTrue(filterState("pod-rotisserie-filter", "Filter on"), "the button shows the filter: ${alice.screen()}")

        alice.tagged("pod-rotisserie-filter-rarity-Rare").performClick()
        waitUntil("no rare card") { shown().isEmpty() && alice.showsText("No cards match") }
        alice.tagged("pod-rotisserie-filter-rarity-Rare").performClick()
        alice.tagged("pod-rotisserie-filter-rarity-Common").performClick()
        // The rarity comes from the card records, read when the popup opened
        waitUntil("the common Sparks") { shown() == setOf("Storm Spark") }

        alice.tagged("pod-rotisserie-filter-clear").performClick()
        waitUntil("every card back") { shown().size > 1 && filterState("pod-rotisserie-filter", "No filter") }
    }

    // DRF-250, DRF-254, DRF-255: in Build Alice's filter for creatures hides her Sparks from Main's columns (her Cubs stay) while Main's header
    // still counts the whole deck; on the Stats tab and back the filter is still set, its popup closed
    @Test fun buildFiltersByItsPopup() {
        launchPlayers()
        val name = "E2E build filter ${UUID.randomUUID().toString().take(6)}"
        val pod = ServerSetup.draftedPod(alice.account, bob.account, name, mainPlains = 10, cards = listOf(TestCards.cub, TestCards.spark))
        openPod(name, pod)
        waitUntil("Alice's Build") { alice.pods.state.detail?.build != null && alice.shows(hasTestTag("pod-build-filter")) }
        val detail = alice.pods.state.detail!!
        val names = detail.definitions.associate { it.digest to it.definition.name }
        val copies = detail.draft!!.pool.associateBy { it.id }
        val main = detail.build!!.copies.filter { it.category == PodDeckCategory.MAIN }.map { copies.getValue(it.copyId) }
        // Alice's six picks are random among the cube's Cubs and Sparks: what each check expects follows what she has
        val cubs = main.any { names[it.definition] == "Ember Cub" }
        val sparks = main.any { names[it.definition] == "Storm Spark" }
        fun shown(card: String) = main.filter { names[it.definition] == card }.any { alice.shows(hasTestTag("pod-build-card-${it.id}")) }
        waitUntil("every Main card shown") { shown("Ember Cub") == cubs && shown("Storm Spark") == sparks }
        val header = "Main ${main.size} + 10 = ${main.size + 10}"
        assertTrue(alice.showsText(header), alice.screen())

        alice.tagged("pod-build-filter").performClick()
        waitUntil("the popup") { alice.shows(hasTestTag("pod-build-filter-type-Creature")) }
        alice.tagged("pod-build-filter-type-Creature").performClick()
        waitUntil("only the Cubs") { shown("Ember Cub") == cubs && !shown("Storm Spark") && filterState("pod-build-filter", "Filter on") }
        assertTrue(alice.showsText(header), "the header counts the whole deck: ${alice.screen()}")

        alice.pods.tab(PodTab.STATS)
        waitUntil("the Stats tab") { !alice.shows(hasTestTag("pod-build")) }
        alice.pods.tab(PodTab.BUILD)
        waitUntil("Build again, still filtered, the popup closed") {
            shown("Ember Cub") == cubs && !shown("Storm Spark") && filterState("pod-build-filter", "Filter on") &&
                !alice.shows(hasTestTag("pod-build-filter-type-Creature"))
        }
    }
}
