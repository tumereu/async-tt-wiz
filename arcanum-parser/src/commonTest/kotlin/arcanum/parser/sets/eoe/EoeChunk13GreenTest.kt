package arcanum.parser.sets.eoe

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Edge of Eternities chunk 13: green cards: devour of lands (CR 702.82c), combat damage that can't be prevented, differently named lands, toughness assigning damage and stationing (CR 702.184c), doubled power (CR 701.10b), blocking a flier, counters put by you.
 */
class EoeChunk13GreenTest {

    @Test
    fun lifeforgeSurge() = card(
        name = "Lifeforge Surge",
        cost = "{1}{G}",
        type = "Instant",
        text = "Put a +1/+1 counter on target creature you control. It gains reach, trample, and indestructible until end of turn. Untap it. (Damage and effects that say \"destroy\" don't destroy it.)",
    ) parsesTo abilities(
        spell(
            doing(Effect.PutCounters(objects = target("creature"), kind = p1p1, amount = n(1))),
            untilEndOfTurn(target("creature"), has(Keyword.Reach, Keyword.Trample, Keyword.Indestructible)),
            doing(Effect.Untap(objects = target("creature"))),
            targets = listOf(slot("creature", and(creature, you))),
        ),
    )

    @Test
    fun floweringBarbtail() = card(
        name = "Flowering Barbtail",
        cost = "{1}{G}",
        type = "Creature — Plant Scorpion",
        pt = "2/2",
        text = """
            Deathtouch
            When this creature enters, another target creature you control gains deathtouch until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Deathtouch,
        triggered(
            entersSelf,
            untilEndOfTurn(target("creature"), has(Keyword.Deathtouch)),
            targets = listOf(slot("creature", and(not(isSelf), creature, you))),
        ),
    )

    @Test
    fun treatyNegotiations() = card(
        name = "Treaty Negotiations",
        cost = "{2}{G}",
        type = "Instant",
        text = "Target creature you control gets +1/+0 and gains vigilance until end of turn. It deals damage equal to its power to target creature an opponent controls.",
    ) parsesTo abilities(
        spell(
            untilEndOfTurn(target("creature"), pt(1, 0), has(Keyword.Vigilance)),
            doing(
                Effect.DealDamage(
                    to = Recipients(objects = target("creature an opponent controls")),
                    amount = Value.Of(property = NumericProperty.Power, objects = target("creature"), how = Combine.Single),
                    source = target("creature"),
                ),
            ),
            targets = listOf(slot("creature", and(creature, you)), slot("creature an opponent controls", and(creature, opponents))),
        ),
    )

    @Test
    fun verdaniGroundgardener() = card(
        name = "Verdani Groundgardener",
        cost = "{1}{G}",
        type = "Creature — Insect Druid",
        pt = "2/3",
        text = "Landfall — Whenever a land you control enters, you gain 1 life.",
    ) parsesTo abilities(
        triggered(
            on(EventPattern.ZoneChange(objects = and(land, you), to = Zone.Battlefield)),
            doing(Effect.GainLife(players = Players.You, amount = n(1))),
            abilityWord = "Landfall",
        ),
    )

    @Test
    fun starvingPlanetmaw() = card(
        name = "Starving Planetmaw",
        cost = "{5}{G}{G}{G}",
        type = "Creature — Leviathan",
        pt = "0/0",
        text = """
            Ward {3}
            Devour land 3 (As this creature enters, you may sacrifice any number of lands. It enters with three times that many +1/+1 counters on it.)
            When this creature enters, look at the top X cards of your library, where X is this creature's power. Put any number of land cards from among them onto the battlefield tapped, then shuffle.
        """,
    ) parsesTo abilities(
        Keyword.Ward(cost = cost(mana("{3}"))),
        Keyword.Devour(amount = 3, what = land),
        triggered(
            entersSelf,
            doing(
                Effect.LookAt(
                    objects = Objects.LibraryCards(
                        of = Players.You,
                        count = Value.Of(property = NumericProperty.Power, objects = Objects.Self, how = Combine.Single),
                        fromTop = true,
                    ),
                    players = Players.You,
                    bind = "looked",
                ),
            ),
            Step.Choose(
                choice = Choice.Objects(filter = land, min = n(0), max = null, from = bound("looked")),
                bind = "chosen",
                chooser = Players.You,
            ),
            doing(Effect.Move(objects = bound("chosen"), to = Destination(zone = Zone.Battlefield, tapped = true))),
            doing(Effect.Shuffle(library = Players.You)),
        ),
    )

    @Test
    fun rampagingHornbeast() = card(
        name = "Rampaging Hornbeast",
        cost = "{G}{G}",
        type = "Creature — Beast",
        pt = "3/2",
        text = """
            This spell can't be countered.
            Trample, haste
            Creature spells you control can't be countered.
            Combat damage can't be prevented.
        """,
    ) parsesTo abilities(
        static(Mechanic.CantBeCountered),
        Keyword.Trample,
        Keyword.Haste,
        static(Mechanic.CantBeCountered, affects = Objects.Each(filter = and(creature, spellFilter, you))),
        static(
            Mechanic.DamageCantBePrevented(
                event = EventPattern.Damage(
                    source = ObjectFilter.Any,
                    to = RecipientFilter(objects = ObjectFilter.Any, players = PlayerFilter.Any),
                    combat = true,
                ),
            ),
        ),
    )

    @Test
    fun sporecapBehemoth() = card(
        name = "Sporecap Behemoth",
        cost = "{6}{G}",
        type = "Creature — Fungus Beast",
        pt = "5/5",
        text = "This spell costs {X} less to cast, where X is the number of differently named lands you control.",
    ) parsesTo abilities(
        static(
            Mechanic.CostChange(
                what = isSelf,
                change = CostChange.Less(
                    mana = ManaCost.parse("{1}")!!,
                    times = Value.CountDistinct(objects = Objects.Each(filter = and(land, you)), quality = SharedQuality.Name),
                ),
            ),
        ),
    )

    @Test
    fun genomePollenDrone() = card(
        name = "Genome Pollen-Drone",
        cost = "{G}",
        type = "Artifact Creature — Robot Insect",
        pt = "1/2",
        text = "{T}, Tap an untapped permanent you control: Add one mana of any color.",
    ) parsesTo abilities(
        activated(
            cost(tap, CostPart.TapObjects(what = CostObjects.Choose(filter = permanentFilter, count = n(1)))),
            doing(Effect.AddMana(mana = ManaProduction(kind = ManaKind.Choose(amount = n(1))), players = Players.You)),
        ),
    )

    @Test
    fun concordantGladeWalker() = card(
        name = "Concordant Glade-Walker",
        cost = "{3}{G}{G}",
        type = "Creature — Beast",
        pt = "*/*",
        text = """
            Ward {2} (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays {2}.)
            Concordant Glade-Walker's power and toughness are each equal to the number of lands you control.
        """,
    ) parsesTo abilities(
        Keyword.Ward(cost = cost(mana("{2}"))),
        static(
            Mechanic.SetPowerToughness(
                power = Value.Count(objects = Objects.Each(filter = and(land, you))),
                toughness = Value.Count(objects = Objects.Each(filter = and(land, you))),
            ),
            characteristicDefining = true,
        ),
    )

    @Test
    fun bloodbondTick() = card(
        name = "Bloodbond Tick",
        cost = "{G}",
        type = "Creature — Mite",
        pt = "1/1",
        text = "Whenever this creature becomes tapped, another target creature you control gets +X/+X until end of turn, where X is this creature's power.",
    ) parsesTo abilities(
        triggered(
            on(EventPattern.Tap(objects = isSelf)),
            untilEndOfTurn(
                target("creature"),
                Mechanic.ModifyPowerToughness(
                    power = Value.Of(property = NumericProperty.Power, objects = Objects.Self, how = Combine.Single),
                    toughness = Value.Of(property = NumericProperty.Power, objects = Objects.Self, how = Combine.Single),
                ),
            ),
            targets = listOf(slot("creature", and(not(isSelf), creature, you))),
        ),
    )

    @Test
    fun frostgrottoSmasher() = card(
        name = "Frostgrotto Smasher",
        cost = "{3}{G}",
        type = "Creature — Beast",
        pt = "4/4",
        text = """
            Trample
            Landfall — Whenever a land you control enters, this creature gets +1/+0 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(
            on(EventPattern.ZoneChange(objects = and(land, you), to = Zone.Battlefield)),
            untilEndOfTurn(Objects.Self, pt(1, 0)),
            abilityWord = "Landfall",
        ),
    )

