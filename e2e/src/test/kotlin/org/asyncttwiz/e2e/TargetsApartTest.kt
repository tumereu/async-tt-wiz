package org.asyncttwiz.e2e

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.performClick
import arcanum.engine.state.StackItemKind
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.PlaySettings
import org.asyncttwiz.protocol.game.ResponseMode
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner playtest 2026-11-27: "Stack spell targeting arrows problems: cannot see what is being targeted when casting a response."
 * Bob casts Ember Flick at one of Alice's three identical Ash Pups: on her table that Pup lies apart from the other two (TBL-053),
 * and when she responds with her own Flick, Bob's arrow to it stays drawn while she picks its target (STK-126).
 */
class TargetsApartTest : E2e() {
    private fun description(player: E2ePlayer, tag: String): String? = player.nodes(hasTestTag(tag)).fetchSemanticsNodes().firstOrNull()
        ?.config?.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()

    /** Alice's Ash Pups on [player]'s screen, from the left. */
    private fun pups(player: E2ePlayer): List<SemanticsNode> {
        val field = player.battlefieldBounds(alice)
        return player.nodes(hasContentDescription("Ash Pup, owner ${alice.name}, ", substring = true)).fetchSemanticsNodes()
            .filter { field.contains(it.boundsInRoot.center) }.sortedBy { it.boundsInRoot.left }
    }

    private fun SemanticsNode.label(): String = config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty()

    /** Bob passes where his phone stops in Alice's turn (his free Flicks could respond there, STK-036). */
    private fun bobPasses() {
        if (!bob.settled(this) || bob.state.turn.active == bob.me) return
        bob.once { bob.stepArrow()?.takeIf { it in listOf("Pass", "Next", "Resolve") }?.let(bob::tap) }
    }

    // TBL-053, TBL-178, STK-058, STK-126, STK-047 (owner playtest 2026-11-27)
    @Test fun theTargetLiesApartAndItsArrowStaysWhileAliceResponds() {
        startGame(TestDeck("Pups and Flicks", listOf(TestCards.mountain to 10, TestCards.pup to 20, TestCards.flick to 10)),
            TestDeck("Flicks", listOf(TestCards.mountain to 20, TestCards.flick to 20)), aliceSettings = PlaySettings(mode = ResponseMode.Normal),
            deal = { a, b -> hand(a).count { it == "Ash Pup" } >= 3 && "Ember Flick" in hand(a) && "Ember Flick" in hand(b) })
        bothOpen()
        bothKeep(bobAway = false)
        for (n in 1..3) {
            alice.playFromHand("Ash Pup", this)
            waitUntil("Pup $n on Alice's battlefield", along = listOf(bob)) { alice.field("Ash Pup").size == n && alice.inOwnMain() }
        }
        alice.hold("To combat")
        waitUntil("Bob's Main 1", along = listOf(alice)) { bobPasses(); bob.inOwnMain() }
        waitUntil("the Pups in one stack on both screens") {
            listOf(alice, bob).all { p -> pups(p).count { "stack of 3" in it.label() } == 1 }
        }

        // Bob casts his Flick at one of Alice's Pups: his tap opens her stack (TBL-165), the next picks the first Pup
        bob.playFromHand("Ember Flick", this)
        waitUntil("Bob's target bar") { bob.shows(hasText("Choose any target") and isHeading()) }
        bob.battlefieldCard("Ash Pup", owner = alice).performClick()
        run(skip = java.time.Duration.ofMillis(300))
        bob.battlefieldCard("Ash Pup", owner = alice, index = 0).performClick()
        waitUntil("Alice holding priority over Bob's Flick") {
            alice.status == GameDevice.Status.YourMove && alice.state.let { s ->
                s.stack.isNotEmpty() && s.priority.holder == alice.me && s.stackItems[s.stack.last()]?.kind == StackItemKind.Spell }
        }
        // TBL-053: on Alice's table the targeted Pup lies on its own right after the stack of the other two, in its row
        waitUntil("the targeted Pup apart on Alice's table") {
            val shown = pups(alice)
            val stack = shown.filter { "stack of 2" in it.label() }
            val alone = shown.filter { "stack of" !in it.label() && it.boundsInRoot.left > (stack.singleOrNull()?.boundsInRoot?.right ?: Float.MAX_VALUE) - 1f }
            stack.size == 1 && alone.size == 1 && kotlin.math.abs(alone.single().boundsInRoot.center.y - stack.single().boundsInRoot.center.y) < 2f
        }
        assertEquals("Targets of the top Stack entry: Ash Pup", description(alice, "stack-target-arrows"), alice.screen())

        // STK-126: Alice responds with her own Flick; while she picks its target, Bob's arrow to the Pup stays drawn
        alice.playFromHand("Ember Flick", this)
        waitUntil("Alice's target bar") { alice.shows(hasText("Choose any target") and isHeading()) }
        run(skip = java.time.Duration.ofMillis(300))
        assertEquals("Targets of the top Stack entry: Ash Pup", description(alice, "stack-target-arrows"),
            "Bob's arrow while Alice picks: ${alice.screen()}")
        // She picks Bob's life square: her Flick goes on the Stack over his, and once it is the top entry its own arrow shows
        alice.node(hasContentDescription("${bob.name}, life", substring = true)).performClick()
        waitUntil("Alice's Flick on the Stack, its arrow at Bob", along = listOf(bob)) {
            alice.state.stack.size == 2 && description(alice, "stack-target-arrows") == "Targets of the top Stack entry: ${bob.name}"
        }
        assertTrue(alice.leaks.leaks.isEmpty() && bob.leaks.leaks.isEmpty(), "${alice.leaks.leaks} ${bob.leaks.leaks}")
    }
}
