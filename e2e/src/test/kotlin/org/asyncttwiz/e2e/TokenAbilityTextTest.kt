package org.asyncttwiz.e2e

import androidx.compose.ui.test.performClick
import org.asyncttwiz.android.AppLog
import org.asyncttwiz.android.AutomatumBoards
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Prod fault 2PTQG5 (owner, 0.2.0, 2026-11-16): a Food token's ability read "{2}, {T}, Sacrifice: …" in its menu and chips,
 * and a stand-in fault was reported. The tokens the rules define read their abilities as the rules word them.
 */
class TokenAbilityTextTest : E2e() {
    private val food = "{2}, {T}, Sacrifice this token: You gain 3 life."
    private val treasure = "{T}, Sacrifice this token: Add one mana of any color."
    private val clue = "{2}, Sacrifice this token: Draw a card."

    // SYS-086, TBL-115 (CR 111.10a, b, f): Alice casts Ember Pantry, making a Food, a Treasure and a Clue; a tap on her Food
    // (not usable now: one mana from the Treasure) opens its menu reading the Food's ability as the rules word it; what both
    // phones offer for the three tokens reads the same, and neither reports an ability without text
    @Test fun theTokensTheRulesDefineReadTheirAbilities() {
        val deck = TestDeck("Pantries", listOf(TestCards.mountain to 20, TestCards.pantry to 20))
        startGame(deck, deal = { a, _ -> "Ember Pantry" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Ember Pantry", this)
        val names = listOf("Food Token", "Treasure Token", "Clue Token")
        waitUntil("the three tokens on Alice's battlefield", along = listOf(bob)) { names.all { alice.field(it).size == 1 } && alice.inOwnMain() }
        waitUntil("and on Bob's table", along = listOf(bob)) { names.all { bob.field(it, alice.me).size == 1 } }

        alice.battlefieldCard("Food Token").performClick()
        waitUntil("the Food's menu reading its ability: ${alice.screen()}") { alice.showsText(food) }

        for (player in listOf(alice, bob)) {
            val plays = AutomatumBoards.cardPlays(player.shown.board, player.id)
            fun texts(name: String) = plays.of(atw(player.field(name, alice.me).single())).map { it.text }
            assertEquals(listOf(food), texts("Food Token"), player.name)
            assertEquals(listOf(treasure), texts("Treasure Token"), player.name)
            assertEquals(listOf(clue), texts("Clue Token"), player.name)
        }
        assertFalse("ability without text" in AppLog.text(), "no stand-in reported")
    }
}
