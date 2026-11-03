package arcanum.parser.sets.eoe

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Edge of Eternities chunk 04: warp on black, red and green cards: warp with a life cost cast from the graveyard, warp given to cards in hand, "cast for its warp cost", exiled cards with warp and warped cards in exile (CR 702.185b), doubled counters, counters moved as it leaves.
 */
class EoeChunk04WarpBlackRedGreenTest {

    @Test
    fun reverseTimeRemnant() = card(
        name = "Reverse-Time Remnant",
        cost = "{6}",
        type = "Creature — Eldrazi",
        pt = "7/5",
        text = """
            When this creature leaves the battlefield, draw a card, then you may put a permanent card with mana value less than or equal to the number of lands you control from your hand onto the battlefield tapped.
            Warp {4} (You may cast this card from your hand for its warp cost. Exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)
        """,
    ) parsesTo abilities(
        triggered(
            on(EventPattern.ZoneChange(objects = isSelf, from = Zone.Battlefield)),
            doing(Effect.Draw(players = Players.You, count = n(1))),
            Step.Choose(
                choice = Choice.Objects(
                    filter = and(
                        or(
                            artifact,
                            ObjectFilter.Type(CardType.Battle),
                            creature,
                            enchantment,
                            land,
                            ObjectFilter.Type(CardType.Planeswalker),
                        ),
                        ObjectFilter.ManaValue(
                            comparison = Comparison.AtMost,
                            value = Value.Count(objects = Objects.Each(filter = and(land, you))),
                        ),
                        inZone(Zone.Hand),
                        ownedByYou,
                    ),
                    min = n(0),
                    max = n(1),
                ),
                bind = "chosen",
                chooser = Players.You,
            ),
            doing(Effect.Move(objects = bound("chosen"), to = Destination(zone = Zone.Battlefield, tapped = true))),
        ),
        warp("{4}"),
    )

