package arcanum.parser.sets.inr

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * INR chunk 15: drawing (CR 121), gaining and losing life (CR 119), gaining abilities (CR 613.1f),
 * looking at cards (CR 701.20 area) and power/toughness changes (CR 613.4c).
 */
class InrChunk15DrawGainLifeLookPtTest {
    // CR 118.1 and 119.4: paying life is a cost; CR 121.1 draws
    @Test
    fun griselbrand() = card(
        name = "Griselbrand", cost = "{4}{B}{B}{B}{B}", type = "Legendary Creature — Demon", pt = "7/7",
        text = """
            Flying, lifelink
            Pay 7 life: Draw seven cards.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Lifelink,
        activated(cost(CostPart.PayLife(n(7))), draw(7)),
    )

    // CR 603.2c: one trigger for one or more creatures; CR 603.4: once each turn
    @Test
    fun morbidOpportunist() = card(
        name = "Morbid Opportunist", cost = "{2}{B}", type = "Creature — Human Rogue", pt = "1/3",
        text = "Whenever one or more other creatures die, draw a card. This ability triggers only once each turn.",
    ) parsesTo abilities(
        triggered(Trigger.On(EventPattern.ZoneChange(and(another, creature), from = Zone.Battlefield, to = Zone.Graveyard), oneOrMore = true),
            draw(1), perTurn = 1),
    )

    // CR 121.1 and 701.9: the targeted player draws, then discards
    @Test
    fun recklessScholar() = card(
        name = "Reckless Scholar", cost = "{2}{U}", type = "Creature — Human Wizard", pt = "2/1",
        text = "{T}: Target player draws a card, then discards a card.",
    ) parsesTo abilities(
        activated(cost(tap), draw(1, Players.Target("player")), discard(1, Players.Target("player")), targets = listOf(targetPlayer)),
    )

    // CR 613.1f and 702.12: other permanents you control have indestructible
    @Test
    fun avacynAngelOfHope() = card(
        name = "Avacyn, Angel of Hope", cost = "{5}{W}{W}{W}", type = "Legendary Creature — Angel", pt = "8/8",
        text = """
            Flying, vigilance, indestructible
            Other permanents you control have indestructible.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Vigilance,
        Keyword.Indestructible,
        static(has(Keyword.Indestructible), affects = Objects.Each(and(another, permanentFilter, you))),
    )

    // CR 611.3a, 508.1k: first strike only while attacking; CR 111.10g: the Blood token
    @Test
    fun bloodPetalCelebrant() = card(
        name = "Blood Petal Celebrant", cost = "{1}{R}", type = "Creature — Vampire", pt = "2/1",
        text = """
            This creature has first strike as long as it's attacking.
            When this creature dies, create a Blood token. (It's an artifact with "{1}, {T}, Discard a card, Sacrifice this token: Draw a card.")
        """,
    ) parsesTo abilities(
        static(has(Keyword.FirstStrike), condition = Condition.ObjectMatches(Objects.Self, attacking)),
        triggered(diesSelf, create(blood)),
    )

