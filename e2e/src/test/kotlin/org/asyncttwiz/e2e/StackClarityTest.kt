package org.asyncttwiz.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.performClick
import arcanum.engine.input.Input
import arcanum.engine.state.StackItemKind
import org.asyncttwiz.android.EngineEventText
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.PlaySettings
import org.asyncttwiz.protocol.game.ResponseMode
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner playtest 2026-11-07: "How are opponent cast spell targets etc shown? My friend cast Fractured Sanity, and whilst I
 * think it's probably the cast not the cycle effect, the UI does not really make this clear." Bob casts Ember Flick at
 * Alice's Ash Pup: at her pause a line on her table says who cast it, what it does and at what, and an arrow points at the
 * Pup. Then Bob cycles Shattered Reason: her line names its cycling trigger, and the event reads that Bob cycled it.
 */
class StackClarityTest : E2e() {
    private val aliceDeck = TestDeck("Pups", listOf(TestCards.mountain to 20, TestCards.pup to 20))
    private val bobDeck = TestDeck("Flicks and Reasons", listOf(TestCards.mountain to 14, TestCards.flick to 13, TestCards.reason to 13))

    private fun description(player: E2ePlayer, tag: String): String? = player.nodes(hasTestTag(tag)).fetchSemanticsNodes().firstOrNull()
        ?.config?.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()

    /** Alice holds priority over Bob's top Stack entry of [kind] (her Normal mode stops there, STK-036). */
    private fun aliceHoldsOver(kind: (StackItemKind?) -> Boolean): Boolean = alice.status == GameDevice.Status.YourMove &&
        alice.state.let { s -> s.stack.isNotEmpty() && s.priority.holder == alice.me && kind(s.stackItems[s.stack.last()]?.kind) }

    // STK-057, STK-058, STK-046, HIS-091 (owner playtest 2026-11-07)
    @Test fun theOtherPlayersSpellAndCyclingTriggerSayWhatTheyAre() {
        startGame(aliceDeck, bobDeck, aliceSettings = PlaySettings(mode = ResponseMode.Normal),
            deal = { a, b -> "Ash Pup" in hand(a) && "Mountain" in hand(b) && "Ember Flick" in hand(b) && "Shattered Reason" in hand(b) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Ash Pup", this)
        waitUntil("the Pup on Alice's battlefield", along = listOf(bob)) { alice.field("Ash Pup").isNotEmpty() && alice.inOwnMain() }
        alice.hold(requireNotNull(alice.stepArrow()))
        // Bob plays Alice's turn back and answers its stops (her pauses his phone was assumed to pass) up to his Main 1
        waitUntil("Bob's Main 1") {
            val inBobsMain = bob.state.turn.active == bob.me && bob.state.turn.step == arcanum.api.TurnStep.PrecombatMain
            when (bob.status) {
                GameDevice.Status.CatchUp -> bob.once { bob.tapToWatch() }
                is GameDevice.Status.Stopped -> if (!inBobsMain) bob.stepArrow()?.let { label -> bob.once { bob.tap(label) } }
                else -> {}
            }
            bob.inOwnMain()
        }
        bob.playFromHand("Mountain", this)
        waitUntil("Bob's Mountain") { bob.field("Mountain").isNotEmpty() && bob.inOwnMain() }

        // Bob casts Ember Flick at Alice's Pup, picking it on his table
        bob.playFromHand("Ember Flick", this)
        waitUntil("Bob's target bar") { bob.shows(hasText("Choose any target") and isHeading()) }
        bob.battlefieldCard("Ash Pup", owner = alice).performClick()
        waitUntil("Alice holding priority over Bob's Flick") { aliceHoldsOver { it == StackItemKind.Spell } }
        waitUntil("the line on Alice's table") {
            description(alice, "stack-line") == "${bob.name} cast Ember Flick · deals 1 damage to any target → Ash Pup"
        }
        assertEquals("Targets of the top Stack entry: Ash Pup", description(alice, "stack-target-arrows"), alice.screen())
        // Bob sees the arrow too (his own spell), but no line
        waitUntil("Bob's arrow") { description(bob, "stack-target-arrows") == "Targets of the top Stack entry: Ash Pup" }
        assertTrue(!bob.shows(hasTestTag("stack-line")), bob.screen())
        alice.tap("Resolve")
        waitUntil("the Flick resolved, the Pup gone", along = listOf(bob)) {
            alice.state.stack.isEmpty() && alice.field("Ash Pup").isEmpty() && bob.inOwnMain()
        }
        assertTrue(!alice.shows(hasTestTag("stack-line")) && !alice.shows(hasTestTag("stack-target-arrows")), alice.screen())

        // Bob cycles Shattered Reason from its chip in one tap (ZON-080): its cycling trigger goes on the Stack over the cycling
        bob.handCard("Shattered Reason").performClick()
        waitUntil("the Reason's chips") { bob.shows(hasContentDescription("Way Cycling {R}")) }
        bob.node(hasContentDescription("Way Cycling {R}")).performClick()
        waitUntil("Alice holding priority over the cycling trigger") { aliceHoldsOver { it == StackItemKind.Triggered } }
        waitUntil("the line naming the cycling trigger") {
            description(alice, "stack-line") == "Shattered Reason's cycling trigger · each opponent mills 4 cards"
        }
        assertTrue(!alice.shows(hasTestTag("stack-target-arrows")), "no targets, no arrows: ${alice.screen()}")
        // HIS-091: the event reads that Bob cycled it
        val cycled = alice.shown.line.last { e -> e.engine?.steps?.any { it.input is Input.Activate } == true }
        assertEquals("${bob.name} cycled Shattered Reason", EngineEventText.of(cycled, alice.state, alice.id, names()))
        assertTrue(alice.leaks.leaks.isEmpty() && bob.leaks.leaks.isEmpty(), "${alice.leaks.leaks} ${bob.leaks.leaks}")
    }
}
