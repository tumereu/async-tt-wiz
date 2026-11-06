package arcanum.parser.sets.dft

import arcanum.api.Color
import arcanum.api.Condition
import arcanum.api.Effect
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Players
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Aetherdrift chunk 17: the lands: the Verges (a second colour only if you control one of two basic land types, CR 602.5b) and the gain lands
 * (enter tapped, CR 614.1c; gain 1 life). The five basic lands only repeat their reminder text and are untested.
 */
class DftChunk17LandsTest {
    @Test fun bleachboneBrink() = verge("Bleachbone Brink", "B", "W", "Plains", "Swamp")
    @Test fun riverpyreBrink() = verge("Riverpyre Brink", "R", "U", "Island", "Mountain")
    @Test fun sunbillowBrink() = verge("Sunbillow Brink", "W", "R", "Mountain", "Plains")
    @Test fun wastewoodBrink() = verge("Wastewood Brink", "G", "B", "Swamp", "Forest")
    @Test fun willowrushBrink() = verge("Willowrush Brink", "U", "G", "Forest", "Island")

    @Test fun bloodfellGrotto() = gainLand("Bloodfell Grotto", "B", "R")
    @Test fun blossomingDunes() = gainLand("Blossoming Dunes", "G", "W")
    @Test fun dismalBayou() = gainLand("Dismal Bayou", "U", "B")
    @Test fun jungleHideaway() = gainLand("Jungle Hideaway", "B", "G")
    @Test fun ruggedUplands() = gainLand("Rugged Uplands", "R", "G")
    @Test fun scouredWastes() = gainLand("Scoured Wastes", "W", "B")
    @Test fun swiftwaterBluffs() = gainLand("Swiftwater Bluffs", "U", "R")
    @Test fun thornwoodCascade() = gainLand("Thornwood Cascade", "G", "U")
    @Test fun tranquilInlet() = gainLand("Tranquil Inlet", "W", "U")
    @Test fun windScarredBluff() = gainLand("Wind-Scarred Bluff", "R", "W")

    /** "{T}: Add {A}." and "{T}: Add {B}. Activate only if you control a [first] or a [second]." (CR 602.5b) */
    private fun verge(name: String, a: String, b: String, first: String, second: String) = card(
        name = name,
        cost = null,
        type = "Land",
        text = """
            {T}: Add {$a}.
            {T}: Add {$b}. Activate only if you control ${article(first)} $first or ${article(second)} $second.
        """,
    ) parsesTo abilities(
        add(a),
        add(b).copy(rules = anyTime.copy(condition = Condition.Exists(and(or(sub(first), sub(second)), you), atLeast = 1))),
    )

    /** "This land enters tapped. When this land enters, you gain 1 life. {T}: Add {A} or {B}." */
    private fun gainLand(name: String, a: String, b: String) = card(
        name = name,
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            When this land enters, you gain 1 life.
            {T}: Add {$a} or {$b}.
        """,
    ) parsesTo abilities(entersTapped, triggered(entersSelf, gainLife(1)), addEither(a, b))

    private fun add(color: String) = activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{$color}")!!)), Players.You)))
    private fun article(word: String) = if (word.first() in "AEIOU") "an" else "a"
}