    // CR 613.1f, 605.1a: creatures you control have a mana ability
    @Test
    fun cryptolithRite() = card(
        name = "Cryptolith Rite", cost = "{1}{G}", type = "Enchantment",
        text = "Creatures you control have \"{T}: Add one mana of any color.\"",
    ) parsesTo abilities(
        static(has(activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1))), Players.You)))), affects = Objects.Each(creatureYouControl)),
    )

    // CR 613.1f: every creature, whoever controls it, has haste
    @Test
    fun massHysteria() = card(
        name = "Mass Hysteria", cost = "{R}", type = "Enchantment",
        text = "All creatures have haste.",
    ) parsesTo abilities(
        static(has(Keyword.Haste), affects = Objects.Each(creature)),
    )

    // CR 603.4: the intervening-if checks another Spirit on entering; CR 119.1: gain 3 life
    @Test
    fun apothecaryGeist() = card(
        name = "Apothecary Geist", cost = "{3}{W}", type = "Creature — Spirit", pt = "2/3",
        text = """
            Flying
            When this creature enters, if you control another Spirit, you gain 3 life.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, gainLife(3), interveningIf = Condition.Exists(and(another, sub("Spirit"), you), atLeast = 1)),
    )

    // CR 602.2 and 118.8: exiling itself from the graveyard is a cost; CR 119.1
    @Test
    fun brambleWurm() = card(
        name = "Bramble Wurm", cost = "{6}{G}", type = "Creature — Wurm", pt = "7/6",
        text = """
            Reach, trample
            When this creature enters, you gain 5 life.
            {2}{G}, Exile this card from your graveyard: You gain 5 life.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        Keyword.Trample,
        triggered(entersSelf, gainLife(5)),
        activated(cost(mana("{2}{G}"), CostPart.Exile(CostObjects.Self, from = Zone.Graveyard)), gainLife(5)),
    )

    // CR 700.2 area: look at the top card, reveal it to put it in hand; CR 603.10a: dies triggers look back
    @Test
    fun archghoulOfThraben() = card(
        name = "Archghoul of Thraben", cost = "{2}{B}", type = "Creature — Zombie Cleric", pt = "3/2",
        text = "Whenever this creature or another Zombie you control dies, look at the top card of your library. If it's a Zombie card, you may reveal it and put it into your hand. If you don't put the card into your hand, you may put it into your graveyard.",
    ) parsesTo abilities(
        triggered(Trigger.On(EventPattern.ZoneChange(or(ObjectFilter.Is(Objects.Self), and(another, sub("Zombie"), you)), from = Zone.Battlefield, to = Zone.Graveyard)),
            Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, n(1), fromTop = true), Players.You, bind = "top")),
            Step.If(Condition.ObjectMatches(Objects.Bound("top"), sub("Zombie")), then = listOf(
                Step.Pay(steps = listOf(
                    doing(Effect.Reveal(Objects.Bound("top"))),
                    doing(Effect.Move(Objects.Bound("top"), Destination(Zone.Hand))),
                ), payer = Players.You, bind = "kept"))),
            Step.If(Condition.Not(Condition.Did("kept")), then = listOf(
                Step.May(listOf(doing(Effect.Move(Objects.Bound("top"), Destination(Zone.Graveyard)))), player = Players.You))),
        ),
    )

    // CR 701.21 area: tapping untapped creatures is a cost (CR 118.3); CR 119.3 life loss
    @Test
    fun siegeZombie() = card(
        name = "Siege Zombie", cost = "{1}{B}", type = "Creature — Zombie", pt = "2/2",
        text = "Tap three untapped creatures you control: Each opponent loses 1 life.",
    ) parsesTo abilities(
        activated(cost(CostPart.TapObjects(CostObjects.Choose(creature, count = n(3)))), loseLife(1, Players.Each(PlayerFilter.Opponent))),
    )

    // CR 613.4c: +1/+0 to other Spirit creatures you control
    @Test
    fun battlegroundGeist() = card(
        name = "Battleground Geist", cost = "{4}{U}", type = "Creature — Spirit", pt = "3/3",
        text = """
            Flying
            Other Spirit creatures you control get +1/+0.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        static(pt(1, 0), affects = Objects.Each(and(another, sub("Spirit"), creature, you))),
    )

    // CR 613.4c: other Zombies you control get +1/+0
    @Test
    fun bladestitchedSkaab() = card(
        name = "Bladestitched Skaab", cost = "{U}{B}", type = "Creature — Zombie Soldier", pt = "2/3",
        text = "Other Zombies you control get +1/+0.",
    ) parsesTo abilities(
        static(pt(1, 0), affects = Objects.Each(and(another, sub("Zombie"), you))),
    )

    // CR 613.4c; CR 613.1d and 205.1b: gaining control, and an added creature type
    @Test
    fun captivatingVampire() = card(
        name = "Captivating Vampire", cost = "{1}{B}{B}", type = "Creature — Vampire", pt = "2/2",
        text = """
            Other Vampire creatures you control get +1/+1.
            Tap five untapped Vampires you control: Gain control of target creature. It becomes a Vampire in addition to its other types.
        """,
    ) parsesTo abilities(
        static(pt(1, 1), affects = Objects.Each(and(another, sub("Vampire"), creature, you))),
        activated(cost(CostPart.TapObjects(CostObjects.Choose(sub("Vampire"), count = n(5)))),
            doing(Effect.ApplyMechanics(listOf(Mechanic.ControlledBy(Players.You)), Affected.Objects(target("creature")), Duration.Indefinitely)),
            doing(Effect.ApplyMechanics(listOf(Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Vampire"))))),
                Affected.Objects(target("creature")), Duration.Indefinitely)),
            targets = listOf(slot("creature", creature))),
    )

    // CR 613.4c, 613.1f: one static ability gives both the bonus and trample to Wolves and Werewolves you control
    @Test
    fun howlpackResurgence() = card(
        name = "Howlpack Resurgence", cost = "{2}{G}", type = "Enchantment",
        text = """
            Flash
            Each creature you control that's a Wolf or a Werewolf gets +1/+1 and has trample.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        static(pt(1, 1), has(Keyword.Trample), affects = Objects.Each(and(creature, you, or(sub("Wolf"), sub("Werewolf"))))),
    )

    // CR 611.3a: a condition on the controller's board; CR 613.4c and 613.1f
    @Test
    fun hungryRidgewolf() = card(
        name = "Hungry Ridgewolf", cost = "{1}{R}", type = "Creature — Wolf", pt = "2/2",
        text = "As long as you control another Wolf or Werewolf, this creature gets +1/+0 and has trample.",
    ) parsesTo abilities(
        static(pt(1, 0), has(Keyword.Trample), condition = Condition.Exists(and(another, or(sub("Wolf"), sub("Werewolf")), you), atLeast = 1)),
    )

    // CR 111.1 and 613.4c: creature tokens you control get +1/+1 and vigilance
    @Test
    fun intangibleVirtue() = card(
        name = "Intangible Virtue", cost = "{1}{W}", type = "Enchantment",
        text = "Creature tokens you control get +1/+1 and have vigilance.",
    ) parsesTo abilities(
        static(pt(1, 1), has(Keyword.Vigilance), affects = Objects.Each(and(creature, token, you))),
    )
}
