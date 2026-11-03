package arcanum.parser.sets.ecl

import arcanum.api.Ability
import arcanum.api.Choice
import arcanum.api.ColorRef
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.TokenSpec
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Lorwyn Eclipsed chunk 16: artifacts and lands. Mana of the colour the creature then becomes (CR 106.1, layer 5), the first tokens each
 * turn replaced by copies of the equipped creature (CR 614.1a, 707.2), a chosen colour for good, "if there are five colors among permanents
 * you control" (CR 105.4), tapping an untapped creature as a cost (CR 602.2b), and lands that enter tapped unless 2 life is paid
 * (CR 614.12, 305.7: the basic land types give their mana abilities).
 */
class EclChunk16ArtifactsAndLandsTest {
    private val shockLand = static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(
        Modification.AsEnters(listOf(Step.Pay(cost(CostPart.PayLife(n(2))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Not(Condition.Did("paid")), then = listOf(Step.Modify(Modification.EntersTapped))))))))

    // the colour chosen for the mana is the colour it becomes
    @Test
    fun scavengingStrawmaw() = card(
        name = "Scavenging Strawmaw",
        cost = "{2}",
        type = "Artifact Creature — Scarecrow",
        text = """
            When this creature enters, surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
            {1}: Add one mana of any color. This creature becomes that color until end of turn. Activate only once each turn.
        """,
        pt = "1/3",
    ) parsesTo abilities(
        triggered(entersSelf, doing(Effect.Surveil(Players.You, n(1)))),
        activated(manaCost("{1}"),
            Step.Choose(Choice.Color(), bind = "color", chooser = Players.You),
            doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1), listOf(ColorRef.Chosen("color")))), Players.You)),
            untilEndOfTurn(Objects.Self, Mechanic.SetColors(listOf(ColorRef.Chosen("color")))),
            rules = anyTime.copy(perTurn = 1)),
    )

    // CR 614.1a: optional, only while attached to a creature and before you have created a token this turn
    @Test
    fun glassthoughtCirclet() = card(
        name = "Glassthought Circlet",
        cost = "{4}",
        type = "Artifact — Equipment",
        text = """
            As long as this Equipment is attached to a creature, the first time you would create one or more tokens each turn, you may instead create that many tokens that are copies of equipped creature.
            Equip {2}
        """,
    ) parsesTo abilities(
        run {
            val yours = and(token, ObjectFilter.OwnedBy(arcanum.api.PlayerFilter.You))
            static(Mechanic.Replacement(EventPattern.CreateToken(yours), modify = listOf(Modification.CreateInstead(TokenSpec.CopyOf(Objects.AttachedTo(Objects.Self)))),
                optional = true), condition = Condition.And(listOf(Condition.ObjectMatches(Objects.AttachedTo(Objects.Self), creature),
                Condition.Not(Condition.HappenedThisTurn(EventPattern.CreateToken(yours), atLeast = 1, measure = Measure.Events)))))
        },
        Keyword.Equip(manaCost("{2}")),
    )

    // CR 105.4: five colours among permanents you control
    @Test
    fun pookasGaze() = card(
        name = "Pooka's Gaze",
        cost = "{2}",
        type = "Artifact",
        text = """
            When this artifact enters, draw a card, then choose a color. This artifact becomes the chosen color.
            {3}, {T}: Draw a card. Activate only if there are five colors among permanents you control.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, draw(1), Step.Choose(Choice.Color(), bind = "color", chooser = Players.You),
            doing(Effect.ApplyMechanics(listOf(Mechanic.SetColors(listOf(ColorRef.Chosen("color")))), arcanum.api.Affected.Objects(Objects.Self), Duration.Indefinitely))),
        activated(cost(mana("{3}"), tap), draw(1), rules = anyTime.copy(condition = Condition.Compare(colorsAmongPermanents, Comparison.AtLeast, n(5)))),
    )

    // CR 602.2b
    @Test
    fun budleafTabor() = card(
        name = "Budleaf Tabor",
        cost = "{1}",
        type = "Artifact",
        text = "{T}, Tap an untapped creature you control: Add one mana of any color.",
    ) parsesTo abilities(
        activated(cost(tap, CostPart.TapObjects(CostObjects.Choose(creature, count = n(1)))), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1))), Players.You))),
    )

    // CR 614.12: the life is paid as it enters
    @Test
    fun goreVault() = card(
        name = "Gore Vault",
        cost = null,
        type = "Land — Swamp Mountain",
        text = """
            ({T}: Add {B} or {R}.)
            As this land enters, you may pay 2 life. If you don't, it enters tapped.
        """,
    ) parsesTo abilities(shockLand)

    @Test
    fun shiftingBarrens() = card(
        name = "Shifting Barrens",
        cost = null,
        type = "Land",
        text = "{T}, Sacrifice this land: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.",
    ) parsesTo abilities(
        activated(cost(tap, sacrificeSelf), search(basicLand()), move(Objects.Bound("found"), Zone.Battlefield, tapped = true), shuffle),
    )

    @Test
    fun sanctifiedSpring() = card(
        name = "Sanctified Spring",
        cost = null,
        type = "Land — Plains Island",
        text = """
            ({T}: Add {W} or {U}.)
            As this land enters, you may pay 2 life. If you don't, it enters tapped.
        """,
    ) parsesTo abilities(shockLand)

    @Test
    fun mossySepulcher() = card(
        name = "Mossy Sepulcher",
        cost = null,
        type = "Land — Swamp Forest",
        text = """
            ({T}: Add {B} or {G}.)
            As this land enters, you may pay 2 life. If you don't, it enters tapped.
        """,
    ) parsesTo abilities(shockLand)

    @Test
    fun vaporFissures() = card(
        name = "Vapor Fissures",
        cost = null,
        type = "Land — Island Mountain",
        text = """
            ({T}: Add {U} or {R}.)
            As this land enters, you may pay 2 life. If you don't, it enters tapped.
        """,
    ) parsesTo abilities(shockLand)

    @Test
    fun shrineOrchard() = card(
        name = "Shrine Orchard",
        cost = null,
        type = "Land — Forest Plains",
        text = """
            ({T}: Add {G} or {W}.)
            As this land enters, you may pay 2 life. If you don't, it enters tapped.
        """,
    ) parsesTo abilities(shockLand)
}
