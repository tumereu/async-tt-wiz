package arcanum.parser.sets.snc

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** SNC chunk 06: the hideaway enchantments, mill, ward and counter conditions (CR 702.75, 701.17, 702.21). */
class SncChunk06HideawayMillWardTest {
    private val hideaway = Keyword.Hideaway(5)
    private val hideawayText = "Hideaway 5 (When this enchantment enters, look at the top five cards of your library, exile one face down, then put the rest on the bottom in a random order.)"
    /** "you may play the exiled card without paying its mana cost" (the card hideaway exiled, CR 702.75a, 607.2a) */
    private val playExiled = Step.May(listOf(doing(Effect.Cast(Objects.LinkedTo(Keyword.Hideaway.Kind), PlayKind.Play, withoutPaying = true,
        caster = Players.You))), Players.You)
    private val citizen = creatureToken("Citizen", listOf(Color.Green, Color.White), 1, 1)

    // CR 702.75a, 701.17a, 603.4 (the "then if" is checked as the trigger resolves, not an intervening if)
    @Test
    fun cemeteryTampering() = card(
        name = "Graveyard Meddling",
        cost = "{2}{B}",
        type = "Enchantment",
        text = """
            $hideawayText
            At the beginning of your upkeep, you may mill three cards. Then if there are twenty or more cards in your graveyard, you may play the exiled card without paying its mana cost.
        """,
    ) parsesTo abilities(
        hideaway,
        triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.You), Step.May(listOf(mill(3)), Players.You),
            ifThen(Condition.Exists(and(inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)), atLeast = 20), playExiled)),
    )

    // CR 702.75a, 122.1
    @Test
    fun fightRigging() = card(
        name = "Bout Fixing",
        cost = "{2}{G}",
        type = "Enchantment",
        text = """
            $hideawayText
            At the beginning of combat on your turn, put a +1/+1 counter on target creature you control. Then if you control a creature with power 7 or greater, you may play the exiled card without paying its mana cost.
        """,
    ) parsesTo abilities(
        hideaway,
        triggered(beginningOfCombatOnYourTurn, counters(target("creature")),
            ifThen(Condition.Exists(and(creature, power(Comparison.AtLeast, 7), you), atLeast = 1), playExiled),
            targets = listOf(slot("creature", creatureYouControl))),
    )

    // CR 702.75a, 508.1 (one trigger for the attack; "that many": the attacking creatures)
    @Test
    fun rabbleRousing() = card(
        name = "Mob Stirring",
        cost = "{4}{W}",
        type = "Enchantment",
        text = """
            $hideawayText
            Whenever you attack with one or more creatures, create that many 1/1 green and white Citizen creature tokens. Then if you control ten or more creatures, you may play the exiled card without paying its mana cost.
        """,
    ) parsesTo abilities(
        hideaway,
        triggered(Trigger.On(EventPattern.Attack(creatureYouControl), oneOrMore = true), create(citizen, Value.EventAmount),
            ifThen(Condition.Exists(creatureYouControl, atLeast = 10), playExiled)),
    )

    // CR 702.75a, 105.2b (multicolored), 118.1 ("you may pay … If you do")
    @Test
    fun widespreadThieving() = card(
        name = "Rampant Pilfering",
        cost = "{2}{R}",
        type = "Enchantment",
        text = """
            $hideawayText
            Whenever you cast a multicolored spell, create a Treasure token. Then you may pay {W}{U}{B}{R}{G}. If you do, you may play the exiled card without paying its mana cost.
        """,
    ) parsesTo abilities(
        hideaway,
        triggered(youCast(ObjectFilter.Multicolored), createTreasure(),
            Step.Pay(cost(mana("{W}{U}{B}{R}{G}")), payer = Players.You, bind = "paid"), ifThen(Condition.Did("paid"), playExiled)),
    )

    // CR 702.75a, 504.1 (the draw step's draw, then only draws within your draw step count), 603.2
    @Test
    fun wiretapping() = card(
        name = "Line Tapping",
        cost = "{4}{U}",
        type = "Enchantment",
        text = """
            $hideawayText
            Whenever you draw your first card during each of your draw steps, draw a card. Then if you have nine or more cards in hand, you may play the exiled card without paying its mana cost.
        """,
    ) parsesTo abilities(
        hideaway,
        triggered(Trigger.On(listOf(EventPattern.Draw(PlayerFilter.You)), nthThisTurn = 1, during = TurnStep.Draw), draw(1),
            ifThen(Condition.Compare(Value.HandSize(Players.You), Comparison.AtLeast, n(9)), playExiled)),
    )

    // CR 615.5 (a prevention effect with an additional effect; "that many": the damage prevented), 701.17a
    @Test
    fun angelOfSuffering() = card(
        name = "Angel of Torment's Toll",
        cost = "{3}{B}{B}",
        type = "Creature — Nightmare Angel",
        pt = "5/3",
        text = """
            Flying
            If damage would be dealt to you, prevent that damage and mill twice that many cards.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        static(Mechanic.Prevention(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(players = PlayerFilter.You)),
            additional = Instructions(steps = listOf(doing(Effect.Mill(Players.You, Value.Times(Value.EventAmount, 2))))))),
    )

    // CR 202.3 (each card has one mana value; a land's is 0), 611.3a, 700.4
    @Test
    fun avenHeartstabber() = card(
        name = "Aven Spinestabber",
        cost = "{U}{B}",
        type = "Creature — Bird Assassin",
        pt = "1/1",
        text = """
            Flying
            As long as there are five or more mana values among cards in your graveyard, this creature gets +2/+2 and has deathtouch.
            When this creature dies, mill two cards, then draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        static(pt(2, 2), has(Keyword.Deathtouch), condition = fiveManaValues),
        triggered(diesSelf, mill(2), draw(1)),
    )

    // CR 202.3, 701.17a
    @Test
    fun snoopingNewsie() = card(
        name = "Prying Paperboy",
        cost = "{U}{B}",
        type = "Creature — Human Rogue",
        pt = "2/2",
        text = """
            When this creature enters, mill two cards. (Put the top two cards of your library into your graveyard.)
            As long as there are five or more mana values among cards in your graveyard, this creature gets +1/+1 and has lifelink.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, mill(2)),
        static(pt(1, 1), has(Keyword.Lifelink), condition = fiveManaValues),
    )

    private val fiveManaValues = Condition.Compare(Value.CountDistinct(Objects.Each(and(inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))),
        SharedQuality.Number(NumericProperty.ManaValue)), Comparison.AtLeast, n(5))

    // CR 606.3 (loyalty abilities), 701.21a, 701.23a, 701.17a ("milled this way": the cards the mill put into the graveyard)
    @Test
    fun vivienOnTheHunt() = card(
        name = "Vivien, Stalker of Prey",
        cost = "{4}{G}{G}",
        type = "Legendary Planeswalker — Vivien",
        loyalty = "4",
        text = """
            [+2]: You may sacrifice a creature. If you do, search your library for a creature card with mana value equal to 1 plus the sacrificed creature's mana value, put it onto the battlefield, then shuffle.
            [+1]: Mill five cards, then put any number of creature cards milled this way into your hand.
            [−1]: Create a 4/4 green Rhino Warrior creature token.
        """,
    ) parsesTo abilities(
        loyalty(2, Step.Pay(cost(sacrificeA(creature, preBind = "sacrificed")), payer = Players.You, bind = "paid"),
            ifThen(Condition.Did("paid"),
                search(and(creature, ObjectFilter.ManaValue(Comparison.Equal, Value.Sum(listOf(n(1),
                    Value.Of(NumericProperty.ManaValue, Objects.Bound("sacrificed"), Combine.Single)))))),
                move(Objects.Bound("found"), Zone.Battlefield), shuffle)),
        loyalty(1, mill(5, postBind = "milled"),
            Step.Choose(Choice.Objects(creature, min = n(0), max = null, from = Objects.Bound("milled")), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Hand)),
        loyalty(-1, create(creatureToken("Rhino Warrior", listOf(Color.Green), 4, 4))),
    )

    private fun loyalty(amount: Int, vararg steps: Step) = activated(Cost(listOf(CostPart.Loyalty(n(amount)))), *steps)

    // CR 701.17a, 107.1a (half, rounded down), 506.2 (defending player), 701.21a
    @Test
    fun dealGoneBad() = card(
        name = "Deal Turned Sour",
        cost = "{3}{B}",
        type = "Instant",
        text = "Target creature gets -3/-3 until end of turn. Target player mills three cards. (They put the top three cards of their library into their graveyard.)",
    ) parsesTo abilities(
        spell(pump(target("creature"), -3, -3), mill(3, Players.Target("player")), targets = listOf(slot("creature", creature), targetPlayer)),
    )

    // CR 107.1a (half, rounded down), 506.2, 701.9a, 701.21a, 603.10a (dies)
    @Test
    fun lordXanderTheCollector() {
        val opponent = Players.Target("opponent")
        val defending = Players.DefendingPlayerOf(Objects.Self)
        val theirNonland = and(nonland, permanentFilter, ObjectFilter.ControlledBy(PlayerFilter.Is(opponent)))
        val half = Value.Half(count(theirNonland), roundUp = false)
        card(
            name = "Lord Vexor, the Hoarder",
            cost = "{4}{U}{B}{R}",
            type = "Legendary Creature — Vampire Demon Noble",
            pt = "6/6",
            text = """
                When Lord Vexor enters, target opponent discards half the cards in their hand, rounded down.
                Whenever Lord Vexor attacks, defending player mills half their library, rounded down.
                When Lord Vexor dies, target opponent sacrifices half the nonland permanents they control of their choice, rounded down.
            """,
        ) parsesTo abilities(
            triggered(entersSelf, doing(Effect.Discard(opponent, Value.Half(Value.HandSize(opponent), roundUp = false), ObjectFilter.Any)),
                targets = listOf(targetOpponent)),
            triggered(attacksSelf, doing(Effect.Mill(defending, Value.Half(Value.Count(Objects.Each(
                and(ObjectFilter.InZone(Zone.Library), ObjectFilter.OwnedBy(PlayerFilter.Is(defending))))), roundUp = false)))),
            triggered(diesSelf, Step.Choose(Choice.Objects(theirNonland, half, half), bind = "sacrificed", chooser = opponent),
                doing(Effect.Sacrifice(Objects.Bound("sacrificed"))), targets = listOf(targetOpponent)),
        )
    }

    // CR 702.19 (trample), 702.21a (ward), 603.4 (intervening if), 101.4 (any opponent: in turn order until one does), 118.3
    @Test
    fun reservoirKraken() = card(
        name = "Cistern Kraken",
        cost = "{2}{U}{U}",
        type = "Creature — Kraken",
        pt = "6/6",
        text = """
            Trample, ward {2}
            At the beginning of each combat, if this creature is untapped, any opponent may tap an untapped creature they control. If they do, tap this creature and create a 1/1 blue Fish creature token with "This token can't be blocked."
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        Keyword.Ward(manaCost("{2}")),
        triggered(stepBegins(TurnStep.BeginningOfCombat, PlayerFilter.Any),
            Step.Pay(cost(CostPart.TapObjects(CostObjects.Choose(creature, count = n(1)))), payer = Players.Each(PlayerFilter.Opponent), bind = "paid"),
            ifThen(Condition.Did("paid"), tapIt(Objects.Self),
                create(creatureToken("Fish", listOf(Color.Blue), 1, 1, abilities = listOf(static(Mechanic.CantBeBlocked))))),
            interveningIf = Condition.ObjectMatches(Objects.Self, not(tapped))),
    )

    // CR 702.3b (defender), 122.1 (counters of any kind, all creatures you control together)
    @Test
    fun backstreetBruiser() = card(
        name = "Alleyway Bruiser",
        cost = "{1}{U}",
        type = "Creature — Octopus Rogue",
        pt = "3/3",
        text = """
            Defender
            As long as there are two or more counters among creatures you control, this creature can attack as though it didn't have defender.
        """,
    ) parsesTo abilities(
        Keyword.Defender,
        static(Mechanic.CanAttackDespiteDefender, condition = Condition.Compare(Value.Of(NumericProperty.Counters(null), Objects.Each(creatureYouControl),
            Combine.Total), Comparison.AtLeast, n(2))),
    )

    // CR 608.2b (checked as it resolves), 122.1 (a counter of any kind)
    @Test
    fun revelationOfPower() = card(
        name = "Unveiling of Might",
        cost = "{1}{W}",
        type = "Instant",
        text = "Target creature gets +2/+2 until end of turn. If it has a counter on it, it also gains flying and lifelink until end of turn.",
    ) parsesTo abilities(
        spell(pump(target("creature"), 2, 2),
            ifThen(Condition.ObjectMatches(target("creature"), ObjectFilter.HasCounter(null, atLeast = 1)), gains(target("creature"), Keyword.Flying, Keyword.Lifelink)),
            targets = listOf(slot("creature", creature))),
    )

    // CR 614.1a (a replacement of creating tokens), 614.5 (one replacement: applied once, whichever token is chosen), 111.2
    @Test
    fun jinnieFayJetmirsSecond() = card(
        name = "Jennet Fay, Jetmir's Aide",
        cost = "{R/G}{G}{G/W}",
        type = "Legendary Creature — Elf Druid",
        pt = "3/3",
        text = "If you would create one or more tokens, you may instead create that many 2/2 green Cat creature tokens with haste or that many 3/1 green Dog creature tokens with vigilance.",
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.CreateToken(you), modify = listOf(Modification.CreateInsteadChosen(listOf(
            creatureToken("Cat", listOf(Color.Green), 2, 2, abilities = listOf(Keyword.Haste)),
            creatureToken("Dog", listOf(Color.Green), 3, 1, abilities = listOf(Keyword.Vigilance))))), optional = true)),
    )
}
