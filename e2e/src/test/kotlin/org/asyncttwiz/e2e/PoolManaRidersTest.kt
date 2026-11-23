package org.asyncttwiz.e2e

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import org.asyncttwiz.android.POOL_MANA_SPARKLE
import org.asyncttwiz.protocol.game.PlaySettings
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Owner playtest note (2026-11-22): mana can carry restrictions and properties ("can only be used to cast creature spells"); are they
 * visible? Alice (Autotap off) taps her Gildleaf Grove ("{T}: Add {G}. Spend this mana only to cast creature spells.") on the table and
 * casts Hedge Cub ({1}): the cast panel's pool mana shows the effects sparkle, holding it says its restriction in its own words with its
 * source, and paying with it casts the Cub.
 */
class PoolManaRidersTest : E2e() {
    private fun E2ePlayer.pool() = state.player(me).manaPool.size

    // STK-228, TBL-136, STK-024 (owner 2026-11-22): restricted pool mana in the cast panel shows the sparkle and its words on hold
    @Test fun restrictedPoolManaShowsTheSparkleAndItsWords() {
        startGame(TestDeck("Gildleaf cubs", listOf(TestCards.gildleaf to 20, TestCards.hedgeCub to 20)),
            aliceSettings = PlaySettings(autotap = false),
            deal = { a, _ -> "Gildleaf Grove" in hand(a) && "Hedge Cub" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Gildleaf Grove", this)
        waitUntil("the Grove out", along = listOf(bob)) { alice.field("Gildleaf Grove").size == 1 && alice.inOwnMain() && alice.engine { device.session.pending.isEmpty() } }
        waitUntil("Alice's game settled") { alice.settled(this) }
        alice.battlefieldCard("Gildleaf Grove").performClick()
        waitUntil("the Grove's mana in Alice's pool", along = listOf(bob)) { alice.pool() == 1 && alice.inOwnMain() && alice.engine { device.session.pending.isEmpty() } }
        assertEquals(listOf("Spend this mana only to cast creature spells."), alice.state.player(alice.me).manaPool.single().words)
        alice.playFromHand("Hedge Cub", this)
        waitUntil("the panel showing the pool's green mana with the sparkle: ${alice.screen()}") {
            alice.shows(hasContentDescription("Green mana from your pool") and hasTestTag(POOL_MANA_SPARKLE))
        }
        val words = "Green mana from your pool. From Gildleaf Grove: Spend this mana only to cast creature spells."
        alice.node(hasTestTag(POOL_MANA_SPARKLE)).performTouchInput { down(center) }
        waitUntil("the hold saying the restriction: ${alice.shownTexts}") { alice.shows(hasText(words)) }
        alice.node(hasTestTag(POOL_MANA_SPARKLE)).performTouchInput { up() }
        assertFalse(alice.shows(hasText("It can be spent only on some spells or abilities", substring = true)), "no generic text")
        waitUntil("the tip gone") { !alice.shows(hasText(words)) }
        alice.described("Green mana from your pool").performClick()
        waitUntil("the green chosen") { alice.shows(hasContentDescription("Green mana from your pool, chosen")) }
        val cast = alice.nodes(hasClickAction() and hasAnyDescendant(hasText("Cast"))).fetchSemanticsNodes().minBy { it.size.width * it.size.height }
        alice.node(SemanticsMatcher("Cast button") { it.id == cast.id }).performClick()
        waitUntil("Hedge Cub out, the pool spent", along = listOf(bob)) {
            alice.field("Hedge Cub").size == 1 && alice.pool() == 0 && alice.inOwnMain() && alice.engine { device.session.pending.isEmpty() }
        }
        assertFalse(alice.refusalSeen, "no refusal: ${alice.shownTexts}")
    }
}
