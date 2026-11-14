package arcanum.parser.sets.otj

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.anyOneColor
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * OTJ chunk 13: plot (CR 702.170) on cards whose other abilities are tested elsewhere in a different wording or not at all here.
 */
class OtjChunk13Plot2Test {
    private val greatestPower = Value.Of(NumericProperty.Power, Objects.Each(and(creature, you)), Combine.Greatest)


    @Test
    fun sootcrowBuzzard() = card(
        name = "Sootcrow Buzzard",
        cost = "{2}{B}",
        type = "Creature — Bird",
        pt = "2/1",
        text = """
            Flying
            This creature enters with a +1/+1 counter on it if a creature died this turn.
            Plot {1}{B} (You may pay {1}{B} and exile this card from your hand. Cast it as a sorcery on a later turn without paying its mana cost. Plot only as a sorcery.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        // CR 614.1c, 700.4: enters with a counter if a creature died (went to a graveyard from the battlefield) this turn
        entersWith(p1p1 to n(1), condition = Condition.HappenedThisTurn(
            EventPattern.ZoneChange(creature, from = Zone.Battlefield, to = Zone.Graveyard), atLeast = 1, measure = Measure.Events)),
        plot("{1}{B}"),
    )

    @Test
    fun dustyCoyote() = card(
        name = "Dusty Coyote",
        cost = "{1}{R}",
        type = "Creature — Coyote",
        pt = "2/2",
        text = """
            Haste
            When this creature enters, another target creature you control gets +1/+1 and gains haste until end of turn.
            Plot {1}{R} (You may pay {1}{R} and exile this card from your hand. Cast it as a sorcery on a later turn without paying its mana cost. Plot only as a sorcery.)
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        triggered(entersSelf, pump(target("creature"), 1, 1, has(Keyword.Haste)),
            targets = listOf(slot("creature", and(another, creature, you)))),
        plot("{1}{R}"),
    )

