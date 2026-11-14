package arcanum.parser.sets.otj

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * OTJ chunk 05: what was cast and how ("if you haven't cast a spell from your hand this turn", a cost to plot, a cost of other players'
 * spells, a cast during a resolution).
 */
class OtjChunk05CastTest {
    private val yourEndStep = stepBegins(TurnStep.End, PlayerFilter.You)

    /** CR 601.2a: a spell cast from its caster's hand, counted for the whole turn. */
    private val noSpellFromHand = Condition.Not(Condition.HappenedThisTurn(EventPattern.Cast(ObjectFilter.Any, PlayerFilter.You, from = Zone.Hand),
        atLeast = 1, measure = Measure.Events))

    // CR 603.4, 601.2a
    @Test
    fun gorgeCrab() = card(
        name = "Gorge Crab",
        cost = "{1}{U}",
        type = "Creature — Crab",
        pt = "0/5",
        text = """
            {1}{U}: This creature gets +2/-2 until end of turn.
            At the beginning of your end step, if you haven't cast a spell from your hand this turn, draw a card, then discard a card.
        """,
    ) parsesTo abilities(
        activated(cost(mana("{1}{U}")), pump(Objects.Self, 2, -2)),
        triggered(yourEndStep, draw(1), discard(1), interveningIf = noSpellFromHand),
    )

    // CR 603.4, 601.2a
    @Test
    fun tessSkyriderCloudScout() = card(
        name = "Tess Skyrider, Cloud Scout",
        cost = "{2}{W}{U}",
        type = "Legendary Creature — Human Scout",
        pt = "3/3",
        text = """
            Flying, vigilance
            At the beginning of your end step, if you haven't cast a spell from your hand this turn, draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Vigilance,
        triggered(yourEndStep, draw(1), interveningIf = noSpellFromHand),
    )

    // CR 603.4, 601.2a, 614.1a
    @Test
    fun meadowMarmot() = card(
        name = "Meadow Marmot",
        cost = "{1}{W}",
        type = "Creature — Squirrel",
        pt = "2/2",
        text = """
            Lifelink
            At the beginning of your end step, if you haven't cast a spell from your hand this turn, put a +1/+1 counter on this creature.
            {4}{W}: Until end of turn, if you would put one or more +1/+1 counters on a creature you control, put that many plus one +1/+1 counters on it instead.
        """,
    ) parsesTo abilities(
        Keyword.Lifelink,
        triggered(yourEndStep, counters(Objects.Self), interveningIf = noSpellFromHand),
        activated(cost(mana("{4}{W}")), doing(Effect.ApplyMechanics(listOf(Mechanic.Replacement(
            EventPattern.CountersPut(creatureYouControl, p1p1, causedBy = you), modify = listOf(Modification.AddAmount(n(1))))), null, Duration.EndOfTurn))),
    )

    // CR 603.4, 601.2a, 122.1b
    @Test
    fun cleverWingwright() = card(
        name = "Clever Wingwright",
        cost = "{2}{W}",
        type = "Creature — Dwarf Artificer",
        pt = "2/4",
        text = "At the beginning of your end step, if you haven't cast a spell from your hand this turn and this creature doesn't have a flying counter on it, put a flying counter on it.",
    ) parsesTo abilities(
        triggered(yourEndStep, counters(Objects.Self, kind = CounterKind.Keyword(Keyword.Flying)),
            interveningIf = Condition.And(listOf(noSpellFromHand,
                Condition.Not(Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(CounterKind.Keyword(Keyword.Flying), 1)))))),
    )

    // CR 603.4, 601.2a
    @Test
    fun herderOfTheLost() = card(
        name = "Herder of the Lost",
        cost = "{3}{W}{U}",
        type = "Creature — Human Soldier",
        pt = "1/4",
        text = """
            Flash
            At the beginning of your end step, if you haven't cast a spell from your hand this turn, create a 2/2 white Spirit creature token with flying.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(yourEndStep, create(creatureToken("Spirit", listOf(Color.White), 2, 2, abilities = listOf(Keyword.Flying))), interveningIf = noSpellFromHand),
    )

