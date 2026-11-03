package arcanum.parser.sets.msh

import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Condition
import arcanum.api.CostChange
import arcanum.api.CounterKind
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.FaceDefinition
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.PrintedNumber
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.Supertype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.TokenSpec
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Marvel Super Heroes chunk 05: Plans (an enchantment type, CR 205.3h) and Sagas (CR 714). A Plan gets plan counters and, as the
 * nth one is put on it (the event that puts it, CR 122.6), is sacrificed for a big effect, often a reflexive trigger (CR 603.12).
 * Controlling another player's next turn (CR 723). Saga chapters with a free next spell (CR 611.2f), a "you may … If you don't"
 * choice, the turn's artifact spells costing less, a named legendary token (CR 111.4), a two-target control change with a total
 * mana value (CR 601.2c) and a fight with up to one other target.
 */
class MshChunk05PlansAndSagasTest {
    @Test
    fun politicalLandslide() = card(
        name = "Political Landslide",
        cost = "{W}",
        type = "Enchantment — Plan",
        text = """
            Whenever a creature you control enters, scry 1 and put a plan counter on this enchantment.
            When the fourth plan counter is put on this enchantment, sacrifice it, draw a card, and put a +1/+1 counter on each creature you control.
        """,
    ) parsesTo abilities(
        triggered(enters(creatureYouControl), scry(1), planCounter),
        triggered(nthPlanCounter(4), sacrificeIt, draw(1), counters(Objects.Each(creatureYouControl))),
    )

