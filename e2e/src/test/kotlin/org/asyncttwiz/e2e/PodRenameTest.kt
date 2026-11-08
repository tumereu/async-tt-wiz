package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals

/**
 * Owner bug, 2026-11-08 (problem report NZTPS6, 0.2.0-beta8): renaming a pod after creating it failed. Emptying the name
 * field to type a new one saved the empty name after a moment; the server refused it and the field went back to the old
 * name with "Settings not saved."
 */
class PodRenameTest : E2e() {
    // DRF-029, NZTPS6: Alice empties the name, the save that follows sends nothing, and the name she then types is saved
    @Test fun aPodIsRenamedByEmptyingTheNameAndTypingANewOne() {
        launchPlayers()
        val name = "E2E pod ${UUID.randomUUID().toString().take(6)}"
        val pod = ServerSetup.setupPod(alice.account, name)
        waitUntil("Alice's menu") { alice.shows(hasTestTag("menu-PODS")) }
        alice.tagged("menu-PODS").performClick()
        waitUntil("the pod in Alice's list") { alice.showsText(name) }
        alice.text(name).performScrollTo().performClick()
        waitUntil("the pod open on Alice's screen") { alice.pods.state.detail?.id == pod && alice.pods.state.pending == 0 }

        alice.pods.editSetup(name = "")
        alice.pods.flushSetup()
        waitUntil("the save settled") { alice.pods.state.pending == 0 && !alice.pods.state.loading }
        assertEquals("", alice.pods.state.detail?.name, "the field stays empty for the new name")
        assertEquals(null, alice.pods.state.error)

        alice.pods.editSetup(name = "Riders of Rohan")
        alice.pods.flushSetup()
        waitUntil("the new name saved") { ServerSetup.podName(alice.account, pod) == "Riders of Rohan" }
        waitUntil("the new name on Alice's screen") { alice.pods.state.pending == 0 && alice.showsText("Riders of Rohan") }
        assertEquals(null, alice.pods.state.error)
    }
}