    @Test
    fun frostplowScout() = card(
        name = "Frostplow Scout",
        cost = "{2}{G}{G}",
        type = "Creature — Insect Scout",
        pt = "2/4",
        text = """
            You may play an additional land on each of your turns.
            You may play lands from your graveyard.
            Landfall — Whenever a land you control enters, mill a card.
        """,
    ) parsesTo abilities(
        playerStatic(Mechanic.ExtraLandPlays(count = 1)),
        static(
            Mechanic.MayPlay(from = Zone.Graveyard, how = PlayKind.Play, holder = PlayerFilter.You),
            affects = Objects.Each(filter = and(land, inZone(Zone.Graveyard), ownedByYou)),
        ),
        triggered(
            on(EventPattern.ZoneChange(objects = and(land, you), to = Zone.Battlefield)),
            doing(Effect.Mill(players = Players.You, count = n(1))),
            abilityWord = "Landfall",
        ),
    )

    @Test
    fun pluckyGreenhorn() = card(
        name = "Plucky Greenhorn",
        cost = "{1}{G}",
        type = "Creature — Insect Citizen",
        pt = "2/2",
        text = "{3}: Put a +1/+1 counter on this creature. Activate only as a sorcery.",
    ) parsesTo abilities(
        activated(cost(mana("{3}")), doing(Effect.PutCounters(objects = Objects.Self, kind = p1p1, amount = n(1))), rules = asSorcery),
    )

