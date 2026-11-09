package org.asyncttwiz.e2e

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlinx.coroutines.runBlocking
import org.asyncttwiz.protocol.PodDeckBasics
import org.asyncttwiz.protocol.SetPodBasics
import org.junit.Test
import java.util.UUID

/** The Build headers' warnings and Submit go by one rule (DRF-094, DRF-119). */
class BuildWarningsTest : E2e() {
    // DRF-094, DRF-119: Alice's Main is one card short: its header warns and Submit is greyed; one more basic ends both at once
    @Test fun mainsWarningAndSubmitChangeTogether() {
        launchPlayers()
        val name = "E2E warning ${UUID.randomUUID().toString().take(6)}"
        val pod = ServerSetup.draftedPod(alice.account, bob.account, name, mainPlains = 0)
        runBlocking {
            org.asyncttwiz.android.PodApi(E2eStack.origin).use { api ->
                val detail = api.detail(alice.account.token, pod)
                val plains = 39 - detail.build!!.copies.size
                api.basics(alice.account.token, pod, SetPodBasics(UUID.randomUUID().toString(), detail.draftAttemptId!!, PodDeckBasics(plains = plains)))
            }
        }
        waitUntil("Alice's menu") { alice.shows(hasTestTag("menu-PODS")) }
        alice.tagged("menu-PODS").performClick()
        waitUntil("the pod in Alice's list") { alice.showsText(name) }
        alice.text(name).performScrollTo().performClick()
        waitUntil("Main's warning, saying why") {
            alice.shows(hasTestTag("pod-build-warning-MAIN") and hasContentDescription("Main needs 40 cards"))
        }
        alice.node(hasClickAction() and hasAnyDescendant(hasText("Submit deck (39)"))).assertIsNotEnabled()

        alice.tagged("pod-build-basics").performClick()
        waitUntil("the basics dialog") { alice.shows(hasTestTag("pod-build-basics-count")) }
        alice.described("Add Plains").performClick()
        alice.text("Done").performClick()
        waitUntil("the warning gone with 40 in Main") {
            !alice.shows(hasTestTag("pod-build-warning-MAIN")) && alice.showsText("Submit deck (40)")
        }
        alice.node(hasClickAction() and hasAnyDescendant(hasText("Submit deck (40)"))).assertIsEnabled()
    }
}
