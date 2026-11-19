package arcanum.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A card's rules reminders ([RulesGlossary.reminders]): the rules words its text names but doesn't explain itself. All cards
 * here are invented, their wording after oracle templates. */
class RulesRemindersTest {
    private fun ids(text: String, vararg names: String) = RulesGlossary.reminders(text, names.toList()).map { it.id }

    // Printed keywords without reminder text get reminders, in the order the text names them
    @Test
    fun printedKeywordsWithoutReminderTextAreReminded() {
        assertEquals(listOf("flying", "ward"), ids("Flying\nWard {2}"))
        assertEquals(listOf("deathtouch", "lifelink", "ward"), ids("Deathtouch, lifelink\nWard—Pay 2 life."))
        // A keyword the text gives to other cards is named too
        assertEquals(listOf("ward"), ids("Other Frogs you control have ward {1}."))
    }

    // Reminder text explains a word: a parenthesis later on its line, anywhere on the card
    @Test
    fun reminderTextExplainsItsWord() {
        assertEquals(emptyList(), ids("Bargain (You may sacrifice an artifact, enchantment, or token as you cast this spell.)\nDraw a card."))
        assertEquals(listOf("flying"), ids("Flying\nWhen this creature enters, it explores. (Reveal the top card of your library.)"))
        // One explained place explains the word for the whole card
        assertEquals(emptyList(), ids("When this creature enters, create a Food token. (It's an artifact.)\nWhenever you sacrifice a Food, draw a card."))
        // Words inside reminder text aren't counted
        assertEquals(emptyList(), ids("Create a Monster Role token attached to target creature you control. (Enchanted creature gets +1/+1 and has trample.)"))
    }

    // Token kinds a card makes or names, Roles by their kind
    @Test
    fun tokenKindsAreReminded() {
        assertEquals(listOf("treasure", "monster-role"), ids("Create a Treasure token.\nCreate a Monster Role token attached to target creature you control."))
        assertEquals(listOf("role"), ids("Whenever a Role you control is put into a graveyard, draw a card."))
        assertEquals(listOf("transform", "incubator"), ids("{T}: Transform target Incubator token you control."))
        // Only as cards print a token kind's name: a lower-case word is no token
        assertEquals(emptyList(), ids("You gain life equal to the blood spilled."))
    }

    // Verbs in their forms, ability labels, other rules words and card states
    @Test
    fun formsLabelsTermsAndStates() {
        assertEquals(listOf("explore"), ids("Whenever a creature you control explores, it gets +1/+0."))
        assertEquals(listOf("forage"), ids("You may cast creature spells from your graveyard by foraging."))
        assertEquals(listOf("transform"), ids("When this creature transforms into its other face, draw a card."))
        assertEquals(listOf("exhaust"), ids("Exhaust — {3}: Put a +1/+1 counter on this Vehicle."))
        assertEquals(listOf("max-speed"), ids("Max speed — This creature gets +1/+0."))
        assertEquals(listOf("suspect", "suspected"), ids("When this creature dies, suspect up to one target creature.\nSuspected creatures get -1/-0."))
        assertEquals(listOf("ring-tempts", "ring-bearer"), ids("Whenever the Ring tempts you, if you chose a creature other than this one as your Ring-bearer, draw a card."))
        assertEquals(listOf("monarch"), ids("When this creature enters, you become the monarch."))
        assertEquals(listOf("cycling"), ids("Swampcycling {2}"))
        assertEquals(listOf("scry"), ids("Target player scries X, then draws a card."))
    }

    // Ability and flavor words leading a line name nothing; a rules word leading one does
    @Test
    fun leadWordsAreNoRulesWords() {
        assertEquals(emptyList(), ids("Landfall — Whenever a land you control enters, you gain 1 life."))
        assertEquals(emptyList(), ids("Echo of the Past — Whenever this creature attacks, draw a card."))
        assertEquals(listOf("prototype"), ids("Prototype {1}{B}{B} — 3/3"))
        assertEquals(listOf("descend"), ids("Descend 4 — This creature gets +2/+2."))
    }

    // The card's own names never count, nor a word inside another ("double-faced" isn't double, flashback isn't flash)
    @Test
    fun ownNamesAndPartsOfWordsAreNoRulesWords() {
        assertEquals(emptyList(), ids("When Flash Ward, the Quick enters, draw a card.", "Flash Ward, the Quick"))
        assertEquals(emptyList(), ids("Whenever Flash Ward attacks, draw a card.", "Flash Ward, the Quick"))
        assertEquals(listOf("empower-jace"), ids("[+1]: Empower Jace X, where X is the number of Islands you control.", "Jace, the Sculptor"))
        assertEquals(listOf("flashback"), ids("Flashback {2}{R}"))
        assertEquals(listOf("transform"), ids("If it's a double-faced card, you may transform it."))
    }

    // What a card list names a keyword, action, token kind or state as finds its explanation
    @Test
    fun lookupByListedName() {
        assertEquals("ward", RulesGlossary.lookup("Ward")?.id)
        assertEquals("hexproof", RulesGlossary.lookup("Hexproof from")?.id)
        assertEquals("cycling", RulesGlossary.lookup("Basic landcycling")?.id)
        assertEquals("landwalk", RulesGlossary.lookup("Islandwalk")?.id)
        assertEquals("role", RulesGlossary.lookup("Role token")?.id)
        assertEquals("food", RulesGlossary.lookup("Food")?.id)
        assertEquals("start-your-engines", RulesGlossary.lookup("Start your engines!")?.id)
        assertEquals("solved", RulesGlossary.lookup("Solved")?.id)
        assertEquals("transform", RulesGlossary.lookup("Transform")?.id)
        assertNull(RulesGlossary.lookup("Landfall"))
        assertNull(RulesGlossary.lookup("Echo of the Past"))
    }
}