    @Test
    fun djinnOfDryWells() = card(
        name = "Djinn of Dry Wells",
        cost = "{4}{U}",
        type = "Creature — Djinn",
        pt = "4/3",
        text = """
            Flying
            Plot {3}{U} (You may pay {3}{U} and exile this card from your hand. Cast it as a sorcery on a later turn without paying its mana cost. Plot only as a sorcery.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        plot("{3}{U}"),
    )

    @Test
    fun roadsideRobbery() = card(
        name = "Roadside Robbery",
        cost = "{1}{R}",
        type = "Sorcery",

        text = """
            You may discard a card or sacrifice a land. If you do, draw two cards.
            Plot {1}{R} (You may pay {1}{R} and exile this card from your hand. Cast it as a sorcery on a later turn without paying its mana cost. Plot only as a sorcery.)
        """,
    ) parsesTo abilities(
        spell(
            Step.Pay(Cost(listOf(CostPart.OneOf(listOf(
                Cost(listOf(CostPart.Discard(CostObjects.Choose(cardFilter, count = n(1))))),
                Cost(listOf(CostPart.Sacrifice(CostObjects.Choose(land, count = n(1))))))))), payer = Players.You, mandatory = false, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(draw(2)))),
        plot("{1}{R}"),
    )

    @Test
    fun recklessWolverine() = card(
        name = "Reckless Wolverine",
        cost = "{2}{R}",
        type = "Creature — Wolverine",
        pt = "3/2",
        text = """
            When this creature enters, exile the top card of your library. Until end of turn, you may play that card.
            Plot {2}{R} (You may pay {2}{R} and exile this card from your hand. Cast it as a sorcery on a later turn without paying its mana cost. Plot only as a sorcery.)
        """,
    ) parsesTo abilities(
        triggered(entersSelf,
            move(Objects.LibraryCards(Players.You, n(1), fromTop = true), Zone.Exile, postBind = "exiled"),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)),
                Affected.Objects(Objects.Bound("exiled")), Duration.EndOfTurn))),
        plot("{2}{R}"),
    )

    @Test
    fun pierShark() = card(
        name = "Pier Shark",
        cost = "{3}{U}",
        type = "Creature — Shark Rogue",
        pt = "3/4",
        text = """
            When this creature enters, if you've cast two or more spells this turn, draw a card.
            Plot {3}{U} (You may pay {3}{U} and exile this card from your hand. Cast it as a sorcery on a later turn without paying its mana cost. Plot only as a sorcery.)
        """,
    ) parsesTo abilities(
        triggered(entersSelf, draw(1), interveningIf = Condition.HappenedThisTurn(
            EventPattern.Cast(ObjectFilter.Any, PlayerFilter.You), atLeast = 2, measure = Measure.Events)),
        plot("{3}{U}"),
    )

    @Test
    fun trailcutterDruid() = card(
        name = "Trailcutter Druid",
        cost = "{2}{G}",
        type = "Creature — Human Druid",
        pt = "4/2",
        text = """
            When this creature enters, add one mana of any color.
            Whenever another creature you control with power 4 or greater enters, draw a card.
            Plot {2}{G} (You may pay {2}{G} and exile this card from your hand. Cast it as a sorcery on a later turn without paying its mana cost. Plot only as a sorcery.)
        """,
    ) parsesTo abilities(
        triggered(entersSelf, Step.Do(Effect.AddMana(ManaProduction(anyOneColor(1)), Players.You))),
        triggered(enters(and(another, creature, you, power(Comparison.AtLeast, 4))), draw(1)),
        plot("{2}{G}"),
    )

    @Test
    fun plunderTheMarsh() = card(
        name = "Plunder the Marsh",
        cost = "{B}{G}",
        type = "Sorcery",

        text = """
            Look at the top X cards of your library, where X is twice the number of lands you control. Put one of them into your hand and the rest on the bottom of your library in a random order.
            Plot {1}{B}{G} (You may pay {1}{B}{G} and exile this card from your hand. Cast it as a sorcery on a later turn without paying its mana cost. Plot only as a sorcery.)
        """,
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, Value.Times(count(and(land, you)), 2), fromTop = true), Players.You, bind = "looked")),
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = Objects.Bound("looked")), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Hand),
            Step.Do(Effect.Move(Objects.Except(Objects.Bound("looked"), Objects.Bound("chosen")),
                Destination(Zone.Library, libraryPosition = -1, randomOrder = true)))),
        plot("{1}{B}{G}"),
    )

    @Test
    fun planTheBreakIn() = card(
        name = "Plan the Break-in",
        cost = "{2}{U}{U}",
        type = "Sorcery",

        text = """
            Surveil 3 if you have no cards in hand. Then draw three cards. (To surveil 3, look at the top three cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
            Plot {3}{U} (You may pay {3}{U} and exile this card from your hand. Cast it as a sorcery on a later turn without paying its mana cost. Plot only as a sorcery.)
        """,
    ) parsesTo abilities(
        spell(
            Step.If(Condition.Not(Condition.Exists(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)), atLeast = 1)), then = listOf(Step.Do(Effect.Surveil(Players.You, n(3))))),
            draw(3)),
        plot("{3}{U}"),
    )

    @Test
    fun ridgebackRhino() = card(
        name = "Ridgeback Rhino",
        cost = "{3}{G}{G}",
        type = "Creature — Rhino Warrior",
        pt = "5/5",
        text = """
            Reach, trample
            Whenever another creature you control enters, put X +1/+1 counters on it, where X is its power.
            Plot {3}{G} (You may pay {3}{G} and exile this card from your hand. Cast it as a sorcery on a later turn without paying its mana cost. Plot only as a sorcery.)
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        Keyword.Trample,
        // the entering creature's power, not this one's
        triggered(enters(and(another, creature, you)),
            counters(Objects.EventObject(EventRole.After), Value.Of(NumericProperty.Power, Objects.EventObject(EventRole.After), Combine.Single))),
        plot("{3}{G}"),
    )

    @Test
    fun gravediggerRobber() = card(
        name = "Gravedigger Robber",
        cost = "{3}{B}",
        type = "Creature — Zombie Rogue",
        pt = "4/3",
        text = """
            When this creature enters, if a creature died this turn, create a 2/2 blue and black Zombie Rogue creature token.
            Plot {2}{B} (You may pay {2}{B} and exile this card from your hand. Cast it as a sorcery on a later turn without paying its mana cost. Plot only as a sorcery.)
        """,
    ) parsesTo abilities(
        triggered(entersSelf,
            create(creatureToken("Zombie Rogue", listOf(Color.Blue, Color.Black), 2, 2)),
            interveningIf = Condition.HappenedThisTurn(
                EventPattern.ZoneChange(creature, from = Zone.Battlefield, to = Zone.Graveyard), atLeast = 1, measure = Measure.Events)),
        plot("{2}{B}"),
    )

    @Test
    fun riseOfTheGophers() = card(
        name = "Rise of the Gophers",
        cost = "{3}{G}",
        type = "Sorcery",

        text = """
            Create X 2/1 green Gopher creature tokens, where X is the number of creature cards in your graveyard.
            Plot {2}{G} (You may pay {2}{G} and exile this card from your hand. Cast it as a sorcery on a later turn without paying its mana cost. Plot only as a sorcery.)
        """,
    ) parsesTo abilities(
        spell(create(creatureToken("Gopher", listOf(Color.Green), 2, 1), count = count(inYourGraveyard(creature)))),
        plot("{2}{G}"),
    )

    @Test
    fun lockboxPicker() = card(
        name = "Lockbox Picker",
        cost = "{2}{U}",
        type = "Creature — Human Rogue",
        pt = "2/3",
        text = """
            When this creature enters, target instant or sorcery card in your graveyard gains flashback until end of turn. The flashback cost is equal to its mana cost. (You may cast that card from your graveyard for its flashback cost. Then exile it.)
            Plot {2}{U} (You may pay {2}{U} and exile this card from your hand. Cast it as a sorcery on a later turn without paying its mana cost. Plot only as a sorcery.)
        """,
    ) parsesTo abilities(
        triggered(entersSelf,
            untilEndOfTurn(target("card"), Mechanic.GainAbilities(listOf(Keyword.Flashback(cost(CostPart.ItsManaCost))))),
            targets = listOf(slot("card", inYourGraveyard(or(instant, sorcery))))),
        plot("{2}{U}"),
    )

    @Test
    fun showyLockbird() = card(
        name = "Showy Lockbird",
        cost = "{1}{R}",
        type = "Creature — Bird Wizard",
        pt = "1/2",
        text = """
            Flying, haste
            Whenever you cast a noncreature spell, this creature gets +2/+0 until end of turn.
            Plot {1}{R} (You may pay {1}{R} and exile this card from your hand. Cast it as a sorcery on a later turn without paying its mana cost. Plot only as a sorcery.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Haste,
        triggered(youCast(not(creature)), pump(Objects.Self, 2, 0)),
        plot("{1}{R}"),
    )

    @Test
    fun gladewoodPaladin() = card(
        name = "Gladewood Paladin",
        cost = "{4}{G}",
        type = "Creature — Human Knight",
        pt = "5/4",
        text = """
            Trample
            When this creature enters, you gain 3 life.
            Plot {3}{G} (You may pay {3}{G} and exile this card from your hand. Cast it as a sorcery on a later turn without paying its mana cost. Plot only as a sorcery.)
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(entersSelf, gainLife(3)),
        plot("{3}{G}"),
    )

    @Test
    fun depotSecurity() = card(
        name = "Depot Security",
        cost = "{4}{W}",
        type = "Creature — Human Soldier",
        pt = "4/5",
        text = """
            When this creature enters, creatures you control get +1/+1 and gain vigilance until end of turn.
            Plot {3}{W} (You may pay {3}{W} and exile this card from your hand. Cast it as a sorcery on a later turn without paying its mana cost. Plot only as a sorcery.)
        """,
    ) parsesTo abilities(
        triggered(entersSelf, pump(Objects.Each(and(creature, you)), 1, 1, has(Keyword.Vigilance))),
        plot("{3}{W}"),
    )

    @Test
    fun rollingBrush() = card(
        name = "Rolling Brush",
        cost = "{1}{G}",
        type = "Sorcery",

        text = """
            Create an X/X green Elemental creature token, where X is the greatest power among creatures you control.
            Plot {2}{G} (You may pay {2}{G} and exile this card from your hand. Cast it as a sorcery on a later turn without paying its mana cost. Plot only as a sorcery.)
        """,
    ) parsesTo abilities(
        spell(Step.Do(Effect.CreateToken(
            TokenSpec.Defined(FaceDefinition("Elemental Token", colorIndicator = listOf(Color.Green), types = listOf(CardType.Creature),
                subtypes = listOf(Subtype("Elemental")), power = PrintedNumber.X, toughness = PrintedNumber.X),
                power = greatestPower, toughness = greatestPower),
            n(1), Players.You))),
        plot("{2}{G}"),
    )

    @Test
    fun shadyContractor() = card(
        name = "Shady Contractor",
        cost = "{2}{B}",
        type = "Creature — Human Assassin",
        pt = "3/2",
        text = """
            When this creature enters, you may sacrifice a creature. When you do, target player draws two cards and loses 2 life.
            Plot {2}{B} (You may pay {2}{B} and exile this card from your hand. Cast it as a sorcery on a later turn without paying its mana cost. Plot only as a sorcery.)
        """,
    ) parsesTo abilities(
        triggered(entersSelf,
            Step.Pay(cost(sacrificeA(creature)), payer = Players.You, mandatory = false, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(reflexive(draw(2, Players.Target("player")), loseLife(2, Players.Target("player")),
                targets = listOf(TargetSlot("player", TargetKind.Players(PlayerFilter.Any), min = n(1), max = n(1))))))),
        plot("{2}{B}"),
    )
}
