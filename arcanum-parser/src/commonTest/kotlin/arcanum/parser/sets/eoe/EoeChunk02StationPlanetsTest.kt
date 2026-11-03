package arcanum.parser.sets.eoe

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Edge of Eternities chunk 02: station symbols granting activated and triggered abilities (CR 721.2a): the Planet lands with mana and token abilities at 12+, a mana ability counting charge counters at 20+, a combat trigger at 2+, and more Spacecraft.
 */
class EoeChunk02StationPlanetsTest {

    @Test
    fun corvessaGaleboundBastion() = card(
        name = "Corvessa, Galebound Bastion",
        cost = null,
        type = "Land — Planet",
        text = """
            This land enters tapped.
            {T}: Add {W}.
            Station (Tap another creature you control: Put charge counters equal to its power on this Planet. Station only as a sorcery.)
            12+ | {3}{W}, {T}: Create a token that's a copy of target artifact or enchantment you control, except it's legendary. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        static(
            Mechanic.Replacement(
                event = EventPattern.ZoneChange(objects = isSelf, to = Zone.Battlefield),
                modify = listOf(Modification.EntersTapped),
            ),
        ),
        activated(
            cost(tap),
            doing(Effect.AddMana(mana = ManaProduction(kind = ManaKind.Fixed(mana = ManaCost.parse("{W}")!!)), players = Players.You)),
        ),
        Keyword.Station,
        station(
            12,
            activated(
                cost(mana("{3}{W}"), tap),
                doing(
                    Effect.CreateToken(
                        token = TokenSpec.CopyOf(
                            objects = target("permanent"),
                            except = listOf(CopyException.Types(addSupertypes = listOf(Supertype.Legendary))),
                        ),
                        count = n(1),
                        controller = Players.You,
                    ),
                ),
                targets = listOf(slot("permanent", and(or(artifact, enchantment), you))),
                rules = asSorcery,
            ),
        ),
    )

    @Test
    fun thessivarStirringHaven() = card(
        name = "Thessivar, Stirring Haven",
        cost = null,
        type = "Land — Planet",
        text = """
            This land enters tapped.
            {T}: Add {G}.
            Station (Tap another creature you control: Put charge counters equal to its power on this Planet. Station only as a sorcery.)
            12+ | {G}, {T}: Add {G} for each creature you control.
        """,
    ) parsesTo abilities(
        static(
            Mechanic.Replacement(
                event = EventPattern.ZoneChange(objects = isSelf, to = Zone.Battlefield),
                modify = listOf(Modification.EntersTapped),
            ),
        ),
        activated(
            cost(tap),
            doing(Effect.AddMana(mana = ManaProduction(kind = ManaKind.Fixed(mana = ManaCost.parse("{G}")!!)), players = Players.You)),
        ),
        Keyword.Station,
        station(
            12,
            activated(
                cost(mana("{G}"), tap),
                doing(
                    Effect.AddMana(
                        mana = ManaProduction(
                            kind = ManaKind.Fixed(
                                mana = ManaCost.parse("{G}")!!,
                                times = Value.Count(objects = Objects.Each(filter = and(creature, you))),
                            ),
                        ),
                        players = Players.You,
                    ),
                ),
            ),
        ),
    )

    @Test
    fun endlessWaymarkCitadel() = card(
        name = "Endless Waymark Citadel",
        cost = "{W}{U}{B}{R}{G}",
        type = "Legendary Artifact — Spacecraft",
        pt = "7/15",
        text = """
            When Endless Waymark Citadel enters, create a tapped 2/2 colorless Robot artifact creature token for each multicolored permanent you control.
            Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at 12+.)
            12+ | Flying
            Whenever Endless Waymark Citadel attacks, draw a card for each multicolored permanent you control.
        """,
    ) parsesTo abilities(
        triggered(
            entersSelf,
            doing(
                Effect.CreateToken(
                    token = robot,
                    count = Value.Count(objects = Objects.Each(filter = and(ObjectFilter.Multicolored, permanentFilter, you))),
                    controller = Players.You,
                    tapped = true,
                ),
            ),
        ),
        Keyword.Station,
        station(12, Keyword.Flying, pt = "7/15"),
        triggered(
            attacksSelf,
            doing(
                Effect.Draw(
                    players = Players.You,
                    count = Value.Count(objects = Objects.Each(filter = and(ObjectFilter.Multicolored, permanentFilter, you))),
                ),
            ),
        ),
    )

    @Test
    fun pyrrhosRemembranceWorld() = card(
        name = "Pyrrhos, Remembrance World",
        cost = null,
        type = "Land — Planet",
        text = """
            This land enters tapped.
            {T}: Add {R}.
            Station (Tap another creature you control: Put charge counters equal to its power on this Planet. Station only as a sorcery.)
            12+ | {1}{R}, {T}, Sacrifice a land: Create a 2/2 colorless Robot artifact creature token, then creatures you control get +1/+0 and gain haste until end of turn.
        """,
    ) parsesTo abilities(
        static(
            Mechanic.Replacement(
                event = EventPattern.ZoneChange(objects = isSelf, to = Zone.Battlefield),
                modify = listOf(Modification.EntersTapped),
            ),
        ),
        activated(
            cost(tap),
            doing(Effect.AddMana(mana = ManaProduction(kind = ManaKind.Fixed(mana = ManaCost.parse("{R}")!!)), players = Players.You)),
        ),
        Keyword.Station,
        station(
            12,
            activated(
                cost(mana("{1}{R}"), tap, CostPart.Sacrifice(what = CostObjects.Choose(filter = land, count = n(1)))),
                doing(Effect.CreateToken(token = robot, count = n(1), controller = Players.You)),
                untilEndOfTurn(Objects.Each(filter = and(creature, you)), pt(1, 0), has(Keyword.Haste)),
            ),
        ),
    )

    @Test
    fun grublingPathfinderCraft() = card(
        name = "Grubling Pathfinder Craft",
        cost = "{2}{G}",
        type = "Artifact — Spacecraft",
        pt = "3/3",
        text = """
            When this Spacecraft enters, you may sacrifice a land or Lander. If you do, search your library for up to two basic land cards, put them onto the battlefield tapped, then shuffle.
            Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at 7+.)
            7+ | Flying
        """,
    ) parsesTo abilities(
        triggered(
            entersSelf,
            Step.Pay(
                cost = cost(CostPart.Sacrifice(what = CostObjects.Choose(filter = or(land, sub("Lander")), count = n(1)))),
                payer = Players.You,
                bind = "paid",
            ),
            Step.If(
                condition = Condition.Did(name = "paid"),
                then = listOf(
                    doing(
                        Effect.Search(
                            library = Players.You,
                            filter = and(ObjectFilter.Super(supertype = Supertype.Basic), land),
                            max = n(2),
                            zones = listOf(Zone.Library),
                            bind = "found",
                            searcher = Players.You,
                        ),
                    ),
                    doing(Effect.Move(objects = bound("found"), to = Destination(zone = Zone.Battlefield, tapped = true))),
                    doing(Effect.Shuffle(library = Players.You)),
                ),
            ),
        ),
        Keyword.Station,
        station(7, Keyword.Flying, pt = "3/3"),
    )

    @Test
    fun maulClassSowingArk() = card(
        name = "Maul-Class Sowing Ark",
        cost = "{2}{G}",
        type = "Artifact — Spacecraft",
        pt = "4/5",
        text = """
            Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at 7+.)
            7+ | Flying
            Whenever this Spacecraft attacks, you may put a creature card from your hand onto the battlefield.
        """,
    ) parsesTo abilities(
        Keyword.Station,
        station(7, Keyword.Flying, pt = "4/5"),
        triggered(
            attacksSelf,
            Step.Choose(
                choice = Choice.Objects(filter = and(creature, inZone(Zone.Hand), ownedByYou), min = n(0), max = n(1)),
                bind = "chosen",
                chooser = Players.You,
            ),
            doing(Effect.Move(objects = bound("chosen"), to = Destination(zone = Zone.Battlefield))),
        ),
    )

    @Test
    fun sampleHauler() = card(
        name = "Sample Hauler",
        cost = "{5}{U}",
        type = "Artifact — Spacecraft",
        pt = "4/7",
        text = """
            When this Spacecraft enters, return up to two target non-Spacecraft creatures to their owners' hands.
            Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at 9+.)
            9+ | Flying
            Whenever this Spacecraft attacks, defending player mills four cards.
        """,
    ) parsesTo abilities(
        triggered(
            entersSelf,
            doing(Effect.Move(objects = target("creatures"), to = Destination(zone = Zone.Hand))),
            targets = listOf(slot("creatures", and(not(sub("Spacecraft")), creature), min = 0, max = 2)),
        ),
        Keyword.Station,
        station(9, Keyword.Flying, pt = "4/7"),
        triggered(attacksSelf, doing(Effect.Mill(players = Players.DefendingPlayerOf(attackers = Objects.Self), count = n(4)))),
    )

    @Test
    fun nyxaraTertiaHollowAltar() = card(
        name = "Nyxara Tertia, Hollow Altar",
        cost = null,
        type = "Land — Planet",
        text = """
            This land enters tapped.
            {T}: Add {B}.
            Station (Tap another creature you control: Put charge counters equal to its power on this Planet. Station only as a sorcery.)
            12+ | {1}{B}, {T}, Pay 2 life, Sacrifice a creature: Draw cards equal to the sacrificed creature's power. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        static(
            Mechanic.Replacement(
                event = EventPattern.ZoneChange(objects = isSelf, to = Zone.Battlefield),
                modify = listOf(Modification.EntersTapped),
            ),
        ),
        activated(
            cost(tap),
            doing(Effect.AddMana(mana = ManaProduction(kind = ManaKind.Fixed(mana = ManaCost.parse("{B}")!!)), players = Players.You)),
        ),
        Keyword.Station,
        station(
            12,
            activated(
                cost(
                    mana("{1}{B}"),
                    tap,
                    CostPart.PayLife(amount = n(2)),
                    CostPart.Sacrifice(what = CostObjects.Choose(filter = creature, count = n(1)), preBind = "sacrificed"),
                ),
                doing(
                    Effect.Draw(
                        players = Players.You,
                        count = Value.Of(property = NumericProperty.Power, objects = bound("sacrificed"), how = Combine.Single),
                    ),
                ),
                rules = asSorcery,
            ),
        ),
    )

    @Test
    fun murmuringRequiemBarge() = card(
        name = "Murmuring Requiem Barge",
        cost = "{4}{B}",
        type = "Artifact — Spacecraft",
        pt = "4/3",
        text = """
            When this Spacecraft enters, each opponent sacrifices a nontoken creature of their choice.
            Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at 7+.)
            7+ | Flying
        """,
    ) parsesTo abilities(
        triggered(
            entersSelf,
            Step.ForEach(
                each = Each.Players(players = Players.Each(filter = PlayerFilter.Opponent)),
                bind = "opponent",
                steps = listOf(
                    Step.Choose(
                        choice = Choice.Objects(
                            filter = and(
                                not(token),
                                creature,
                                ObjectFilter.ControlledBy(players = PlayerFilter.Is(players = Players.Bound(name = "opponent"))),
                            ),
                            min = n(1),
                            max = n(1),
                        ),
                        bind = "sacrificed",
                        chooser = Players.Bound(name = "opponent"),
                    ),
                    doing(Effect.Sacrifice(objects = bound("sacrificed"))),
                ),
                together = true,
            ),
        ),
        Keyword.Station,
        station(7, Keyword.Flying, pt = "4/3"),
    )

    @Test
    fun fabricatorTestbed() = card(
        name = "Fabricator Testbed",
        cost = "{U}",
        type = "Artifact — Spacecraft",
        pt = "4/4",
        text = """
            Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at 9+.)
            2+ | At the beginning of combat on your turn, up to one other target artifact you control becomes an artifact creature with base power and toughness 2/2 and gains flying until end of turn.
            9+ | Flying, vigilance
        """,
    ) parsesTo abilities(
        Keyword.Station,
        station(
            2,
            triggered(
                on(EventPattern.StepBegins(step = TurnStep.BeginningOfCombat, whose = PlayerFilter.You)),
                untilEndOfTurn(
                    target("artifact"),
                    Mechanic.SetTypes(add = listOf(CardType.Artifact, CardType.Creature)),
                    Mechanic.SetPowerToughness(power = n(2), toughness = n(2)),
                    has(Keyword.Flying),
                ),
                targets = listOf(slot("artifact", and(not(isSelf), artifact, you), min = 0)),
            ),
        ),
        station(9, Keyword.Flying, Keyword.Vigilance, pt = "4/4"),
    )

    @Test
    fun thePerpetualLift() = card(
        name = "The Perpetual Lift",
        cost = "{5}",
        type = "Legendary Artifact — Spacecraft",
        text = """
            {T}: Add {C}{C}{C}.
            Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery.)
            20+ | {T}: Add X mana of any one color, where X is the number of charge counters on The Perpetual Lift.
        """,
    ) parsesTo abilities(
        activated(
            cost(tap),
            doing(Effect.AddMana(mana = ManaProduction(kind = ManaKind.Fixed(mana = ManaCost.parse("{C}{C}{C}")!!)), players = Players.You)),
        ),
        Keyword.Station,
        station(
            20,
            activated(
                cost(tap),
                doing(
                    Effect.AddMana(
                        mana = ManaProduction(
                            kind = ManaKind.Choose(
                                amount = Value.Of(
                                    property = NumericProperty.Counters(kind = charge),
                                    objects = Objects.Self,
                                    how = Combine.Single,
                                ),
                            ),
                        ),
                        players = Players.You,
                    ),
                ),
            ),
        ),
    )

    @Test
    fun maeltharColossalGodcore() = card(
        name = "Maelthar, Colossal Godcore",
        cost = null,
        type = "Land — Planet",
        text = """
            This land enters tapped.
            {T}: Add {U}.
            Station (Tap another creature you control: Put charge counters equal to its power on this Planet. Station only as a sorcery.)
            12+ | {U}, {T}: Add {U} for each artifact you control.
        """,
    ) parsesTo abilities(
        static(
            Mechanic.Replacement(
                event = EventPattern.ZoneChange(objects = isSelf, to = Zone.Battlefield),
                modify = listOf(Modification.EntersTapped),
            ),
        ),
        activated(
            cost(tap),
            doing(Effect.AddMana(mana = ManaProduction(kind = ManaKind.Fixed(mana = ManaCost.parse("{U}")!!)), players = Players.You)),
        ),
        Keyword.Station,
        station(
            12,
            activated(
                cost(mana("{U}"), tap),
                doing(
                    Effect.AddMana(
                        mana = ManaProduction(
                            kind = ManaKind.Fixed(
                                mana = ManaCost.parse("{U}")!!,
                                times = Value.Count(objects = Objects.Each(filter = and(artifact, you))),
                            ),
                        ),
                        players = Players.You,
                    ),
                ),
            ),
        ),
    )

    @Test
    fun coilwallSweepcraft() = card(
        name = "Coilwall Sweepcraft",
        cost = "{2}",
        type = "Artifact — Spacecraft",
        pt = "2/2",
        text = """
            When this Spacecraft enters, surveil 2.
            Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at 4+.)
            4+ | Flying
        """,
    ) parsesTo abilities(
        triggered(entersSelf, doing(Effect.Surveil(players = Players.You, count = n(2)))),
        Keyword.Station,
        station(4, Keyword.Flying, pt = "2/2"),
    )
}
