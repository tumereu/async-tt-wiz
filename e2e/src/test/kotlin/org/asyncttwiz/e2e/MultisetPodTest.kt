package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import org.asyncttwiz.protocol.PodDetail
import org.asyncttwiz.protocol.PodPhase
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Multiset drafting (owner, 2026-11-10): Alice, whose card records are the server's catalog (the test cards'), makes a
 * Booster pod whose first round is Test Set and whose second and third rounds are Thornwall Crossing, by tapping the pack
 * grid's row labels; she saves that as a preset, creates the pod and starts it with Bob. Every round's packs come from
 * its own set, and the preset brings the grid back to a new pod.
 */
class MultisetPodTest : E2e() {
    private val testSet = setOf("Test Bear", "Test Caller", "Test Bolt", "Test Knight")
    private fun thornwall(name: String) = name.startsWith("Thorn Scout") || name.endsWith("// Thornwall Colossus")

    private fun headNames(detail: PodDetail): List<String> {
        val names = detail.definitions.associate { it.digest to it.definition.name }
        return detail.draft!!.head!!.cards.map { names.getValue(it.definition) }
    }

    private fun chooseFor(tag: String, setName: String) {
        alice.tagged(tag).performScrollTo().performClick()
        waitUntil("the set list for $tag") { alice.shows(hasTestTag("pack-set-title")) }
        alice.node(hasClickAction() and hasAnyDescendant(hasText(setName))).performScrollTo().performClick()
        waitUntil("the set list closed") { !alice.pods.setPicker.state.open }
    }

    // DRF-177, DRF-178, DRF-179, DRF-169, DRF-032
    @Test fun aMixedPackGridDraftsEachRoundFromItsOwnSetAndAPresetKeepsIt() {
        launchPlayers(aliceCatalog = true)
        waitUntil("Alice's card records installed") { alice.catalog!!.state.ready }
        waitUntil("Alice's menu") { alice.shows(hasTestTag("menu-PODS")) }
        alice.tagged("menu-PODS").performClick()
        waitUntil("Alice's Pods") { alice.shows(hasTestTag("new-pod")) }
        alice.tagged("new-pod").performClick()
        waitUntil("the New pod form") { alice.pods.state.creating != null }
        alice.pods.chooseSet()
        waitUntil("the Source dialog") { alice.showsText("Sets") }
        alice.node(hasText("Sets")).performClick()
        waitUntil("the set in the list") { alice.showsText("Test Set") }
        alice.node(hasClickAction() and hasAnyDescendant(hasText("Test Set"))).performScrollTo().performClick()
        waitUntil("the set chosen as the source") { alice.pods.state.creating?.set?.code == "TST" }
        // One set by default; Mix sets opens the grid: three rounds of one pack (Booster's defaults)
        waitUntil("Mix sets") { alice.shows(hasTestTag("mix-sets")) }
        alice.tagged("mix-sets").performScrollTo().performClick()
        waitUntil("the pack grid") { alice.shows(hasTestTag("pack-grid")) }
        chooseFor("pack-row-1", "Thornwall Crossing")
        chooseFor("pack-row-2", "Thornwall Crossing")
        assertEquals(listOf("TST", "TWL"), alice.pods.state.creating!!.packs)
        waitUntil("the source reads Mixed sets") { alice.showsText("Mixed sets") }

        // Saved as a preset, with its grid
        val presetName = "Two sets ${UUID.randomUUID().toString().take(8)}"
        alice.tagged("pod-presets").performClick()
        waitUntil("the presets") { alice.shows(hasTestTag("pod-preset-save")) }
        alice.tagged("pod-preset-save").performClick()
        waitUntil("the preset name field") { alice.shows(hasTestTag("pod-preset-name")) }
        alice.tagged("pod-preset-name").performTextInput(presetName)
        alice.node(hasClickAction() and hasAnyDescendant(hasText("Save"))).performClick()
        waitUntil("the preset saved") { ServerSetup.presets(alice.account).any { it.name == presetName } }
        val preset = ServerSetup.presets(alice.account).single { it.name == presetName }
        assertEquals(listOf("TST", "TWL"), preset.packSets)
        assertEquals("TST", preset.setCode)
        alice.pods.presets.dismiss()

        alice.nodes(hasClickAction() and hasAnyDescendant(hasText("Create"))).onFirst().performClick()
        waitUntil("the pod from the grid") { alice.pods.state.detail?.source?.packSets == listOf("TST", "TWL") }
        val pod = alice.pods.state.detail!!
        assertEquals(PodPhase.SETUP, pod.phase)

        // Round 1's packs are Test Set's
        ServerSetup.joinAndStart(alice.account, bob.account, pod.id)
        alice.pods.refresh()
        waitUntil("Alice's first pack") { alice.pods.state.detail?.draft?.head != null }
        val first = alice.pods.state.detail!!
        assertEquals(1, first.draft!!.head!!.number)
        assertTrue(headNames(first).all { it in testSet }, headNames(first).toString())
        // Rounds 2 and 3 are Thornwall Crossing's
        for (round in listOf(2, 3)) {
            ServerSetup.pickUntil(listOf(alice.account, bob.account), pod.id) {
                listOf(alice.account, bob.account).all { ServerSetup.pod(it, pod.id).draft?.head?.number == round }
            }
            for (player in listOf(alice.account, bob.account)) {
                val names = headNames(ServerSetup.pod(player, pod.id))
                assertEquals(15, names.size, "round $round: Thornwall Crossing's play booster")
                assertTrue(names.all(::thornwall), "round $round of $player: $names")
            }
        }
        alice.pods.refresh()
        waitUntil("Alice's round 3 pack on her phone") { alice.pods.state.detail?.draft?.head?.number == 3 }
        assertTrue(headNames(alice.pods.state.detail!!).all(::thornwall))

        // The preset brings the grid back to a new pod
        alice.pods.newPod()
        waitUntil("a new pod form") { alice.pods.state.creating != null }
        alice.tagged("pod-presets").performClick()
        waitUntil("the saved preset listed") { alice.showsText(presetName) }
        alice.text(presetName).performClick()
        waitUntil("the preset's grid") { alice.pods.state.creating?.packs == listOf("TST", "TWL") }
        waitUntil("the grid shown") { alice.shows(hasTestTag("pack-grid")) }
    }
}
