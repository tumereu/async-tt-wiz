package arcanum.parser.cube

import arcanum.api.Ability
import arcanum.api.CardDefinition
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.DefinitionChecks
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
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

/** The printed characteristics read back unchanged and the abilities are [expected]; their texts are checked apart
 * ([checkTexts]), so expectations state what the text means. */
private fun checkFace(printed: OracleCard, face: FaceDefinition, expected: List<Ability>) {
    assertEquals(printed.typeLine, TypeLine.render(face), "type line")
    assertEquals(printed.manaCost, face.manaCost?.toString(), "mana cost")
    assertEquals(printed.colorIndicator, face.colorIndicator?.joinToString("") { it.symbol.toString() }, "colour indicator")
    assertEquals(listOf(printed.power, printed.toughness, printed.loyalty), listOf(face.power, face.toughness, face.loyalty).map(PrintedNumbers::render), "P/T, loyalty")
    assertEquals(expected, face.abilities.map(::withoutText), printed.name)
    checkTexts(printed, face.abilities)
}

/** [ability] without its printed text and its modes' texts. */
fun withoutText(ability: Ability): Ability = when (ability) {
    is Ability.Spell -> ability.copy(text = null, instructions = withoutText(ability.instructions))
    is Ability.Activated -> ability.copy(text = null, instructions = withoutText(ability.instructions))
    is Ability.Triggered -> ability.copy(text = null, instructions = withoutText(ability.instructions))
    is Ability.Static -> ability.copy(text = null)
    is Ability.SpecialAction -> ability.copy(text = null, instructions = withoutText(ability.instructions))
    // blb: a class level bar's abilities keep their lines too
    is arcanum.api.Keyword.ClassLevel -> ability.copy(abilities = ability.abilities.map(::withoutText))
    else -> ability
}

private fun withoutText(instructions: Instructions): Instructions =
    instructions.copy(modes = instructions.modes?.let { m -> m.copy(options = m.options.map { it.copy(text = null) }) })

/**
 * Each parsed ability keeps its printed text: one or more whole lines of the card, in order, without reminder text (a
 * spell's lines, a modal spell's "Choose one —" with its "• " lines, a leveler band); each mode its "• " line without
 * the bullet. Keywords have their own text; unparsed text stays as printed.
 */
private fun checkTexts(printed: OracleCard, abilities: List<Ability>) {
    val lines = printed.text.lines().map { Regex("""\s*\([^)]*\)""").replace(it, "").trim() }.filter { it.isNotEmpty() }
    var from = 0
    abilities.forEach { ability ->
        val (text, instructions) = when (ability) {
            is Ability.Spell -> ability.text to ability.instructions
            is Ability.Activated -> ability.text to ability.instructions
            is Ability.Triggered -> ability.text to ability.instructions
            is Ability.Static -> ability.text to null
            is Ability.SpecialAction -> ability.text to ability.instructions
            else -> return@forEach
        }
        val own = text?.lines() ?: kotlin.test.fail("${printed.name}: no text for $ability")
        val at = (from until lines.size).firstOrNull { lines.drop(it).take(own.size) == own }
            ?: (0 until lines.size).firstOrNull { lines.drop(it).take(own.size) == own }
            ?: kotlin.test.fail("${printed.name}: \"$text\" isn't the card's lines in order: $lines")
        from = at
        instructions?.modes?.options?.forEach { mode ->
            // blb chunk 06: a pawprint mode keeps its whole line ("{P} — Draw a card.", CR 700.2i)
            assertEquals(true, "• ${mode.text}" in own || mode.text in own, "${printed.name}: mode text \"${mode.text}\" isn't one of the ability's lines")
        }
    }
}

/** A card with no rules text. */
val none: List<Ability> = emptyList()

fun abilities(vararg abilities: Ability): List<Ability> = abilities.toList()

/** A cost of only mana, as written ("{2}{U}"). */
fun manaCost(text: String): Cost = Cost.mana(ManaCost.parse(text)!!)

/** "[amount] mana of any one color": the player chooses one colour for all of it (CR 106.1b). */
fun anyOneColor(amount: Int): ManaKind = ManaKind.Choose(amount = Value.Fixed(amount))

// ---- Enters-the-battlefield creatures (chunks 30–33)

/** "When this creature enters" (CR 603.6a). */
val thisEnters: Trigger = Trigger.On(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield))

/** "When this creature enters, [steps]" with its targets and intervening "if" (CR 603.4). */
fun whenThisEnters(vararg steps: Step, targets: List<TargetSlot> = emptyList(), interveningIf: Condition? = null) = Ability.Triggered(
    trigger = thisEnters,
    instructions = Instructions(targets = targets, steps = steps.toList()),
    interveningIf = interveningIf,
)

/** "[You] draw [n] cards." */
fun drawCards(n: Int, players: Players = Players.You): Step = Step.Do(Effect.Draw(players, count = Value.Fixed(n)))

/** A target slot of objects. */
fun objectSlot(name: String, filter: ObjectFilter, min: Int = 1) =
    TargetSlot(name = name, what = TargetKind.Objects(filter), min = Value.Fixed(min), max = Value.Fixed(1))

/** Each of [filters], as one "and". */
fun allOf(vararg filters: ObjectFilter): ObjectFilter = ObjectFilter.And(filters.toList())

/** Your graveyard, as a zone filter. */
val inYourGraveyard: List<ObjectFilter> = listOf(ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))
