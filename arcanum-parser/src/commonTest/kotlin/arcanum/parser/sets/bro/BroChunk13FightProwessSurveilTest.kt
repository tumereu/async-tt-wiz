package arcanum.parser.sets.bro

import arcanum.api.Ability
import arcanum.api.Instructions
import arcanum.api.CardType
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Effect
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Affected
import arcanum.api.Zone
import arcanum.api.Comparison
import arcanum.api.NumericProperty
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Choice
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** BRO chunk 13: Fight, Prowess, Surveil, and additional costs, "can't be blocked by", "can't be countered", "can't gain life". */
class BroChunk13FightProwessSurveilTest {
    private val opponentCreature = and(creature, ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You)))
    private fun surveilN(n: Int) = doing(Effect.Surveil(Players.You, n(n)))

    // CR 700.2 (modes), 701.23 (search), 701.14a (fight)
    @Test
    fun wildTrampling() = card(
        name = "Wild Trampling",
        cost = "{G}",
        type = "Sorcery",
        text = """
            Choose one —
            • Search your library for a basic land card, reveal it, put it into your hand, then shuffle.
            • Target creature you control fights target creature you don't control. (Each deals damage equal to its power to the other.)
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
                mode(search(basicLand(), reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle),
                mode(doing(Effect.Fight(target("creature"), target("second creature"))),
                    targets = listOf(slot("creature", creatureYouControl), slot("second creature", opponentCreature))),
        ))),
    )

    // CR 701.14a, 611.2a: the bonus is applied first, so the fight deals the larger power; "it" is the first target
    @Test
    fun hardPress() = card(
        name = "Hard Press",
        cost = "{1}{G}",
        type = "Sorcery",
        text = "Target creature you control gets +1/+2 until end of turn. It fights target creature you don't control. (Each deals damage equal to its power to the other.)",
    ) parsesTo abilities(
        spell(pump(target("creature"), 1, 2), doing(Effect.Fight(target("creature"), target("second creature"))),
            targets = listOf(slot("creature", creatureYouControl), slot("second creature", opponentCreature))),
    )

    // CR 702.108a (prowess), 702.21a (ward)
    @Test
    fun forgeKeeper() = card(
        name = "Hearth Forge Keeper",
        cost = "{1}{R}",
        type = "Creature — Dwarf Wizard",
        pt = "1/3",
        text = """
            Ward—Pay 2 life. (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays 2 life.)
            Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
        """,
    ) parsesTo abilities(Keyword.Ward(cost(CostPart.PayLife(n(2)))), Keyword.Prowess)

    @Test
    fun swiftdancer() = card(
        name = "Ember Swiftdancer",
        cost = "{R}",
        type = "Creature — Human Monk",
        pt = "1/2",
        text = """
            Haste
            Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
        """,
    ) parsesTo abilities(Keyword.Haste, Keyword.Prowess)

    @Test
    fun tideCommando() = card(
        name = "Tide Commando",
        cost = "{2}{U}",
        type = "Creature — Human Soldier",
        pt = "2/2",
        text = """
            Flying
            Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
        """,
    ) parsesTo abilities(Keyword.Flying, Keyword.Prowess)

    // CR 604.1, 611.3a: the +2/+0 lasts only while an Island is controlled; CR 701.25a (surveil)
    @Test
    fun seasideBulwark() = card(
        name = "Seaside Bulwark",
        cost = "{2}",
        type = "Artifact Creature — Wall",
        pt = "1/3",
        text = """
            Defender
            This creature gets +2/+0 as long as you control an Island.
            {2}, {T}: Surveil 1. (Look at the top card of your library. You may put that card into your graveyard.)
        """,
    ) parsesTo abilities(
        Keyword.Defender,
        static(pt(2, 0), condition = Condition.Exists(and(sub("Island"), you), atLeast = 1)),
        activated(cost(mana("{2}"), tap), surveilN(1)),
    )

    // CR 701.25a: surveil 2, then the second sentence is a separate step
    @Test
    fun tidalCurate() = card(
        name = "Tidal Curate",
        cost = "{1}{U}",
        type = "Instant",
        text = """
            Surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
            Draw a card.
        """,
    ) parsesTo abilities(spell(surveilN(2), draw(1)))

    // CR 605.1a (mana ability), 602.5b: both abilities need the land's {T}
    @Test
    fun digsiteAnnex() = card(
        name = "Digsite Annex",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {3}, {T}: Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
        """,
    ) parsesTo abilities(
        activated(cost(tap), doing(Effect.AddMana(arcanum.api.ManaProduction(arcanum.api.ManaKind.Fixed(arcanum.api.ManaCost.parse("{C}")!!)), Players.You))),
        activated(cost(mana("{3}"), tap), surveilN(1)),
    )

    // CR 613.1f, 702.20a (vigilance), 113.3d: the granted ability belongs to every creature you control
    @Test
    fun mentorOfDigs() = card(
        name = "Mentor of Digs",
        cost = "{1}{G}{W}{U}",
        type = "Legendary Creature — Human Artificer",
        pt = "4/3",
        text = """
            Creatures you control have vigilance and "{T}: Surveil 1." (To surveil 1, look at the top card of your library. You may put that card into your graveyard.)
            {2}{G}{G}{W}{W}{U}{U}, Exile this card from your graveyard: Return any number of target artifact cards with total mana value 10 or less from your graveyard to the battlefield. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        static(has(Keyword.Vigilance, activated(cost(tap), surveilN(1))), affects = Objects.Each(creatureYouControl)),
        Ability.Activated(
            cost(mana("{2}{G}{G}{W}{W}{U}{U}"), CostPart.Exile(CostObjects.Self, from = Zone.Graveyard)),
            Instructions(
                targets = listOf(TargetSlot("cards", TargetKind.Objects(and(artifact, inYourGraveyard())),
                    min = n(0), max = null, totalAtMost = Choice.TotalLimit(NumericProperty.ManaValue, n(10)))),
                steps = listOf(move(target("cards"), Zone.Battlefield)),
            ),
            asSorcery,
        ),
    )

    // CR 118.8 (additional cost), 701.21a (sacrifice): the cost text span covers the whole "sacrifice a creature"
    @Test
    fun grimBargain() = card(
        name = "Grim Bargain",
        cost = "{1}{B}",
        type = "Sorcery",
        text = """
            As an additional cost to cast this spell, sacrifice a creature.
            Search your library for a card, put that card into your hand, then shuffle.
        """,
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = cost(sacrificeA(creature)))),
        spell(search(cardFilter), move(Objects.Bound("found"), Zone.Hand), shuffle),
    )

    @Test
    fun sacrificialFracture() = card(
        name = "Sacrificial Fracture",
        cost = "{1}{B}",
        type = "Sorcery",
        text = """
            As an additional cost to cast this spell, sacrifice an artifact or creature.
            Destroy target creature or planeswalker.
        """,
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = cost(sacrificeA(or(artifact, creature))))),
        spell(destroy(target("permanent")),
            targets = listOf(slot("permanent", or(creature, ObjectFilter.Type(CardType.Planeswalker))))),
    )

    // CR 118.8, 701.26a (tap): two untapped permanents of the listed types the caster controls
    @Test
    fun warbandElite() = card(
        name = "Warband Elite",
        cost = "{2}{W}",
        type = "Creature — Human Soldier",
        pt = "4/4",
        text = "As an additional cost to cast this spell, tap two untapped artifacts, creatures, and/or lands you control.",
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = cost(CostPart.TapObjects(CostObjects.Choose(or(artifact, creature, land), count = n(2)))))),
    )

    // CR 509.1b: a blocking restriction on this creature
    @Test
    fun mossSprite() = card(
        name = "Moss Sprite",
        cost = "{1}{G}",
        type = "Creature — Faerie",
        pt = "2/2",
        text = """
            This creature can't be blocked by artifact creatures.
            {7}: Put two +1/+1 counters on this creature.
        """,
    ) parsesTo abilities(
        static(Mechanic.CantBeBlockedBy(and(artifact, creature))),
        activated(cost(mana("{7}")), counters(Objects.Self, 2)),
    )

    // CR 101.2 / 701.5a: the spell can't be countered; CR 700.4 / 608.2b: "its mana value" is read from the destroyed artifact
    @Test
    fun razeDown() = card(
        name = "Raze Down",
        cost = "{2}{R}",
        type = "Sorcery",
        text = """
            This spell can't be countered.
            Destroy target artifact. If its mana value was 1 or less, draw a card.
        """,
    ) parsesTo abilities(
        static(Mechanic.CantBeCountered),
        spell(destroy(target("artifact")),
            Step.If(Condition.ObjectMatches(target("artifact"), manaValue(Comparison.AtMost, 1)), then = listOf(draw(1))),
            targets = listOf(slot("artifact", artifact))),
    )

    // CR 119.7: no player can gain life
    @Test
    fun emberCindermaw() = card(
        name = "Ember Cindermaw",
        cost = "{2}{R}",
        type = "Creature — Dinosaur Beast",
        pt = "4/3",
        text = """
            Trample (This creature can deal excess combat damage to the player or planeswalker it's attacking.)
            Players can't gain life.
        """,
    ) parsesTo abilities(Keyword.Trample, playerStatic(Mechanic.CantGainLife, players = Players.Each(PlayerFilter.Any)))
}
