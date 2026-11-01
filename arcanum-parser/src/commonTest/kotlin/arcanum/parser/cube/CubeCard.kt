package arcanum.parser.cube

import arcanum.api.Ability
import arcanum.api.CardDefinition
import arcanum.api.Cost
import arcanum.api.DefinitionChecks
import arcanum.api.FaceDefinition
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.Value
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
fun card(
    name: String, cost: String?, type: String, text: String = "", pt: String? = null, loyalty: String? = null,
    colorIndicator: String? = null,
): OracleCard {
    val (power, toughness) = pt?.split('/')?.let { it[0] to it[1] } ?: (null to null)
    // Multi-line text is written one rules line per source line, indented: strip the indentation.
    val oracle = if (text.startsWith("\n")) text.trimIndent() else text
    return OracleCard(name, cost, type, oracle, power, toughness, loyalty, colorIndicator = colorIndicator)
}

infix fun OracleCard.parsesTo(expected: List<Ability>) {
    val face = OracleParser.parse(this)
    checkFace(this, face, expected)
    assertEquals(emptyList(), DefinitionChecks.problems(CardDefinition(Layout.Normal, listOf(face))), name)
}

/** A card with two faces or halves (CR 709 split, 712 double-faced, 715 adventurer): each face, written as
 * `card(…) to abilities(…)`, is checked like [parsesTo], and the whole card's definition passes [DefinitionChecks]. */
fun cardFaces(layout: Layout, vararg faces: Pair<OracleCard, List<Ability>>) {
    val definition = OracleParser.parseCard(layout, faces.map { it.first })
    assertEquals(layout, definition.layout)
    faces.zip(definition.faces).forEach { (printed, face) -> checkFace(printed.first, face, printed.second) }
    assertEquals(emptyList(), DefinitionChecks.problems(definition), faces.first().first.name)
}

/** The printed characteristics read back unchanged and the abilities are [expected]. */
private fun checkFace(printed: OracleCard, face: FaceDefinition, expected: List<Ability>) {
    assertEquals(printed.typeLine, TypeLine.render(face), "type line")
    assertEquals(printed.manaCost, face.manaCost?.toString(), "mana cost")
    assertEquals(printed.colorIndicator, face.colorIndicator?.joinToString("") { it.symbol.toString() }, "colour indicator")
    assertEquals(listOf(printed.power, printed.toughness, printed.loyalty), listOf(face.power, face.toughness, face.loyalty).map(PrintedNumbers::render), "P/T, loyalty")
    assertEquals(expected, face.abilities, printed.name)
}

/** A card with no rules text. */
val none: List<Ability> = emptyList()

fun abilities(vararg abilities: Ability): List<Ability> = abilities.toList()

/** A cost of only mana, as written ("{2}{U}"). */
fun manaCost(text: String): Cost = Cost.mana(ManaCost.parse(text)!!)

/** "[amount] mana of any one color": the player chooses one colour for all of it (CR 106.1b). */
fun anyOneColor(amount: Int): ManaKind = ManaKind.Choose(amount = Value.Fixed(amount))