    @Test
    fun whiptailStalker() = card(
        name = "Whiptail Stalker",
        cost = "{4}{G}{G}",
        type = "Creature — Plant Beast",
        pt = "5/7",
        text = """
            This spell costs {2} less to cast if your opponents control three or more creatures.
            Reach
        """,
    ) parsesTo abilities(
        static(
            Mechanic.CostChange(what = isSelf, change = CostChange.Less(mana = ManaCost.parse("{2}")!!, times = n(1))),
            condition = Condition.Compare(
                left = Value.Count(objects = Objects.Each(filter = and(creature, opponents))),
                comparison = Comparison.AtLeast,
                right = n(3),
            ),
        ),
        Keyword.Reach,
    )

    @Test
    fun thawwalkerMourner() = card(
        name = "Thawwalker Mourner",
        cost = "{2}{G}",
        type = "Creature — Insect Soldier",
        pt = "3/3",
        text = "Whenever a creature you control with a +1/+1 counter on it dies, draw a card.",
    ) parsesTo abilities(
        triggered(
            on(
                EventPattern.ZoneChange(
                    objects = and(creature, you, ObjectFilter.HasCounter(kind = p1p1, atLeast = 1)),
                    from = Zone.Battlefield,
                    to = Zone.Graveyard,
                ),
            ),
            doing(Effect.Draw(players = Players.You, count = n(1))),
        ),
    )

    @Test
    fun thawwalkersKit() = card(
        name = "Thawwalker's Kit",
        cost = "{G}",
        type = "Artifact — Equipment",
        text = """
            When this Equipment enters, attach it to target creature you control.
            Equipped creature gets +2/+1 and has reach.
            Equip {5} ({5}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        triggered(
            entersSelf,
            doing(Effect.Attach(objects = Objects.Self, to = Recipients(objects = target("creature")))),
            targets = listOf(slot("creature", and(creature, you))),
        ),
        static(pt(2, 1), has(Keyword.Reach), affects = Objects.AttachedTo(of = Objects.Self)),
        Keyword.Equip(cost = cost(mana("{5}"))),
    )

    @Test
    fun thawwalkersGrit() = card(
        name = "Thawwalker's Grit",
        cost = "{G}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature you control
            When this Aura enters, enchanted creature fights up to one target creature an opponent controls. (Each deals damage equal to its power to the other.)
            Enchanted creature gets +0/+2 and can't be blocked by more than one creature.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(what = TargetKind.Objects(filter = and(creature, you))),
        triggered(
            entersSelf,
            doing(Effect.Fight(first = Objects.AttachedTo(of = Objects.Self), second = target("creature"))),
            targets = listOf(slot("creature", and(creature, opponents), min = 0)),
        ),
        static(pt(0, 2), Mechanic.MaximumBlockers(count = 1), affects = Objects.AttachedTo(of = Objects.Self)),
    )

    @Test
    fun coilselfDevourer() = card(
        name = "Coilself Devourer",
        cost = "{2}{G}{G}",
        type = "Creature — Plant Wurm",
        pt = "1/3",
        text = "At the beginning of combat on your turn, put X +1/+1 counters on each creature you control, where X is this creature's power.",
    ) parsesTo abilities(
        triggered(
            on(EventPattern.StepBegins(step = TurnStep.BeginningOfCombat, whose = PlayerFilter.You)),
            doing(
                Effect.PutCounters(
                    objects = Objects.Each(filter = and(creature, you)),
                    kind = p1p1,
                    amount = Value.Of(property = NumericProperty.Power, objects = Objects.Self, how = Combine.Single),
                ),
            ),
        ),
    )

    @Test
    fun drawBackThroughTheLoom() = card(
        name = "Draw Back Through the Loom",
        cost = "{3}{G}{G}",
        type = "Sorcery",
        text = "Return up to two target nonland permanent cards from your graveyard to your hand, then return up to two target land cards from your graveyard to the battlefield tapped.",
    ) parsesTo abilities(
        spell(
            doing(Effect.Move(objects = target("cards"), to = Destination(zone = Zone.Hand))),
            doing(Effect.Move(objects = target("second cards"), to = Destination(zone = Zone.Battlefield, tapped = true))),
            targets = listOf(
                slot(
                    "cards",
                    and(
                        not(land),
                        or(
                            artifact,
                            ObjectFilter.Type(CardType.Battle),
                            creature,
                            enchantment,
                            land,
                            ObjectFilter.Type(CardType.Planeswalker),
                        ),
                        inZone(Zone.Graveyard),
                        ownedByYou,
                    ),
                    min = 0,
                    max = 2,
                ),
                slot("second cards", and(land, inZone(Zone.Graveyard), ownedByYou), min = 0, max = 2),
            ),
        ),
    )

    @Test
    fun brokenPinions() = card(
        name = "Broken Pinions",
        cost = "{2}{G}",
        type = "Sorcery",
        text = "Destroy target artifact, enchantment, or creature with flying. Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)",
    ) parsesTo abilities(
        spell(
            doing(Effect.Destroy(objects = target("permanent"))),
            doing(Effect.Surveil(players = Players.You, count = n(1))),
            targets = listOf(slot("permanent", or(artifact, enchantment, and(creature, ObjectFilter.HasKeyword(keyword = Keyword.Flying))))),
        ),
    )

    @Test
    fun cloudbarb() = card(
        name = "Cloudbarb",
        cost = "{2}{G}",
        type = "Creature — Insect Warrior",
        pt = "3/3",
        text = """
            Reach
            Whenever this creature blocks a creature with flying, this creature gets +5/+0 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        triggered(
            // CR 509.3b: once for each creature with flying it blocks
            on(EventPattern.Block(blocker = isSelf, attacker = and(creature, ObjectFilter.HasKeyword(keyword = Keyword.Flying)), perAttacker = true)),
            untilEndOfTurn(Objects.Self, pt(5, 0)),
        ),
    )

