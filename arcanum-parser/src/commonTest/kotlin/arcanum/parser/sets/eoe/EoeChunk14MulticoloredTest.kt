package arcanum.parser.sets.eoe

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Edge of Eternities chunk 14: multicolored cards: a *’goyf counting exiled cards, mana spent as counters, counters removed from two creatures, base P/T by lands, odd or even destruction and control, Foods with an ability, affinity given to spells, half a library, dies-or-exiled triggers by power.
 */
class EoeChunk14MulticoloredTest {

    @Test
    fun velunirSlumberingNovice() = card(
        name = "Velunir, Slumbering Novice",
        cost = "{1}{U}{B}",
        type = "Legendary Creature — Human Cleric",
        pt = "2/3",
        text = """
            When Velunir enters, draw two cards. Then discard two cards unless you discard an artifact card.
            During your turn, Velunir has deathtouch.
        """,
    ) parsesTo abilities(
        triggered(
            entersSelf,
            doing(Effect.Draw(players = Players.You, count = n(2))),
            Step.Pay(
                cost = cost(CostPart.Discard(what = CostObjects.Choose(filter = artifact, count = n(1)))),
                payer = Players.You,
                bind = "paid",
            ),
            Step.If(
                condition = Condition.Not(condition = Condition.Did(name = "paid")),
                then = listOf(doing(Effect.Discard(players = Players.You, count = n(2), filter = ObjectFilter.Any))),
            ),
        ),
        static(has(Keyword.Deathtouch), condition = Condition.YourTurn),
    )

    @Test
    fun starwastegoyf() = card(
        name = "Starwastegoyf",
        cost = "{B}{G}",
        type = "Creature — Elemental Lhurgoyf",
        pt = "*/1+*",
        text = "Starwastegoyf's power is equal to the number of cards you own in exile and its toughness is equal to that number plus 1.",
    ) parsesTo abilities(
        static(
            Mechanic.SetPowerToughness(
                power = Value.Count(objects = Objects.Each(filter = and(cardFilter, ownedByYou, inZone(Zone.Exile)))),
                toughness = Value.Sum(
                    parts = listOf(Value.Count(objects = Objects.Each(filter = and(cardFilter, ownedByYou, inZone(Zone.Exile)))), n(1)),
                ),
            ),
            characteristicDefining = true,
        ),
    )

    @Test
    fun twinnoraFusionComposite() = card(
        name = "Twinnora, Fusion Composite",
        cost = "{X}{G}{W}",
        type = "Legendary Artifact Creature — Construct",
        pt = "0/1",
        text = """
            Trample
            Twinnora enters with a number of +1/+1 counters on it equal to the amount of mana spent to cast it.
            Whenever you attack, you may remove a +1/+1 counter from each of two creatures you control. If you do, draw a card and create a 2/2 colorless Robot artifact creature token.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        static(
            Mechanic.Replacement(
                event = EventPattern.ZoneChange(objects = isSelf, to = Zone.Battlefield),
                modify = listOf(Modification.EntersWithCounters(kind = p1p1, amount = Value.ManaSpent(of = Objects.Self))),
            ),
        ),
        triggered(
            Trigger.On(events = listOf(EventPattern.Attack(attacker = you)), oneOrMore = true),
            Step.Pay(
                cost = cost(
                    CostPart.RemoveCounters(
                        kind = p1p1,
                        amount = n(1),
                        from = CostObjects.Choose(
                            filter = and(creature, you, ObjectFilter.HasCounter(kind = p1p1, atLeast = 1)),
                            count = n(2),
                        ),
                    ),
                ),
                payer = Players.You,
                bind = "paid",
            ),
            Step.If(
                condition = Condition.Did(name = "paid"),
                then = listOf(
                    doing(Effect.Draw(players = Players.You, count = n(1))),
                    doing(Effect.CreateToken(token = robot, count = n(1), controller = Players.You)),
                ),
            ),
        ),
    )

    @Test
    fun geneshiftChrysalid() = card(
        name = "Geneshift Chrysalid",
        cost = "{G}{U}",
        type = "Creature — Insect Druid",
        pt = "1/3",
        text = """
            Flying
            Landfall — Whenever a land you control enters, target creature has base power and toughness 3/3 until end of turn. If you control six or more lands, that creature has base power and toughness 6/6 until end of turn instead.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(
            on(EventPattern.ZoneChange(objects = and(land, you), to = Zone.Battlefield)),
            Step.If(
                condition = Condition.Exists(filter = and(land, you), atLeast = 6),
                then = listOf(untilEndOfTurn(target("creature"), Mechanic.SetPowerToughness(power = n(6), toughness = n(6)))),
                otherwise = listOf(untilEndOfTurn(target("creature"), Mechanic.SetPowerToughness(power = n(3), toughness = n(3)))),
            ),
            targets = listOf(slot("creature", creature)),
            abilityWord = "Landfall",
        ),
    )

