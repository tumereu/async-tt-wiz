package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals

/** A draft deck's cover: chosen in Build, kept by the server, on the draft deck once submitted. */
class DraftDeckCoverTest : E2e() {
    // DRF-166, LOB-058: Alice taps the thumbnail beside the deck's name in Build and picks a card that isn't the default; the
    // server keeps it, her submitted draft deck has it, and a later choice in Build reaches the draft deck too
    @Test fun aCoverChosenInBuildGoesToTheDraftDeck() {
        launchPlayers()
        val name = "E2E cover ${UUID.randomUUID().toString().take(6)}"
        val pod = ServerSetup.draftedPod(alice.account, bob.account, name)
        waitUntil("Alice's menu") { alice.shows(hasTestTag("menu-PODS")) }
        alice.tagged("menu-PODS").performClick()
        waitUntil("the pod in Alice's list") { alice.showsText(name) }
        alice.text(name).performScrollTo().performClick()
        waitUntil("Alice's Build") { alice.shows(hasTestTag("pod-build-cover")) }
        val detail = alice.pods.state.detail!!
        val definitions = detail.draft!!.pool.associate { it.id to it.definition }
        val choices = detail.build!!.copies.map { definitions.getValue(it.copyId) }.distinct()
        check(choices.size > 1) { "the pool needs a second card to choose: $choices" }

        fun choose(digest: String) {
            alice.tagged("pod-build-cover").performClick()
            waitUntil("the cover list") { alice.shows(hasTestTag("pod-build-cover-$digest")) }
            alice.tagged("pod-build-cover-$digest").performClick()
            waitUntil("the choice saved") {
                alice.pods.state.pending == 0 && server(pod).deckCover == digest
            }
        }
        alice.tagged("pod-build-cover").performClick()
        waitUntil("the first card as the cover before any choice") {
            alice.shows(hasTestTag("pod-build-cover-${choices.first()}") and hasAnyDescendant(hasContentDescription("Selected cover")))
        }
        // LOB-058, DRF-166 (owner playtest 2026-11-09): the choices are listed alphabetically
        val names = choices.associateWith { digest -> alice.pods.state.detail!!.definitions.single { it.digest == digest }.definition.name }
        val listed = choices.sortedBy { alice.tagged("pod-build-cover-$it").fetchSemanticsNode().boundsInRoot.top }.map(names::getValue)
        assertEquals(listed.sortedBy { it.lowercase() }, listed, "the cover choices in alphabetical order")
        alice.text("Cancel").performClick()
        waitUntil("the list closed") { !alice.shows(hasTestTag("pod-build-cover-dialog")) }

        choose(choices[1])
        ServerSetup.submitPodDeck(alice.account, pod)
        assertEquals(choices[1], draftDeckCover())
        choose(choices[0])
        assertEquals(choices[0], draftDeckCover())
    }

    private fun server(pod: String) = runBlocking {
        org.asyncttwiz.android.PodApi(E2eStack.origin).use { it.detail(alice.account.token, pod) }
    }

    private fun draftDeckCover() = runBlocking {
        org.asyncttwiz.android.DeckApi(E2eStack.origin).use { it.drafts(alice.account.token) }.decks.single().cover?.definition
    }
}
