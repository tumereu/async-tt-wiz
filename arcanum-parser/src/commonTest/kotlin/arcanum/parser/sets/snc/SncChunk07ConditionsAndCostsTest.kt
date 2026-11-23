package arcanum.parser.sets.snc

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** SNC chunk 07: conditions on mana spent, entered creatures, life and draws; cost reductions; lists of costs and effects; casting from exile and the top of a library. */
class SncChunk07ConditionsAndCostsTest {
    private val fiveManaValues = Condition.Compare(Value.CountDistinct(Objects.Each(and(inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))),
        SharedQuality.Number(NumericProperty.ManaValue)), Comparison.AtLeast, n(5))
    private val eachOpponent = Players.Each(PlayerFilter.Opponent)
    private fun eachOpponentSacrifices(filter: ObjectFilter) = Step.ForEach(Each.Players(eachOpponent), "opponent", listOf(
        Step.Choose(Choice.Objects(and(filter, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("opponent")))), n(1), n(1)),
            bind = "sacrificed", chooser = Players.Bound("opponent")),
        doing(Effect.Sacrifice(Objects.Bound("sacrificed")))), together = true)

    // CR 602.2b, 601.2h (the mana spent to activate this ability, generic paid with coloured mana counts its colours), 608.2c ("instead")
    @Test
    fun paragonOfModernity() = card(
        name = "Exemplar of Novelty",
        cost = "{4}",
        type = "Artifact Creature — Angel Warrior",
        pt = "2/2",
        text = """
            Flying
            {3}: This creature gets +1/+1 until end of turn. If exactly three colors of mana were spent to activate this ability, put a +1/+1 counter on it instead.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        activated(manaCost("{3}"),
            ifThen(Condition.Compare(Value.ManaSpent(distinctColors = true, of = Objects.ThisAbility), Comparison.Equal, n(3)),
                counters(Objects.Self), otherwise = listOf(pump(Objects.Self, 1, 1)))),
    )

    // CR 603.4 (not an intervening if: checked as it resolves), 700.4 (counts creatures, not events)
    @Test
    fun parkHeightsPegasus() = card(
        name = "Skyline Courser",
        cost = "{G}{W}",
        type = "Creature — Pegasus",
        pt = "2/1",
        text = """
            Flying, trample
            Whenever this creature deals combat damage to a player, draw a card if you had two or more creatures enter the battlefield under your control this turn.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Trample,
        triggered(combatDamageToAPlayer,
            ifThen(Condition.HappenedThisTurn(EventPattern.ZoneChange(and(creature, you), to = Zone.Battlefield), atLeast = 2, measure = Measure.Objects),
                draw(1))),
    )

    // CR 601.2f, 103.4 (starting life total), 107.1b (the reduction is never negative: only while life is below it)
    @Test
    fun shadowOfMortality() = card(
        name = "Specter of Frailty",
        cost = "{13}{B}{B}",
        type = "Creature — Avatar",
        pt = "7/7",
        text = "If your life total is less than your starting life total, this spell costs {X} less to cast, where X is the difference.",
    ) parsesTo abilities(
        static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{1}")!!,
            Value.Difference(Value.StartingLife(Players.You), Value.LifeOf(Players.You)))),
            condition = Condition.Compare(Value.LifeOf(Players.You), Comparison.Less, Value.StartingLife(Players.You))),
    )

    // CR 702.8a (flash works from the zone the card is cast from, so the ability isn't limited to the Stack), 202.3 (distinct mana values)
    @Test
    fun graveyardShift() = card(
        name = "Graveside Overtime",
        cost = "{4}{B}",
        type = "Sorcery",
        text = """
            This spell has flash as long as there are five or more mana values among cards in your graveyard.
            Return target creature card from your graveyard to the battlefield.
        """,
    ) parsesTo abilities(
        static(has(Keyword.Flash), condition = fiveManaValues),
        spell(move(target("card"), Zone.Battlefield), targets = listOf(slot("card", inYourGraveyard(creature)))),
    )

    // CR 202.3, 613.4c
    @Test
    fun syndicateInfiltrator() = card(
        name = "Cartel Mole",
        cost = "{2}{U}{B}",
        type = "Creature — Vampire Wizard",
        pt = "3/3",
        text = """
            Flying
            As long as there are five or more mana values among cards in your graveyard, this creature gets +2/+2.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        static(pt(2, 2), condition = fiveManaValues),
    )

    // CR 602.2b, 106.3 (mana from a Treasure: its source, as it last existed)
    @Test
    fun jetmirsFixer() = card(
        name = "Backroom Bruiser",
        cost = "{R}{G}",
        type = "Creature — Cat Warrior",
        pt = "2/2",
        text = "{R}{G}: This creature gets +1/+1 until end of turn. If mana from a Treasure was spent to activate this ability, put a +1/+1 counter on this creature instead.",
    ) parsesTo abilities(
        activated(manaCost("{R}{G}"),
            ifThen(Condition.Compare(Value.ManaSpent(of = Objects.ThisAbility, source = sub("Treasure")), Comparison.Greater, n(0)),
                counters(Objects.Self), otherwise = listOf(pump(Objects.Self, 1, 1)))),
    )

    // CR 115.1 (up to one target), 608.2c (the rest only if the exile moved a card)
    @Test
    fun corpseAppraiser() = card(
        name = "Cadaver Assessor",
        cost = "{U}{B}{R}",
        type = "Creature — Vampire Rogue",
        pt = "3/3",
        text = "When this creature enters, exile up to one target creature card from a graveyard. If a card is put into exile this way, look at the top three cards of your library, then put one of those cards into your hand and the rest into your graveyard.",
    ) parsesTo abilities(
        triggered(entersSelf,
            move(target("card"), Zone.Exile, postBind = "exiled"),
            ifThen(Condition.NonEmpty("exiled"),
                doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(3), fromTop = true), bind = "seen", players = Players.You)),
                Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = Objects.Bound("seen")), bind = "kept", chooser = Players.You),
                move(Objects.Bound("kept"), Zone.Hand),
                move(Objects.Except(Objects.Bound("seen"), Objects.Bound("kept")), Zone.Graveyard)),
            targets = listOf(slot("card", and(creature, inZone(Zone.Graveyard)), min = 0))),
    )

    // CR 202.3, 608.2c ("unless": the discard only while the condition doesn't hold)
    @Test
    fun taintedIndulgence() = card(
        name = "Sullied Excess",
        cost = "{U}{B}",
        type = "Instant",
        text = "Draw two cards. Then discard a card unless there are five or more mana values among cards in your graveyard.",
    ) parsesTo abilities(
        spell(draw(2), ifThen(Condition.Not(fiveManaValues), discard(1))),
    )

    // CR 611.2a (one card in all over the duration), 305.1 (a land is played)
    @Test
    fun unluckyWitness() = card(
        name = "Hapless Bystander",
        cost = "{R}",
        type = "Creature — Human Citizen",
        pt = "1/1",
        text = "When this creature dies, exile the top two cards of your library. Until your next end step, you may play one of those cards.",
    ) parsesTo abilities(
        triggered(diesSelf,
            move(Objects.LibraryCards(Players.You, n(2), fromTop = true), Zone.Exile, postBind = "exiled"),
            doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You, total = 1)),
                Affected.Objects(Objects.Bound("exiled")), Duration.UntilStep(TurnStep.End, PlayerFilter.You)))),
    )

    // CR 601.2f (an activation cost reduced while the condition holds), 509.1b
    @Test
    fun sewerCrocodile() = card(
        name = "Drainpipe Gator",
        cost = "{5}{U}",
        type = "Creature — Crocodile",
        pt = "4/6",
        text = "{3}{U}: This creature can't be blocked this turn. This ability costs {3} less to activate if there are five or more mana values among cards in your graveyard.",
    ) parsesTo abilities(
        activated(manaCost("{3}{U}"), untilEndOfTurn(Objects.Self, Mechanic.CantBeBlocked),
            rules = anyTime.copy(costChange = CostChange.Less(ManaCost.parse("{3}")!!, Value.If(fiveManaValues, n(1))))),
    )

    // CR 601.2b, 601.2f (one of three additional costs), 608.2c (each opponent does each in the order written), 701.21a
    @Test
    fun duskMangler() = card(
        name = "Twilight Ravager",
        cost = "{5}{B}{B}",
        type = "Creature — Horror",
        pt = "5/4",
        text = """
            As an additional cost to cast this spell, sacrifice a creature, discard a card, or pay 4 life.
            When this creature enters, each opponent sacrifices a creature of their choice, discards a card, and loses 4 life.
        """,
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = cost(CostPart.OneOf(listOf(cost(sacrificeA(creature)), cost(discardACard), cost(CostPart.PayLife(n(4)))))))),
        triggered(entersSelf, eachOpponentSacrifices(creature), discard(1, eachOpponent), loseLife(4, eachOpponent)),
    )

    // CR 601.2f, 121.2 (one opponent drew four: not all opponents together)
    @Test
    fun evenTheScore() = card(
        name = "Settle the Tab",
        cost = "{X}{U}{U}{U}",
        type = "Instant",
        text = """
            This spell costs {U}{U}{U} less to cast if an opponent has drawn four or more cards this turn.
            Draw X cards.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{U}{U}{U}")!!, n(1))),
            condition = Condition.PlayerExists(PlayerFilter.And(listOf(PlayerFilter.Opponent, PlayerFilter.Where(Condition.HappenedThisTurn(
                EventPattern.Draw(PlayerFilter.Is(Players.Tested)), atLeast = 4, measure = Measure.Events)))), atLeast = 1)),
        spell(doing(Effect.Draw(Players.You, Value.Announced(name = "X"))), announced = listOf("X")),
    )

    // CR 601.2f (removing a counter is an additional cost), 601.2a (only spells from the top card), 122.1
    @Test
    fun falcoSparaPactweaver() = card(
        name = "Corvo Venn, Dealbinder",
        cost = "{1}{G}{W}{U}",
        type = "Legendary Creature — Bird Demon",
        pt = "3/3",
        text = """
            Flying, trample
            Corvo Venn enters with a shield counter on it.
            You may look at the top card of your library any time.
            You may cast spells from the top of your library by removing a counter from a creature you control in addition to paying their other costs.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Trample,
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.EntersWithCounters(CounterKind.Shield, n(1))))),
        playerStatic(Mechanic.MayLookAt(Objects.LibraryCards(of = Players.You, count = n(1), fromTop = true))),
        static(Mechanic.MayPlay(Zone.Library, PlayKind.Cast, topOnly = true, holder = PlayerFilter.You, additionalCost = cost(
            CostPart.RemoveCounters(null, n(1), CostObjects.Choose(creatureYouControl, count = n(1))))),
            affects = Objects.Each(and(nonland, inZone(Zone.Library), ObjectFilter.OwnedBy(PlayerFilter.You)))),
    )

    // CR 105.2 (exactly three colors), 106.6 (restricted mana), 601.2a
    @Test
    fun meetingOfTheFive() = card(
        name = "Summit of the Families",
        cost = "{3}{W}{U}{B}{R}{G}",
        type = "Sorcery",
        text = "Exile the top ten cards of your library. You may cast spells with exactly three colors from among them this turn. Add {W}{W}{U}{U}{B}{B}{R}{R}{G}{G}. Spend this mana only to cast spells with exactly three colors.",
    ) parsesTo abilities(
        spell(
            move(Objects.LibraryCards(Players.You, n(10), fromTop = true), Zone.Exile, postBind = "exiled"),
            doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Cast, resultingSpell = ObjectFilter.ColorCount(3), holder = PlayerFilter.You)),
                Affected.Objects(Objects.Bound("exiled")), Duration.EndOfTurn)),
            doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{W}{W}{U}{U}{B}{B}{R}{R}{G}{G}")!!),
                restriction = and(ObjectFilter.Category(ObjectCategory.Spell), ObjectFilter.ColorCount(3))), players = Players.You))),
    )
}