    @Test
    fun orlenaRisingRecruit() = card(
        name = "Orlena, Rising Recruit",
        cost = "{2}{G}{W}{W}",
        type = "Legendary Creature — Human Soldier",
        pt = "3/3",
        text = """
            Whenever Orlena enters or attacks, put a +1/+1 counter on target creature you control.
            Whenever one or more creatures you control with +1/+1 counters on them deal combat damage to a player, draw a card.
        """,
    ) parsesTo abilities(
        triggered(
            Trigger.On(
                events = listOf(EventPattern.ZoneChange(objects = isSelf, to = Zone.Battlefield), EventPattern.Attack(attacker = isSelf)),
            ),
            doing(Effect.PutCounters(objects = target("creature"), kind = p1p1, amount = n(1))),
            targets = listOf(slot("creature", and(creature, you))),
        ),
        triggered(
            Trigger.On(
                events = listOf(
                    EventPattern.Damage(
                        source = and(creature, you, ObjectFilter.HasCounter(kind = p1p1, atLeast = 1)),
                        to = RecipientFilter(players = PlayerFilter.Any),
                        combat = true,
                    ),
                ),
                oneOrMore = true,
                batchPerPlayer = true,
            ),
            doing(Effect.Draw(players = Players.You, count = n(1))),
        ),
    )

