package arcanum.parser.sets.tdm

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Tarkir: Dragonstorm chunk 01: endure by the permanent itself, by the creature that entered and as an activated ability (CR 701.63), mobilize N and mobilize X (CR 702.181), Warrior tokens that can't be sacrificed during your end step.
 */
class TdmChunk01EndureAndMobilizeTest {
    @Test
    fun velashaEnduringLineage() = card(
        name = "Velasha, Enduring Lineage",
        cost = "{2}{W}",
        type = "Legendary Creature — Spirit Soldier",
        pt = "2/2",
        text = """
            Flash
            First strike
            Whenever another nontoken creature you control dies, Velasha endures 2. (Put two +1/+1 counters on it or create a 2/2 white Spirit creature token.)
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.FirstStrike,
        Ability.Triggered(
            trigger = Trigger.On(
                events = listOf(
                    EventPattern.ZoneChange(
                        objects = and(not(isSelf), not(token), creature, you),
                        from = Zone.Battlefield,
                        to = Zone.Graveyard,
                    ),
                ),
            ),
            instructions = Instructions(steps = listOf(doing(Effect.Action(action = KeywordAction.Endure(objects = Objects.Self, count = n(2)))))),
        ),
    )

    @Test
    fun avengerOfTheSlain() = card(
        name = "Avenger of the Slain",
        cost = "{2}{B}",
        type = "Creature — Human Warrior",
        pt = "2/4",
        text = """
            Deathtouch
            Mobilize X, where X is the number of creature cards in your graveyard. (Whenever this creature attacks, create X tapped and attacking 1/1 red Warrior creature tokens. Sacrifice them at the beginning of the next end step.)
        """,
    ) parsesTo abilities(
        Keyword.Deathtouch,
        Ability.Triggered(
            trigger = attacksSelf,
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.CreateToken(
                            token = TokenSpec.Defined(
                                face = FaceDefinition(
                                    name = "Warrior Token",
                                    colorIndicator = listOf(Color.Red),
                                    types = listOf(CardType.Creature),
                                    subtypes = listOf(Subtype(name = "Warrior")),
                                    power = PrintedNumber.Fixed(value = 1),
                                    toughness = PrintedNumber.Fixed(value = 1),
                                ),
                            ),
                            count = Value.Count(objects = Objects.Each(filter = and(creature, inZone(Zone.Graveyard), ownedByYou))),
                            controller = Players.You,
                            tapped = true,
                            attacking = AttackTarget.Chosen,
                            bind = "mobilized",
                        ),
                    ),
                    doing(
                        Effect.CreateDelayedTrigger(
                            trigger = Trigger.On(events = listOf(EventPattern.StepBegins(step = TurnStep.End, whose = PlayerFilter.Any))),
                            instructions = Instructions(steps = listOf(doing(Effect.Sacrifice(objects = bound("mobilized"))))),
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun ossuaryButcher() = card(
        name = "Ossuary Butcher",
        cost = "{1}{R}{W}{B}",
        type = "Creature — Demon",
        pt = "4/4",
        text = """
            Mobilize 2 (Whenever this creature attacks, create two tapped and attacking 1/1 red Warrior creature tokens. Sacrifice them at the beginning of the next end step.)
            Attacking tokens you control have deathtouch.
        """,
    ) parsesTo abilities(
        Keyword.Mobilize(amount = 2),
        Ability.Static(
            mechanics = listOf(Mechanic.GainAbilities(abilities = listOf(Keyword.Deathtouch))),
            affects = Affected.Objects(objects = Objects.Each(filter = and(attacking, token, you))),
        ),
    )

    @Test
    fun steppePackhaulers() = card(
        name = "Steppe Packhaulers",
        cost = "{2}{W}",
        type = "Creature — Ox",
        pt = "0/4",
        text = """
            Vigilance
            Mobilize 3 (Whenever this creature attacks, create three tapped and attacking 1/1 red Warrior creature tokens. Sacrifice them at the beginning of the next end step.)
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        Keyword.Mobilize(amount = 3),
    )