    @Test
    fun loomworkSentinel() = card(
        name = "Loomwork Sentinel",
        cost = "{3}{G}",
        type = "Artifact Creature — Robot Soldier",
        pt = "3/4",
        text = """
            Vigilance
            Each creature you control with toughness greater than its power assigns combat damage equal to its toughness rather than its power.
            Each creature you control with toughness greater than its power stations permanents using its toughness rather than its power.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        static(
            Mechanic.AssignsDamageByToughness,
            affects = Objects.Each(
                filter = and(
                    creature,
                    you,
                    ObjectFilter.Toughness(
                        comparison = Comparison.Greater,
                        value = Value.Of(property = NumericProperty.Power, objects = Objects.Tested, how = Combine.Single),
                    ),
                ),
            ),
        ),
        static(
            Mechanic.StationsWithToughness,
            affects = Objects.Each(
                filter = and(
                    creature,
                    you,
                    ObjectFilter.Toughness(
                        comparison = Comparison.Greater,
                        value = Value.Of(property = NumericProperty.Power, objects = Objects.Tested, how = Combine.Single),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun groundbonding() = card(
        name = "Groundbonding",
        cost = "{2}{G}",
        type = "Enchantment",
        text = "Whenever you put one or more +1/+1 counters on a creature you control, you may draw that many cards. Do this only once each turn.",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(
                events = listOf(EventPattern.CountersPut(objects = and(creature, you), kind = p1p1, causedBy = you)),
                oneOrMore = true,
            ),
            instructions = Instructions(
                steps = listOf(
                    Step.May(steps = listOf(doing(Effect.Draw(players = Players.You, count = Value.EventAmount))), player = Players.You),
                ),
            ),
            actionOncePerTurn = true,
        ),
    )

    @Test
    fun meltcaller() = card(
        name = "Meltcaller",
        cost = "{2}{G}",
        type = "Creature — Insect Scout",
        pt = "4/2",
        text = "When this creature enters or dies, surveil 1. (Look at the top card of your library. You may put it into your graveyard.)",
    ) parsesTo abilities(
        triggered(
            Trigger.On(
                events = listOf(
                    EventPattern.ZoneChange(objects = isSelf, to = Zone.Battlefield),
                    EventPattern.ZoneChange(objects = isSelf, from = Zone.Battlefield, to = Zone.Graveyard),
                ),
            ),
            doing(Effect.Surveil(players = Players.You, count = n(1))),
        ),
    )
}
