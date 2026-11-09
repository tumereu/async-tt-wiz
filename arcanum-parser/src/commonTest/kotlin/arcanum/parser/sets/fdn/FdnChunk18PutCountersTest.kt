package arcanum.parser.sets.fdn

import arcanum.api.Ability
import arcanum.api.Color
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CounterKind
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.Measure
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Step
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Chunk 18 of FDN: wordings that put +1/+1 (or loyalty) counters. Invented names, the real oracle text. */
class FdnChunk18PutCountersTest {
    private val opponentLostLife = Condition.HappenedThisTurn(EventPattern.LoseLife(PlayerFilter.Opponent), atLeast = 1, measure = Measure.Events)
    private val bigCreature = and(creature, power(Comparison.AtLeast, 4))

    // CR 606.4 (loyalty cost), 115.4 (up to one target), 122.1; CR 702.4 double strike; CR 111.1 (tokens), X = your life total
    @Test
    fun ajaniCallerOfThePride() = card(
        name = "Ajani, Herald of the Pride",
        cost = "{1}{W}{W}",
        type = "Legendary Planeswalker — Ajani",
        loyalty = "4",
        text = """
            [+1]: Put a +1/+1 counter on up to one target creature.
            [−3]: Target creature gains flying and double strike until end of turn.
            [−8]: Create X 2/2 white Cat creature tokens, where X is your life total.
        """,
    ) parsesTo abilities(
        loyalty(1, counters(target("creature")), targets = listOf(slot("creature", creature, min = 0, max = 1))),
        loyalty(-3, gains(target("creature"), Keyword.Flying, Keyword.DoubleStrike), targets = listOf(slot("creature", creature))),
        loyalty(-8, create(creatureToken("Cat", listOf(Color.White), 2, 2), Value.LifeOf(Players.You))),
    )