    @Test
    fun heirOfTempests() = card(
        name = "Heir of Tempests",
        cost = "{W}",
        type = "Creature — Human Soldier",
        pt = "2/1",
        text = "Whenever this creature attacks, you may pay {1}{W}. If you do, it endures 1. (Put a +1/+1 counter on it or create a 1/1 white Spirit creature token.)",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = attacksSelf,
            instructions = Instructions(
                steps = listOf(
                    Step.Pay(cost = Cost(parts = listOf(mana("{1}{W}"))), payer = Players.You, bind = "paid"),
                    Step.If(
                        condition = Condition.Did(name = "paid"),
                        then = listOf(doing(Effect.Action(action = KeywordAction.Endure(objects = Objects.Self, count = n(1))))),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun wyrmbackLancer() = card(
        name = "Wyrmback Lancer",
        cost = "{3}{W}",
        type = "Creature — Human Soldier",
        pt = "3/3",
        text = """
            Flying
            Mobilize 1 (Whenever this creature attacks, create a tapped and attacking 1/1 red Warrior creature token. Sacrifice it at the beginning of the next end step.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Mobilize(amount = 1),
    )

    @Test
    fun dustvaleEarthshaper() = card(
        name = "Dustvale Earthshaper",
        cost = "{5}{G}",
        type = "Creature — Elephant Druid",
        pt = "4/4",
        text = """
            Reach
            When this creature enters, it endures 3. (Put three +1/+1 counters on it or create a 3/3 white Spirit creature token.)
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(steps = listOf(doing(Effect.Action(action = KeywordAction.Endure(objects = Objects.Self, count = n(3)))))),
        ),
    )

    @Test
    fun bastionKinWarden() = card(
        name = "Bastion Kin-Warden",
        cost = "{1}{W}",
        type = "Creature — Dog Soldier",
        pt = "1/2",
        text = "When this creature enters, it endures 1. (Put a +1/+1 counter on it or create a 1/1 white Spirit creature token.)",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(steps = listOf(doing(Effect.Action(action = KeywordAction.Endure(objects = Objects.Self, count = n(1)))))),
        ),
    )

    @Test
    fun spiritSwornVanguard() = card(
        name = "Spirit-Sworn Vanguard",
        cost = "{4}{G}",
        type = "Creature — Human Soldier",
        pt = "3/2",
        text = "Whenever this creature enters or attacks, it endures 2. (Put two +1/+1 counters on it or create a 2/2 white Spirit creature token.)",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(
                events = listOf(
                    EventPattern.ZoneChange(objects = isSelf, to = Zone.Battlefield),
                    EventPattern.Attack(attacker = isSelf, withOthers = 0),
                ),
            ),
            instructions = Instructions(steps = listOf(doing(Effect.Action(action = KeywordAction.Endure(objects = Objects.Self, count = n(2)))))),
        ),
    )

    @Test
    fun rootlineTender() = card(
        name = "Rootline Tender",
        cost = "{2}{B}",
        type = "Creature — Human Druid",
        pt = "2/1",
        text = """
            Lifelink
            When this creature enters, it endures 1. (Put a +1/+1 counter on it or create a 1/1 white Spirit creature token.)
        """,
    ) parsesTo abilities(
        Keyword.Lifelink,
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(steps = listOf(doing(Effect.Action(action = KeywordAction.Endure(objects = Objects.Self, count = n(1)))))),
        ),
    )

    @Test
    fun kreshakNovice() = card(
        name = "Kreshak Novice",
        cost = "{1}{B}",
        type = "Creature — Human Cleric",
        pt = "2/2",
        text = "{X}{B}, {T}, Pay X life: This creature endures X. Activate only as a sorcery. (Put X +1/+1 counters on it or create an X/X white Spirit creature token.)",
    ) parsesTo abilities(
        Ability.Activated(
            cost = Cost(parts = listOf(mana("{X}{B}"), tap, CostPart.PayLife(amount = Value.Announced(name = "X")))),
            instructions = Instructions(
                steps = listOf(doing(Effect.Action(action = KeywordAction.Endure(objects = Objects.Self, count = Value.Announced(name = "X"))))),
                announced = listOf("X"),
            ),
            rules = asSorcery,
        ),
    )

