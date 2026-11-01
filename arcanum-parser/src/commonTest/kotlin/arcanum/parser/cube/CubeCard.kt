package arcanum.parser.cube

import arcanum.api.Ability
import arcanum.api.CardDefinition
import arcanum.api.Cost
import arcanum.api.DefinitionChecks
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.parser.OracleCard
import arcanum.parser.OracleParser
import arcanum.parser.PrintedNumbers
import arcanum.parser.TypeLine
import kotlin.test.assertEquals

/**
 * Helpers for the cube parsing tests (D43, D46): every card of the first target cube, one test each. A test card is
 * invented: a new name in the spirit of the original and, where it doesn't change how the card plays, a different
 * mana cost or subtypes. Its rules text is the original's exact oracle text (with the new name). Each test states what
 * the text must parse to; the helper also checks that the printed characteristics read back unchanged, that nothing is
 * left unparsed and that the definition passes [DefinitionChecks].
 */
fun card(name: String, cost: String?, type: String, text: String = "", pt: String? = null, loyalty: String? = null): OracleCard {
    val (power, toughness) = pt?.split('/')?.let { it[0] to it[1] } ?: (null to null)
    return OracleCard(name, cost, type, text, power, toughness, loyalty)
}

infix fun OracleCard.parsesTo(expected: List<Ability>) {
    val face = OracleParser.parse(this)
    assertEquals(typeLine, TypeLine.render(face), "type line")
    assertEquals(manaCost, face.manaCost?.toString(), "mana cost")
    assertEquals(listOf(power, toughness, loyalty), listOf(face.power, face.toughness, face.loyalty).map(PrintedNumbers::render), "P/T, loyalty")
    assertEquals(expected, face.abilities, name)
    assertEquals(emptyList(), DefinitionChecks.problems(CardDefinition(Layout.Normal, listOf(face))), name)
}

/** A card with no rules text. */
val none: List<Ability> = emptyList()

fun abilities(vararg abilities: Ability): List<Ability> = abilities.toList()

fun mana(text: String): Cost = Cost.mana(ManaCost.parse(text)!!)
