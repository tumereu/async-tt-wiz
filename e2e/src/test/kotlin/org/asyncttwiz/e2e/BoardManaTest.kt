package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.performClick
import org.asyncttwiz.protocol.game.PlaySettings
import org.junit.Test
import kotlin.test.assertFalse

/**
 * Owner notes 2026-11-14 (STK-024): casting with a mana source whose cost taps other creatures ("Tap two untapped creatures you
 * control: Add one mana of any color.") when no payment is chosen for you: the cast panel never offers a choice naming cards; it
 * says what is still owed and that the mana is produced on the board, where a tap on the source pays.
 */
class BoardManaTest : E2e() {
    // STK-024 (owner, 2026-11-14): Autotap off, Alice casts Blue Note ({U}) with her Hearth Elders and two Ash Pups out: the panel
    // says "Still owed {U}: produce the mana on the board." with no chip naming the Elders or the Pups; a tap on the Elders on the
    // table pays, and Cast on the main button casts it, two of her creatures tapped for it
    @Test fun aSourceTappingOtherCreaturesIsNeverAChoiceByName() {
        startGame(TestDeck("Elders", listOf(TestCards.elders to 14, TestCards.pup to 13, TestCards.blueNote to 13)), TestDeck.glimmers(),
            aliceSettings = PlaySettings(autotap = false), deal = { a, _ ->
                hand(a).count { it == "Hearth Elders" } >= 1 && hand(a).count { it == "Ash Pup" } >= 2 && "Blue Note" in hand(a)
            })
        bothOpen()
        bothKeep(bobAway = false)
        for (card in listOf("Hearth Elders", "Ash Pup", "Ash Pup")) {
            val before = alice.field(card).size
            alice.playFromHand(card, this)
            waitUntil("$card on Alice's battlefield", along = listOf(bob)) {
                alice.field(card).size == before + 1 && alice.inOwnMain() && alice.engine { device.session.pending.isEmpty() }
            }
        }
        alice.playFromHand("Blue Note", this)
        waitUntil("the panel, nothing paying: ${alice.screen()}") {
            alice.shows(hasContentDescription("Not paid: Still owed {U}: produce the mana on the board."))
        }
        assertFalse(alice.shows(hasContentDescription("Mana source ", substring = true)), "no source offered by name: ${alice.screen()}")
        assertFalse(alice.shows(hasText("Hearth Elders:", substring = true)), "no chip naming the cards the cost taps: ${alice.screen()}")
        alice.tap("Hide cast panel")
        waitUntil("the panel hidden") { alice.showsControl("Show cast panel") }
        alice.battlefieldCard("Hearth Elders").performClick()
        waitUntil("paid by the Elders, Cast usable on the main button: ${alice.screen()}") {
            alice.shows(hasTestTag("main-button") and hasContentDescription("Cast") and isEnabled())
        }
        val hand = alice.state.player(alice.me).hand.size
        alice.tap("Cast")
        waitUntil("Blue Note resolved, two creatures tapped for it", along = listOf(bob)) {
            alice.state.stack.isEmpty() && alice.inOwnMain() && alice.state.player(alice.me).hand.size == hand &&
                (alice.field("Hearth Elders") + alice.field("Ash Pup")).count { alice.state.obj(it)?.tapped == true } == 2
        }
        assertFalse(alice.refusalSeen, "no refusal: ${alice.shownTexts}")
    }
}
