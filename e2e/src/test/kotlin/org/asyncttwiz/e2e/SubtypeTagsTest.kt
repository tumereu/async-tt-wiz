package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import org.junit.Test
import kotlin.test.assertFalse

/**
 * Owner, 2026-11-21: cards that count a subtype ("+1/+1 for each Lizard you control") were hard to judge, as battlefield cards
 * didn't show their types. Now each shows its card types, and a tag for each subtype a card its player can see refers to: their own
 * hand, either battlefield and the Stack, never the other player's hand. Alice holds a Lizard lord and plays a Lizard and a Goblin;
 * Bob holds a Goblin lord. Alice's phone tags her Lizard, Bob's her Goblin; neither tags what only the other's hidden hand refers to,
 * until Alice casts her lord and both see it.
 */
class SubtypeTagsTest : E2e() {
    private val aliceDeck = TestDeck("Lizards", listOf(TestCards.mountain to 10, TestCards.basker to 10, TestCards.cinderGoblin to 10,
        TestCards.scaleChief to 10))
    private val bobDeck = TestDeck("Goblins", listOf(TestCards.mountain to 20, TestCards.warcaller to 20))

    // TBL-233, TBL-234, TBL-235, TBL-237 (owner, 2026-11-21)
    @Test fun tagsShowWhatTheCardsEachPlayerCanSeeReferTo() {
        startGame(aliceDeck, bobDeck, deal = { a, b ->
            listOf("Sunbaked Basker", "Cinder Goblin", "Dunescale Chief").all { it in hand(a) } && "Goblin Warcaller" in hand(b) })
        bothOpen()
        bothKeep(bobAway = false)
        for (name in listOf("Sunbaked Basker", "Cinder Goblin")) {
            alice.playFromHand(name, this)
            waitUntil(name, along = listOf(bob)) { alice.field(name).isNotEmpty() && alice.inOwnMain() }
        }
        fun card(name: String) = hasContentDescription("$name, owner ${alice.name}, ", substring = true)
        // Read aloud before its counters (it has none): its card types, then its tags
        val lizardTagged = card("Sunbaked Basker") and hasContentDescription(", Creature, Lizard tag", substring = true)
        val lizardPlain = card("Sunbaked Basker") and hasContentDescription(", Creature", substring = true)
        val goblinTagged = card("Cinder Goblin") and hasContentDescription(", Creature, Goblin tag", substring = true)
        val goblinPlain = card("Cinder Goblin") and hasContentDescription(", Creature", substring = true)
        waitUntil("Alice's own hand tags her Lizard on her phone") { alice.shows(lizardTagged) && alice.shows(goblinPlain) }
        waitUntil("Bob's hand tags Alice's Goblin on his phone", along = listOf(alice)) { bob.shows(goblinTagged) && bob.shows(lizardPlain) }
        assertFalse(alice.shows(goblinTagged), "Bob's hand is hidden from Alice: ${alice.screen()}")
        assertFalse(bob.shows(lizardTagged), "Alice's hand is hidden from Bob: ${bob.screen()}")
        // Cast, the lord is on the battlefield: both see it
        alice.playFromHand("Dunescale Chief", this)
        waitUntil("the Chief", along = listOf(bob)) { alice.field("Dunescale Chief").isNotEmpty() && alice.inOwnMain() }
        waitUntil("Bob's phone tags Alice's Lizards now", along = listOf(alice)) { bob.shows(lizardTagged) }
        assertFalse(alice.refusalSeen || bob.refusalSeen, "no refusal")
    }
}
