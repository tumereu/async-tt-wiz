package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner playtest 2026-11-05 evening: an X cast has no separate OK before Cast, X can't be set past what the mana pays, and
 * the cast panel has no Cancel. Alice plays a Mountain and casts Ember Burst ({X}: X damage to any target): X goes from 0
 * to 1 and no further, Cast goes on to the target, and Bob takes 1.
 */
class XCastTest : E2e() {
    /** The cast panel's Cast button: the smallest clickable node holding the word "Cast". */
    private fun E2ePlayer.castButton(): androidx.compose.ui.semantics.SemanticsNode =
        nodes(hasClickAction() and hasAnyDescendant(hasText("Cast"))).fetchSemanticsNodes().minBy { it.size.width * it.size.height }

    // STK-023, STK-138, STK-052, STK-140: X bounded by the mana (one Mountain: at most 1), no OK and no Cancel; Cast answers X and asks the target
    @Test fun anXCastHasNoOkAndStopsAtWhatTheManaPays() {
        startGame(TestDeck.bursts(), deal = { a, _ -> "Mountain" in hand(a) && "Ember Burst" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield", along = listOf(bob)) { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        alice.playFromHand("Ember Burst", this)
        waitUntil("Alice's cast panel with X at 0") { alice.shows(hasContentDescription("Cast panel")) && alice.shows(hasContentDescription("Value 0")) }
        assertTrue(alice.shows(hasText("Cast Ember Burst") and isHeading()), "the panel's title names the card (STK-023)")
        assertTrue(alice.shows(hasContentDescription("One less") and isNotEnabled()), "X at its least")
        alice.tap("One more")
        waitUntil("X at 1") { alice.shows(hasContentDescription("Value 1")) }
        assertTrue(alice.shows(hasContentDescription("One more") and isNotEnabled()), "+ stops at what one Mountain pays")
        assertFalse(alice.showsText("OK"), "no OK")
        assertFalse(alice.showsText("Cancel"), "no Cancel")
        val cast = alice.castButton()
        assertTrue(isEnabled().matches(cast), "Cast enabled")
        alice.node(androidx.compose.ui.test.SemanticsMatcher("Cast button") { it.id == cast.id }).performClick()
        waitUntil("Alice's target bar") { alice.shows(hasText("Choose any target") and isHeading()) }
        assertFalse(alice.showsText("Cancel"), "the target bar has no Cancel")
        alice.node(hasContentDescription("${bob.name}, life 20", substring = true)).performClick()
        waitUntil("the Burst dealing 1 to Bob, on the server", along = listOf(bob)) {
            alice.state.player(bob.me).life == 19 && alice.engine { device.session.pending.isEmpty() }
        }
        assertFalse(alice.refusalSeen, "no refusal: ${alice.shownTexts}")
    }

    // STK-023, STK-139, STK-052, STK-140 (owner gallery review, 2026-11-06): a tap on X opens a numeric input; a value past what the mana pays
    // isn't taken (its check greyed, the reason shown), one it pays is; Cast goes on with it
    @Test fun xIsTypedInTheNumericInput() {
        startGame(TestDeck.bursts(), deal = { a, _ -> "Mountain" in hand(a) && "Ember Burst" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield", along = listOf(bob)) { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        alice.playFromHand("Ember Burst", this)
        waitUntil("Alice's cast panel with X at 0") { alice.shows(hasContentDescription("Cast panel")) && alice.shows(hasContentDescription("Value 0")) }
        alice.described("Value 0").performClick()
        waitUntil("the numeric input") { alice.shows(hasContentDescription("Number")) }
        alice.described("Number").performTextReplacement("5")
        waitUntil("5 refused") { alice.showsText("At most 1.") }
        val ok = hasClickAction() and hasContentDescription("Use this number")
        assertTrue(alice.shows(ok and isNotEnabled()), "the check greyed past what the mana pays")
        alice.described("Number").performTextReplacement("1")
        waitUntil("1 taken") { alice.shows(ok and isEnabled()) }
        alice.node(ok and isEnabled()).performClick()
        waitUntil("X at 1") { alice.shows(hasContentDescription("Value 1")) && !alice.shows(hasContentDescription("Number")) }
        val cast = alice.castButton()
        alice.node(androidx.compose.ui.test.SemanticsMatcher("Cast button") { it.id == cast.id }).performClick()
        waitUntil("Alice's target bar") { alice.shows(hasText("Choose any target") and isHeading()) }
        alice.node(hasContentDescription("${bob.name}, life 20", substring = true)).performClick()
        waitUntil("the Burst dealing 1 to Bob", along = listOf(bob)) { alice.state.player(bob.me).life == 19 }
        assertFalse(alice.refusalSeen, "no refusal: ${alice.shownTexts}")
    }

    /** How many of [this] player's table cards are outlined as paying the open cast (STK-024, STK-156's amber outline). */
    private fun E2ePlayer.outlinedSources(): Int =
        nodes(androidx.compose.ui.test.SemanticsMatcher("source") { "source" in it.config.getOrElse(org.asyncttwiz.android.TableMarks) { emptySet() } })
            .fetchSemanticsNodes().size

    // STK-024, STK-164 (owner notes 2026-11-14 evening: changing X while casting didn't change the outlined sources): with three Ember
    // Shards out, the outline follows X as it is set: none at X = 0, one at 1, two at 2, one again at 1; Cast pays with them
    @Test fun theOutlineFollowsX() {
        startGame(TestDeck("Shard bursts", listOf(TestCards.shard to 20, TestCards.burst to 20)),
            deal = { a, _ -> hand(a).count { it == "Ember Shard" } >= 3 && "Ember Burst" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        repeat(3) { n ->
            alice.playFromHand("Ember Shard", this)
            waitUntil("${n + 1} Shards out", along = listOf(bob)) {
                alice.field("Ember Shard").size == n + 1 && alice.inOwnMain() && alice.engine { device.session.pending.isEmpty() }
            }
        }
        alice.playFromHand("Ember Burst", this)
        waitUntil("Alice's cast panel with X at 0") { alice.shows(hasContentDescription("Cast panel")) && alice.shows(hasContentDescription("Value 0")) }
        waitUntil("nothing outlined at X = 0") { alice.outlinedSources() == 0 }
        alice.tap("One more")
        waitUntil("one Shard outlined at X = 1: ${alice.outlinedSources()}") { alice.shows(hasContentDescription("Value 1")) && alice.outlinedSources() == 1 }
        alice.tap("One more")
        waitUntil("two Shards outlined at X = 2: ${alice.outlinedSources()}") { alice.shows(hasContentDescription("Value 2")) && alice.outlinedSources() == 2 }
        alice.tap("One less")
        waitUntil("one Shard outlined at X = 1 again: ${alice.outlinedSources()}") { alice.shows(hasContentDescription("Value 1")) && alice.outlinedSources() == 1 }
        alice.tap("One more")
        waitUntil("two again") { alice.shows(hasContentDescription("Value 2")) && alice.outlinedSources() == 2 }
        val cast = alice.castButton()
        alice.node(androidx.compose.ui.test.SemanticsMatcher("Cast button") { it.id == cast.id }).performClick()
        waitUntil("Alice's target bar") { alice.shows(hasText("Choose any target") and isHeading()) }
        alice.node(hasContentDescription("${bob.name}, life 20", substring = true)).performClick()
        waitUntil("the Burst dealing 2 to Bob, on the server", along = listOf(bob)) {
            alice.state.player(bob.me).life == 18 && alice.engine { device.session.pending.isEmpty() }
        }
        assertEquals(2, alice.field("Ember Shard").count { alice.state.obj(it)?.tapped == true }, "the two outlined Shards paid")
        assertFalse(alice.refusalSeen, "no refusal: ${alice.shownTexts}")
    }
}
