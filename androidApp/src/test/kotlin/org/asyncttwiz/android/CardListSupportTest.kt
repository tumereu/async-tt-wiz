package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import java.util.UUID
import kotlin.test.*

/** LOB-115, DRF-161: decks' and cubes' Automatum support, from the card catalog's marks (section 3.8). Invented cards. */
class CardListSupportTest {
    private fun id() = UUID.randomUUID().toString()
    private fun card(name: String, text: String) = CardDefinition(name, listOf(DefinitionFace(id(), name, typeLine = "Creature — Bear",
        manaCost = "{1}{G}", rulesText = text, power = "2", toughness = "2", sourceData = """{"setCode":"TST","number":"1"}""")),
        printingId = id())
    private val plain = card("Plain Cub", "Trample")
    private val odd = card("Odd Cub", "Whenever the moon is full, this creature dances a slow jig.")
    private val banded = card("Banded Cub", "Whenever the tide turns, this creature hums an old sea song.")
    private val unreadable = CardDefinition("Broken Cub", listOf(DefinitionFace(id(), "Broken Cub", typeLine = "Creature — Bear", rulesText = "Trample")),
        layout = "split")
    private val cards = listOf(plain, odd, banded, unreadable).associateBy(DeckJson::digest)
    /** The card records' marks: the odd, banded and unreadable cubs' printings aren't supported. */
    private val unsupportedPrintings = listOf(odd, banded, unreadable).mapNotNull { it.printingId }.toSet()
    private var installed = true
    private val check = CardListSupportCheck({ emptyMap() }, { ids -> if (installed) ids.filter { it in unsupportedPrintings }.toSet() else null },
        parserVersion = 21, engineVersion = 3)
    private fun submitted(vararg definitions: CardDefinition) = definitions.map { SubmittedDefinition(DeckJson.digest(it), it) }

    // LOB-115: the cards whose printings the card catalog doesn't mark as supported, and a card without a printing
    @Test fun aListNamesItsCardsTheCatalogDoesntSupport() {
        assertEquals(CardListSupport(21, engineVersion = 3), check.support(submitted(plain)))
        assertEquals(CardListSupport(21, listOf("Banded Cub", "Broken Cub", "Odd Cub"), 3), check.support(submitted(plain, odd, banded, unreadable)))
        assertEquals(CardListSupport(21, listOf("Plain Cub"), 3), check.support(submitted(plain.copy(printingId = null))))
        assertEquals("Automatum can't play 3 cards yet: Banded Cub, Broken Cub, Odd Cub", automatumSupportTip(check.support(submitted(odd, banded, unreadable))))
        assertEquals("Automatum can't play 1 card yet: Odd Cub", automatumSupportTip(CardListSupport(21, listOf("Odd Cub"))))
        // Without card records nothing is known, so nothing is kept
        installed = false
        assertFailsWith<IllegalStateException> { check.support(submitted(plain)) }
    }
}