    @Test
    fun edgeOfTheHive() = card(
        name = "Edge of the Hive",
        cost = "{3}{B}",
        type = "Creature — Insect Assassin",
        pt = "3/1",
        text = """
            When this creature enters, choose one —
            • Put two +1/+1 counters on this creature.
            • Put target exiled card with warp on the bottom of its owner's library.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(
                modes = Modes(
                    options = listOf(
                        Mode(
                            instructions = Instructions(
                                steps = listOf(doing(Effect.PutCounters(objects = Objects.Self, kind = p1p1, amount = n(2)))),
                            ),
                        ),
                        Mode(
                            instructions = Instructions(
                                targets = listOf(
                                    slot("card", and(cardFilter, inZone(Zone.Exile), ObjectFilter.HasKeyword(keyword = Keyword.Warp.Kind))),
                                ),
                                steps = listOf(
                                    doing(
                                        Effect.Move(objects = target("card"), to = Destination(zone = Zone.Library, libraryPosition = -1)),
                                    ),
                                ),
                            ),
                        ),
                    ),
                    min = 1,
                    max = 1,
                    chooser = Players.You,
                ),
            ),
        ),
    )

    @Test
    fun hivewatchVanguard() = card(
        name = "Hivewatch Vanguard",
        cost = "{X}{G}{G}",
        type = "Creature — Insect Knight",
        pt = "0/0",
        text = """
            This creature enters with X +1/+1 counters on it.
            When this creature leaves the battlefield, put its counters on target creature you control.
            Warp {X}{G} (You may cast this card from your hand for its warp cost. Exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)
        """,
    ) parsesTo abilities(
        static(
            Mechanic.Replacement(
                event = EventPattern.ZoneChange(objects = isSelf, to = Zone.Battlefield),
                modify = listOf(Modification.EntersWithCounters(kind = p1p1, amount = Value.CastX(of = Objects.Self))),
            ),
        ),
        triggered(
            on(EventPattern.ZoneChange(objects = isSelf, from = Zone.Battlefield)),
            doing(
                Effect.MoveCounters(
                    from = Objects.Self,
                    to = target("creature"),
                    kind = null,
                    amount = Value.Of(property = NumericProperty.Counters(kind = null), objects = Objects.Self, how = Combine.Single),
                ),
            ),
            targets = listOf(slot("creature", and(creature, you))),
        ),
        warp("{X}{G}"),
    )

    @Test
    fun forgottenJuggernaut() = card(
        name = "Forgotten Juggernaut",
        cost = "{9}",
        type = "Artifact Creature — Robot Giant",
        pt = "9/9",
        text = "Warp {3} (You may cast this card from your hand for its warp cost. Exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)",
    ) parsesTo abilities(
        warp("{3}"),
    )

    @Test
    fun nearCollision() = card(
        name = "Near Collision",
        cost = "{1}{G}",
        type = "Instant",
        text = """
            As an additional cost to cast this spell, choose a creature you control or a warped creature card you own in exile.
            Near Collision deals damage equal to the power of the chosen creature or card to target creature.
        """,
    ) parsesTo abilities(
        static(
            Mechanic.AdditionalCost(
                cost = cost(
                    CostPart.Choose(
                        choice = Choice.Objects(
                            filter = or(
                                and(creature, you),
                                and(
                                    creature,
                                    ownedByYou,
                                    inZone(Zone.Exile),
                                    ObjectFilter.HasStatus(status = Status.Marked(mark = ObjectMark.Warped)),
                                ),
                            ),
                            min = n(1),
                            max = n(1),
                        ),
                        bind = "chosen",
                    ),
                ),
            ),
        ),
        spell(
            doing(
                Effect.DealDamage(
                    to = Recipients(objects = target("creature")),
                    amount = Value.Of(property = NumericProperty.Power, objects = bound("chosen"), how = Combine.Single),
                    source = Objects.Self,
                ),
            ),
            targets = listOf(slot("creature", creature)),
        ),
    )

    @Test
    fun drixDestinyweaver() = card(
        name = "Drix Destinyweaver",
        cost = "{3}{G}",
        type = "Creature — Drix Wizard",
        pt = "3/2",
        text = """
            When this creature enters, put a +1/+1 counter on target creature.
            Each creature you control with a +1/+1 counter on it has trample.
            Warp {1}{G} (You may cast this card from your hand for its warp cost. Exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)
        """,
    ) parsesTo abilities(
        triggered(
            entersSelf,
            doing(Effect.PutCounters(objects = target("creature"), kind = p1p1, amount = n(1))),
            targets = listOf(slot("creature", creature)),
        ),
        static(has(Keyword.Trample), affects = Objects.Each(filter = and(creature, you, ObjectFilter.HasCounter(kind = p1p1, atLeast = 1)))),
        warp("{1}{G}"),
    )

    @Test
    fun colonyArchitecture() = card(
        name = "Colony Architecture",
        cost = "{3}{G}{G}",
        type = "Enchantment",
        text = """
            Landfall — Whenever a land you control enters, create a 2/2 colorless Robot artifact creature token.
            Warp {1}{G} (You may cast this card from your hand for its warp cost. Exile this enchantment at the beginning of the next end step, then you may cast it from exile on a later turn.)
        """,
    ) parsesTo abilities(
        triggered(
            on(EventPattern.ZoneChange(objects = and(land, you), to = Zone.Battlefield)),
            doing(Effect.CreateToken(token = robot, count = n(1), controller = Players.You)),
            abilityWord = "Landfall",
        ),
        warp("{1}{G}"),
    )

    @Test
    fun maximumThrust() = card(
        name = "Maximum Thrust",
        cost = "{R}",
        type = "Instant",
        text = "Target creature you control gets +3/+2 until end of turn. If that creature was cast for its warp cost, it also gains trample and haste until end of turn.",
    ) parsesTo abilities(
        spell(
            untilEndOfTurn(target("creature"), pt(3, 2)),
            Step.If(
                condition = Condition.Paid(keyword = Keyword.Warp.Kind, times = 1, of = target("creature")),
                then = listOf(untilEndOfTurn(target("creature"), has(Keyword.Trample, Keyword.Haste))),
            ),
            targets = listOf(slot("creature", and(creature, you))),
        ),
    )

    @Test
    fun sproutingCrawler() = card(
        name = "Sprouting Crawler",
        cost = "{4}{G}",
        type = "Creature — Plant Wurm",
        pt = "5/5",
        text = """
            When this creature enters, you gain 2 life.
            Warp {1}{G} (You may cast this card from your hand for its warp cost. Exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)
        """,
    ) parsesTo abilities(
        triggered(entersSelf, doing(Effect.GainLife(players = Players.You, amount = n(2)))),
        warp("{1}{G}"),
    )

    @Test
    fun cargoBayStaging() = card(
        name = "Cargo Bay Staging",
        cost = "{3}{G}",
        type = "Enchantment",
        text = """
            If one or more counters would be put on a creature, Spacecraft, or Planet you control, twice that many of each of those kinds of counters are put on it instead.
            Warp {G} (You may cast this card from your hand for its warp cost. Exile this enchantment at the beginning of the next end step, then you may cast it from exile on a later turn.)
        """,
    ) parsesTo abilities(
        static(
            Mechanic.Replacement(
                event = EventPattern.CountersPut(objects = and(or(creature, sub("Spacecraft"), sub("Planet")), you)),
                modify = listOf(Modification.MultiplyAmount(factor = 2)),
            ),
        ),
        warp("{G}"),
    )

    @Test
    fun fallenSquadCaptain() = card(
        name = "Fallen Squad Captain",
        cost = "{3}{R}",
        type = "Creature — Kavu Soldier",
        pt = "4/3",
        text = """
            During your turn, other creatures you control get +1/+0.
            Warp {1}{R} (You may cast this card from your hand for its warp cost. Exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)
        """,
    ) parsesTo abilities(
        static(pt(1, 0), affects = Objects.Each(filter = and(not(isSelf), creature, you)), condition = Condition.YourTurn),
        warp("{1}{R}"),
    )

    @Test
    fun strengthshapeAttuner() = card(
        name = "Strengthshape Attuner",
        cost = "{2}{G}{G}",
        type = "Creature — Insect Druid",
        pt = "4/4",
        text = """
            Landfall — Whenever a land you control enters, double the power of target creature you control until end of turn.
            Warp {2}{G} (You may cast this card from your hand for its warp cost. Exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)
        """,
    ) parsesTo abilities(
        triggered(
            on(EventPattern.ZoneChange(objects = and(land, you), to = Zone.Battlefield)),
            untilEndOfTurn(
                target("creature"),
                Mechanic.ModifyPowerToughness(
                    power = Value.Of(property = NumericProperty.Power, objects = target("creature"), how = Combine.Single),
                    toughness = n(0),
                ),
            ),
            targets = listOf(slot("creature", and(creature, you))),
            abilityWord = "Landfall",
        ),
        warp("{2}{G}"),
    )

    @Test
    fun supernovaWyrm() = card(
        name = "Supernova Wyrm",
        cost = "{3}{R}{R}",
        type = "Creature — Dragon",
        pt = "4/5",
        text = """
            Flying, haste
            When this creature enters, it deals 1 damage to target creature an opponent controls.
            Warp {2}{R} (You may cast this card from your hand for its warp cost. Exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Haste,
        triggered(
            entersSelf,
            doing(Effect.DealDamage(to = Recipients(objects = target("creature")), amount = n(1), source = Objects.Self)),
            targets = listOf(slot("creature", and(creature, opponents))),
        ),
        warp("{2}{R}"),
    )

    @Test
    fun apogeeSummoner() = card(
        name = "Apogee Summoner",
        cost = "{4}{B}",
        type = "Creature — Horror",
        pt = "4/5",
        text = """
            When this creature enters, until end of turn, another target creature you control gets +2/+0 and gains "When this creature dies, return it to the battlefield tapped under its owner's control."
            Warp {1}{B} (You may cast this card from your hand for its warp cost. Exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)
        """,
    ) parsesTo abilities(
        triggered(
            entersSelf,
            untilEndOfTurn(
                target("creature"),
                pt(2, 0),
                has(
                    triggered(
                        diesSelf,
                        doing(
                            Effect.Move(
                                objects = Objects.EventObject(role = EventRole.After),
                                to = Destination(
                                    zone = Zone.Battlefield,
                                    tapped = true,
                                    controller = Players.OwnerOf(objects = Objects.EventObject(role = EventRole.After)),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            targets = listOf(slot("creature", and(not(isSelf), creature, you))),
        ),
        warp("{1}{B}"),
    )

    @Test
    fun summitEnvoy() = card(
        name = "Summit Envoy",
        cost = "{1}{U}{R}",
        type = "Artifact Creature — Robot",
        pt = "3/3",
        text = """
            Whenever you cast an artifact spell, create a 1/1 colorless Drone artifact creature token with flying and "This token can block only creatures with flying."
            Warp {U/R} (You may cast this card from your hand for its warp cost. Exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)
        """,
    ) parsesTo abilities(
        triggered(
            on(EventPattern.Cast(spell = artifact, by = PlayerFilter.You)),
            doing(Effect.CreateToken(token = drone, count = n(1), controller = Players.You, bind = "token")),
        ),
        warp("{U/R}"),
    )

    @Test
    fun probabilityMechanic() = card(
        name = "Probability Mechanic",
        cost = "{2}{R}",
        type = "Creature — Kavu Artificer",
        pt = "3/3",
        text = """
            Whenever this creature or another Kavu you control enters, exile the top card of your library. For as long as that card remains exiled, you may play it if you control a Kavu.
            Warp {1}{R} (You may cast this card from your hand for its warp cost. Exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)
        """,
    ) parsesTo abilities(
        triggered(
            on(EventPattern.ZoneChange(objects = or(isSelf, and(not(isSelf), sub("Kavu"), you)), to = Zone.Battlefield)),
            doing(
                Effect.Move(
                    objects = Objects.LibraryCards(of = Players.You, count = n(1), fromTop = true),
                    to = Destination(zone = Zone.Exile),
                    postBind = "exiled",
                ),
            ),
            doing(
                Effect.ApplyMechanics(
                    mechanics = listOf(
                        Mechanic.MayPlay(
                            from = Zone.Exile,
                            how = PlayKind.Play,
                            holder = PlayerFilter.You,
                            condition = Condition.Exists(filter = and(sub("Kavu"), you), atLeast = 1),
                        ),
                    ),
                    affects = Affected.Objects(objects = bound("exiled")),
                    duration = Duration.Indefinitely,
                ),
            ),
        ),
        warp("{1}{R}"),
    )

    @Test
    fun crimsonPantherAutomaton() = card(
        name = "Crimson Panther Automaton",
        cost = "{3}{R}",
        type = "Artifact Creature — Robot Cat",
        pt = "3/3",
        text = """
            Haste
            Warp {1}{R} (You may cast this card from your hand for its warp cost. Exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        warp("{1}{R}"),
    )

    @Test
    fun murmuringNullspawn() = card(
        name = "Murmuring Nullspawn",
        cost = "{2}{B}",
        type = "Creature — Vampire Soldier",
        pt = "2/2",
        text = """
            Whenever this creature or another creature or artifact you control dies, target opponent loses 1 life and you gain 1 life.
            Warp {B} (You may cast this card from your hand for its warp cost. Exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)
        """,
    ) parsesTo abilities(
        triggered(
            on(
                EventPattern.ZoneChange(
                    objects = or(isSelf, and(not(isSelf), or(creature, artifact), you)),
                    from = Zone.Battlefield,
                    to = Zone.Graveyard,
                ),
            ),
            doing(Effect.LoseLife(players = Players.Target(slot = "opponent"), amount = n(1))),
            doing(Effect.GainLife(players = Players.You, amount = n(1))),
            targets = listOf(
                TargetSlot(name = "opponent", what = TargetKind.Players(filter = PlayerFilter.Opponent), min = n(1), max = n(1)),
            ),
        ),
        warp("{B}"),
    )

    @Test
    fun brakkosStalwartLieutenant() = card(
        name = "Brakkos, Stalwart Lieutenant",
        cost = "{2}{R}{R}",
        type = "Legendary Creature — Kavu Pilot",
        pt = "3/5",
        text = """
            Other creatures you control have haste.
            Artifact cards and red creature cards in your hand have warp {2}{R}. (You may cast a card from your hand for its warp cost. Exile that permanent at the beginning of the next end step, then you may cast it from exile on a later turn.)
        """,
    ) parsesTo abilities(
        static(has(Keyword.Haste), affects = Objects.Each(filter = and(not(isSelf), creature, you))),
        static(
            has(warp("{2}{R}")),
            affects = Objects.Each(
                filter = and(
                    or(artifact, and(ObjectFilter.OfColor(color = ColorRef.Literal(color = Color.Red)), creature)),
                    inZone(Zone.Hand),
                    ownedByYou,
                ),
            ),
        ),
    )

    @Test
    fun chronolineReaper() = card(
        name = "Chronoline Reaper",
        cost = "{B}{B}",
        type = "Creature — Drix Warlock",
        pt = "2/2",
        text = """
            Haste
            You may cast this card from your graveyard using its warp ability.
            Warp—{B}, Pay 2 life. (You may cast this card from your hand or graveyard for its warp cost. If you do, exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        static(Mechanic.MayPlay(from = Zone.Graveyard, how = PlayKind.Cast, holder = PlayerFilter.You, viaAlternative = Keyword.Warp.Kind)),
        Keyword.Warp(cost = cost(mana("{B}"), CostPart.PayLife(amount = n(2)))),
    )

    @Test
    fun loomstalkerZealot() = card(
        name = "Loomstalker Zealot",
        cost = "{2}{R}",
        type = "Creature — Drix Artificer",
        pt = "2/3",
        text = """
            Whenever another creature or artifact you control enters, this creature deals 1 damage to each opponent.
            Warp {R} (You may cast this card from your hand for its warp cost. Exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)
        """,
    ) parsesTo abilities(
        triggered(
            on(EventPattern.ZoneChange(objects = and(not(isSelf), or(creature, artifact), you), to = Zone.Battlefield)),
            doing(
                Effect.DealDamage(
                    to = Recipients(players = Players.Each(filter = PlayerFilter.Opponent)),
                    amount = n(1),
                    source = Objects.Self,
                ),
            ),
        ),
        warp("{R}"),
    )
}