    // CR 700.2 (modes), 115.1, 122.1
    @Test
    fun apothecaryStomper() = card(
        name = "Herbalist Stomper",
        cost = "{4}{G}{G}",
        type = "Creature — Elephant",
        pt = "4/4",
        text = """
            Vigilance (Attacking doesn't cause this creature to tap.)
            When this creature enters, choose one —
            • Put two +1/+1 counters on target creature you control.
            • You gain 4 life.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        Ability.Triggered(entersSelf, Instructions(modes = modes(
            mode(counters(target("creature"), 2), targets = listOf(slot("creature", creatureYouControl))),
            mode(gainLife(4)),
        ))),
    )

    // CR 508.1 (declaring attackers), 603.2, 122.1
    @Test
    fun armasaurGuide() = card(
        name = "Ridgeback Guide",
        cost = "{4}{W}",
        type = "Creature — Dinosaur",
        pt = "4/4",
        text = """
            Vigilance (Attacking doesn't cause this creature to tap.)
            Whenever you attack with three or more creatures, put a +1/+1 counter on target creature you control.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        triggered(Trigger.On(listOf(EventPattern.Attack(creatureYouControl)), oneOrMore = true, atLeast = 3), counters(target("creature")), targets = listOf(slot("creature", creatureYouControl))),
    )

    // CR 603.2, 117.1a (cast any time with flash, during an opponent's turn), 122.1
    @Test
    fun brineborneCutthroat() = card(
        name = "Tidebound Cutthroat",
        cost = "{1}{U}",
        type = "Creature — Merfolk Pirate",
        pt = "2/1",
        text = """
            Flash (You may cast this spell any time you could cast an instant.)
            Whenever you cast a spell during an opponent's turn, put a +1/+1 counter on this creature.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(youCast(ObjectFilter.Any), counters(Objects.Self), triggersOnlyIf = Condition.Not(Condition.YourTurn)),
    )

    // CR 122.1, 702.9 (flying), 615.1 (prevention), 506.3 (combat damage): the prevention names the target creature
    @Test
    fun fleetingFlight() = card(
        name = "Fleeting Glide",
        cost = "{W}",
        type = "Instant",
        text = "Put a +1/+1 counter on target creature. It gains flying until end of turn. Prevent all combat damage that would be dealt to it this turn.",
    ) parsesTo abilities(
        spell(
            counters(target("creature")),
            gains(target("creature"), Keyword.Flying),
            doing(Effect.ApplyMechanics(listOf(Mechanic.Prevention(EventPattern.Damage(ObjectFilter.Any,
                RecipientFilter(objects = ObjectFilter.Is(target("creature"))), combat = true))), null, Duration.EndOfTurn)),
            targets = listOf(slot("creature", creature)),
        ),
    )

    // CR 603.2, 115.1 ("another"), 122.1: the counter goes on the creature that entered
    @Test
    fun goodFortuneUnicorn() = card(
        name = "Fair-Fortune Unicorn",
        cost = "{1}{G}{W}",
        type = "Creature — Unicorn",
        pt = "2/2",
        text = "Whenever another creature you control enters, put a +1/+1 counter on that creature.",
    ) parsesTo abilities(
        triggered(enters(and(another, creature, you)), counters(Objects.EventObject(arcanum.api.EventRole.After))),
    )

    // CR 603.2, 115.1 ("another"), 608.2h (power as the ability resolves), 122.1, 702.10 (haste)
    @Test
    fun halanaAndAlena() = card(
        name = "Halana and Alena, Pals",
        cost = "{2}{R}{G}",
        type = "Legendary Creature — Human Ranger",
        pt = "2/3",
        text = """
            Reach (This creature can block creatures with flying.)
            First strike (This creature deals combat damage before creatures without first strike.)
            At the beginning of combat on your turn, put X +1/+1 counters on another target creature you control, where X is Halana and Alena's power. That creature gains haste until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        Keyword.FirstStrike,
        triggered(beginningOfCombatOnYourTurn,
            counters(target("creature"), power(Objects.Self)),
            gains(target("creature"), Keyword.Haste),
            targets = listOf(slot("creature", and(another, creature, you)))),
    )

    // CR 603.2, 701.21 (sacrifice), 608.2c ("if you do"), 700.4 (dies), 111.1 (nontoken)
    @Test
    fun highSocietyHunter() = card(
        name = "High-Rank Hunter",
        cost = "{3}{B}{B}",
        type = "Creature — Vampire Noble",
        pt = "5/3",
        text = """
            Flying
            Whenever this creature attacks, you may sacrifice another creature. If you do, put a +1/+1 counter on this creature.
            Whenever another nontoken creature dies, draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(attacksSelf,
            Step.Pay(cost(sacrificeA(and(another, creature))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(counters(Objects.Self)))),
        triggered(dies(and(another, not(token), creature)), draw(1)),
    )

    // CR 602.5b (activated ability), 115.1, 508.1k (attacking), 205.3m (Cat), 608.2c (if), 702.7 (first strike)
    @Test
    fun ingeniousLeonin() = card(
        name = "Clever Leonin",
        cost = "{4}{W}",
        type = "Creature — Cat Soldier",
        pt = "4/4",
        text = "{3}{W}: Put a +1/+1 counter on another target attacking creature you control. If that creature is a Cat, it gains first strike until end of turn. (It deals combat damage before creatures without first strike.)",
    ) parsesTo abilities(
        activated(manaCost("{3}{W}"),
            counters(target("creature")),
            Step.If(Condition.ObjectMatches(target("creature"), sub("Cat")), then = listOf(gains(target("creature"), Keyword.FirstStrike))),
            targets = listOf(slot("creature", and(another, attacking, creature, you)))),
    )

    // CR 603.2, 120.1 (combat damage), 122.1 (loyalty counters), 306.5b
    @Test
    fun kaitoCunningInfiltrator() = card(
        name = "Kaito, Crafty Infiltrator",
        cost = "{1}{U}{U}",
        type = "Legendary Planeswalker — Kaito",
        loyalty = "3",
        text = """
            Whenever a creature you control deals combat damage to a player, put a loyalty counter on Kaito.
            [+1]: Up to one target creature you control can't be blocked this turn. Draw a card, then discard a card.
            [−2]: Create a 2/1 blue Ninja creature token.
            [−9]: You get an emblem with "Whenever a player casts a spell, you create a 2/1 blue Ninja creature token."
        """,
    ) parsesTo abilities(
        triggered(on(EventPattern.Damage(creatureYouControl, RecipientFilter(players = PlayerFilter.Any), combat = true)),
            counters(Objects.Self, 1, CounterKind.Loyalty)),
        loyalty(1, untilEndOfTurn(target("creature"), Mechanic.CantBeBlocked), draw(1), discard(1),
            targets = listOf(slot("creature", creatureYouControl, min = 0, max = 1))),
        loyalty(-2, create(creatureToken("Ninja", listOf(Color.Blue), 2, 1))),
        loyalty(-9, doing(Effect.CreateEmblem(listOf(
            triggered(on(EventPattern.Cast(ObjectFilter.Any, PlayerFilter.Any)), create(creatureToken("Ninja", listOf(Color.Blue), 2, 1))),
        ), Players.You))),
    )

    // CR 603.2, 700.2? no: 603.5 (may), 105.1 (blue or black), 122.1
    @Test
    fun moldAdder() = card(
        name = "Mildew Adder",
        cost = "{G}",
        type = "Creature — Fungus Snake",
        pt = "1/1",
        text = "Whenever an opponent casts a blue or black spell, you may put a +1/+1 counter on this creature.",
    ) parsesTo abilities(
        triggered(on(EventPattern.Cast(or(ObjectFilter.OfColor(Color.Blue), ObjectFilter.OfColor(Color.Black)), PlayerFilter.Opponent)),
            Step.May(listOf(counters(Objects.Self)), player = Players.You)),
    )

    // CR 603.4 (intervening if), 603.2, 122.1, 202.3
    @Test
    fun nessianHornbeetle() = card(
        name = "Nessian Hornweevil",
        cost = "{1}{G}",
        type = "Creature — Insect",
        pt = "2/2",
        text = "At the beginning of combat on your turn, if you control another creature with power 4 or greater, put a +1/+1 counter on this creature.",
    ) parsesTo abilities(
        triggered(beginningOfCombatOnYourTurn, counters(Objects.Self), interveningIf = Condition.Exists(and(another, creature, power(Comparison.AtLeast, 4), you), 1)),
    )

    // CR 603.4, 119.3 (life lost), 205.3m (Vampire), 122.1
    @Test
    fun stromkirkBloodthief() = card(
        name = "Stormkirk Bloodthief",
        cost = "{2}{B}",
        type = "Creature — Vampire Rogue",
        pt = "2/2",
        text = "At the beginning of your end step, if an opponent lost life this turn, put a +1/+1 counter on target Vampire you control.",
    ) parsesTo abilities(
        triggered(stepBegins(TurnStep.End, PlayerFilter.You), counters(target("Vampire")),
            targets = listOf(slot("Vampire", and(sub("Vampire"), you))), interveningIf = opponentLostLife),
    )

    // CR 700.2 (modes), 603.2, 603.4 style "if" inside a mode (an instruction's own condition, 608.2c), 122.1
    @Test
    fun sylvanScavenging() = card(
        name = "Sylvan Foraging",
        cost = "{1}{G}{G}",
        type = "Enchantment",
        text = """
            At the beginning of your end step, choose one —
            • Put a +1/+1 counter on target creature you control.
            • Create a 3/3 green Raccoon creature token if you control a creature with power 4 or greater.
        """,
    ) parsesTo abilities(
        Ability.Triggered(stepBegins(TurnStep.End, PlayerFilter.You), Instructions(modes = modes(
            mode(counters(target("creature")), targets = listOf(slot("creature", creatureYouControl))),
            mode(Step.If(Condition.Exists(and(bigCreature, you), 1), then = listOf(create(creatureToken("Raccoon", listOf(Color.Green), 3, 3))))),
        ))),
    )

    // CR 602.5d (sorcery timing), 115.1, 122.1
    @Test
    fun treetopSnarespinner() = card(
        name = "Treetop Webspinner",
        cost = "{3}{G}",
        type = "Creature — Spider",
        pt = "1/4",
        text = """
            Reach (This creature can block creatures with flying.)
            Deathtouch (Any amount of damage this deals to a creature is enough to destroy it.)
            {2}{G}: Put a +1/+1 counter on target creature you control. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        Keyword.Deathtouch,
        activated(manaCost("{2}{G}"), counters(target("creature")), targets = listOf(slot("creature", creatureYouControl)), rules = asSorcery),
    )

    // CR 603.2, 115.1 ("another"), 205.3m (Angel), 122.1
    @Test
    fun youthfulValkyrie() = card(
        name = "Youthful Seraph",
        cost = "{1}{W}",
        type = "Creature — Angel",
        pt = "1/3",
        text = """
            Flying
            Whenever another Angel you control enters, put a +1/+1 counter on this creature.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(enters(and(another, sub("Angel"), you)), counters(Objects.Self)),
    )
}