    // CR 601.2f, 118.7, 702.170a: plotting is the special action of plot
    @Test
    fun docBrambleWilyHealer() = card(
        name = "Doc Bramble, Wily Healer",
        cost = "{G}{U}",
        type = "Legendary Creature — Bear Druid",
        pt = "2/3",
        text = """
            Spells you cast from your graveyard or from exile cost {2} less to cast.
            Plotting cards from your hand costs {2} less.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(and(spellFilter, you, or(ObjectFilter.WasCast(Zone.Graveyard), ObjectFilter.WasCast(Zone.Exile))),
            CostChange.Less(ManaCost.parse("{2}")!!, n(1)))),
        static(Mechanic.CostChange(and(ObjectFilter.AbilityOf(source = ObjectFilter.InZone(Zone.Hand), keyword = Keyword.Plot.Kind), you),
            CostChange.Less(ManaCost.parse("{2}")!!, n(1)))),
    )

    // CR 101.2, 601.2a: each player by their own casts
    @Test
    fun sundownStandoff() = card(
        name = "Sundown Standoff",
        cost = "{1}{W}",
        type = "Enchantment",
        text = """
            Each player can't cast more than one spell each turn.
            {4}{R}, Sacrifice this enchantment: It deals 5 damage to any target.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.CantCast(ObjectFilter.Any)), affects = Affected.Players(Players.Each(PlayerFilter.Any)),
            condition = Condition.HappenedThisTurn(EventPattern.Cast(ObjectFilter.Any, PlayerFilter.Is(Players.Affected)), atLeast = 1, measure = Measure.Events)),
        activated(cost(mana("{4}{R}"), sacrificeSelf), damage(5, toAny), targets = listOf(anyTarget)),
    )

    // CR 601.2f, 700.13
    @Test
    fun snatchTheLedger() = card(
        name = "Snatch the Ledger",
        cost = "{2}{U}",
        type = "Sorcery",
        text = """
            This spell costs {1} less to cast if you've committed a crime this turn. (Targeting opponents, anything they control, and/or cards in their graveyards is a crime.)
            Draw two cards.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{1}")!!, n(1))),
            condition = Condition.HappenedThisTurn(EventPattern.CommitCrime(PlayerFilter.You), atLeast = 1, measure = Measure.Events)),
        spell(draw(2)),
    )

    // CR 601.2f, 119.4, 608.2h: the entering creature's power as it last existed
    @Test
    fun dreadOfTheCrags() = card(
        name = "Dread of the Crags",
        cost = "{3}{R}{R}",
        type = "Creature — Dragon",
        pt = "5/4",
        text = """
            Flying
            Spells your opponents cast that target this creature cost an additional 3 life to cast.
            Whenever another creature you control enters, this creature deals damage equal to that creature's power to any target.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        static(Mechanic.AdditionalCostOf(and(opponents, ObjectFilter.Targets(objects = Objects.Self)), Cost(listOf(CostPart.PayLife(n(3)))))),
        triggered(enters(and(another, creature, you)),
            damage(Value.Of(NumericProperty.Power, Objects.EventObject(EventRole.After), Combine.Single), toAny), targets = listOf(anyTarget)),
    )

    // CR 608.2g, 601.2h: cast while the ability resolves
    @Test
    fun twigbonesTheFilcher() = card(
        name = "Twigbones, the Filcher",
        cost = "{B}",
        type = "Legendary Creature — Skeleton Rogue",
        pt = "1/1",
        text = """
            Deathtouch
            Whenever Twigbones deals combat damage to a player, you may cast target nonland permanent card from that player's graveyard, and mana of any type can be spent to cast that spell.
        """,
    ) parsesTo abilities(
        Keyword.Deathtouch,
        triggered(combatDamageToAPlayer,
            Step.May(listOf(doing(Effect.Cast(target("card"), PlayKind.Cast, caster = Players.You, manaAsAnyType = true))), Players.You),
            targets = listOf(slot("card", and(nonland, permanentCard, inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.EventPlayer)))))),
    )

    // CR 601.2a, 608.2g, 608.2h: the triggering spell's mana value; "if you don't" is whether a spell was cast
    @Test
    fun kipTheDrifter() = card(
        name = "Kip, the Drifter",
        cost = "{G}{W}{U}",
        type = "Legendary Creature — Human Faerie Rogue",
        pt = "3/3",
        text = """
            Flying, lifelink
            Whenever you cast a spell from anywhere other than your hand, you may cast a permanent spell with equal or lesser mana value from your hand without paying its mana cost. If you don't, you may put a land card from your hand onto the battlefield.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Lifelink,
        triggered(youCast(not(ObjectFilter.WasCast(Zone.Hand))),
            Step.Choose(Choice.Objects(and(permanentCard, nonland,
                ObjectFilter.ManaValue(Comparison.AtMost, Value.Of(NumericProperty.ManaValue, Objects.EventObject(EventRole.Object), Combine.Single)),
                inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)), n(0), n(1)), bind = "card", chooser = Players.You),
            doing(Effect.Cast(Objects.Bound("card"), PlayKind.Cast, withoutPaying = true, bind = "spell", caster = Players.You)),
            ifThen(Condition.Not(Condition.NonEmpty("spell")),
                Step.Choose(Choice.Objects(and(land, inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)), n(0), n(1)), bind = "chosen", chooser = Players.You),
                move(Objects.Bound("chosen"), Zone.Battlefield))),
    )
}
