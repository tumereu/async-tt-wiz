package arcanum.parser.cube

import arcanum.api.Ability
import arcanum.api.CardDefinition
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.DefinitionChecks
import arcanum.api.Effect
import arcanum.api.EventPattern
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
fun card(name: String, cost: String?, type: String, text: String = "", pt: String? = null, loyalty: String? = null): OracleCard {
    val (power, toughness) = pt?.split('/')?.let { it[0] to it[1] } ?: (null to null)
    // Multi-line text is written one rules line per source line, indented: strip the indentation.
    val oracle = if (text.startsWith("\n")) text.trimIndent() else text
    return OracleCard(name, cost, type, oracle, power, toughness, loyalty)
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
    TargetSlot(name = name, what = TargetKind.Objects(filter), min = Value.Fixed(min))

/** Each of [filters], as one "and". */
fun allOf(vararg filters: ObjectFilter): ObjectFilter = ObjectFilter.And(filters.toList())

/** Your graveyard, as a zone filter. */
val inYourGraveyard: List<ObjectFilter> = listOf(ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))
