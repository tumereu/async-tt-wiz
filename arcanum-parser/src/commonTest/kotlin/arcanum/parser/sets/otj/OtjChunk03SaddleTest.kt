package arcanum.parser.sets.otj

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.cube.whenThisEnters
import kotlin.test.Test

/*
 * OTJ chunk 03: Mounts with saddle (CR 702.171): "whenever this creature attacks while saddled" (CR 702.171b), "this creature is
 * saddled" / "isn't saddled" as a condition, and "creature(s) that saddled it this turn" (tapped to pay its saddle cost, CR 702.171c).
 */
class OtjChunk03SaddleTest {
    private fun flashback(cost: Cost) = untilEndOfTurn(target("card"), Mechanic.GainAbilities(listOf(Keyword.Flashback(cost))))

    // CR 702.34a, 702.171b: the flashback cost is chosen as the ability resolves (CR 608.2c)
    @Test
    fun scrollkeeperNewt() = card(
        name = "Scrollkeeper Newt",
        cost = "{1}{U}",
        type = "Creature — Salamander Mount",
        text = """
            Whenever this creature deals combat damage to a player, target instant or sorcery card in your graveyard gains flashback until end of turn. The flashback cost is equal to its mana cost. That card gains flashback {0} until end of turn instead if this creature is saddled. (You may cast that card from your graveyard for its flashback cost. Then exile it.)
            Saddle 3
        """,
        pt = "2/2",
    ) parsesTo abilities(
        triggered(combatDamageToAPlayer,
            ifThen(selfSaddled, flashback(manaCost("{0}")), otherwise = listOf(flashback(Cost(listOf(CostPart.ItsManaCost))))),
            targets = listOf(slot("card", inYourGraveyard(or(instant, sorcery))))),
        Keyword.Saddle(3),
    )

    // CR 608.2c: "those creatures" are the creatures the counters were put on
    @Test
    fun leapingDuskcat() = card(
        name = "Leaping Duskcat",
        cost = "{5}{W}",
        type = "Creature — Cat Beast Mount",
        text = """
            Whenever this creature attacks while saddled, put a +1/+1 counter on each other creature you control. You gain 1 life for each of those creatures.
            Saddle 2 (Tap any number of other creatures you control with total power 2 or more: This Mount becomes saddled until end of turn. Saddle only as a sorcery.)
        """,
        pt = "4/7",
    ) parsesTo abilities(
        attacksWhileSaddled(
            Step.Bind(Objects.Each(and(another, creature, you)), "those creatures"),
            counters(Objects.Bound("those creatures")),
            Step.Do(Effect.GainLife(Players.You, Value.Count(Objects.Bound("those creatures"))))),
        Keyword.Saddle(2),
    )

    // CR 702.171c, 707.2 (copiable values), 508.4 (enters attacking), 603.7 (delayed sacrifice); repeated once: two choices, two tokens
    @Test
    fun ruinThunderingStallion() = card(
        name = "Ruin, Thundering Stallion",
        cost = "{4}{R}{R}",
        type = "Legendary Creature — Horse Mount",
        text = """
            Haste
            Whenever Ruin attacks while saddled, choose a nonlegendary creature that saddled it this turn and create a tapped and attacking token that's a copy of it. Sacrifice that token at the beginning of the next end step. Repeat this process once.
            Saddle 1
        """,
        pt = "4/6",
    ) parsesTo abilities(
        Keyword.Haste,
        attacksWhileSaddled(Step.Repeat(n(2), listOf(
            Step.Choose(Choice.Objects(thatSaddledIt(and(not(legendary), creature)), n(1), n(1)), bind = "chosen", chooser = Players.You),
            Step.Do(Effect.CreateToken(TokenSpec.CopyOf(Objects.Bound("chosen")), n(1), Players.You, tapped = true, attacking = AttackTarget.Chosen,
                bind = "tokens")),
            delayed(on(EventPattern.StepBegins(TurnStep.End, PlayerFilter.Any)), Step.Do(Effect.Sacrifice(Objects.Bound("tokens"))))))),
        Keyword.Saddle(1),
    )

    // CR 702.171b: saddled or not, checked as it resolves; CR 608.2h: the revealed card's mana value
    @Test
    fun venomhideBronco() = card(
        name = "Venomhide Bronco",
        cost = "{1}{B}",
        type = "Creature — Snake Horse Mount",
        text = """
            Whenever this creature attacks, reveal the top card of your library and put it into your hand. You lose life equal to that card's mana value if this creature isn't saddled. Otherwise, each opponent loses that much life.
            Saddle 3 (Tap any number of other creatures you control with total power 3 or more: This Mount becomes saddled until end of turn. Saddle only as a sorcery.)
        """,
        pt = "2/2",
    ) parsesTo abilities(
        triggered(attacksSelf,
            Step.Do(Effect.Reveal(Objects.LibraryCards(Players.You, n(1), fromTop = true), bind = "revealed")),
            move(Objects.Bound("revealed"), Zone.Hand),
            ifThen(Condition.Not(selfSaddled),
                Step.Do(Effect.LoseLife(Players.You, Value.Of(NumericProperty.ManaValue, Objects.Bound("revealed"), Combine.Single))),
                otherwise = listOf(Step.Do(Effect.LoseLife(Players.Each(PlayerFilter.Opponent),
                    Value.Of(NumericProperty.ManaValue, Objects.Bound("revealed"), Combine.Single)))))),
        Keyword.Saddle(3),
    )