    // one trigger for the batch of creatures becoming tapped; a reflexive trigger after the sacrifice (CR 603.12)
    @Test
    fun reviseTheRecords() = card(
        name = "Revise the Records",
        cost = "{2}{U}",
        type = "Enchantment — Plan",
        text = """
            Whenever one or more creatures you control become tapped, draw a card, then discard a card and put a plan counter on this enchantment.
            When the fourth plan counter is put on this enchantment, sacrifice it. When you do, return up to two target instant and/or sorcery cards from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        triggered(arcanum.api.Trigger.On(EventPattern.Tap(creatureYouControl), oneOrMore = true), draw(1), discard(1), planCounter),
        triggered(nthPlanCounter(4), sacrificeIt, reflexive(move(target("cards"), Zone.Hand),
            targets = listOf(slot("cards", inYourGraveyard(or(instant, sorcery)), min = 0, max = 2)))),
    )

    // CR 723: controlling another player's turn
    @Test
    fun forgeAnInfinitePrism() = card(
        name = "Forge an Infinite Prism",
        cost = "{2}{B}",
        type = "Enchantment — Plan",
        text = """
            Whenever you draw your second card each turn, create a 2/1 black Villain creature token with menace and put a plan counter on this enchantment.
            When the seventh plan counter is put on this enchantment, sacrifice it. When you do, you control target opponent during their next turn. (You see all cards that player could see and make all decisions for them.)
        """,
    ) parsesTo abilities(
        triggered(secondCardEachTurn, create(villain2), planCounter),
        triggered(nthPlanCounter(7), sacrificeIt, reflexive(Step.Do(Effect.ControlNextTurn(Players.Target("opponent"), Players.You)),
            targets = listOf(targetOpponent))),
    )

    // CR 608.2g: casting spells from among the exiled cards while the ability resolves
    @Test
    fun tyrannyReignsEternal() = card(
        name = "Tyranny Reigns Eternal",
        cost = "{1}{B}",
        type = "Enchantment — Plan",
        text = """
            Whenever a Villain you control enters, each opponent loses 1 life and you gain 1 life. Put a plan counter on this enchantment.
            When the fifth plan counter is put on this enchantment, sacrifice it. When you do, target opponent exiles the top five cards of their library. You may cast up to two spells from among the exiled cards without paying their mana costs.
        """,
    ) parsesTo abilities(
        triggered(enters(and(villainFilter, you)), loseLife(1, Players.Each(PlayerFilter.Opponent)), gainLife(1), planCounter),
        triggered(nthPlanCounter(5), sacrificeIt, reflexive(
            move(Objects.LibraryCards(Players.Target("opponent"), n(5), fromTop = true), Zone.Exile, postBind = "exiled"),
            Step.Choose(Choice.Objects(nonland, n(0), n(2), from = Objects.Bound("exiled")), bind = "chosen", chooser = Players.You),
            Step.ForEach(arcanum.api.Each.Objects(Objects.Bound("chosen")), "card", listOf(
                Step.Do(Effect.Cast(Objects.Bound("card"), PlayKind.Cast, withoutPaying = true, caster = Players.You)))),
            targets = listOf(targetOpponent))),
    )

    // creature cards put into your graveyard from anywhere: one trigger for the batch
    @Test
    fun riseOfTheAutomatons() = card(
        name = "Rise of the Automatons",
        cost = "{3}{B}",
        type = "Enchantment — Plan",
        text = """
            Whenever one or more creature cards are put into your graveyard from anywhere, you draw a card, lose 1 life, and put a plan counter on this enchantment.
            When the third plan counter is put on this enchantment, sacrifice it and create three 2/2 colorless Robot Villain artifact creature tokens.
        """,
    ) parsesTo abilities(
        triggered(arcanum.api.Trigger.On(EventPattern.ZoneChange(and(creature, cardFilter, ObjectFilter.OwnedBy(PlayerFilter.You)), to = Zone.Graveyard), oneOrMore = true),
            draw(1), loseLife(1), planCounter),
        triggered(nthPlanCounter(3), sacrificeIt,
            create(creatureToken("Robot Villain", emptyList(), 2, 2, types = listOf(CardType.Artifact, CardType.Creature)), n(3))),
    )

    // CR 601.2d: damage divided among one or two targets as the reflexive trigger is put on the Stack
    @Test
    fun ruinToOurFoes() = card(
        name = "Ruin to Our Foes",
        cost = "{2}{R}",
        type = "Enchantment — Plan",
        text = """
            Whenever you cast a noncreature spell, create a tapped Treasure token and put a plan counter on this enchantment.
            When the fourth plan counter is put on this enchantment, sacrifice it. When you do, it deals 7 damage divided as you choose among one or two targets.
        """,
    ) parsesTo abilities(
        triggered(youCast(noncreatureSpell), createTreasure(tapped = true), planCounter),
        triggered(nthPlanCounter(4), sacrificeIt, reflexive(damage(Value.DividedShare, toAny),
            targets = listOf(anyTarget.copy(max = n(2), divided = n(7))))),
    )

    // CR 207.2c: landfall; CR 122.1b: an indestructible counter
    @Test
    fun seizeTheRealm() = card(
        name = "Seize the Realm",
        cost = "{1}{G}",
        type = "Enchantment — Plan",
        text = """
            Landfall — Whenever a land you control enters, put a +1/+1 counter on target creature you control and a plan counter on this enchantment.
            When the fourth plan counter is put on this enchantment, sacrifice it. When you do, put an indestructible counter on target creature you control.
        """,
    ) parsesTo abilities(
        triggered(landfall, counters(target("creature")), planCounter, targets = listOf(slot("creature", creatureYouControl)), abilityWord = "Landfall"),
        triggered(nthPlanCounter(4), sacrificeIt, reflexive(counters(target("creature"), 1, CounterKind.Keyword(Keyword.Indestructible.kind)),
            targets = listOf(slot("creature", creatureYouControl)))),
    )

    // "You may put … If you don't, draw a card": nothing chosen means not done
    @Test
    fun originOfTheGuardians() = card(
        name = "Origin of the Guardians",
        cost = "{1}{W}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I — Scry 2.
            II — You may put a Hero creature card with mana value 3 or less from your hand onto the battlefield. If you don't, draw a card.
            III — Put a +1/+1 counter on each creature you control.
        """,
    ) parsesTo abilities(
        chapter(1, scry(2)),
        chapter(2,
            Step.Choose(Choice.Objects(and(hero, creature, manaValue(arcanum.api.Comparison.AtMost, 3), inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)),
                n(0), n(1)), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Battlefield),
            Step.If(Condition.Not(Condition.NonEmpty("chosen")), then = listOf(draw(1)))),
        chapter(3, counters(Objects.Each(creatureYouControl))),
    )

    // CR 611.2f: the next such spell gets an alternative cost of {0}, "without paying its mana cost" (CR 118.9)
    @Test
    fun worldWarBrute() = card(
        name = "World War Brute",
        cost = "{3}{G}{G}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I — The next red or green creature spell you cast this turn can be cast without paying its mana cost.
            II — Put three +1/+1 counters on target creature you control.
            III — Choose target creature you control. Until end of turn, double its power and toughness and it gains trample.
        """,
    ) parsesTo abilities(
        chapter(1, Step.Do(Effect.ApplyMechanics(listOf(Mechanic.AlternativeCost(cost = manaCost("{0}"))),
            Affected.NextSpells(and(or(ObjectFilter.OfColor(arcanum.api.ColorRef.Literal(Color.Red)), ObjectFilter.OfColor(arcanum.api.ColorRef.Literal(Color.Green))),
                creature, spellFilter, you), 1), Duration.EndOfTurn))),
        chapter(2, counters(target("creature"), 3), targets = listOf(slot("creature", creatureYouControl))),
        chapter(3, untilEndOfTurn(target("creature"), Mechanic.ModifyPowerToughness(power(target("creature")), toughness(target("creature"))), has(Keyword.Trample)),
            targets = listOf(slot("creature", creatureYouControl))),
    )

    // "you may [do X]. If you do": drawing as the optional action; the turn's artifact spells cost less (CR 611.2c)
    @Test
    fun armorFeud() = card(
        name = "Armor Feud",
        cost = "{2}{U}{R}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I — You may draw a card for each artifact you control. If you do, each opponent draws a card.
            II — Artifact spells you cast this turn cost {1} less to cast.
            III — This Saga deals X damage to target opponent, where X is the greatest mana value among artifacts you control.
        """,
    ) parsesTo abilities(
        chapter(1, Step.Pay(steps = listOf(Step.Do(Effect.Draw(Players.You, count(and(artifact, you))))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(draw(1, Players.Each(PlayerFilter.Opponent))))),
        chapter(2, Step.Do(Effect.ApplyMechanics(listOf(Mechanic.CostChange(and(artifact, spellFilter, you), CostChange.Less(ManaCost.parse("{1}")!!, n(1)))),
            null, Duration.EndOfTurn))),
        chapter(3, damage(Value.Of(NumericProperty.ManaValue, Objects.Each(and(artifact, you)), Combine.Greatest), Recipients(players = Players.Target("opponent"))),
            targets = listOf(targetOpponent)),
    )

    @Test
    fun guardiansUnderSiege() = card(
        name = "Guardians: Under Siege",
        cost = "{2}{B}{R}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I — Create two 2/1 black Villain creature tokens with menace.
            II — This Saga deals 2 damage to each non-Villain creature and each opponent.
            III — Create a Treasure token for each Villain you control.
        """,
    ) parsesTo abilities(
        chapter(1, create(villain2, n(2))),
        chapter(2, damage(2, Recipients(objects = Objects.Each(and(not(villainFilter), creature)), players = Players.Each(PlayerFilter.Opponent)))),
        chapter(3, createTreasure(count(and(villainFilter, you)))),
    )

    // CR 714.2c: "II, III —" is one chapter ability for each; CR 111.4: a named legendary token with a quoted trigger
    @Test
    fun theComingOfTheDevourer() = card(
        name = "The Coming of the Devourer",
        cost = "{2}{B}{B}{G}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after IV.)
            I — Destroy up to one target nonland permanent.
            II, III — Each opponent loses 2 life.
            IV — Create Worldeater, a legendary 16/16 black Elder Alien creature token with flying, trample, and "Whenever Worldeater attacks, destroy target land."
        """,
    ) parsesTo abilities(
        chapter(1, destroy(target("permanent")), targets = listOf(slot("permanent", and(nonland, permanentFilter), min = 0))),
        chapters(listOf(2, 3), loseLife(2, Players.Each(PlayerFilter.Opponent))),
        chapter(4, create(TokenSpec.Defined(FaceDefinition("Worldeater", colorIndicator = listOf(Color.Black), supertypes = listOf(Supertype.Legendary),
            types = listOf(CardType.Creature), subtypes = listOf(Subtype("Elder"), Subtype("Alien")), power = PrintedNumber.Fixed(16), toughness = PrintedNumber.Fixed(16),
            abilities = listOf(Keyword.Flying, Keyword.Trample, triggered(attacksSelf, destroy(target("land")), targets = listOf(slot("land", land)))))))),
    )

    // CR 601.2c: the targets' total mana value is checked as they are chosen; control for as long as the Saga stays (CR 611.2b)
    @Test
    fun theGreatHeroSchism() = card(
        name = "The Great Hero Schism",
        cost = "{3}{R}{W}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I — Gain control of up to two target creatures with total mana value 6 or less for as long as this Saga remains on the battlefield.
            II — Creatures you control get +1/+1 and gain vigilance until end of turn.
            III — Target creature you control fights up to one other target creature.
        """,
    ) parsesTo abilities(
        chapter(1, Step.Do(Effect.ApplyMechanics(listOf(Mechanic.ControlledBy(Players.You)), Affected.Objects(target("creature")), Duration.UntilLeaves(Objects.Self))),
            targets = listOf(TargetSlot("creature", TargetKind.Objects(creature), min = n(0), max = n(2),
                totalAtMost = Choice.TotalLimit(NumericProperty.ManaValue, n(6))))),
        chapter(2, pump(Objects.Each(creatureYouControl), 1, 1, has(Keyword.Vigilance))),
        chapter(3, Step.Do(Effect.Fight(target("creature"), target("second creature"))),
            targets = listOf(slot("creature", creatureYouControl), slot("second creature", creature, min = 0).copy(distinctFrom = listOf("creature")))),
    )
}
