package arcanum.parser.sets.mh2

import arcanum.api.Ability
import arcanum.api.AbilityKind
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Modern Horizons 2 chunk 14: lands: fetch lands (CR 701.23), indestructible artifact lands entering tapped (the ten Bridges), mana spent
 * only on artifacts (CR 106.6) with modular, a Saga land (CR 714) whose chapters grant abilities and search for a card by its mana cost
 * (CR 202.1), and a land that makes every land a Forest (CR 305.7, layer 4).
 */
class Mh2Chunk14LandsTest {
    /** "{T}, Pay 1 life, Sacrifice this land: Search your library for a [a] or [b] card, put it onto the battlefield, then shuffle." */
    private fun fetchLand(a: String, b: String) = abilities(
        activated(cost(tap, CostPart.PayLife(n(1)), sacrificeSelf), search(or(sub(a), sub(b))), move(Objects.Bound("found"), Zone.Battlefield), shuffle),
    )

    private fun fetch(name: String, a: String, b: String) = card(name = name, cost = null, type = "Land",
        text = "{T}, Pay 1 life, Sacrifice this land: Search your library for a${if (a[0] in "AEIOU") "n" else ""} $a or $b card, put it onto the battlefield, then shuffle.")

    /** A Bridge: "This land enters tapped. / Indestructible / {T}: Add {A} or {B}." */
    private fun bridge(name: String, a: String, b: String) = card(name = name, cost = null, type = "Artifact Land", text = """
        This land enters tapped.
        Indestructible
        {T}: Add {$a} or {$b}.
    """) parsesTo abilities(entersTapped, Keyword.Indestructible, addEither(a, b))

    // CR 701.23a: a land card with that land type
    @Test fun parchedButte() = fetch("Parched Butte", "Mountain", "Plains") parsesTo fetchLand("Mountain", "Plains")
    @Test fun bogBarrens() = fetch("Bog Barrens", "Plains", "Swamp") parsesTo fetchLand("Plains", "Swamp")
    @Test fun fogboundJungle() = fetch("Fogbound Jungle", "Forest", "Island") parsesTo fetchLand("Forest", "Island")
    @Test fun boilingMere() = fetch("Boiling Mere", "Island", "Mountain") parsesTo fetchLand("Island", "Mountain")
    @Test fun mossyCrypts() = fetch("Mossy Crypts", "Swamp", "Forest") parsesTo fetchLand("Swamp", "Forest")

    // CR 614.1c: it enters tapped; CR 702.12 indestructible
    @Test fun duskmossSpan() = bridge("Duskmoss Span", "B", "G")
    @Test fun slagforgeSpan() = bridge("Slagforge Span", "B", "R")
    @Test fun gildmireSpan() = bridge("Gildmire Span", "W", "B")
    @Test fun fogvaultSpan() = bridge("Fogvault Span", "U", "B")
    @Test fun bladetideSpan() = bridge("Bladetide Span", "W", "U")
    @Test fun rustdaleSpan() = bridge("Rustdale Span", "R", "W")
    @Test fun silvercragSpan() = bridge("Silvercrag Span", "U", "R")
    @Test fun cinderwoodsSpan() = bridge("Cinderwoods Span", "R", "G")
    @Test fun snarlpoolSpan() = bridge("Snarlpool Span", "G", "U")
    @Test fun briarglintSpan() = bridge("Briarglint Span", "G", "W")

    // CR 106.6: mana that can only be spent on artifact spells and abilities of artifacts
    @Test
    fun powerStorehouse() = card(
        name = "Power Storehouse",
        cost = null,
        type = "Artifact Land",
        text = """
            This land enters tapped.
            {T}: Add {C}.
            {T}: Add one mana of any color. Spend this mana only to cast artifact spells or activate abilities of artifacts.
            Modular 1
        """,
    ) parsesTo abilities(
        entersTapped,
        addColorless,
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1)), restriction = or(and(spellFilter, artifact),
            ObjectFilter.AbilityOf(kind = AbilityKind.Activated, source = artifact))), Players.You))),
        Keyword.Modular(1),
    )

    // CR 714.2: chapter abilities; CR 202.1: a card whose mana cost is {0} or {1}
    @Test
    fun urzasEpic() = card(
        name = "Urza's Epic",
        cost = null,
        type = "Enchantment Land — Urza's Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I — This Saga gains "{T}: Add {C}."
            II — This Saga gains "{2}, {T}: Create a 0/0 colorless Construct artifact creature token with 'This token gets +1/+1 for each artifact you control.'"
            III — Search your library for an artifact card with mana cost {0} or {1}, put it onto the battlefield, then shuffle.
        """,
    ) parsesTo abilities(
        chapter(1, doing(Effect.ApplyMechanics(listOf(has(addColorless)), Affected.Objects(Objects.Self), Duration.Indefinitely))),
        chapter(2, doing(Effect.ApplyMechanics(listOf(has(activated(cost(mana("{2}"), tap), create(creatureToken("Construct", emptyList(), 0, 0,
            abilities = listOf(static(Mechanic.ModifyPowerToughness(count(and(artifact, you)), count(and(artifact, you))))),
            types = listOf(CardType.Artifact, CardType.Creature)))))), Affected.Objects(Objects.Self), Duration.Indefinitely))),
        chapter(3, search(and(artifact, or(ObjectFilter.HasManaCost(ManaCost.parse("{0}")!!), ObjectFilter.HasManaCost(ManaCost.parse("{1}")!!)))),
            move(Objects.Bound("found"), Zone.Battlefield), shuffle),
    )

    // CR 305.7: Forest in addition to its other land types
    @Test
    fun yavimayaCradleOfRoots() = card(
        name = "Yavimaya, Cradle of Roots",
        cost = null,
        type = "Legendary Land",
        text = "Each land is a Forest in addition to its other land types.",
    ) parsesTo abilities(static(Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Forest")))), affects = Objects.Each(land)))
}
