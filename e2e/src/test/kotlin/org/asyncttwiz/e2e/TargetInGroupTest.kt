package org.asyncttwiz.e2e

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Test

/**
 * Owner bug, prod 2026-11-07: the opponent cast a spell with two targets at the owner's two creatures lying in one
 * battlefield group (a stack, TBL-053) and couldn't target both: a tap on the group meant one creature (the identical cards
 * of a stack counting as one), and the next tap meant that same one again. Now, while targets are picked, a tap on a group
 * of several always opens it, and each member is picked by a tap of its own (TBL-130).
 */
class TargetInGroupTest : E2e() {
    /** Alice's Ash Pups on Bob's screen, from the left. */
    private fun pups(): List<SemanticsNode> {
        val field = bob.battlefieldBounds(alice)
        return bob.nodes(hasContentDescription("Ash Pup, owner ${alice.name}, ", substring = true)).fetchSemanticsNodes()
            .filter { field.contains(it.boundsInRoot.center) }.sortedBy { it.boundsInRoot.left }
    }

    /** Whether Alice's three Pups lie side by side on Bob's screen, each whole: their group opened (in a closed stack the
     * cards under the top one show only as thin strips). */
    private fun opened(): Boolean = pups().let { p ->
        val sizes = p.map { minOf(it.boundsInRoot.width, it.boundsInRoot.height) }
        p.size == 3 && sizes.min() > .8f * sizes.max() &&
            p.zipWithNext().all { (a, b) -> b.boundsInRoot.left >= a.boundsInRoot.right - 1f } }

    private fun SemanticsNode.tapped(): Boolean =
        config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty().contains(", tapped")

    private fun tappedPups(): Int = alice.field("Ash Pup").count { alice.state.obj(it)?.tapped == true }

    /** Bob passes where his phone stops in Alice's turn (his free Snares could respond there, STK-036). */
    private fun bobPasses() {
        if (!bob.settled(this) || bob.state.turn.active == bob.me) return
        bob.once { bob.stepArrow()?.takeIf { it in listOf("Pass", "Next", "Resolve") }?.let(bob::tap) }
    }

    /** Bob casts a Frost Snare and taps Alice's (closed) group of Pups: it opens. */
    private fun snareOpensThePups() {
        bob.playFromHand("Frost Snare", this)
        waitUntil("Bob's target bar") { bob.shows(hasContentDescription("Choose targets")) }
        bob.battlefieldCard("Ash Pup", owner = alice).performClick()
        waitUntil("Alice's Pups opened side by side on Bob's screen") { opened() }
    }

    // TBL-130, TBL-053, STK-047: Alice's three identical Ash Pups lie in one stack; Bob's Frost Snare ("Tap up to two target
    // creatures") opens the stack on his first tap and picks two of the Pups one tap each: both are tapped, and the group
    // closes once the targets are chosen. His second Snare picks from the same group, now a tapped and an untapped Pup:
    // both are targeted, all three Pups end tapped
    @Test fun twoTargetsArePickedFromOneGroup() {
        startGame(TestDeck("Pups", listOf(TestCards.mountain to 10, TestCards.pup to 30)),
            TestDeck("Snares", listOf(TestCards.mountain to 20, TestCards.snare to 20)),
            deal = { a, b -> hand(a).count { it == "Ash Pup" } >= 3 && hand(b).count { it == "Frost Snare" } >= 2 })
        bothOpen()
        bothKeep(bobAway = false)
        for (n in 1..3) {
            alice.playFromHand("Ash Pup", this)
            waitUntil("Pup $n on Alice's battlefield", along = listOf(bob)) { alice.field("Ash Pup").size == n && alice.inOwnMain() }
        }
        alice.hold("To combat")
        waitUntil("Bob's Main 1", along = listOf(alice)) { bobPasses(); bob.inOwnMain() }
        waitUntil("the Pups in one stack on Bob's screen") {
            bob.shows(hasContentDescription("Ash Pup, owner ${alice.name},", substring = true) and
                hasContentDescription("stack of 3", substring = true))
        }

        // Two identical, untapped Pups
        snareOpensThePups()
        bob.battlefieldCard("Ash Pup", owner = alice, index = 0).performClick()
        run(skip = java.time.Duration.ofMillis(300))
        check(opened()) { "the group stays open while targets are picked from it: ${bob.screen()}" }
        bob.battlefieldCard("Ash Pup", owner = alice, index = 1).performClick()
        waitUntil("two of Alice's Pups tapped by the Snare", along = listOf(alice)) { tappedPups() == 2 && bob.inOwnMain() }
        waitUntil("the group closed again on Bob's screen") { !opened() }

        // A tapped and an untapped Pup of the same group
        snareOpensThePups()
        val shown = pups()
        val untapped = shown.indexOfFirst { !it.tapped() }
        val tapped = shown.indexOfFirst { it.tapped() }
        check(untapped >= 0 && tapped >= 0) { "a tapped and an untapped Pup: ${bob.screen()}" }
        bob.battlefieldCard("Ash Pup", owner = alice, index = untapped).performClick()
        run(skip = java.time.Duration.ofMillis(300))
        bob.battlefieldCard("Ash Pup", owner = alice, index = tapped).performClick()
        waitUntil("the Snare resolved: all three Pups tapped", along = listOf(alice)) {
            tappedPups() == 3 && bob.inOwnMain() && bob.state.stack.isEmpty()
        }
    }
}
