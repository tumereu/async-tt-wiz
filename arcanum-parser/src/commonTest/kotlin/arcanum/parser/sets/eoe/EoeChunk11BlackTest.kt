package arcanum.parser.sets.eoe

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Edge of Eternities chunk 11: black cards: modes, sacrifice outlets and triggers, power after a -3/-0, "wasn't dealt damage this turn", X and -X, granted damage triggers, opponents exiling creatures for a linked return, "It's a Skeleton … and has no abilities", X spells.
 */
class EoeChunk11BlackTest {

    @Test
    fun nemesissCharm() = card(
        name = "Nemesis's Charm",
        cost = "{B}{B}{B}",
        type = "Instant",
        text = """
            Choose one —
            • Exile target creature or planeswalker.
            • Return one or two target creature and/or planeswalker cards from your graveyard to your hand.
            • Put two +1/+1 counters on target creature you control. It gains lifelink until end of turn.
        """,
    ) parsesTo abilities(
        Ability.Spell(
            instructions = Instructions(
                modes = Modes(
                    options = listOf(
                        Mode(
                            instructions = Instructions(
                                targets = listOf(slot("permanent", or(creature, ObjectFilter.Type(CardType.Planeswalker)))),
                                steps = listOf(doing(Effect.Move(objects = target("permanent"), to = Destination(zone = Zone.Exile)))),
                            ),
                        ),
                        Mode(
                            instructions = Instructions(
                                targets = listOf(
                                    slot(
                                        "creature and/or planeswalker cards",
                                        and(or(creature, ObjectFilter.Type(CardType.Planeswalker)), inZone(Zone.Graveyard), ownedByYou),
                                        max = 2,
                                    ),
                                ),
                                steps = listOf(
                                    doing(
                                        Effect.Move(
                                            objects = target("creature and/or planeswalker cards"),
                                            to = Destination(zone = Zone.Hand),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                        Mode(
                            instructions = Instructions(
                                targets = listOf(slot("creature", and(creature, you))),
                                steps = listOf(
                                    doing(Effect.PutCounters(objects = target("creature"), kind = p1p1, amount = n(2))),
                                    untilEndOfTurn(target("creature"), has(Keyword.Lifelink)),
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
    fun meteorCreeper() = card(
        name = "Meteor Creeper",
        cost = "{2}{B}",
        type = "Creature — Insect Horror",
        pt = "2/3",
        text = """
            Lifelink
            Whenever this creature attacks, you may sacrifice another creature or artifact. If you do, this creature gets +2/+0 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Lifelink,
        triggered(
            attacksSelf,
            Step.Pay(
                cost = cost(CostPart.Sacrifice(what = CostObjects.Choose(filter = and(not(isSelf), or(creature, artifact)), count = n(1)))),
                payer = Players.You,
                bind = "paid",
            ),
            Step.If(condition = Condition.Did(name = "paid"), then = listOf(untilEndOfTurn(Objects.Self, pt(2, 0)))),
        ),
    )

    @Test
    fun shadowedFortitude() = card(
        name = "Shadowed Fortitude",
        cost = "{1}{B}",
        type = "Instant",
        text = """
            This spell costs {1} less to cast if it targets a blocking creature.
            Target creature gets +2/+0 and gains indestructible until end of turn. (Damage and effects that say "destroy" don't destroy it.)
        """,
    ) parsesTo abilities(
        static(
            Mechanic.CostChange(what = isSelf, change = CostChange.Less(mana = ManaCost.parse("{1}")!!, times = n(1))),
            condition = Condition.ObjectMatches(objects = target("creature"), filter = ObjectFilter.HasStatus(status = Status.Blocking)),
        ),
        spell(untilEndOfTurn(target("creature"), pt(2, 0), has(Keyword.Indestructible)), targets = listOf(slot("creature", creature))),
    )

    @Test
    fun ventTheAirlock() = card(
        name = "Vent the Airlock",
        cost = "{1}{B}",
        type = "Instant",
        text = "Target creature gets -3/-0 until end of turn. Then if that creature's power is 0 or less, destroy it.",
    ) parsesTo abilities(
        spell(
            untilEndOfTurn(target("creature"), pt(-3, 0)),
            Step.If(
                condition = Condition.ObjectMatches(
                    objects = target("creature"),
                    filter = ObjectFilter.Power(comparison = Comparison.AtMost, value = n(0)),
                ),
                then = listOf(doing(Effect.Destroy(objects = target("creature")))),
            ),
            targets = listOf(slot("creature", creature)),
        ),
    )

    @Test
    fun questionableMorsel() = card(
        name = "Questionable Morsel",
        cost = "{2}{B}",
        type = "Artifact — Food",
        text = """
            Flash
            When this artifact enters, up to one target creature gets -3/-3 until end of turn.
            {2}, {T}, Sacrifice this artifact: You gain 3 life.
            {2}, {T}, Sacrifice this artifact: Target opponent loses 3 life.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(entersSelf, untilEndOfTurn(target("creature"), pt(-3, -3)), targets = listOf(slot("creature", creature, min = 0))),
        activated(
            cost(mana("{2}"), tap, CostPart.Sacrifice(what = CostObjects.Self)),
            doing(Effect.GainLife(players = Players.You, amount = n(3))),
        ),
        activated(
            cost(mana("{2}"), tap, CostPart.Sacrifice(what = CostObjects.Self)),
            doing(Effect.LoseLife(players = Players.Target(slot = "opponent"), amount = n(3))),
            targets = listOf(
                TargetSlot(name = "opponent", what = TargetKind.Players(filter = PlayerFilter.Opponent), min = n(1), max = n(1)),
            ),
        ),
    )

    @Test
    fun welcomeTheAbyss() = card(
        name = "Welcome the Abyss",
        cost = "{B}",
        type = "Sorcery",
        text = """
            As an additional cost to cast this spell, sacrifice an artifact or creature.
            Destroy target creature or Spacecraft.
        """,
    ) parsesTo abilities(
        static(
            Mechanic.AdditionalCost(
                cost = cost(CostPart.Sacrifice(what = CostObjects.Choose(filter = or(artifact, creature), count = n(1)))),
            ),
        ),
        spell(doing(Effect.Destroy(objects = target("permanent"))), targets = listOf(slot("permanent", or(creature, sub("Spacecraft"))))),
    )

    @Test
    fun fallenStarsDevotee() = card(
        name = "Fallen Star's Devotee",
        cost = "{2}{B}",
        type = "Creature — Human Wizard",
        pt = "3/1",
        text = "When this creature enters, destroy up to one other target creature. If that creature wasn't dealt damage this turn, its controller draws two cards.",
    ) parsesTo abilities(
        triggered(
            entersSelf,
            doing(Effect.Destroy(objects = target("creature"))),
            Step.If(
                condition = Condition.Not(
                    condition = Condition.ObjectMatches(
                        objects = target("creature"),
                        filter = ObjectFilter.ThisTurn(
                            event = EventPattern.Damage(source = ObjectFilter.Any, to = RecipientFilter(objects = ObjectFilter.Any)),
                            role = EventRole.Object,
                        ),
                    ),
                ),
                then = listOf(doing(Effect.Draw(players = Players.ControllerOf(objects = target("creature")), count = n(2)))),
            ),
            targets = listOf(slot("creature", and(not(isSelf), creature), min = 0)),
        ),
    )

    @Test
    fun gravedgeBruiser() = card(
        name = "Gravedge Bruiser",
        cost = "{3}{B}",
        type = "Creature — Human Soldier",
        pt = "3/4",
        text = "As long as you control an artifact, this creature gets +1/+0 and has deathtouch.",
    ) parsesTo abilities(
        static(pt(1, 0), has(Keyword.Deathtouch), condition = Condition.Exists(filter = and(artifact, you), atLeast = 1)),
    )

    @Test
    fun gravcrush() = card(
        name = "Gravcrush",
        cost = "{3}{B}",
        type = "Instant",
        text = "Exile target creature or Spacecraft.",
    ) parsesTo abilities(
        spell(
            doing(Effect.Move(objects = target("permanent"), to = Destination(zone = Zone.Exile))),
            targets = listOf(slot("permanent", or(creature, sub("Spacecraft")))),
        ),
    )

    @Test
    fun gravjetZealot() = card(
        name = "Gravjet Zealot",
        cost = "{2}{B}",
        type = "Creature — Human Scout",
        pt = "2/1",
        text = """
            Flying
            When this creature dies, create a tapped 2/2 colorless Robot artifact creature token.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(diesSelf, doing(Effect.CreateToken(token = robot, count = n(1), controller = Players.You, tapped = true))),
    )

    @Test
    fun hullgouger() = card(
        name = "Hullgouger",
        cost = "{B}",
        type = "Artifact Creature — Robot Assassin",
        pt = "1/1",
        text = "Deathtouch",
    ) parsesTo abilities(
        Keyword.Deathtouch,
    )

    @Test
    fun lamplessPreacher() = card(
        name = "Lampless Preacher",
        cost = "{1}{B}",
        type = "Creature — Vampire Cleric",
        pt = "2/2",
        text = "Whenever you sacrifice another creature or artifact, put a +1/+1 counter on this creature.",
    ) parsesTo abilities(
        triggered(
            on(EventPattern.Sacrifice(objects = and(not(isSelf), or(creature, artifact), you))),
            doing(Effect.PutCounters(objects = Objects.Self, kind = p1p1, amount = n(1))),
        ),
    )

    @Test
    fun zealotCircuitLeech() = card(
        name = "Zealot Circuit-Leech",
        cost = "{4}{B}{B}",
        type = "Artifact Creature — Nautilus",
        pt = "4/4",
        text = """
            Flying
            When this creature enters, until end of turn, target creature you control gets +X/+0 and target creature an opponent controls gets -0/-X, where X is the number of artifacts you control.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(
            entersSelf,
            untilEndOfTurn(
                target("creature"),
                Mechanic.ModifyPowerToughness(power = Value.Count(objects = Objects.Each(filter = and(artifact, you))), toughness = n(0)),
            ),
            untilEndOfTurn(
                target("second creature"),
                Mechanic.ModifyPowerToughness(
                    power = n(0),
                    toughness = Value.Times(value = Value.Count(objects = Objects.Each(filter = and(artifact, you))), factor = -1),
                ),
            ),
            targets = listOf(slot("creature", and(creature, you)), slot("second creature", and(creature, opponents))),
        ),
    )

    @Test
    fun zealotPicket() = card(
        name = "Zealot Picket",
        cost = "{B}",
        type = "Artifact Creature — Robot",
        pt = "4/1",
        text = "Defender",
    ) parsesTo abilities(
        Keyword.Defender,
    )

    @Test
    fun dirgeObelisk() = card(
        name = "Dirge Obelisk",
        cost = "{2}{B}",
        type = "Artifact",
        text = "{T}: Until end of turn, target creature gains \"Whenever this creature is dealt damage, you draw that many cards and lose that much life.\" That creature's controller may have this artifact deal 1 damage to it. Activate only as a sorcery.",
    ) parsesTo abilities(
        activated(
            cost(tap),
            untilEndOfTurn(
                target("creature"),
                has(
                    triggered(
                        on(EventPattern.Damage(source = ObjectFilter.Any, to = RecipientFilter(objects = isSelf), per = DamageOccurrence.Recipient)),
                        doing(Effect.Draw(players = Players.You, count = Value.EventAmount)),
                        doing(Effect.LoseLife(players = Players.You, amount = Value.EventAmount)),
                    ),
                ),
            ),
            Step.May(
                steps = listOf(
                    doing(Effect.DealDamage(to = Recipients(objects = target("creature")), amount = n(1), source = Objects.Self)),
                ),
                player = Players.ControllerOf(objects = target("creature")),
            ),
            targets = listOf(slot("creature", creature)),
            rules = asSorcery,
        ),
    )

    @Test
    fun nerezzaTheGreatHollow() = card(
        name = "Nerezza, the Great Hollow",
        cost = "{2}{B}{B}",
        type = "Legendary Enchantment",
        text = """
            Whenever a creature you control dies, each opponent chooses a creature they control and exiles it.
            At the beginning of your end step, if a player controls no creatures, sacrifice Nerezza, then put a creature card exiled with it onto the battlefield under your control with two additional +1/+1 counters on it.
        """,
    ) parsesTo abilities(
        triggered(
            on(EventPattern.ZoneChange(objects = and(creature, you), from = Zone.Battlefield, to = Zone.Graveyard)),
            Step.ForEach(
                each = Each.Players(players = Players.Each(filter = PlayerFilter.Opponent)),
                bind = "opponent",
                steps = listOf(
                    Step.Choose(
                        choice = Choice.Objects(
                            filter = and(
                                creature,
                                ObjectFilter.ControlledBy(players = PlayerFilter.Is(players = Players.Bound(name = "opponent"))),
                            ),
                            min = n(1),
                            max = n(1),
                        ),
                        bind = "chosen",
                        chooser = Players.Bound(name = "opponent"),
                    ),
                    doing(Effect.Move(objects = bound("chosen"), to = Destination(zone = Zone.Exile), postBind = "exiled")),
                ),
                together = true,
            ),
            doing(Effect.Remember(key = "exiled", objects = bound("exiled"))),
        ),
        triggered(
            on(EventPattern.StepBegins(step = TurnStep.End, whose = PlayerFilter.You)),
            doing(Effect.Sacrifice(objects = Objects.Self)),
            Step.Choose(
                choice = Choice.Objects(filter = creature, min = n(1), max = n(1), from = Objects.Remembered(key = "exiled")),
                bind = "chosen",
                chooser = Players.You,
            ),
            doing(
                Effect.Move(
                    objects = bound("chosen"),
                    to = Destination(
                        zone = Zone.Battlefield,
                        controller = Players.You,
                        counters = listOf(CountersToPut(kind = p1p1, amount = n(2))),
                    ),
                ),
            ),
            interveningIf = Condition.PlayerExists(
                filter = PlayerFilter.Where(
                    condition = Condition.Not(
                        condition = Condition.Exists(
                            filter = and(creature, ObjectFilter.ControlledBy(players = PlayerFilter.Is(players = Players.Tested))),
                            atLeast = 1,
                        ),
                    ),
                ),
                atLeast = 1,
            ),
        ),
    )

    @Test
    fun duskWrecker() = card(
        name = "Dusk Wrecker",
        cost = "{1}{B}",
        type = "Creature — Human Rogue",
        pt = "4/1",
        text = """
            Menace
            Ward—Discard a card.
            Whenever this creature attacks, put a +1/+1 counter on target creature an opponent controls.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        Keyword.Ward(cost = cost(CostPart.Discard(what = CostObjects.Choose(filter = cardFilter, count = n(1))))),
        triggered(
            attacksSelf,
            doing(Effect.PutCounters(objects = target("creature"), kind = p1p1, amount = n(1))),
            targets = listOf(slot("creature", and(creature, opponents))),
        ),
    )

    @Test
    fun hiveThinner() = card(
        name = "Hive Thinner",
        cost = "{3}{B}",
        type = "Creature — Insect Warrior",
        pt = "2/4",
        text = """
            Flying
            Whenever this creature becomes tapped, you may sacrifice another creature or artifact. If you do, draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(
            on(EventPattern.Tap(objects = isSelf)),
            Step.Pay(
                cost = cost(CostPart.Sacrifice(what = CostObjects.Choose(filter = and(not(isSelf), or(creature, artifact)), count = n(1)))),
                payer = Players.You,
                bind = "paid",
            ),
            Step.If(condition = Condition.Did(name = "paid"), then = listOf(doing(Effect.Draw(players = Players.You, count = n(1))))),
        ),
    )

    @Test
    fun shadowcollarFanatic() = card(
        name = "Shadowcollar Fanatic",
        cost = "{1}{B}",
        type = "Creature — Human Cleric",
        pt = "3/2",
        text = "Sacrifice another creature or artifact: Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)",
    ) parsesTo abilities(
        activated(
            cost(CostPart.Sacrifice(what = CostObjects.Choose(filter = and(not(isSelf), or(creature, artifact)), count = n(1)))),
            doing(Effect.Surveil(players = Players.You, count = n(1))),
        ),
    )

    @Test
    fun plagueScarab() = card(
        name = "Plague Scarab",
        cost = "{1}{B}",
        type = "Artifact Creature — Insect",
        pt = "1/1",
        text = "When this creature enters, each opponent discards a card.",
    ) parsesTo abilities(
        triggered(
            entersSelf,
            doing(Effect.Discard(players = Players.Each(filter = PlayerFilter.Opponent), count = n(1), filter = ObjectFilter.Any)),
        ),
    )

    @Test
    fun castOutByBallot() = card(
        name = "Cast Out by Ballot",
        cost = "{3}{B}",
        type = "Sorcery",
        text = """
            Convoke (Your creatures can help cast this spell. Each creature you tap while casting this spell pays for {1} or one mana of that creature's color.)
            Destroy target creature.
        """,
    ) parsesTo abilities(
        Keyword.Convoke,
        spell(doing(Effect.Destroy(objects = target("creature"))), targets = listOf(slot("creature", creature))),
    )

    @Test
    fun zhuRavelBonechorister() = card(
        name = "Zhu-Ravel, Bonechorister",
        cost = "{1}{B}{B}",
        type = "Legendary Creature — Human Wizard",
        pt = "2/3",
        text = "{T}: Return target creature card from your graveyard to the battlefield. It's a Skeleton in addition to its other types and has no abilities. Activate only as a sorcery.",
    ) parsesTo abilities(
        activated(
            cost(tap),
            doing(
                Effect.Move(
                    objects = target("card"),
                    to = Destination(
                        zone = Zone.Battlefield,
                        entersAs = listOf(
                            Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(subtype = Subtype("Skeleton")))),
                            Mechanic.LoseAbilities(),
                        ),
                    ),
                ),
            ),
            targets = listOf(slot("card", and(creature, inZone(Zone.Graveyard), ownedByYou))),
            rules = asSorcery,
        ),
    )

    @Test
    fun nullPointRequiem() = card(
        name = "Null Point Requiem",
        cost = "{X}{B}",
        type = "Sorcery",
        text = "Destroy all creatures with toughness X or less. You lose X life. If X is 6 or more, return a creature card put into a graveyard this way to the battlefield under your control.",
    ) parsesTo abilities(
        spell(
            doing(
                Effect.Destroy(
                    objects = Objects.Each(
                        filter = and(creature, ObjectFilter.Toughness(comparison = Comparison.AtMost, value = Value.Announced(name = "X"))),
                    ),
                    postBind = "destroyed",
                ),
            ),
            doing(Effect.LoseLife(players = Players.You, amount = Value.Announced(name = "X"))),
            Step.If(
                condition = Condition.Compare(left = Value.Announced(name = "X"), comparison = Comparison.AtLeast, right = n(6)),
                then = listOf(
                    Step.Choose(
                        choice = Choice.Objects(
                            filter = and(creature, inZone(Zone.Graveyard)),
                            min = n(1),
                            max = n(1),
                            from = bound("destroyed"),
                        ),
                        bind = "chosen",
                        chooser = Players.You,
                    ),
                    doing(Effect.Move(objects = bound("chosen"), to = Destination(zone = Zone.Battlefield, controller = Players.You))),
                ),
            ),
            announced = listOf("X"),
        ),
    )
}
