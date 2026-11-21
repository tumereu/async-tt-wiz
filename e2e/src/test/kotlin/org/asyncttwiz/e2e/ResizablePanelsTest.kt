package org.asyncttwiz.e2e

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import arcanum.engine.decision.Decision
import org.asyncttwiz.android.PANEL_HANDLE_TAG
import org.asyncttwiz.android.PanelSizePreferences
import org.asyncttwiz.appui.AppPlatform
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Resizable panels (owner, post-0.3.0 playtest: "all panels are that size if they are draggable"): Alice has made the panels
 * smaller before; she casts an Ember Recruiter, whose trigger first asks "you may" in a panel that fits (no handle, owner
 * 2026-11-21, E14), then searches her library (the panel lists the whole library, cut off at that size, so it has the handle);
 * she drags the panel's handle down to make it smaller. Her app restarts; her second Recruiter's search panel opens at that
 * same size.
 */
class ResizablePanelsTest : E2e() {
    private val choice = hasContentDescription("Automatum choice")
    private val inPanel = hasAnyAncestor(choice)

    /** Alice casts a Recruiter from her hand and says Yes to its search: its panel shows the library. */
    private fun aliceSearches(player: E2ePlayer) {
        player.playFromHand("Ember Recruiter", this)
        waitUntil("the Recruiter's trigger asking", along = listOf(bob)) {
            // Alice passes her own spell and trigger on (STK-026) as the main button says
            if (player.stepArrow() == "Resolve" || player.stepArrow() == "Pass") player.once { player.tap(player.stepArrow()!!) }
            player.state.pending?.decision is Decision.May && player.showsText("Yes")
        }
        // STK-222 (owner 2026-11-21, E14): the "you may" panel fits its content, so it has no handle
        assertFalse(player.shows(hasTestTag(PANEL_HANDLE_TAG)), "no handle on the panel that fits: ${player.screen()}")
        player.text("Yes").performClick()
        waitUntil("Alice's search: ${player.screen()}") {
            player.state.pending?.decision is Decision.ChooseObjects && player.shows(hasContentDescription("Ember Recruiter") and inPanel)
        }
        run(skip = java.time.Duration.ofMillis(500))
    }

    /** The search panel's height on [player]'s screen (px). */
    private fun height(player: E2ePlayer) = player.node(choice).fetchSemanticsNode().boundsInRoot.height

    /** [player] picks a Recruiter in the search and confirms: the search is done, on the server. */
    private fun finds(player: E2ePlayer) {
        val hand = player.state.player(player.me).hand.size
        // At the smallest size the cards may lie below the panel's words: scrolled into view first
        player.nodes(hasContentDescription("Ember Recruiter") and inPanel and !hasTestTag("choice-source"))[0].performScrollTo().performClick()
        waitUntil("the Recruiter picked") { player.shows(hasContentDescription("Ember Recruiter, chosen") and inPanel) }
        player.text("OK").performClick()
        waitUntil("the Recruiter found, on the server", along = listOf(bob)) {
            player.state.player(player.me).hand.size == hand + 1 && player.engine { device.session.pending.isEmpty() } &&
                serverGame()?.let { it.localHeads[player.id] == it.headId } == true
        }
    }

    // STK-222, STK-223: a panel that fits has no handle; dragging the cut-off search panel's handle down makes it smaller; after a
    // restart, the next panel opens at that size
    @Test fun aPanelsSizeIsSharedAndKeptAcrossARestart() {
        // The panels' shared size made smaller before (as by an earlier drag), so the search's cards don't all fit
        PanelSizePreferences(AppPlatform.current).write(240f)
        startGame(TestDeck("Recruiters", listOf(TestCards.mountain to 14, TestCards.recruiter to 13, TestCards.spark to 13)),
            deal = { a, _ -> hand(a).count { it == "Ember Recruiter" } >= 2 })
        bothOpen()
        bothKeep(bobAway = false)
        aliceSearches(alice)
        val before = height(alice)
        assertTrue(alice.shows(hasTestTag(PANEL_HANDLE_TAG)), "the handle on the search panel: ${alice.screen()}")
        alice.node(hasTestTag(PANEL_HANDLE_TAG)).performTouchInput {
            down(center)
            repeat(10) { moveBy(Offset(0f, 15f)) }
            up()
        }
        run(skip = java.time.Duration.ofMillis(500))
        val smaller = height(alice)
        assertTrue(smaller < before - 100f, "smaller by the drag: $before → $smaller px")
        finds(alice)

        alice = restart(alice)
        alice.openGame(game, this)
        waitUntil("Alice's table in her Main 1 again") { alice.inOwnMain() }
        aliceSearches(alice)
        assertEquals(smaller, height(alice), "the next panel, after the restart, has the size she dragged")
        finds(alice)
        assertFalse(alice.refusalSeen, "no refusal: ${alice.shownTexts}")
    }
}
