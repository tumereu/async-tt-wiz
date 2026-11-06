package org.asyncttwiz.e2e

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import arcanum.engine.input.Input
import arcanum.engine.state.TargetRef
import org.asyncttwiz.protocol.game.PlaySettings
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner, 2026-11-06 (Inspect shows changes): holding a card lists under it only what changed on it, the effects without
 * their source; a card and what is attached to it show with a line between them; a drag to the left pins Inspect, where a
 * tap on a change names its source. Alice's Ash Hound pumps itself ({R}: +1/+0 until end of turn) and wears an Ember
 * Blade (+1/+0).
 */
class InspectChangesTest : E2e() {
    private val changes = hasAnyAncestor(hasContentDescription("Card changes"))

    /** Every Activate Alice's device has made on its line, by the source card's id. */
    private fun activated(): List<String> = alice.engine { device.session.events }.flatMap { e ->
        e.engine?.steps.orEmpty().mapNotNull { (it.input as? Input.Activate)?.source?.let(::atw) } }

    /** Alice's Hound, pumped once, with her Ember Blade on it, in her Main 1. */
    private fun aliceEquipsHerPumpedHound() {
        startGame(TestDeck("Blades", listOf(TestCards.mountain to 14, TestCards.ashHound to 13, TestCards.blade to 13)),
            aliceSettings = PlaySettings(alwaysAssumeResolves = true),
            deal = { a, _ -> listOf("Mountain", "Ash Hound", "Ember Blade").all { it in hand(a) } })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield", along = listOf(bob)) { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        alice.playFromHand("Ash Hound", this)
        waitUntil("the Hound on the battlefield", along = listOf(bob)) { alice.field("Ash Hound").isNotEmpty() && alice.inOwnMain() }
        val hound = alice.field("Ash Hound").single()
        // TBL-122: its one usable ability, the pump, goes at once
        alice.battlefieldCard("Ash Hound").performClick()
        waitUntil("the pump resolved", along = listOf(bob)) {
            if (alice.stepArrow() == "Resolve") alice.once { alice.tap("Resolve") }
            atw(hound) in activated() && alice.state.stack.isEmpty() && alice.inOwnMain()
        }
        alice.playFromHand("Ember Blade", this)
        waitUntil("the Blade on the battlefield", along = listOf(bob)) { alice.field("Ember Blade").isNotEmpty() && alice.inOwnMain() }
        val blade = alice.field("Ember Blade").single()
        alice.battlefieldCard("Ember Blade").performClick()
        waitUntil("the Equip asking for its target") { alice.shows(hasContentDescription("Choose targets")) }
        alice.battlefieldCard("Ash Hound").performClick()
        waitUntil("the Blade on the Hound", along = listOf(bob)) {
            (alice.state.obj(blade)?.attachedTo as? TargetRef.Object)?.id == hound && alice.state.stack.isEmpty() && alice.inOwnMain()
        }
        run(skip = java.time.Duration.ofMillis(500))
    }

    /** The change lines Inspect shows now, top to bottom. */
    private fun lines(): List<String> = alice.nodes(hasText("", substring = true) and changes).fetchSemanticsNodes()
        .sortedBy { it.positionInRoot.y }.flatMap { node ->
            if (androidx.compose.ui.semantics.SemanticsProperties.Text in node.config)
                node.config[androidx.compose.ui.semantics.SemanticsProperties.Text].map { it.text } else emptyList() }

    // TBL-104, TBL-111 (owner 2026-11-06): holding the equipped Hound shows the Hound with its changes under it (the pump
    // until end of turn, the Blade's +1/+0, both without their source, entered this turn), a line, then the Blade with what
    // it is attached to; Bob, holding Alice's Hound, sees the same changes
    @Test fun holdingACardListsWhatChangedOnIt() {
        aliceEquipsHerPumpedHound()
        holding(alice.battlefieldCard("Ash Hound")) {
            assertTrue(alice.shows(hasContentDescription("Card inspection content")), alice.screen())
            assertEquals(listOf("+1/+0 until end of turn", "+1/+0", "Entered this turn",
                "Attached to Ash Hound", "Entered this turn"), lines())
            alice.nodes(hasContentDescription("Card changes")).fetchSemanticsNodes().let { assertEquals(2, it.size) }
            assertTrue(!alice.showsText("From Ember Blade"), "no source unless pinned and asked")
        }
        assertTrue(!alice.shows(hasContentDescription("Card inspection content")), "closed on release")
        // Bob watches Alice's moves (playing along) until her equipped Hound shows on his table
        waitUntil("Alice's Hound with its Blade on Bob's table", along = listOf(bob)) {
            bob.showsOnBattlefield("Ash Hound", alice) &&
                bob.field("Ember Blade", alice.me).any { bob.state.obj(it)?.attachedTo != null }
        }
        holding(bob.battlefieldCard("Ash Hound", alice)) {
            assertTrue(bob.showsText("+1/+0 until end of turn") && bob.showsText("Attached to Ash Hound"), bob.screen())
        }
    }

    // TBL-131 (owner 2026-11-06): pinned Inspect: holding the Hound and dragging left keeps Inspect open after release with a
    // close button; a tap on a change names its source; Close closes it
    @Test fun aDragToTheLeftPinsInspect() {
        aliceEquipsHerPumpedHound()
        val hound = alice.battlefieldCard("Ash Hound")
        holding(hound) { moveHeldFinger(hound, Offset(-200f, 0f)) }
        run(skip = java.time.Duration.ofMillis(400))
        assertTrue(alice.shows(hasContentDescription("Card inspection content")), "still open after release: ${alice.screen()}")
        assertTrue(alice.showsControl("Close inspection"), alice.screen())
        alice.text("+1/+0").performClick()
        waitUntil("the Blade named as the source") { alice.showsText("From Ember Blade") }
        alice.text("+1/+0 until end of turn").performClick()
        waitUntil("the Hound named as the pump's source") { alice.showsText("From Ash Hound") }
        alice.tap("Close inspection")
        waitUntil("Inspect closed") { !alice.shows(hasContentDescription("Card inspection content")) }
        // The next hold is an ordinary one again: it closes on release
        holding(alice.battlefieldCard("Ash Hound")) { assertTrue(alice.shows(hasContentDescription("Card inspection content"))) }
        assertTrue(!alice.shows(hasContentDescription("Card inspection content")), "closed on release")
    }
}