    @Test
    fun duskbladeCompany() = card(
        name = "Duskblade Company",
        cost = "{2}{B}",
        type = "Creature — Goblin Soldier",
        pt = "1/3",
        text = """
            Deathtouch
            Mobilize 1 (Whenever this creature attacks, create a tapped and attacking 1/1 red Warrior creature token. Sacrifice it at the beginning of the next end step.)
            When this creature enters, surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
        """,
    ) parsesTo abilities(
        Keyword.Deathtouch,
        Keyword.Mobilize(amount = 1),
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(steps = listOf(doing(Effect.Surveil(players = Players.You, count = n(1))))),
        ),
    )

    @Test
    fun crownedVictor() = card(
        name = "Crowned Victor",
        cost = "{2/R}{2/W}{2/B}",
        type = "Creature — Orc Warrior",
        pt = "3/3",
        text = """
            Mobilize 1 (Whenever this creature attacks, create a tapped and attacking 1/1 red Warrior creature token. Sacrifice it at the beginning of the next end step.)
            When this creature enters, target creature gets +1/+0 and gains indestructible until end of turn. (Damage and effects that say "destroy" don't destroy it.)
        """,
    ) parsesTo abilities(
        Keyword.Mobilize(amount = 1),
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(
                targets = listOf(TargetSlot(name = "creature", what = TargetKind.Objects(filter = creature), min = n(1), max = n(1))),
                steps = listOf(
                    doing(
                        Effect.ApplyMechanics(
                            mechanics = listOf(
                                Mechanic.ModifyPowerToughness(power = n(1), toughness = n(0)),
                                Mechanic.GainAbilities(abilities = listOf(Keyword.Indestructible)),
                            ),
                            affects = Affected.Objects(objects = target("creature")),
                            duration = Duration.EndOfTurn,
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun dunecrawlOutrider() = card(
        name = "Dunecrawl Outrider",
        cost = "{3}{B}",
        type = "Creature — Goblin Soldier",
        pt = "2/1",
        text = """
            Menace (This creature can't be blocked except by two or more creatures.)
            When this creature enters, it endures 2. (Put two +1/+1 counters on it or create a 2/2 white Spirit creature token.)
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        Ability.Triggered(
            trigger = entersSelf,
            instructions = Instructions(steps = listOf(doing(Effect.Action(action = KeywordAction.Endure(objects = Objects.Self, count = n(2)))))),
        ),
    )

    @Test
    fun joltBrigade() = card(
        name = "Jolt Brigade",
        cost = "{1}{R}",
        type = "Creature — Goblin Soldier",
        pt = "1/3",
        text = """
            Menace (This creature can't be blocked except by two or more creatures.)
            Mobilize 1 (Whenever this creature attacks, create a tapped and attacking 1/1 red Warrior creature token. Sacrifice it at the beginning of the next end step.)
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        Keyword.Mobilize(amount = 1),
    )

    @Test
    fun pitfallSurveyor() = card(
        name = "Pitfall Surveyor",
        cost = "{1}{B}",
        type = "Creature — Bird Scout",
        pt = "1/3",
        text = """
            Flying
            Whenever this creature attacks, you lose 1 life and this creature endures 1. (Put a +1/+1 counter on it or create a 1/1 white Spirit creature token.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Triggered(
            trigger = attacksSelf,
            instructions = Instructions(
                steps = listOf(
                    doing(Effect.LoseLife(players = Players.You, amount = n(1))),
                    doing(Effect.Action(action = KeywordAction.Endure(objects = Objects.Self, count = n(1)))),
                ),
            ),
        ),
    )

    @Test
    fun arenaHeadliner() = card(
        name = "Arena Headliner",
        cost = "{R}",
        type = "Creature — Goblin Warrior",
        pt = "1/1",
        text = """
            Mobilize 1 (Whenever this creature attacks, create a tapped and attacking 1/1 red Warrior creature token. Sacrifice it at the beginning of the next end step.)
            {1}{R}, Sacrifice this creature: It deals damage equal to the number of creatures you control to target creature.
        """,
    ) parsesTo abilities(
        Keyword.Mobilize(amount = 1),
        Ability.Activated(
            cost = Cost(parts = listOf(mana("{1}{R}"), CostPart.Sacrifice(what = CostObjects.Self))),
            instructions = Instructions(
                targets = listOf(TargetSlot(name = "creature", what = TargetKind.Objects(filter = creature), min = n(1), max = n(1))),
                steps = listOf(
                    doing(
                        Effect.DealDamage(
                            to = Recipients(objects = target("creature")),
                            amount = Value.Count(objects = Objects.Each(filter = and(creature, you))),
                            source = Objects.Self,
                        ),
                    ),
                ),
            ),
            rules = anyTime,
        ),
    )

    @Test
    fun reveredStormchanter() = card(
        name = "Revered Stormchanter",
        cost = "{3}{B}",
        type = "Creature — Orc Cleric",
        pt = "3/3",
        text = """
            Mobilize 1 (Whenever this creature attacks, create a tapped and attacking 1/1 red Warrior creature token. Sacrifice it at the beginning of the next end step.)
            Whenever this creature or another creature you control dies, each opponent loses 1 life and you gain 1 life.
        """,
    ) parsesTo abilities(
        Keyword.Mobilize(amount = 1),
        Ability.Triggered(
            trigger = Trigger.On(
                events = listOf(
                    EventPattern.ZoneChange(
                        objects = or(isSelf, and(not(isSelf), creature, you)),
                        from = Zone.Battlefield,
                        to = Zone.Graveyard,
                    ),
                ),
            ),
            instructions = Instructions(
                steps = listOf(
                    doing(Effect.LoseLife(players = Players.Each(filter = PlayerFilter.Opponent), amount = n(1))),
                    doing(Effect.GainLife(players = Players.You, amount = n(1))),
                ),
            ),
        ),
    )

    @Test
    fun heraldOfTriumph() = card(
        name = "Herald of Triumph",
        cost = "{1}{W}",
        type = "Creature — Human Bard",
        pt = "1/3",
        text = """
            Mobilize 2 (Whenever this creature attacks, create two tapped and attacking 1/1 red Warrior creature tokens. Sacrifice them at the beginning of the next end step.)
            Your opponents can't cast spells during your turn.
        """,
    ) parsesTo abilities(
        Keyword.Mobilize(amount = 2),
        Ability.Static(
            mechanics = listOf(Mechanic.CantCast(spells = ObjectFilter.Any)),
            affects = Affected.Players(players = Players.Each(filter = PlayerFilter.Opponent)),
            condition = Condition.YourTurn,
        ),
    )

    @Test
    fun warDrive() = card(
        name = "War Drive",
        cost = "{3}{R}",
        type = "Enchantment",
        text = """
            Creatures you control get +1/+0.
            Whenever you attack, create a 1/1 red Warrior creature token that's tapped and attacking. Sacrifice it at the beginning of the next end step.
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(Mechanic.ModifyPowerToughness(power = n(1), toughness = n(0))),
            affects = Affected.Objects(objects = Objects.Each(filter = and(creature, you))),
        ),
        Ability.Triggered(
            trigger = Trigger.On(events = listOf(EventPattern.Attack(attacker = you, withOthers = 0)), oneOrMore = true),
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.CreateToken(
                            token = TokenSpec.Defined(
                                face = FaceDefinition(
                                    name = "Warrior Token",
                                    colorIndicator = listOf(Color.Red),
                                    types = listOf(CardType.Creature),
                                    subtypes = listOf(Subtype(name = "Warrior")),
                                    power = PrintedNumber.Fixed(value = 1),
                                    toughness = PrintedNumber.Fixed(value = 1),
                                ),
                            ),
                            count = n(1),
                            controller = Players.You,
                            tapped = true,
                            attacking = AttackTarget.Chosen,
                            bind = "token",
                        ),
                    ),
                    doing(
                        Effect.CreateDelayedTrigger(
                            trigger = Trigger.On(events = listOf(EventPattern.StepBegins(step = TurnStep.End, whose = PlayerFilter.Any))),
                            instructions = Instructions(steps = listOf(doing(Effect.Sacrifice(objects = bound("token"))))),
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun keeperOfTheThicket() = card(
        name = "Keeper of the Thicket",
        cost = "{2}{G}",
        type = "Creature — Hydra",
        pt = "2/2",
        text = """
            At the beginning of your end step, put a +1/+1 counter on this creature.
            Whenever another nontoken creature you control enters, it endures X, where X is the number of counters on this creature. (Put X +1/+1 counters on the creature that entered or create an X/X white Spirit creature token.)
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(events = listOf(EventPattern.StepBegins(step = TurnStep.End, whose = PlayerFilter.You))),
            instructions = Instructions(steps = listOf(doing(Effect.PutCounters(objects = Objects.Self, kind = p1p1, amount = n(1))))),
        ),
        Ability.Triggered(
            trigger = Trigger.On(
                events = listOf(EventPattern.ZoneChange(objects = and(not(isSelf), not(token), creature, you), to = Zone.Battlefield)),
            ),
            instructions = Instructions(
                steps = listOf(
                    doing(
                        Effect.Action(
                            action = KeywordAction.Endure(
                                objects = Objects.EventObject(role = EventRole.After),
                                count = Value.Of(property = NumericProperty.Counters(kind = null), objects = Objects.Self, how = Combine.Single),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun warchiefsVanguard() = card(
        name = "Warchief's Vanguard",
        cost = "{2}{R}",
        type = "Creature — Dog Soldier",
        pt = "*/3",
        text = """
            Mobilize 1 (Whenever this creature attacks, create a tapped and attacking 1/1 red Warrior creature token. Sacrifice it at the beginning of the next end step.)
            Warchief's Vanguard's power is equal to the number of creatures you control.
        """,
    ) parsesTo abilities(
        Keyword.Mobilize(amount = 1),
        Ability.Static(
            mechanics = listOf(Mechanic.SetPowerToughness(power = Value.Count(objects = Objects.Each(filter = and(creature, you))), toughness = null)),
            affects = Affected.Objects(objects = Objects.Self),
            characteristicDefining = true,
        ),
    )

    @Test
    fun ghurakThundersEdict() = card(
        name = "Ghurak, Thunder's Edict",
        cost = "{R}{W}{B}",
        type = "Legendary Creature — Orc Warrior",
        pt = "2/4",
        text = """
            Mobilize 2 (Whenever this creature attacks, create two tapped and attacking 1/1 red Warrior creature tokens. Sacrifice them at the beginning of the next end step.)
            During your end step, Warrior tokens you control have "This token can't be sacrificed."
        """,
    ) parsesTo abilities(
        Keyword.Mobilize(amount = 2),
        Ability.Static(
            mechanics = listOf(
                Mechanic.GainAbilities(
                    abilities = listOf(
                        Ability.Static(mechanics = listOf(Mechanic.CantBeSacrificed), affects = Affected.Objects(objects = Objects.Self)),
                    ),
                ),
            ),
            affects = Affected.Objects(objects = Objects.Each(filter = and(sub("Warrior"), token, you))),
            condition = Condition.And(all = listOf(Condition.YourTurn, Condition.StepIs(step = TurnStep.End))),
        ),
    )
}
