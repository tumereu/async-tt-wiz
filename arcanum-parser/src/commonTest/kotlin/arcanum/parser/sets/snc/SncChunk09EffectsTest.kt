package arcanum.parser.sets.snc

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** SNC chunk 09: copies of exiled cards, chosen keywords, X values of mana values and greatest numbers, phasing, counters on two kinds,
 * total mana value, lesser mana value after a sacrifice, attack and excess-damage triggers, a graveyard permission with an extra cost. */
class SncChunk09EffectsTest {
    private val planeswalker = ObjectFilter.Type(CardType.Planeswalker)
    private val shieldOnEntering = static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
        modify = listOf(Modification.EntersWithCounters(CounterKind.Shield, n(1)))))
    private val inYourGraveyard = and(inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))
    private val fiveManaValues = Condition.Compare(Value.CountDistinct(Objects.Each(inYourGraveyard),
        SharedQuality.Number(NumericProperty.ManaValue)), Comparison.AtLeast, n(5))
    private val untilNextTurn = Duration.UntilYourNextTurn

    // CR 607.2a (cards exiled with it), 707.12 (casting copies of cards), 603.2 (first such spell each turn)
    @Test
    fun arcaneBombardment() = card(
        name = "Spellstorm Archive",
        cost = "{4}{R}{R}",
        type = "Enchantment",
        text = """
            Whenever you cast your first instant or sorcery spell each turn, exile an instant or sorcery card at random from your graveyard. Then copy each card exiled with this enchantment. You may cast any number of the copies without paying their mana costs.
        """,
    ) parsesTo abilities(
        triggered(Trigger.On(listOf(EventPattern.Cast(and(or(instant, sorcery), spellFilter), PlayerFilter.You)), nthThisTurn = 1),
            doing(Effect.Move(Objects.Random(Objects.Each(and(or(instant, sorcery), inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))), n(1)),
                Destination(Zone.Exile), postBind = "exiled")),
            doing(Effect.Remember("exiled", Objects.Bound("exiled"))),
            doing(Effect.CopyCard(Objects.Remembered("exiled"), Players.You, bind = "copies")),
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(0), null, from = Objects.Bound("copies")), bind = "toCast", chooser = Players.You),
            doing(Effect.Cast(Objects.Bound("toCast"), PlayKind.Cast, withoutPaying = true, caster = Players.You))),
    )

    // CR 115.1 (up to one target), 608.2d (the choice as it resolves, one keyword for both)
    @Test
    fun ballroomBrawlers() = card(
        name = "Gala Bruisers",
        cost = "{3}{W}{W}",
        type = "Creature — Human Warrior",
        pt = "3/5",
        text = """
            Whenever this creature attacks, this creature and up to one other target creature you control both gain your choice of first strike or lifelink until end of turn.
        """,
    ) parsesTo abilities(
        triggered(attacksSelf,
            Step.Choose(Choice.Option(listOf("first strike", "lifelink")), bind = "keyword", chooser = Players.You),
            ifThen(Condition.Chose("keyword", "first strike"),
                gains(Objects.Union(listOf(Objects.Self, target("creature"))), Keyword.FirstStrike),
                otherwise = listOf(gains(Objects.Union(listOf(Objects.Self, target("creature"))), Keyword.Lifelink))),
            targets = listOf(slot("creature", and(another, creatureYouControl), min = 0))),
    )

    // CR 122.1 (shield counter), 115.3 ("other": not this creature)
    @Test
    fun wingshieldAgent() = card(
        name = "Skyguard Operative",
        cost = "{2}{U}",
        type = "Creature — Human Soldier",
        pt = "2/3",
        text = """
            This creature enters with a shield counter on it. (If it would be dealt damage or destroyed, remove a shield counter from it instead.)
            Whenever this creature attacks, up to one other target creature gains flying until end of turn.
        """,
    ) parsesTo abilities(
        shieldOnEntering,
        triggered(attacksSelf, gains(target("creature"), Keyword.Flying), targets = listOf(slot("creature", and(another, creature), min = 0))),
    )

    // CR 202.3 (mana values; a land's is 0), 611.2a (X fixed as it resolves), 701.9 (discard)
    @Test
    fun allSeeingArbiter() = card(
        name = "Omniscient Magistrate",
        cost = "{4}{U}{U}",
        type = "Creature — Avatar",
        pt = "5/4",
        text = """
            Flying
            Whenever this creature enters or attacks, draw two cards, then discard a card.
            Whenever you discard a card, target creature an opponent controls gets -X/-0 until your next turn, where X is the number of different mana values among cards in your graveyard.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersOrAttacksSelf, draw(2), discard(1)),
        triggered(on(EventPattern.Discard(ObjectFilter.Any, PlayerFilter.You)),
            doing(Effect.ApplyMechanics(listOf(Mechanic.ModifyPowerToughness(Value.Times(Value.CountDistinct(Objects.Each(and(cardFilter, inYourGraveyard)),
                SharedQuality.Number(NumericProperty.ManaValue)), -1), n(0))), Affected.Objects(target("creature")), untilNextTurn)),
            targets = listOf(slot("creature", creatureOpponentControls))),
    )

    // CR 611.2c (X fixed as it resolves), 208.1 (power and toughness)
    @Test
    fun freelanceMuscle() = card(
        name = "Hired Bruiser Rhino",
        cost = "{4}{G}",
        type = "Creature — Rhino Warrior",
        pt = "4/4",
        text = """
            Whenever this creature attacks or blocks, it gets +X/+X until end of turn, where X is the greatest power and/or toughness among other creatures you control.
        """,
    ) parsesTo abilities(
        triggered(Trigger.On(listOf(EventPattern.Attack(ObjectFilter.Is(Objects.Self)), EventPattern.Block(ObjectFilter.Is(Objects.Self), ObjectFilter.Any))),
            untilEndOfTurn(Objects.Self, Mechanic.ModifyPowerToughness(greatest, greatest))),
    )
    private val others = Objects.Each(and(another, creatureYouControl))
    private val greatest = Value.Max(listOf(Value.Of(NumericProperty.Power, others, Combine.Greatest), Value.Of(NumericProperty.Toughness, others, Combine.Greatest)))

    // CR 613.4c (each bonus its own effect), 611.3a (as long as)
    @Test
    fun jetmirNexusOfRevels() = card(
        name = "Velkar, Nexus of Revelry",
        cost = "{1}{R}{G}{W}",
        type = "Legendary Creature — Cat Demon",
        pt = "5/4",
        text = """
            Creatures you control get +1/+0 and have vigilance as long as you control three or more creatures.
            Creatures you control also get +1/+0 and have trample as long as you control six or more creatures.
            Creatures you control also get +1/+0 and have double strike as long as you control nine or more creatures.
        """,
    ) parsesTo abilities(
        static(pt(1, 0), has(Keyword.Vigilance), affects = Objects.Each(creatureYouControl), condition = controlAtLeast(3)),
        static(pt(1, 0), has(Keyword.Trample), affects = Objects.Each(creatureYouControl), condition = controlAtLeast(6)),
        static(pt(1, 0), has(Keyword.DoubleStrike), affects = Objects.Each(creatureYouControl), condition = controlAtLeast(9)),
    )
    private fun controlAtLeast(n: Int) = Condition.Exists(creatureYouControl, n)

    // CR 401.2 (each library's top card), 121.1
    @Test
    fun caseTheJoint() = card(
        name = "Scout the Vault",
        cost = "{3}{U}",
        type = "Instant",
        text = "Draw two cards, then look at the top card of each player's library.",
    ) parsesTo abilities(
        spell(draw(2), doing(Effect.LookAt(Objects.LibraryCards(Players.Each(PlayerFilter.Any), n(1), fromTop = true), Players.You))),
    )

    // CR 601.2f (an additional cost), 614.1a (exiled instead), 601.2a (cast from the graveyard), 505.1 (only during your turn)
    @Test
    fun maestrosAscendancy() = card(
        name = "Virtuoso Supremacy",
        cost = "{U}{B}{R}",
        type = "Enchantment",
        text = """
            Once during each of your turns, you may cast an instant or sorcery spell from your graveyard by sacrificing a creature in addition to paying its other costs. If a spell cast this way would be put into your graveyard, exile it instead.
        """,
    ) parsesTo abilities(
        static(Mechanic.MayPlay(Zone.Graveyard, PlayKind.Cast, perTurn = 1, holder = PlayerFilter.You, additionalCost = cost(sacrificeA(creature)),
            then = Destination(Zone.Exile), thenInsteadOf = Zone.Graveyard),
            affects = Objects.Each(and(or(instant, sorcery), inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))),
            condition = Condition.YourTurn),
    )

    // CR 702.26b (phasing out the target)
    @Test
    fun slipOutTheBack() = card(
        name = "Duck Out the Side",
        cost = "{U}",
        type = "Instant",
        text = "Put a +1/+1 counter on target creature. It phases out. (Treat it and anything attached to it as though they don't exist until its controller's next turn.)",
    ) parsesTo abilities(
        spell(counters(target("creature")), doing(Effect.PhaseOut(target("creature"))), targets = listOf(slot("creature", creature))),
    )

    // CR 122.1, 122.6 (counters on each)
    @Test
    fun brokersAscendancy() = card(
        name = "Dealers Supremacy",
        cost = "{G}{W}{U}",
        type = "Enchantment",
        text = "At the beginning of your end step, put a +1/+1 counter on each creature you control and a loyalty counter on each planeswalker you control.",
    ) parsesTo abilities(
        triggered(stepBegins(TurnStep.End, PlayerFilter.You),
            counters(Objects.Each(creatureYouControl)),
            counters(Objects.Each(and(planeswalker, you)), kind = CounterKind.Loyalty)),
    )

    // CR 122.1 (a counter of any kind), 118.3, 608.2d ("you may … If you do")
    @Test
    fun sanctuaryWarden() = card(
        name = "Haven Sentinel Angel",
        cost = "{4}{W}{W}",
        type = "Creature — Angel Soldier",
        pt = "5/5",
        text = """
            Flying
            This creature enters with two shield counters on it.
            Whenever this creature enters or attacks, you may remove a counter from a creature or planeswalker you control. If you do, draw a card and create a 1/1 green and white Citizen creature token.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.EntersWithCounters(CounterKind.Shield, n(2))))),
        triggered(entersOrAttacksSelf,
            Step.Pay(cost(CostPart.RemoveCounters(null, n(1), CostObjects.Choose(and(or(creature, planeswalker), you), count = n(1)))), mandatory = false,
                payer = Players.You, bind = "paid"),
            ifThen(Condition.Did("paid"), draw(1), create(creatureToken("Citizen", listOf(Color.Green, Color.White), 1, 1)))),
    )

    // CR 601.2c (total mana value of the targets), 115.1
    @Test
    fun patchUp() = card(
        name = "Stitch Together Quick",
        cost = "{2}{W}",
        type = "Sorcery",
        text = "Return up to three target creature cards with total mana value 3 or less from your graveyard to the battlefield.",
    ) parsesTo abilities(
        spell(move(target("cards"), Zone.Battlefield), targets = listOf(TargetSlot("cards", TargetKind.Objects(and(creature, inZone(Zone.Graveyard),
            ObjectFilter.OwnedBy(PlayerFilter.You))), min = n(0), max = n(3), totalAtMost = Choice.TotalLimit(NumericProperty.ManaValue, n(3))))),
    )

    // CR 603.10a (the sacrificed creature as it last existed), 603.2h ("Do this only once each turn")
    @Test
    fun riveteersAscendancy() = card(
        name = "Forgers Supremacy",
        cost = "{B}{R}{G}",
        type = "Enchantment",
        text = "Whenever you sacrifice a creature, you may return target creature card with lesser mana value from your graveyard to the battlefield tapped. Do this only once each turn.",
    ) parsesTo abilities(
        Ability.Triggered(on(EventPattern.Sacrifice(creatureYouControl)), Instructions(
            targets = listOf(slot("card", and(creature, ObjectFilter.ManaValue(Comparison.Less, Value.Of(NumericProperty.ManaValue,
                Objects.EventObject(EventRole.Before), Combine.Single)), inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)))),
            steps = listOf(Step.May(listOf(move(target("card"), Zone.Battlefield, tapped = true)), player = Players.You))), actionOncePerTurn = true),
    )

    // CR 202.3, 603.4 (intervening if), 119.4 (paying life)
    @Test
    fun sanguineSpy() = card(
        name = "Crimson Informant",
        cost = "{2}{B}",
        type = "Creature — Vampire Rogue",
        pt = "2/3",
        text = """
            Menace, lifelink
            {1}, Sacrifice another creature: Surveil 1. (Look at the top card of your library. You may put that card into your graveyard.)
            At the beginning of your end step, if there are five or more mana values among cards in your graveyard, you may pay 2 life. If you do, draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        Keyword.Lifelink,
        activated(cost(mana("{1}"), sacrificeA(and(another, creature))), doing(Effect.Surveil(Players.You, n(1)))),
        triggered(stepBegins(TurnStep.End, PlayerFilter.You),
            Step.Pay(cost(CostPart.PayLife(n(2))), mandatory = false, payer = Players.You, bind = "paid"),
            ifThen(Condition.Did("paid"), draw(1)),
            interveningIf = fiveManaValues),
    )

    // CR 502.3 (doesn't untap during its controller's next untap step), 608.2c
    @Test
    fun celestialRegulator() = card(
        name = "Astral Arbitrator",
        cost = "{1}{W}{U}",
        type = "Creature — Angel Advisor",
        pt = "2/3",
        text = """
            Flying
            When this creature enters, choose target creature you don't control and tap it. If you control a creature with a counter on it, the chosen creature doesn't untap during its controller's next untap step.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf,
            tapIt(target("creature")),
            ifThen(Condition.Exists(and(creature, ObjectFilter.HasCounter(null, 1), you), 1),
                untilStep(target("creature"))),
            targets = listOf(slot("creature", and(creature, ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You)))))),
    )
    private fun untilStep(objects: Objects) = doing(Effect.ApplyMechanics(listOf(Mechanic.DoesntUntap), Affected.Objects(objects),
        Duration.ThroughNextStep(TurnStep.Untap, Players.ControllerOf(objects))))

    // CR 508.1b (a player or planeswalker, not a battle), 603.2c (once for the declaration)
    @Test
    fun rigoStreetwiseMentor() = card(
        name = "Pello, Alley Mentor",
        cost = "{G/W}{W}{W/U}",
        type = "Legendary Creature — Cat Citizen",
        pt = "2/2",
        text = """
            Pello enters with a shield counter on it. (If it would be dealt damage or destroyed, remove a shield counter from it instead.)
            Whenever you attack a player or planeswalker with one or more creatures with power 1 or less, draw a card.
        """,
    ) parsesTo abilities(
        shieldOnEntering,
        triggered(Trigger.On(EventPattern.Attack(and(creature, power(Comparison.AtMost, 1), you), target = RecipientFilter(objects = planeswalker,
            players = PlayerFilter.Any)), oneOrMore = true), draw(1)),
    )

    // CR 120.4a (excess damage), 603.12 (reflexive trigger), 601.2c
    @Test
    fun unleashTheInferno() = card(
        name = "Unchain the Blaze",
        cost = "{1}{B}{R}{G}",
        type = "Instant",
        text = "This spell deals 7 damage to target creature or planeswalker. When it deals excess damage this way, destroy target artifact or enchantment an opponent controls with mana value less than or equal to that amount of excess damage.",
    ) parsesTo abilities(
        spell(doing(Effect.DealDamage(Recipients(objects = target("permanent")), n(7), Objects.Self, bind = "damage")),
            ifThen(Condition.Compare(Value.Excess("damage"), Comparison.Greater, n(0)), reflexive(destroy(target("permanent")),
                targets = listOf(slot("permanent", and(or(artifact, enchantment), opponents, ObjectFilter.ManaValue(Comparison.AtMost, Value.Excess("damage"))))))),
            targets = listOf(slot("permanent", or(creature, planeswalker)))),
    )
}