    @Test
    fun quellanMaeltharExile() = card(
        name = "Qu'ellan, Maelthar Exile",
        cost = "{1}{U}{R}",
        type = "Legendary Creature — Jellyfish Advisor",
        pt = "1/3",
        text = """
            Flying
            Whenever an artifact you control enters, put a +1/+1 counter on target creature.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(
            on(EventPattern.ZoneChange(objects = and(artifact, you), to = Zone.Battlefield)),
            doing(Effect.PutCounters(objects = target("creature"), kind = p1p1, amount = n(1))),
            targets = listOf(slot("creature", creature)),
        ),
    )

    @Test
    fun rebelliousSlaughter() = card(
        name = "Rebellious Slaughter",
        cost = "{3}{B}{B}{R}{R}",
        type = "Sorcery",
        text = "Choose odd or even. Destroy each creature with mana value of the chosen quality. Then gain control of all creatures until end of turn. Untap them. They gain haste until end of turn. (Zero is even.)",
    ) parsesTo abilities(
        spell(
            Step.Choose(choice = Choice.Option(options = listOf("odd", "even")), bind = "quality", chooser = Players.You),
            doing(
                Effect.Destroy(
                    objects = Objects.Each(
                        filter = and(
                            creature,
                            ObjectFilter.Where(
                                condition = Condition.Or(
                                    any = listOf(
                                        Condition.And(
                                            all = listOf(
                                                Condition.Chose(name = "quality", option = "odd"),
                                                Condition.Odd(
                                                    value = Value.Of(
                                                        property = NumericProperty.ManaValue,
                                                        objects = Objects.Tested,
                                                        how = Combine.Single,
                                                    ),
                                                ),
                                            ),
                                        ),
                                        Condition.And(
                                            all = listOf(
                                                Condition.Chose(name = "quality", option = "even"),
                                                Condition.Not(
                                                    condition = Condition.Odd(
                                                        value = Value.Of(
                                                            property = NumericProperty.ManaValue,
                                                            objects = Objects.Tested,
                                                            how = Combine.Single,
                                                        ),
                                                    ),
                                                ),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            Step.Bind(objects = Objects.Each(filter = creature), bind = "gained"),
            untilEndOfTurn(bound("gained"), Mechanic.ControlledBy(players = Players.You)),
            doing(Effect.Untap(objects = bound("gained"))),
            untilEndOfTurn(bound("gained"), has(Keyword.Haste)),
        ),
    )

    @Test
    fun brisquelNimbleGalleychef() = card(
        name = "Brisquel, Nimble Galleychef",
        cost = "{R}{W}",
        type = "Legendary Creature — Lobster Citizen",
        pt = "2/2",
        text = """
            Artifacts you control are Foods in addition to their other types and have "{2}, {T}, Sacrifice this artifact: You gain 3 life."
            {1}, {T}, Sacrifice a Food: Brisquel deals 3 damage to each opponent.
            At the beginning of each end step, if you gained life this turn, untap Brisquel.
        """,
    ) parsesTo abilities(
        static(
            Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(subtype = Subtype("Food")))),
            has(
                activated(
                    cost(mana("{2}"), tap, CostPart.Sacrifice(what = CostObjects.Self)),
                    doing(Effect.GainLife(players = Players.You, amount = n(3))),
                ),
            ),
            affects = Objects.Each(filter = and(artifact, you)),
        ),
        activated(
            cost(mana("{1}"), tap, CostPart.Sacrifice(what = CostObjects.Choose(filter = sub("Food"), count = n(1)))),
            doing(
                Effect.DealDamage(
                    to = Recipients(players = Players.Each(filter = PlayerFilter.Opponent)),
                    amount = n(3),
                    source = Objects.Self,
                ),
            ),
        ),
        triggered(
            on(EventPattern.StepBegins(step = TurnStep.End, whose = PlayerFilter.Any)),
            doing(Effect.Untap(objects = Objects.Self)),
            interveningIf = Condition.HappenedThisTurn(
                event = EventPattern.GainLife(players = PlayerFilter.You),
                atLeast = 1,
                measure = Measure.Events,
            ),
        ),
    )

    @Test
    fun orloVesselsMechanic() = card(
        name = "Orlo, Vessel's Mechanic",
        cost = "{2}{R}{W}",
        type = "Legendary Creature — Human Artificer",
        pt = "2/4",
        text = "At the beginning of your end step, if you control two or more tapped creatures, create a tapped 2/2 colorless Robot artifact creature token.",
    ) parsesTo abilities(
        triggered(
            on(EventPattern.StepBegins(step = TurnStep.End, whose = PlayerFilter.You)),
            doing(Effect.CreateToken(token = robot, count = n(1), controller = Players.You, tapped = true)),
            interveningIf = Condition.Exists(filter = and(tapped, creature, you), atLeast = 2),
        ),
    )

    @Test
    fun orloFreewheelingSkipper() = card(
        name = "Orlo, Freewheeling Skipper",
        cost = "{4}{R}{W}",
        type = "Legendary Creature — Human Artificer Rogue",
        pt = "4/4",
        text = """
            Double strike, vigilance
            Spells you cast have affinity for artifacts. (They cost {1} less to cast for each artifact you control.)
        """,
    ) parsesTo abilities(
        Keyword.DoubleStrike,
        Keyword.Vigilance,
        static(has(Keyword.Affinity(forWhat = artifact, printed = "artifacts")), affects = Objects.Each(filter = and(spellFilter, you))),
    )

    @Test
    fun sowingArkNursemaid() = card(
        name = "Sowing-Ark Nursemaid",
        cost = "{B}{G}",
        type = "Creature — Insect Citizen",
        pt = "2/3",
        text = """
            When this creature enters, mill three cards. (Put the top three cards of your library into your graveyard.)
            {3}{B}{G}, Sacrifice this creature: Return target creature or Spacecraft card from your graveyard to the battlefield. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, doing(Effect.Mill(players = Players.You, count = n(3)))),
        activated(
            cost(mana("{3}{B}{G}"), CostPart.Sacrifice(what = CostObjects.Self)),
            doing(Effect.Move(objects = target("card"), to = Destination(zone = Zone.Battlefield))),
            targets = listOf(slot("card", and(or(creature, sub("Spacecraft")), inZone(Zone.Graveyard), ownedByYou))),
            rules = asSorcery,
        ),
    )

    @Test
    fun collapseOfTheHorizon() = card(
        name = "Collapse of the Horizon",
        cost = "{3}{U}{B}{B}",
        type = "Sorcery",
        text = "Destroy all creatures, then any number of target players each mill half their library, rounded down.",
    ) parsesTo abilities(
        spell(
            doing(Effect.Destroy(objects = Objects.Each(filter = creature))),
            Step.ForEach(
                each = Each.Players(players = Players.Target(slot = "players")),
                bind = "player",
                steps = listOf(
                    doing(
                        Effect.Mill(
                            players = Players.Bound(name = "player"),
                            count = Value.Half(
                                value = Value.Count(
                                    objects = Objects.Each(
                                        filter = and(
                                            inZone(Zone.Library),
                                            ObjectFilter.OwnedBy(players = PlayerFilter.Is(players = Players.Bound(name = "player"))),
                                        ),
                                    ),
                                ),
                                roundUp = false,
                            ),
                        ),
                    ),
                ),
                together = true,
            ),
            targets = listOf(TargetSlot(name = "players", what = TargetKind.Players(filter = PlayerFilter.Any), min = n(0), max = null)),
        ),
    )

    @Test
    fun chronoSpatialRift() = card(
        name = "Chrono-Spatial Rift",
        cost = "{2}{W}{U}",
        type = "Sorcery",
        text = "Target player mills cards equal to your life total.",
    ) parsesTo abilities(
        spell(
            doing(Effect.Mill(players = Players.Target(slot = "player"), count = Value.LifeOf(players = Players.You))),
            targets = listOf(TargetSlot(name = "player", what = TargetKind.Players(filter = PlayerFilter.Any), min = n(1), max = n(1))),
        ),
    )

    @Test
    fun outpostOverseer() = card(
        name = "Outpost Overseer",
        cost = "{W}{U}",
        type = "Creature — Lizard Artificer",
        pt = "2/2",
        text = "Whenever you cast your second spell each turn, create a 1/1 colorless Drone artifact creature token with flying and \"This token can block only creatures with flying.\"",
    ) parsesTo abilities(
        triggered(
            Trigger.On(events = listOf(EventPattern.Cast(spell = ObjectFilter.Any, by = PlayerFilter.You)), nthThisTurn = 2),
            doing(Effect.CreateToken(token = drone, count = n(1), controller = Players.You, bind = "token")),
        ),
    )

    @Test
    fun sirHarvellDaystarParagon() = card(
        name = "Sir Harvell, Daystar Paragon",
        cost = "{W}{B}",
        type = "Legendary Creature — Human Knight",
        pt = "2/2",
        text = """
            Vigilance, menace
            Whenever another creature you control dies or is put into exile, put a +1/+1 counter on Sir Harvell and you gain 1 life.
            When Sir Harvell dies or is put into exile while its power is 4 or greater, destroy up to one target nonland permanent.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        Keyword.Menace,
        triggered(
            Trigger.On(
                events = listOf(
                    EventPattern.ZoneChange(objects = and(not(isSelf), creature, you), from = Zone.Battlefield, to = Zone.Graveyard),
                    EventPattern.ZoneChange(objects = and(not(isSelf), creature, you), from = Zone.Battlefield, to = Zone.Exile),
                ),
            ),
            doing(Effect.PutCounters(objects = Objects.Self, kind = p1p1, amount = n(1))),
            doing(Effect.GainLife(players = Players.You, amount = n(1))),
        ),
        triggered(
            Trigger.On(
                events = listOf(
                    EventPattern.ZoneChange(objects = isSelf, from = Zone.Battlefield, to = Zone.Graveyard),
                    EventPattern.ZoneChange(objects = isSelf, from = Zone.Battlefield, to = Zone.Exile),
                ),
            ),
            doing(Effect.Destroy(objects = target("permanent"))),
            targets = listOf(slot("permanent", and(not(land), permanentFilter), min = 0)),
            triggersOnlyIf = Condition.ObjectMatches(
                objects = Objects.EventObject(role = EventRole.Before),
                filter = ObjectFilter.Power(comparison = Comparison.AtLeast, value = n(4)),
            ),
        ),
    )

    @Test
    fun sirHarvellTheLuminous() = card(
        name = "Sir Harvell, the Luminous",
        cost = "{2}{W}{B}{B}",
        type = "Legendary Creature — Human Knight",
        pt = "4/4",
        text = """
            Deathtouch, lifelink
            Whenever Sir Harvell enters or attacks, other creatures you control get +1/+0 and gain deathtouch until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Deathtouch,
        Keyword.Lifelink,
        triggered(
            Trigger.On(
                events = listOf(EventPattern.ZoneChange(objects = isSelf, to = Zone.Battlefield), EventPattern.Attack(attacker = isSelf)),
            ),
            untilEndOfTurn(Objects.Each(filter = and(not(isSelf), creature, you)), pt(1, 0), has(Keyword.Deathtouch)),
        ),
    )

    @Test
    fun brakkosRemembranceStandard() = card(
        name = "Brakkos, Remembrance Standard",
        cost = "{1}{R}{G}",
        type = "Legendary Creature — Kavu Pilot",
        pt = "2/4",
        text = "Landfall — Whenever a land you control enters, Brakkos deals 1 damage to each opponent. If this is the second time this ability has resolved this turn, draw a card.",
    ) parsesTo abilities(
        triggered(
            on(EventPattern.ZoneChange(objects = and(land, you), to = Zone.Battlefield)),
            doing(
                Effect.DealDamage(
                    to = Recipients(players = Players.Each(filter = PlayerFilter.Opponent)),
                    amount = n(1),
                    source = Objects.Self,
                ),
            ),
            Step.If(
                condition = Condition.Compare(
                    left = Value.AbilityUsesThisTurn(resolved = true),
                    comparison = Comparison.Equal,
                    right = n(2),
                ),
                then = listOf(doing(Effect.Draw(players = Players.You, count = n(1)))),
            ),
            abilityWord = "Landfall",
        ),
    )
}
