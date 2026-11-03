package arcanum.parser.sets.tla

import arcanum.api.Ability
import arcanum.api.Condition
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.SharedQuality
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.OracleCard
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Avatar: The Last Airbender chunk 18: artifacts and lands: a mana ability once each turn, untapping in other players' untap steps, scrying
 * or surveilling that lets you cast the top card once each turn (CR 603.2h), an Equipment's granted ability that returns the Equipment
 * (CR 113.10), mana by the most creatures sharing a creature type, the cycle of tapped dual lands with a sacrifice draw, lands that enter
 * tapped unless you control a basic land with an activated ability, a land with vigilance, a land that can't be blocked, and two targets
 * that share a creature type (CR 115.1).
 */
class TlaChunk18ArtifactsAndLandsTest {
    private fun add(mana: String) = activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse(mana)!!)), Players.You)))
    private val entersTappedUnlessBasic = static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
        modify = listOf(Modification.EntersTapped)), condition = Condition.Not(Condition.Exists(and(basic, land, you), 1)))

    /** "This land enters tapped. {T}: Add {A} or {B}. {4}, {T}, Sacrifice this land: Draw a card." */
    private fun gainLand(name: String, a: String, b: String): OracleCard = card(
        name = name,
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            {T}: Add {$a} or {$b}.
            {4}, {T}, Sacrifice this land: Draw a card.
        """,
    )
    private fun gainLandAbilities(a: String, b: String) = abilities(entersTapped, addEither(a, b), activated(cost(mana("{4}"), tap, sacrificeSelf), draw(1)))

    // CR 602.5b
    @Test
    fun casksOfVolatileJelly() = card(
        name = "Casks of Volatile Jelly",
        cost = "{1}",
        type = "Artifact",
        text = """
            {1}: Add one mana of any color. Activate only once each turn.
            {5}, {T}, Sacrifice this artifact: It deals 5 damage to target creature.
        """,
    ) parsesTo abilities(
        activated(cost(mana("{1}")), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1))), Players.You)), rules = anyTime.copy(perTurn = 1)),
        activated(cost(mana("{5}"), tap, sacrificeSelf), damage(5, toObjects(target("creature"))), targets = listOf(slot("creature", creature))),
    )

    // CR 502.3
    @Test
    fun shapersWaterFlask() = card(
        name = "Shaper's Water Flask",
        cost = "{3}",
        type = "Artifact",
        text = """
            Untap this artifact during each other player's untap step.
            {T}: Add one mana of any color.
        """,
    ) parsesTo abilities(
        static(Mechanic.UntapsDuringOtherUntapSteps),
        addAnyColor,
    )

    @Test
    fun emberLegionWarship() = card(
        name = "Ember Legion Warship",
        cost = "{3}",
        type = "Artifact — Vehicle",
        text = """
            Reach
            When this Vehicle dies, create a Clue token. (It's an artifact with "{2}, Sacrifice this token: Draw a card.")
            Crew 2 (Tap any number of creatures you control with total power 2 or more: This Vehicle becomes an artifact creature until end of turn.)
        """,
        pt = "4/4",
    ) parsesTo abilities(
        Keyword.Reach,
        triggered(diesSelf, createClue),
        Keyword.Crew(2),
    )

    @Test
    fun starfallSaber() = card(
        name = "Starfall Saber",
        cost = "{7}",
        type = "Artifact — Equipment",
        text = """
            When this Equipment enters, destroy target permanent.
            Equipped creature gets +3/+3.
            Equip {3} ({3}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        triggered(entersSelf, destroy(target("permanent")), targets = listOf(slot("permanent", permanentFilter))),
        static(pt(3, 3), affects = Objects.AttachedTo(Objects.Self)),
        Keyword.Equip(manaCost("{3}")),
    )

    // CR 603.2h: once each turn, casting the top card for free
    @Test
    fun orreryOfOrvath() = card(
        name = "Orrery of Orvath",
        cost = "{6}",
        type = "Legendary Artifact",
        text = """
            {1}, {T}: Scry 2.
            Whenever you scry or surveil, look at the top card of your library. You may cast that card without paying its mana cost. Do this only once each turn. (Look at the card after you scry or surveil.)
        """,
    ) parsesTo abilities(
        activated(cost(mana("{1}"), tap), scry(2)),
        triggered(Trigger.On(listOf(EventPattern.Scry(PlayerFilter.You), EventPattern.Surveil(PlayerFilter.You))),
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(1), fromTop = true), Players.You, bind = "looked")),
            Step.Pay(steps = listOf(doing(Effect.Cast(Objects.Bound("looked"), PlayKind.Cast, withoutPaying = true, caster = Players.You))), payer = Players.You,
                bind = "cast"),
            actionOncePerTurn = true),
    )

    // CR 113.10: the granted ability returns the Equipment that gives it
    @Test
    fun faithfulReturningBlade() = card(
        name = "Faithful Returning Blade",
        cost = "{1}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature has "{1}, {T}: Tap target creature. Return Faithful Returning Blade to its owner's hand."
            Equip {1} ({1}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        static(has(activated(cost(mana("{1}"), tap), tapIt(target("creature")), move(Objects.Granter, Zone.Hand), targets = listOf(slot("creature", creature)))),
            affects = Objects.AttachedTo(Objects.Self)),
        Keyword.Equip(manaCost("{1}")),
    )

    @Test
    fun theWallsOfStoneholm() = card(
        name = "The Walls of Stoneholm",
        cost = "{8}",
        type = "Legendary Artifact Creature — Wall",
        text = """
            Defender
            Other permanents you control have indestructible.
        """,
        pt = "0/30",
    ) parsesTo abilities(
        Keyword.Defender,
        static(has(Keyword.Indestructible), affects = Objects.Each(and(another, permanentFilter, you))),
    )

    // the most creatures sharing one creature type
    @Test
    fun paleBlossomTile() = card(
        name = "Pale Blossom Tile",
        cost = "{4}",
        type = "Artifact",
        text = """
            This artifact enters tapped.
            {T}: Add X mana of any one color, where X is the greatest number of creatures you control that have a creature type in common.
        """,
    ) parsesTo abilities(
        entersTapped,
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(Value.MostSharing(Objects.Each(creatureYouControl), SharedQuality.CreatureType))),
            Players.You))),
    )

    @Test
    fun forsakenWindShrine() = card(
        name = "Forsaken Wind Shrine",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped unless you control a basic land.
            {T}: Add {W}.
            {3}{W}, {T}: Put a +1/+1 counter on each creature you control.
        """,
    ) parsesTo abilities(
        entersTappedUnlessBasic,
        add("{W}"),
        activated(cost(mana("{3}{W}"), tap), counters(Objects.Each(creatureYouControl))),
    )

    @Test
    fun frostwallCitadel() = card(
        name = "Frostwall Citadel",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped unless you control a basic land.
            {T}: Add {U}.
            {2}{U}, {T}: Draw a card, then discard a card.
        """,
    ) parsesTo abilities(
        entersTappedUnlessBasic,
        add("{U}"),
        activated(cost(mana("{2}{U}"), tap), draw(1), discard(1)),
    )

    @Test fun airshipBoilerRoom() = gainLand("Airship Boiler Room", "U", "R") parsesTo gainLandAbilities("U", "R")
    @Test fun scaldingPitPrison() = gainLand("Scalding Pit Prison", "B", "R") parsesTo gainLandAbilities("B", "R")
    @Test fun mistyHollowBog() = gainLand("Misty Hollow Bog", "B", "G") parsesTo gainLandAbilities("B", "G")
    @Test fun reedIsleVillage() = gainLand("Reed Isle Village", "G", "W") parsesTo gainLandAbilities("G", "W")
    @Test fun contemplationPools() = gainLand("Contemplation Pools", "G", "U") parsesTo gainLandAbilities("G", "U")
    @Test fun hazyPalmsOasis() = gainLand("Hazy Palms Oasis", "W", "B") parsesTo gainLandAbilities("W", "B")
    @Test fun northernIceGates() = gainLand("Northern Ice Gates", "W", "U") parsesTo gainLandAbilities("W", "U")
    @Test fun cliffsideCapital() = gainLand("Cliffside Capital", "R", "G") parsesTo gainLandAbilities("R", "G")
    @Test fun wyrmsStrait() = gainLand("Wyrm's Strait", "U", "B") parsesTo gainLandAbilities("U", "B")
    @Test fun sunKissedSummit() = gainLand("Sun-Kissed Summit", "R", "W") parsesTo gainLandAbilities("R", "W")

    @Test
    fun domainOfVyrr() = card(
        name = "Domain of Vyrr",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped unless you control a basic land.
            {T}: Add {B}.
            {3}{B}, {T}: Create a 1/1 colorless Spirit creature token with "This token can't block or be blocked by non-Spirit creatures."
        """,
    ) parsesTo abilities(
        entersTappedUnlessBasic,
        add("{B}"),
        activated(cost(mana("{3}{B}"), tap), create(spiritToken)),
    )

    // CR 702.20b: a land with vigilance (it matters only if it becomes a creature)
    @Test
    fun brawlArena() = card(
        name = "Brawl Arena",
        cost = null,
        type = "Land",
        text = """
            Vigilance
            When this land enters, scry 1. (Look at the top card of your library. You may put it on the bottom.)
            {T}: Add {C}.
            {1}, {T}: Add one mana of any color.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        triggered(entersSelf, scry(1)),
        addColorless,
        activated(cost(mana("{1}"), tap), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1))), Players.You))),
    )

    // CR 115.1 (two targets sharing a creature type), 509.1b
    @Test
    fun hiddenPassage() = card(
        name = "Hidden Passage",
        cost = null,
        type = "Land — Cave",
        text = """
            This land can't be blocked.
            {T}: Add {C}.
            {4}, {T}: Two target creatures you control that share a creature type can't be blocked this turn.
        """,
    ) parsesTo abilities(
        static(Mechanic.CantBeBlocked),
        addColorless,
        activated(cost(mana("{4}"), tap), untilEndOfTurn(Objects.Union(listOf(target("creatures"), target("second creatures"))), Mechanic.CantBeBlocked),
            targets = listOf(slot("creatures", creatureYouControl),
                TargetSlot("second creatures", TargetKind.Objects(and(creature, you, ObjectFilter.Shares(SharedQuality.CreatureType, target("creatures")))),
                    min = n(1), max = n(1), distinctFrom = listOf("creatures")))),
    )
}
