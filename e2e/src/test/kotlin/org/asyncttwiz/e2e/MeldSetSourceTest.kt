package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import org.asyncttwiz.android.PodSetChoice
import org.asyncttwiz.protocol.PodPhase
import org.asyncttwiz.protocol.SetBoosterKind
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Problem report 7ZZC89: Final Fantasy couldn't be chosen as a draft set ("Could not load source."): MTGJSON links a meld
 * result to both its fronts, which failed the whole set. Alice's phone has the server's card catalog (the test cards',
 * with Thornwall Crossing, an invented set with a meld pair): she picks the set in the Source dialog and creates the pod,
 * which goes to the server by the set's code (DRF-169); once Bob joins and she starts, her first pack is the server's,
 * built from its card catalog.
 */
class MeldSetSourceTest : E2e() {
    // DRF-167, DRF-019, DRF-169, DRF-090
    @Test fun aSetWithMeldCardsCanBeChosenAndItsPodStartsWithServerBuiltPacks() {
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
        waitUntil("the set in the list") { alice.showsText("Thornwall Crossing") }
        alice.node(hasText("Thornwall Crossing")).performClick()
        waitUntil("the set chosen as the source") { alice.pods.state.creating?.set != null }
        assertEquals(PodSetChoice("TWL", "Thornwall Crossing"), alice.pods.state.creating!!.set)
        alice.nodes(hasClickAction() and hasAnyDescendant(hasText("Create"))).onFirst().performClick()
        waitUntil("the pod from the set") { alice.pods.state.detail?.source?.setCode == "TWL" }
        val detail = alice.pods.state.detail!!
        assertEquals(PodPhase.SETUP, detail.phase)
        assertEquals(SetBoosterKind.RECIPE_BASED, detail.source.boosterKind)
        assertEquals(null, alice.pods.state.error)
        // Bob joins and Alice starts: her first pack is the set's play booster, from the server's card catalog
        ServerSetup.joinAndStart(alice.account, bob.account, detail.id)
        alice.pods.refresh()
        waitUntil("Alice's first pack") { alice.pods.state.detail?.draft?.head != null }
        val started = alice.pods.state.detail!!
        val pack = started.draft!!.head!!.cards
        assertEquals(15, pack.size, "the set's play booster")
        val names = started.definitions.associate { it.digest to it.definition.name }
        assertTrue(pack.all { names.getValue(it.definition).let { name -> name.startsWith("Thorn Scout") || name.endsWith("// Thornwall Colossus") } },
            pack.map { names[it.definition] }.toString())
    }
}
