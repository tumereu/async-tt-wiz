package org.asyncttwiz.e2e

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import org.asyncttwiz.protocol.DraftSettings
import org.asyncttwiz.protocol.PodDeckCategory
import org.junit.Test
import org.robolectric.annotation.Config
import java.util.UUID
import kotlin.test.assertTrue

/**
 * A pod on the desktop layout (docs/specs/web-client.md §5, §7.6, step 6b; wireframe D9): Alice, on a computer at 1440 × 900,
 * picks with clicks and keys and moves her Build cards by their edge zones; Bob on his phone's layout.
 */
@Config(qualifiers = "w1440dp-h900dp-land-mdpi")
class DesktopDraftTest : E2e() {
    private fun openPod(pod: String) {
        waitUntil("Alice's side menu with the pod") { alice.shows(hasTestTag("navigation-pod-$pod")) }
        alice.tagged("navigation-pod-$pod").performClick()
    }

    private fun card(id: String) = alice.tagged("pod-pick-card-$id")
    private fun marked(id: String, mark: String) =
        alice.shows(hasTestTag("pod-pick-card-$id") and SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, mark))

    // WEB-044, WEB-028, WEB-036, DRF-060: a right-click burns a card, a click keeps another, K over a third keeps it instead,
    // Enter confirms: the server takes the third card into Alice's pool. Owner playtest 2026-11-16: Confirm sits at the end of the
    // trays' row, not full width under them; WEB-062: the tabs share the header's row
    @Test fun aPickByClicksAndKeys() {
        launchPlayers(aliceDesktop = true)
        val name = "E2E desktop pick ${UUID.randomUUID().toString().take(6)}"
        val pod = ServerSetup.draftingPod(alice.account, bob.account, name,
            settings = DraftSettings(packsPerPlayer = 3, cardsPerPack = 3, keep = 1, burn = 1))
        openPod(pod)
        waitUntil("Alice's pack") { alice.pods.state.detail?.takeIf { it.id == pod }?.draft?.head != null }
        val (burned, clicked, keyed) = alice.pods.state.detail!!.draft!!.head!!.cards.map { it.id }
        waitUntil("Alice's pick screen") { alice.shows(hasTestTag("pod-pick-card-$keyed")) }
        card(burned).performMouseInput { moveTo(center); press(androidx.compose.ui.test.MouseButton.Secondary); release(androidx.compose.ui.test.MouseButton.Secondary) }
        waitUntil("a right-click burned the first card") { marked(burned, "Burn") }
        card(clicked).performMouseInput { click() }
        waitUntil("a click kept the second") { marked(clicked, "Keep") }
        // WEB-044 (owner playtest 2026-11-15): the marks and the trays' chips half as large again as the phone's
        fun height(matcher: SemanticsMatcher) = alice.nodes(matcher).onFirst().fetchSemanticsNode().boundsInRoot.height
        for (id in listOf(burned, clicked)) {
            val badge = height(hasTestTag("pod-pick-badge") and hasAnyAncestor(hasTestTag("pod-pick-card-$id")))
            assertTrue(badge >= 28f, "a 1.5× badge on $id: $badge")
        }
        for (chip in listOf("pod-pick-keep-chip", "pod-pick-burn-chip"))
            height(hasTestTag(chip)).let { assertTrue(it >= 18f, "a 1.5× $chip: $it") }
        fun bounds(matcher: SemanticsMatcher) = alice.nodes(matcher).onFirst().fetchSemanticsNode().boundsInRoot
        val confirm = bounds(hasText("Confirm"))
        val trays = bounds(hasTestTag("pod-pick-burn-tray"))
        assertTrue(confirm.left >= trays.right && confirm.top < trays.bottom && confirm.bottom > trays.top,
            "Confirm at the end of the trays' row: $confirm, $trays")
        val header = bounds(hasTestTag("pod-play-header"))
        val tabs = bounds(hasTestTag("pod-tabs"))
        assertTrue(tabs.top >= header.top && tabs.bottom <= header.bottom, "the tabs in the header's row: $tabs, $header")
        card(keyed).performMouseInput { moveTo(center) }
        run()
        assertTrue(alice.key("k", true), "K taken over the third card")
        alice.key("k", false)
        waitUntil("K kept the third instead") { marked(keyed, "Keep") && marked(clicked, "Unmarked") && marked(burned, "Burn") }
        assertTrue(alice.key("Enter", true), "Enter taken")
        alice.key("Enter", false)
        waitUntil("the server took the third card into Alice's pool") {
            ServerSetup.pod(alice.account, pod).draft?.pool.orEmpty().any { it.id == keyed }
        }
        assertTrue(ServerSetup.pod(alice.account, pod).draft?.pool.orEmpty().none { it.id == clicked || it.id == burned }, "only the kept card")
    }

    // WEB-061, DRF-118 (owner playtest 2026-11-15): Alice's Stats keeps a readable width in two columns, the curve and the
    // mana table left, the donut, notes and counters right, where she writes a note and raises a counter
    @Test fun statsInTwoColumnsWithNotes() {
        launchPlayers(aliceDesktop = true)
        val name = "E2E desktop stats ${UUID.randomUUID().toString().take(6)}"
        val pod = ServerSetup.draftedPod(alice.account, bob.account, name)
        openPod(pod)
        waitUntil("Alice's Build") { alice.pods.state.detail?.takeIf { it.id == pod }?.build != null && alice.shows(hasTestTag("pod-build")) }
        alice.text("Stats").performClick()
        waitUntil("Alice's Stats in two columns") { alice.shows(hasTestTag("pod-stats-left")) && alice.shows(hasTestTag("pod-stats-right")) }
        fun bounds(tag: String) = alice.tagged(tag).fetchSemanticsNode().boundsInRoot
        assertTrue(bounds("pod-stats").width <= 1120f, "a readable width: ${bounds("pod-stats")}")
        val left = bounds("pod-stats-left")
        val right = bounds("pod-stats-right")
        assertTrue(right.left > left.right, "side by side: $left, $right")
        for (tag in listOf("pod-stats-curve", "pod-stats-mana"))
            assertTrue(bounds(tag).let { it.left >= left.left && it.right <= left.right }, "$tag on the left")
        for (tag in listOf("pod-stats-donut-Colors", "pod-stats-notes", "pod-stats-counter-4"))
            assertTrue(bounds(tag).let { it.left >= right.left && it.right <= right.right }, "$tag on the right")
        alice.tagged("pod-stats-notes").performTextInput("Green-white, two drops")
        alice.described("Increase Green").performClick()
        // (The test players' apps don't save notes: their pod controller has no save scheduler, so only Alice's own notes are read.)
        waitUntil("the note and the counter in Alice's notes") {
            alice.pods.notes()?.let { it.text == "Green-white, two drops" && it.counters[4] == 1 } == true &&
                alice.shows(hasTestTag("pod-stats-counter-4") and hasAnyDescendant(hasContentDescription("Green 1")))
        }
    }

    // WEB-045, WEB-046, DRF-099: a click on a Main card's left edge zone moves it to Side; → over another Main card moves it to Maybe.
    // Owner playtest 2026-11-16: a click in a card's middle opens no menu and moves nothing
    @Test fun buildCardsMoveByTheirEdgeZones() {
        launchPlayers(aliceDesktop = true)
        val name = "E2E desktop build ${UUID.randomUUID().toString().take(6)}"
        val pod = ServerSetup.draftedPod(alice.account, bob.account, name)
        openPod(pod)
        waitUntil("Alice's Build") { alice.pods.state.detail?.takeIf { it.id == pod }?.build != null && alice.shows(hasTestTag("pod-build")) }
        fun count(category: PodDeckCategory) = alice.pods.state.detail!!.build!!.copies.count { it.category == category }
        fun mainCard() = alice.nodes(hasContentDescription(", Main", substring = true)).onFirst()
        val main = count(PodDeckCategory.MAIN)
        mainCard().performMouseInput { click(center) }
        run()
        assertTrue(!alice.showsText("→ Side") && !alice.showsText("→ Maybe"), "no card menu on the desktop layout")
        assertTrue(count(PodDeckCategory.MAIN) == main && alice.pods.state.pending == 0, "a click in the middle moves nothing")
        mainCard().performMouseInput { moveTo(Offset(width * .1f, height / 2f)) }
        waitUntil("the left edge zone shown") { alice.shows(hasTestTag("edge-zone-left-active")) && alice.showsText("◂ Side") }
        mainCard().performMouseInput { click(Offset(width * .1f, height / 2f)) }
        waitUntil("one card in Side") { count(PodDeckCategory.SIDE) == 1 && count(PodDeckCategory.MAIN) == main - 1 && alice.pods.state.pending == 0 }
        mainCard().performMouseInput { moveTo(center) }
        run()
        assertTrue(alice.key("ArrowRight", true), "→ taken over a Main card")
        alice.key("ArrowRight", false)
        waitUntil("one card in Maybe") { count(PodDeckCategory.MAYBE) == 1 && count(PodDeckCategory.MAIN) == main - 2 && alice.pods.state.pending == 0 }
        assertTrue(!alice.showsText("Review ›"), "no one-by-one review on the desktop layout")
    }

    // WEB-063, WEB-045 (owner playtest 2026-11-16): Build's columns overlap, only the covered cards' name strips showing; the
    // pointer on a strip brings that card to the front with its edge zones; a Shift+click on an edge zone moves every copy
    @Test fun buildColumnsOverlapAndShiftClickMovesEveryCopy() {
        launchPlayers(aliceDesktop = true)
        val name = "E2E desktop columns ${UUID.randomUUID().toString().take(6)}"
        // Three one-mana red cards, four of each: Alice's six picks are at least two of them, in one column, one with copies
        val pod = ServerSetup.draftedPod(alice.account, bob.account, name, cards = listOf(TestCards.spark, TestCards.cub, TestCards.hound))
        openPod(pod)
        waitUntil("Alice's Build") { alice.pods.state.detail?.takeIf { it.id == pod }?.build != null && alice.shows(hasTestTag("pod-build")) }
        val strip = SemanticsMatcher("a covered card's name strip") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("pod-build-strip-") == true
        }
        waitUntil("a covered card in the one-mana column") { alice.shows(strip) }
        val cardBounds = alice.nodes(SemanticsMatcher("a Build card") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("pod-build-card-") == true
        }).fetchSemanticsNodes().map { it.boundsInRoot }.sortedBy { it.top }
        assertTrue(cardBounds.size >= 2 && cardBounds[1].top - cardBounds[0].top < cardBounds[0].height * .2f,
            "only a name strip of the covered card shows: $cardBounds")
        alice.nodes(strip).onFirst().performMouseInput { moveTo(Offset(width * .9f, height / 2f)) }
        waitUntil("the covered card's edge zones") { alice.shows(hasTestTag("edge-zone-right-active")) && alice.showsText("Maybe ▸") }
        // The card Alice has most copies of goes to Maybe whole by a Shift+click on its right edge zone
        val detail = alice.pods.state.detail!!
        val copies = detail.draft!!.pool.associateBy { it.id }
        val (digest, ids) = detail.build!!.copies.filter { it.category == PodDeckCategory.MAIN }
            .groupBy { copies.getValue(it.copyId).definition }.maxBy { it.value.size }
        assertTrue(ids.size >= 2, "a card with copies: $ids")
        val cardName = detail.definitions.first { it.digest == digest }.definition.name
        val card = alice.nodes(hasContentDescription("$cardName, ${ids.size} copies, Main")).onFirst()
        card.performMouseInput { moveTo(Offset(width * .9f, height / 2f)) }
        run()
        alice.key("Shift", true, shift = true)
        card.performMouseInput { click(Offset(width * .9f, height / 2f)) }
        alice.key("Shift", false)
        fun maybe() = alice.pods.state.detail!!.build!!.copies.count { it.category == PodDeckCategory.MAYBE }
        waitUntil("every copy of $cardName in Maybe") { maybe() == ids.size && alice.pods.state.pending == 0 }
        assertTrue(ServerSetup.pod(alice.account, pod).build!!.copies.filter { it.copyId in ids.map { it.copyId } }
            .all { it.category == PodDeckCategory.MAYBE }, "the server has every copy in Maybe")
    }
}
