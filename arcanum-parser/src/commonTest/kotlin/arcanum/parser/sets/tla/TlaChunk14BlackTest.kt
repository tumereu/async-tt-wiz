package arcanum.parser.sets.tla

import arcanum.api.Ability
import arcanum.api.AbilityLabel
import arcanum.api.AttackTarget
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.TokenSpec
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Avatar: The Last Airbender chunk 14: black cards: sacrifices as optional costs and as effects, counters moved onto this creature from one
 * that died (CR 603.10a), "those creatures" fixed once (CR 608.2h), a named token entering attacking (CR 508.4), two cards drawn this turn,
 * removing up to three counters, a creature copying itself, the cards a creature exiled and the abilities of the last one chosen (CR 607.2a,
 * 613.1f), and an ability that works from the graveyard (CR 113.6c).
 */
class TlaChunk14BlackTest {
    private val drewTwo = Condition.HappenedThisTurn(EventPattern.Draw(PlayerFilter.You), atLeast = 2, measure = Measure.Events)

    // CR 118.12: both parts happen only if you sacrificed
    @Test
    fun mantisMaskedTraders() = card(
        name = "Mantis-Masked Traders",
        cost = "{4}{B}",
        type = "Creature — Human Citizen",
        text = "Whenever this creature attacks, you may sacrifice another creature or artifact. If you do, draw a card and put a +1/+1 counter on this creature.",
        pt = "5/4",
    ) parsesTo abilities(
        triggered(attacksSelf,
            Step.Pay(cost(CostPart.Sacrifice(CostObjects.Choose(and(another, or(creature, artifact)), count = n(1)))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(draw(1), counters(Objects.Self)))),
    )

    // CR 603.10a: the counters it had as it last existed
    @Test
    fun vultureHornetSwarm() = card(
        name = "Vulture-Hornet Swarm",
        cost = "{3}{B}",
        type = "Creature — Bird Insect",
        text = """
            Flying
            When this creature enters, you may sacrifice an artifact or creature. If you do, draw a card.
            Whenever another creature you control dies, if it had counters on it, put its counters on this creature.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, Step.Pay(cost(sacrificeA(or(artifact, creature))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(draw(1)))),
        triggered(dies(and(another, creature, you)), doing(Effect.PutCountersOf(Objects.Self, of = leftOne)),
            interveningIf = Condition.ObjectMatches(leftOne, ObjectFilter.HasCounter(null, atLeast = 1))),
    )

    @Test
    fun heartlessExaminer() = card(
        name = "Heartless Examiner",
        cost = "{B}",
        type = "Creature — Human Soldier",
        text = """
            Menace (This creature can't be blocked except by two or more creatures.)
            When this creature dies, it deals 1 damage to you. Create a Clue token. (It's an artifact with "{2}, Sacrifice this token: Draw a card.")
        """,
        pt = "1/1",
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(diesSelf, damage(1, Recipients(players = Players.You)), createClue),
    )

    @Test
    fun ravineCreeper() = card(
        name = "Ravine Creeper",
        cost = "{4}{B}{B}",
        type = "Creature — Spider Beast",
        text = """
            Deathtouch
            When this creature enters, create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
            Swampcycling {2} ({2}, Discard this card: Search your library for a Swamp card, reveal it, put it into your hand, then shuffle.)
        """,
        pt = "6/6",
    ) parsesTo abilities(
        Keyword.Deathtouch,
        triggered(entersSelf, createFood),
        Keyword.Typecycling(sub("Swamp"), "Swampcycling", manaCost("{2}")),
    )

    @Test
    fun lynxCaiman() = card(
        name = "Lynx-Caiman",
        cost = "{6}{B}",
        type = "Creature — Fish Crocodile",
        text = """
            Lifelink
            When this creature enters, it deals damage equal to the number of Swamps you control to any target.
        """,
        pt = "3/2",
    ) parsesTo abilities(
        Keyword.Lifelink,
        triggered(entersSelf, damage(count(and(sub("Swamp"), you)), toAny), targets = listOf(anyTarget)),
    )

    @Test
    fun crookedPalaceClerk() = card(
        name = "Crooked Palace Clerk",
        cost = "{1}{B}",
        type = "Creature — Human Advisor",
        text = "When this creature enters, target opponent discards a card.",
        pt = "1/1",
    ) parsesTo abilities(
        triggered(entersSelf, discard(1, Players.Target("opponent")), targets = listOf(targetOpponent)),
    )

    // CR 608.2h: "those creatures" are the ones that lost their abilities
    @Test
    fun dayOfDarkenedSun() = card(
        name = "Day of Darkened Sun",
        cost = "{X}{B}{B}",
        type = "Sorcery",
        text = "Each creature with mana value X or less loses all abilities until end of turn. Destroy those creatures.",
    ) parsesTo abilities(
        spell(Step.Bind(Objects.Each(and(creature, ObjectFilter.ManaValue(Comparison.AtMost, x))), "those"),
            untilEndOfTurn(Objects.Bound("those"), Mechanic.LoseAbilities()),
            destroy(Objects.Bound("those")),
            announced = listOf("X")),
    )

    // CR 601.2b: "A or B" as an additional cost
    @Test
    fun lethalExactness() = card(
        name = "Lethal Exactness",
        cost = "{B}",
        type = "Sorcery",
        text = """
            As an additional cost to cast this spell, pay {4} or sacrifice an artifact or creature.
            Destroy target creature.
        """,
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = cost(CostPart.OneOf(listOf(manaCost("{4}"), cost(sacrificeA(or(artifact, creature)))))))),
        spell(destroy(target("creature")), targets = listOf(slot("creature", creature))),
    )

    @Test
    fun grandCollapse() = card(
        name = "Grand Collapse",
        cost = "{1}{B}",
        type = "Sorcery",
        text = "Exile target creature with mana value 3 or greater.",
    ) parsesTo abilities(
        spell(move(target("creature"), Zone.Exile), targets = listOf(slot("creature", and(creature, manaValue(Comparison.AtLeast, 3))))),
    )

    // CR 603.12 (tapping it as an optional cost, then a reflexive trigger), 613.1f
    @Test
    fun theEmberLegionDrill() = card(
        name = "The Ember Legion Drill",
        cost = "{2}{B}{B}",
        type = "Legendary Artifact — Vehicle",
        text = """
            Trample
            When The Ember Legion Drill enters, you may tap it. When you do, destroy target creature with power 4 or less.
            {1}: Permanents your opponents control lose hexproof and indestructible until end of turn.
            Crew 2
        """,
        pt = "6/3",
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(entersSelf, Step.Pay(cost(tap), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(reflexive(destroy(target("creature")),
                targets = listOf(slot("creature", and(creature, power(Comparison.AtMost, 4)))))))),
        activated(cost(mana("{1}")), untilEndOfTurn(Objects.Each(and(permanentFilter, opponents)), Mechanic.LoseAbilities(Keyword.Hexproof),
            Mechanic.LoseAbilities(Keyword.Indestructible))),
        Keyword.Crew(2),
    )

    // CR 207.2c (raid), 603.4
    @Test
    fun emberLegionEngineer() = card(
        name = "Ember Legion Engineer",
        cost = "{2}{B}",
        type = "Creature — Human Artificer",
        text = "Raid — At the beginning of your end step, if you attacked this turn, put a +1/+1 counter on another target creature or Vehicle you control.",
        pt = "2/3",
    ) parsesTo abilities(
        triggered(yourEndStep, counters(target("permanent")), targets = listOf(slot("permanent", and(another, or(creature, sub("Vehicle")), you))),
            interveningIf = Condition.HappenedThisTurn(EventPattern.Attack(you), atLeast = 1, measure = Measure.Events), abilityWord = "Raid"),
    )

    // CR 111.4 (a token with a name), 508.4 (entering tapped and attacking), 603.7
    @Test
    fun emberFleetCatapult() = card(
        name = "Ember Fleet Catapult",
        cost = "{2}{B}",
        type = "Artifact Creature — Wall",
        text = """
            Defender, reach
            Whenever you attack, create a 2/1 colorless Construct artifact creature token with flying named Ballistic Boulder that's tapped and attacking. Sacrifice that token at the beginning of the next end step.
        """,
        pt = "0/4",
    ) parsesTo abilities(
        Keyword.Defender,
        Keyword.Reach,
        triggered(youAttack,
            create(creatureToken("Construct", emptyList(), 2, 1, abilities = listOf(Keyword.Flying), types = listOf(CardType.Artifact, CardType.Creature),
                name = "Ballistic Boulder"), bind = "token", tapped = true, attacking = AttackTarget.Chosen),
            delayed(stepBegins(TurnStep.End, PlayerFilter.Any), doing(Effect.Sacrifice(Objects.Bound("token"))))),
    )

    @Test
    fun mistyBogHunters() = card(
        name = "Misty Bog Hunters",
        cost = "{3}{B}",
        type = "Creature — Human Ranger Ally",
        text = "As long as you've drawn two or more cards this turn, this creature has lifelink and menace.",
        pt = "3/4",
    ) parsesTo abilities(
        static(has(Keyword.Lifelink, Keyword.Menace), condition = drewTwo),
    )

    // CR 122.8: removing up to three counters (a number chosen as it resolves)
    @Test
    fun coldHeartedDeed() = card(
        name = "Cold-Hearted Deed",
        cost = "{1}{B}",
        type = "Instant",
        text = """
            Choose one —
            • Destroy target creature with no counters on it.
            • Remove up to three counters from target creature.
        """,
    ) parsesTo abilities(
        modal(
            mode(destroy(target("creature")), targets = listOf(slot("creature", and(creature, not(ObjectFilter.HasCounter(null, atLeast = 1)))))),
            mode(Step.Choose(Choice.Number(n(0), n(3)), bind = "removed", chooser = Players.You),
                doing(Effect.RemoveCounters(target("creature"), null, arcanum.api.Value.Bound("removed"))),
                targets = listOf(slot("creature", creature))),
        ),
    )

    @Test
    fun boarBaboon() = card(
        name = "Boar-Baboon",
        cost = "{2}{B}",
        type = "Creature — Boar Monkey",
        text = """
            At the beginning of combat on your turn, target creature you control with a +1/+1 counter on it gains menace until end of turn.
            Exhaust — {5}: Put two +1/+1 counters on this creature. (Activate each exhaust ability only once.)
        """,
        pt = "3/2",
    ) parsesTo abilities(
        triggered(beginningOfCombatOnYourTurn, gains(target("creature"), Keyword.Menace),
            targets = listOf(slot("creature", and(creature, you, ObjectFilter.HasCounter(p1p1, atLeast = 1))))),
        activated(cost(mana("{5}")), counters(Objects.Self, 2), label = AbilityLabel.Exhaust),
    )

    // CR 707.2 (a token copy of itself), 701.21a
    @Test
    fun smilingClerkOneOfMany() = card(
        name = "Smiling Clerk, One of Many",
        cost = "{1}{B}",
        type = "Creature — Human Advisor",
        text = "{B}, {T}: Surveil 1. Create a token that's a copy of this creature, then sacrifice an artifact or creature. Activate only as a sorcery. (To surveil 1, look at the top card of your library. You may put it into your graveyard.)",
        pt = "2/2",
    ) parsesTo abilities(
        activated(cost(mana("{B}"), tap), surveil(1), doing(Effect.CreateToken(TokenSpec.CopyOf(Objects.Self), n(1), Players.You)),
            Step.Choose(Choice.Objects(and(or(artifact, creature), you), n(1), n(1)), bind = "sacrificed", chooser = Players.You),
            doing(Effect.Sacrifice(Objects.Bound("sacrificed"))),
            rules = asSorcery),
    )

    @Test
    fun saskaBountyHunter() = card(
        name = "Saska, Bounty Hunter",
        cost = "{1}{B}",
        type = "Legendary Creature — Human Mercenary",
        text = """
            Saska can't be blocked as long as you've drawn two or more cards this turn.
            {1}, Sacrifice another creature: Create a Clue token. Activate only during your turn. (It's an artifact with "{2}, Sacrifice this token: Draw a card.")
        """,
        pt = "2/2",
    ) parsesTo abilities(
        static(Mechanic.CantBeBlocked, condition = drewTwo),
        activated(cost(mana("{1}"), sacrificeA(and(another, creature))), createClue, rules = duringYourTurn),
    )

    // CR 607.2a (every card exiled with it), 613.1f (the last chosen card's activated and triggered abilities)
    @Test
    fun vyrrTheMaskTaker() = card(
        name = "Vyrr, the Mask Taker",
        cost = "{4}{B}{B}",
        type = "Legendary Creature — Shapeshifter Spirit",
        text = """
            When Vyrr enters, exile up to one other target creature.
            Whenever another nontoken creature dies, you may exile it.
            Pay 1 life: Choose a creature card exiled with Vyrr.
            Vyrr has all activated and triggered abilities of the last chosen card.
        """,
        pt = "6/6",
    ) parsesTo abilities(
        triggered(entersSelf, move(target("creature"), Zone.Exile, postBind = "exiled"), doing(Effect.Remember("exiled", Objects.Bound("exiled"))),
            targets = listOf(slot("creature", and(another, creature), min = 0))),
        triggered(dies(and(another, not(token), creature)), Step.May(listOf(move(enteredOne, Zone.Exile, postBind = "exiled"),
            doing(Effect.Remember("exiled", Objects.Bound("exiled")))), player = Players.You)),
        activated(cost(CostPart.PayLife(n(1))), Step.Choose(Choice.Objects(and(creature, inZone(Zone.Exile)), n(1), n(1), from = Objects.Remembered("exiled")),
            bind = "chosen", chooser = Players.You, remember = true)),
        static(Mechanic.GainAbilitiesOf(Objects.Remembered("chosen"), triggered = true)),
    )

    @Test
    fun ilsaScornfulStriker() = card(
        name = "Ilsa, Scornful Striker",
        cost = "{1}{B}",
        type = "Legendary Creature — Human Noble Ally",
        text = """
            First strike
            Whenever a player casts a noncreature spell, they lose 2 life.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.FirstStrike,
        triggered(on(EventPattern.Cast(noncreature, PlayerFilter.Any)), loseLife(2, Players.EventPlayer)),
    )

    // CR 113.6c: an ability that works in the graveyard
    @Test
    fun peddlerOfManyMasks() = card(
        name = "Peddler of Many Masks",
        cost = "{1}{B}",
        type = "Creature — Human Peasant Ally",
        text = "{2}{B}: Return this card from your graveyard to your hand.",
        pt = "2/2",
    ) parsesTo abilities(
        Ability.Activated(cost(mana("{2}{B}")), Instructions(steps = listOf(move(Objects.Self, Zone.Hand))), anyTime, functionsIn = listOf(Zone.Graveyard)),
    )
}
