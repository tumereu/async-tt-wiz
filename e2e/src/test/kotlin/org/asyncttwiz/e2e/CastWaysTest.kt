package org.asyncttwiz.e2e

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner-approved casting rework (2026-11-07): a raised hand card's chips are its complete ways to cast it, a chip's tap
 * casts at once (ZON-080), and the cast panel shows what pays the cast, chosen by tapping sources on the table (STK-024).
 * Alice's deck: Mountains, Ember Shards (a free mana rock) and Ember Kits (free, offspring {R}).
 */
class CastWaysTest : E2e() {
    /** The cast panel's Cast button: the smallest clickable node holding the word "Cast". */
    private fun E2ePlayer.castButton(): SemanticsNode =
        nodes(hasClickAction() and hasAnyDescendant(hasText("Cast"))).fetchSemanticsNodes().minBy { it.size.width * it.size.height }

    /** The payment line's "Payment: Paid with …" text, null when the panel shows none. */
    private fun E2ePlayer.payment(): String? = nodes(hasContentDescription("Payment: ", substring = true)).fetchSemanticsNodes()
        .firstOrNull()?.config?.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }?.joinToString()

    private fun E2ePlayer.tapped(name: String): Boolean = field(name).single().let { state.obj(it)?.tapped == true }

    // ZON-080 (owner 2026-11-07): the raised Kit's "Cast with offspring {R}" chip casts it with offspring in one tap: no
    // cast panel, Autotap taps the Mountain and the Kit enters with its token copy
    @Test fun anOffspringChipCastsInOneTap() {
        startGame(TestDeck.kits(), deal = { a, _ -> "Mountain" in hand(a) && "Ember Kit" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield", along = listOf(bob)) { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        alice.handCard("Ember Kit").performClick()
        waitUntil("the Kit's chips") { alice.shows(hasContentDescription("Way Cast with offspring {R}")) }
        assertTrue(alice.shows(hasContentDescription("Way Cast")), "the plain cast's chip too: ${alice.screen()}")
        alice.node(hasContentDescription("Way Cast with offspring {R}")).performClick()
        assertFalse(alice.shows(hasContentDescription("Cast panel")), "no cast panel")
        waitUntil("the Kit and its token copy on the battlefield, on the server", along = listOf(bob)) {
            alice.field("Ember Kit").size == 2 && alice.inOwnMain() && alice.engine { device.session.pending.isEmpty() }
        }
        assertTrue(alice.tapped("Mountain"), "Autotap tapped the Mountain")
        assertFalse(alice.refusalSeen, "no refusal: ${alice.shownTexts}")
    }

    // STK-024 (owner 2026-11-08, partial override): in the Kit's cast panel with Offspring on, Alice takes Autotap's source out on
    // the table (the panel hidden with its eye meanwhile, as it covers her lands in portrait): Autotap fills in with the other one;
    // Cast pays with it: it is tapped, the one she took out stays untapped
    @Test fun thePanelPaysWithAHandPickedSource() {
        startGame(TestDeck.kits(), deal = { a, _ -> "Mountain" in hand(a) && "Ember Shard" in hand(a) && "Ember Kit" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield", along = listOf(bob)) { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        alice.playFromHand("Ember Shard", this)
        waitUntil("the Shard on the battlefield", along = listOf(bob)) { alice.field("Ember Shard").isNotEmpty() && alice.inOwnMain() }
        // A tap on the raised Kit (two ways) opens its cast panel
        alice.playFromHand("Ember Kit", this)
        waitUntil("the Kit's cast panel") { alice.shows(hasContentDescription("Extra Offspring {R}")) }
        alice.node(hasContentDescription("Extra Offspring {R}")).performClick()
        waitUntil("Autotap's source chosen") { alice.payment()?.startsWith("Payment: Paid with ") == true }
        val suggested = alice.payment()!!.removePrefix("Payment: Paid with ")
        val other = if (suggested == "Mountain") "Ember Shard" else "Mountain"
        // STK-024 (owner gallery review 2026-11-09, gallery 102): what pays reads from the table's outlines, not a sentence of the
        // panel's; a source under the panel is the one thing the table can't show, so only it is named
        assertFalse(alice.showsText("Paid with", substring = true), "no sentence naming the sources: ${alice.screen()}")
        waitUntil("the source under the panel named: ${alice.screen()}") { alice.showsText("Out of view: $suggested") }
        fun onTable(name: String, check: () -> Boolean, what: String) {
            alice.tap("Hide cast panel")
            waitUntil("the panel hidden") { alice.showsControl("Show cast panel") }
            alice.battlefieldCard(name).performClick()
            alice.tap("Show cast panel")
            waitUntil(what) { check() }
        }
        onTable(suggested, { alice.payment() == "Payment: Paid with $other" }, "$suggested taken out: Autotap fills in with $other")
        val cast = alice.castButton()
        alice.node(SemanticsMatcher("Cast button") { it.id == cast.id }).performClick()
        waitUntil("the Kit and its token copy on the battlefield, on the server", along = listOf(bob)) {
            alice.field("Ember Kit").size == 2 && alice.inOwnMain() && alice.engine { device.session.pending.isEmpty() }
        }
        assertTrue(alice.tapped(other), "$other, filled in by Autotap, paid")
        assertFalse(alice.tapped(suggested), "$suggested, taken out by hand, stayed untapped")
        assertFalse(alice.refusalSeen, "no refusal: ${alice.shownTexts}")
    }
}
