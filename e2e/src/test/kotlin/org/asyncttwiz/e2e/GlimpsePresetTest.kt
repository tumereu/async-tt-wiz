package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import org.asyncttwiz.protocol.DraftSettings
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Owner, 2026-11-14 (DRF-031, DRF-033): Glimpse is a preset every account starts with, an ordinary one: it fills the New pod
 * form like any preset, and it can be deleted.
 */
class GlimpsePresetTest : E2e() {
    // DRF-031, DRF-032, DRF-033: Alice's new account has Glimpse on the server; on New pod it loads 9 packs that burn 2,
    // and its delete button (confirmed) removes it from her presets
    @Test fun everyAccountHasAGlimpsePresetThatCanBeDeleted() {
        launchPlayers()
        assertEquals(listOf("Glimpse" to DraftSettings.glimpse()), ServerSetup.presets(alice.account).map { it.name to it.settings })
        waitUntil("Alice's menu") { alice.shows(hasTestTag("menu-PODS")) }
        alice.tagged("menu-PODS").performClick()
        waitUntil("Alice's Pods") { alice.shows(hasTestTag("new-pod")) }
        alice.tagged("new-pod").performClick()
        waitUntil("the New pod form") { alice.pods.state.creating != null }
        alice.tagged("pod-presets").performClick()
        waitUntil("Glimpse in Alice's presets") { alice.showsText("Glimpse") && alice.showsText("Booster · No source · 9×15") }
        alice.text("Glimpse").performClick()
        waitUntil("the form filled from Glimpse") { alice.pods.state.creating?.settings == DraftSettings.glimpse() }

        alice.tagged("pod-presets").performClick()
        waitUntil("the presets again") { alice.shows(hasContentDescription("Delete Glimpse")) }
        alice.described("Delete Glimpse").performClick()
        waitUntil("the delete question") { alice.showsText("Delete “Glimpse”?") }
        alice.node(hasClickAction() and hasAnyDescendant(hasText("Delete"))).performClick()
        waitUntil("Glimpse deleted") { ServerSetup.presets(alice.account).isEmpty() && !alice.showsText("Booster · No source · 9×15") }
    }
}