    // CR 603.7 (delayed trigger at end of combat), 702.171c, 400.7 (the cards return as new objects under their owners' control)
    @Test
    fun providenceFaithfulMare() = card(
        name = "Providence, Faithful Mare",
        cost = "{2}{W}",
        type = "Legendary Creature — Beast Mount",
        text = """
            When Providence enters, scry 2.
            Whenever Providence attacks while saddled, at end of combat, exile it and up to one creature that saddled it this turn, then return those cards to the battlefield under their owner's control.
            Saddle 1
        """,
        pt = "2/4",
    ) parsesTo abilities(
        whenThisEnters(scry(2)),
        attacksWhileSaddled(delayed(on(EventPattern.StepBegins(TurnStep.EndOfCombat, PlayerFilter.Any)),
            Step.Choose(Choice.Objects(thatSaddledIt(creature), n(0), n(1)), bind = "chosen", chooser = Players.You),
            move(Objects.Union(listOf(Objects.Self, Objects.Bound("chosen"))), Zone.Exile, postBind = "exiled"),
            move(Objects.Bound("exiled"), Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled"))))),
        Keyword.Saddle(1),
    )

    // CR 702.171c: only a creature that saddled it this turn is a legal target
    @Test
    fun riverdamBeaver() = card(
        name = "Riverdam Beaver",
        cost = "{3}{G}",
        type = "Creature — Beaver Mount",
        text = """
            Vigilance
            Whenever this creature attacks while saddled, put a +1/+1 counter on target creature that saddled it this turn.
            Saddle 3 (Tap any number of other creatures you control with total power 3 or more: This Mount becomes saddled until end of turn. Saddle only as a sorcery.)
        """,
        pt = "4/4",
    ) parsesTo abilities(
        Keyword.Vigilance,
        attacksWhileSaddled(counters(target("creature")), targets = listOf(slot("creature", thatSaddledIt(creature)))),
        Keyword.Saddle(3),
    )

    // CR 107.1 (any number, zero included), 702.171c
    @Test
    fun wanderingPossum() = card(
        name = "Wandering Possum",
        cost = "{2}{G}",
        type = "Creature — Possum Mount",
        text = """
            Whenever this creature attacks while saddled, it gets +1/+2 until end of turn. Then you may return any number of creatures that saddled it this turn to their owner's hand.
            Saddle 1 (Tap any number of other creatures you control with total power 1 or more: This Mount becomes saddled until end of turn. Saddle only as a sorcery.)
        """,
        pt = "3/3",
    ) parsesTo abilities(
        attacksWhileSaddled(
            pump(Objects.Self, 1, 2),
            Step.Choose(Choice.Objects(thatSaddledIt(creature), n(0), null), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Hand)),
        Keyword.Saddle(1),
    )

    // CR 702.171c; CR 608.2h: X is the sacrificed creature's power as it last existed
    @Test
    fun theBogmawRaveningMount() = card(
        name = "The Bogmaw, Ravening Mount",
        cost = "{3}{B}{G}",
        type = "Legendary Creature — Frog Horror Mount",
        text = """
            Trample, haste
            Whenever The Bogmaw deals combat damage to a player, you may sacrifice a creature that saddled it this turn. If you do, draw X cards, then put up to X land cards from your hand onto the battlefield tapped, where X is the sacrificed creature's power.
            Saddle 1
        """,
        pt = "6/5",
    ) parsesTo abilities(
        Keyword.Trample,
        Keyword.Haste,
        triggered(combatDamageToAPlayer,
            Step.Pay(cost(CostPart.Sacrifice(CostObjects.Choose(thatSaddledIt(creature), n(1)), preBind = "sacrificed")), payer = Players.You,
                bind = "paid", mandatory = false),
            ifThen(Condition.Did("paid"),
                Step.Do(Effect.Draw(Players.You, power(Objects.Bound("sacrificed")))),
                Step.Choose(Choice.Objects(and(land, inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)), n(0), power(Objects.Bound("sacrificed"))),
                    bind = "chosen", chooser = Players.You),
                move(Objects.Bound("chosen"), Zone.Battlefield, tapped = true))),
        Keyword.Saddle(1),
    )

    @Test
    fun harnessedRam() = card(
        name = "Harnessed Ram",
        cost = "{3}{W}",
        type = "Creature — Sheep Mount",
        text = """
            Vigilance
            Whenever this creature attacks while saddled, create a 1/1 white Sheep creature token.
            Saddle 2 (Tap any number of other creatures you control with total power 2 or more: This Mount becomes saddled until end of turn. Saddle only as a sorcery.)
        """,
        pt = "3/4",
    ) parsesTo abilities(
        Keyword.Vigilance,
        attacksWhileSaddled(create(creatureToken("Sheep", listOf(Color.White), 1, 1))),
        Keyword.Saddle(2),
    )

    // CR 608.2h: X counted as it resolves
    @Test
    fun herdflockGryff() = card(
        name = "Herdflock Gryff",
        cost = "{1}{G}{W}",
        type = "Creature — Hippogriff Mount",
        text = """
            Flying, lifelink
            Whenever this creature attacks while saddled, it gets +X/+X until end of turn, where X is the number of Mounts you control.
            Saddle 3 (Tap any number of other creatures you control with total power 3 or more: This Mount becomes saddled until end of turn. Saddle only as a sorcery.)
        """,
        pt = "1/4",
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Lifelink,
        attacksWhileSaddled(untilEndOfTurn(Objects.Self, Mechanic.ModifyPowerToughness(count(and(sub("Mount"), you)), count(and(sub("Mount"), you))))),
        Keyword.Saddle(3),
    )

    @Test
    fun trailherdGrizzly() = card(
        name = "Trailherd Grizzly",
        cost = "{2}{G}",
        type = "Creature — Bear Mount",
        text = """
            Whenever this creature attacks while saddled, creatures you control gain trample until end of turn.
            Saddle 1 (Tap any number of other creatures you control with total power 1 or more: This Mount becomes saddled until end of turn. Saddle only as a sorcery.)
        """,
        pt = "4/2",
    ) parsesTo abilities(
        attacksWhileSaddled(gains(Objects.Each(creatureYouControl), Keyword.Trample)),
        Keyword.Saddle(1),
    )

    // CR 400.7: the exiled card is a new object the permission names
    @Test
    fun dunescaleCourser() = card(
        name = "Dunescale Courser",
        cost = "{2}{R}",
        type = "Creature — Lizard Mount",
        text = """
            Whenever this creature attacks while saddled, exile the top card of your library. Until the end of your next turn, you may play that card.
            Saddle 1 (Tap any number of other creatures you control with total power 1 or more: This Mount becomes saddled until end of turn. Saddle only as a sorcery.)
        """,
        pt = "4/2",
    ) parsesTo abilities(
        attacksWhileSaddled(
            move(Objects.LibraryCards(Players.You, n(1), fromTop = true), Zone.Exile, postBind = "exiled"),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)),
                Affected.Objects(Objects.Bound("exiled")), Duration.UntilEndOfYourNextTurn))),
        Keyword.Saddle(1),
    )

    @Test
    fun spinebackedCharger() = card(
        name = "Spinebacked Charger",
        cost = "{3}{R}",
        type = "Creature — Porcupine Mount",
        text = """
            Whenever this creature attacks while saddled, it gets +1/+2 and gains menace until end of turn. (It can't be blocked except by two or more creatures.)
            Saddle 2 (Tap any number of other creatures you control with total power 2 or more: This Mount becomes saddled until end of turn. Saddle only as a sorcery.)
        """,
        pt = "4/3",
    ) parsesTo abilities(
        attacksWhileSaddled(pump(Objects.Self, 1, 2, has(Keyword.Menace))),
        Keyword.Saddle(2),
    )

    @Test
    fun haloedSteed() = card(
        name = "Haloed Steed",
        cost = "{G}{W}",
        type = "Creature — Unicorn Mount",
        text = """
            First strike, lifelink
            Whenever this creature attacks while saddled, create a 3/3 white Angel creature token with flying.
            Saddle 4 (Tap any number of other creatures you control with total power 4 or more: This Mount becomes saddled until end of turn. Saddle only as a sorcery.)
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.FirstStrike,
        Keyword.Lifelink,
        attacksWhileSaddled(create(creatureToken("Angel", listOf(Color.White), 3, 3, listOf(Keyword.Flying)))),
        Keyword.Saddle(4),
    )

    @Test
    fun drilledArynx() = card(
        name = "Drilled Arynx",
        cost = "{1}{W}",
        type = "Creature — Cat Beast Mount",
        text = """
            Whenever this creature attacks while saddled, it gains first strike until end of turn. Scry 1. (Look at the top card of your library. You may put that card on the bottom.)
            Saddle 2 (Tap any number of other creatures you control with total power 2 or more: This Mount becomes saddled until end of turn. Saddle only as a sorcery.)
        """,
        pt = "3/1",
    ) parsesTo abilities(
        attacksWhileSaddled(gains(Objects.Self, Keyword.FirstStrike), scry(1)),
        Keyword.Saddle(2),
    )
}
