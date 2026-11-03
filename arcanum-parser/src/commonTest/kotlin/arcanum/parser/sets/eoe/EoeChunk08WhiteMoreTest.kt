package arcanum.parser.sets.eoe

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Edge of Eternities chunk 08: more white cards: an opponent exiling a card it may play at a cost, counters for each other creature and artifact, a cage with linked exiled cards, total mana value targets, a conditional activation cost reduction, untapping on an opponent's turn.
 */
class EoeChunk08WhiteMoreTest {

    @Test
    fun glowstallInterrogator() = card(
        name = "Glowstall Interrogator",
        cost = "{W}",
        type = "Creature — Angel Wizard",
        pt = "2/1",
        text = """
            Vigilance
            When this creature enters, each opponent exiles a card from their hand and may play that card for as long as it remains exiled. Each spell cast this way costs {1} more to cast. Each land played this way enters tapped.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        triggered(
            entersSelf,
            Step.ForEach(
                each = Each.Players(players = Players.Each(filter = PlayerFilter.Opponent)),
                bind = "opponent",
                steps = listOf(
                    Step.Choose(
                        choice = Choice.Objects(
                            filter = and(
                                inZone(Zone.Hand),
                                ObjectFilter.OwnedBy(players = PlayerFilter.Is(players = Players.Bound(name = "opponent"))),
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
            Step.ForEach(
                each = Each.Objects(objects = bound("exiled")),
                bind = "card",
                steps = listOf(
                    doing(
                        Effect.ApplyMechanics(
                            mechanics = listOf(
                                Mechanic.MayPlay(
                                    from = Zone.Exile,
                                    how = PlayKind.Play,
                                    holder = PlayerFilter.Is(players = Players.OwnerOf(objects = bound("card"))),
                                    costChange = CostChange.More(mana = ManaCost.parse("{1}")!!, times = n(1)),
                                    landsEnterTapped = true,
                                ),
                            ),
                            affects = Affected.Objects(objects = bound("card")),
                            duration = Duration.Indefinitely,
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun lumenknightVanguard() = card(
        name = "Lumenknight Vanguard",
        cost = "{3}{W}",
        type = "Creature — Human Knight",
        pt = "2/2",
        text = "This creature enters with a +1/+1 counter on it for each other creature and/or artifact you control.",
    ) parsesTo abilities(
        static(
            Mechanic.Replacement(
                event = EventPattern.ZoneChange(objects = isSelf, to = Zone.Battlefield),
                modify = listOf(
                    Modification.EntersWithCounters(
                        kind = p1p1,
                        amount = Value.Count(objects = Objects.Each(filter = and(not(isSelf), or(creature, artifact), you))),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun summitStarprison() = card(
        name = "Summit Starprison",
        cost = "{1}{W}{W}",
        type = "Artifact",
        text = """
            When this artifact enters, exile all artifacts and creatures with mana value 2 or less until this artifact leaves the battlefield.
            {6}{W}{W}: Put each card exiled with this artifact into its owner's graveyard, then create a 2/2 colorless Robot artifact creature token for each card put into a graveyard this way. Sacrifice this artifact.
        """,
    ) parsesTo abilities(
        triggered(
            entersSelf,
            doing(
                Effect.Move(
                    objects = Objects.Each(
                        filter = and(or(artifact, creature), ObjectFilter.ManaValue(comparison = Comparison.AtMost, value = n(2))),
                    ),
                    to = Destination(zone = Zone.Exile),
                    untilLeaves = Objects.Self,
                    postBind = "exiled",
                ),
            ),
            doing(Effect.Remember(key = "exiled", objects = bound("exiled"))),
        ),
        activated(
            cost(mana("{6}{W}{W}")),
            doing(Effect.Move(objects = Objects.Remembered(key = "exiled"), to = Destination(zone = Zone.Graveyard), postBind = "put")),
            doing(
                Effect.CreateToken(
                    token = robot,
                    count = Value.Count(objects = Objects.Where(objects = bound("put"), filter = inZone(Zone.Graveyard))),
                    controller = Players.You,
                ),
            ),
            doing(Effect.Sacrifice(objects = Objects.Self)),
        ),
    )

    @Test
    fun quasarWingAce() = card(
        name = "Quasar Wing Ace",
        cost = "{1}{W}",
        type = "Creature — Human Pilot",
        pt = "1/2",
        text = "When this creature enters, look at the top five cards of your library. You may reveal a Spacecraft card from among them and put it into your hand. Put the rest on the bottom of your library in a random order. If you didn't put a card into your hand this way, put a +1/+1 counter on this creature.",
    ) parsesTo abilities(
        triggered(
            entersSelf,
            doing(
                Effect.LookAt(
                    objects = Objects.LibraryCards(of = Players.You, count = n(5), fromTop = true),
                    players = Players.You,
                    bind = "seen",
                ),
            ),
            Step.Choose(
                choice = Choice.Objects(filter = sub("Spacecraft"), min = n(0), max = n(1), from = bound("seen")),
                bind = "chosen",
                chooser = Players.You,
            ),
            doing(Effect.Reveal(objects = bound("chosen"))),
            doing(Effect.Move(objects = bound("chosen"), to = Destination(zone = Zone.Hand))),
            doing(
                Effect.Move(
                    objects = Objects.Except(base = bound("seen"), minus = bound("chosen")),
                    to = Destination(zone = Zone.Library, libraryPosition = -1, randomOrder = true),
                ),
            ),
            Step.If(
                condition = Condition.Not(condition = Condition.NonEmpty(name = "chosen")),
                then = listOf(doing(Effect.PutCounters(objects = Objects.Self, kind = p1p1, amount = n(1)))),
            ),
        ),
    )

    @Test
    fun gleamingBlow() = card(
        name = "Gleaming Blow",
        cost = "{3}{W}",
        type = "Instant",
        text = "Destroy target artifact or tapped creature. You gain 3 life.",
    ) parsesTo abilities(
        spell(
            doing(Effect.Destroy(objects = target("permanent"))),
            doing(Effect.GainLife(players = Players.You, amount = n(3))),
            targets = listOf(slot("permanent", or(artifact, and(tapped, creature)))),
        ),
    )

    @Test
    fun redirectCircuits() = card(
        name = "Redirect Circuits",
        cost = "{W}",
        type = "Instant",
        text = """
            Choose one —
            • Target artifact or creature gains indestructible until end of turn. (Damage and effects that say "destroy" don't destroy it.)
            • Redirect Circuits deals 2 damage to target tapped creature.
        """,
    ) parsesTo abilities(
        Ability.Spell(
            instructions = Instructions(
                modes = Modes(
                    options = listOf(
                        Mode(
                            instructions = Instructions(
                                targets = listOf(slot("permanent", or(artifact, creature))),
                                steps = listOf(untilEndOfTurn(target("permanent"), has(Keyword.Indestructible))),
                            ),
                        ),
                        Mode(
                            instructions = Instructions(
                                targets = listOf(slot("creature", and(tapped, creature))),
                                steps = listOf(
                                    doing(
                                        Effect.DealDamage(
                                            to = Recipients(objects = target("creature")),
                                            amount = n(2),
                                            source = Objects.Self,
                                        ),
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
    fun searchTheWreckage() = card(
        name = "Search the Wreckage",
        cost = "{2}{W}",
        type = "Sorcery",
        text = "Return up to three target creature cards with total mana value 3 or less from your graveyard to the battlefield. Put a +1/+1 counter on each of them.",
    ) parsesTo abilities(
        spell(
            doing(Effect.Move(objects = target("cards"), to = Destination(zone = Zone.Battlefield), postBind = "returned")),
            doing(Effect.PutCounters(objects = bound("returned"), kind = p1p1, amount = n(1))),
            targets = listOf(
                TargetSlot(
                    name = "cards",
                    what = TargetKind.Objects(filter = and(creature, inZone(Zone.Graveyard), ownedByYou)),
                    min = n(0),
                    max = n(3),
                    totalAtMost = Choice.TotalLimit(property = NumericProperty.ManaValue, atMost = n(3)),
                ),
            ),
        ),
    )

    @Test
    fun riftTear() = card(
        name = "Rift Tear",
        cost = "{W}",
        type = "Enchantment",
        text = "When this enchantment enters, exile target nonland permanent an opponent controls with mana value 2 or less until this enchantment leaves the battlefield.",
    ) parsesTo abilities(
        triggered(
            entersSelf,
            doing(Effect.Move(objects = target("permanent"), to = Destination(zone = Zone.Exile), untilLeaves = Objects.Self)),
            targets = listOf(
                slot(
                    "permanent",
                    and(not(land), permanentFilter, opponents, ObjectFilter.ManaValue(comparison = Comparison.AtMost, value = n(2))),
                ),
            ),
        ),
    )

    @Test
    fun pagesGlowblade() = card(
        name = "Page's Glowblade",
        cost = "{W}",
        type = "Artifact — Equipment",
        text = """
            Flash
            When this Equipment enters, attach it to target creature you control. That creature gains first strike until end of turn.
            Equipped creature gets +1/+0.
            Equip {3}
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(
            entersSelf,
            doing(Effect.Attach(objects = Objects.Self, to = Recipients(objects = target("creature")))),
            untilEndOfTurn(target("creature"), has(Keyword.FirstStrike)),
            targets = listOf(slot("creature", and(creature, you))),
        ),
        static(pt(1, 0), affects = Objects.AttachedTo(of = Objects.Self)),
        Keyword.Equip(cost = cost(mana("{3}"))),
    )

    @Test
    fun dogfighterAce() = card(
        name = "Dogfighter Ace",
        cost = "{1}{W}",
        type = "Creature — Human Pilot",
        pt = "2/2",
        text = "Whenever this creature becomes tapped, surveil 1. (Look at the top card of your library. You may put it into your graveyard.)",
    ) parsesTo abilities(
        triggered(on(EventPattern.Tap(objects = isSelf)), doing(Effect.Surveil(players = Players.You, count = n(1)))),
    )

    @Test
    fun spacedockGuard() = card(
        name = "Spacedock Guard",
        cost = "{W}",
        type = "Artifact Creature — Robot Soldier",
        pt = "1/1",
        text = "{3}{W}, {T}: Tap another target creature. This ability costs {2} less to activate if you control a creature with a +1/+1 counter on it.",
    ) parsesTo abilities(
        activated(
            cost(mana("{3}{W}"), tap),
            doing(Effect.Tap(objects = target("creature"))),
            targets = listOf(slot("creature", and(not(isSelf), creature))),
            rules = ActivationRules(
                timing = Timing.Any,
                activatedBy = PlayerFilter.You,
                costChange = CostChange.Less(
                    mana = ManaCost.parse("{2}")!!,
                    times = Value.Min(
                        parts = listOf(
                            Value.Count(
                                objects = Objects.Each(filter = and(creature, ObjectFilter.HasCounter(kind = p1p1, atLeast = 1), you)),
                            ),
                            n(1),
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun daystarCleric() = card(
        name = "Daystar Cleric",
        cost = "{1}{W}",
        type = "Creature — Human Cleric",
        pt = "3/2",
        text = """
            At the beginning of your end step, if you control two or more tapped creatures, put a +1/+1 counter on target creature you control.
            {2}, Remove a +1/+1 counter from a creature you control: Tap target artifact or creature.
        """,
    ) parsesTo abilities(
        triggered(
            on(EventPattern.StepBegins(step = TurnStep.End, whose = PlayerFilter.You)),
            doing(Effect.PutCounters(objects = target("creature"), kind = p1p1, amount = n(1))),
            targets = listOf(slot("creature", and(creature, you))),
            interveningIf = Condition.Exists(filter = and(tapped, creature, you), atLeast = 2),
        ),
        activated(
            cost(
                mana("{2}"),
                CostPart.RemoveCounters(kind = p1p1, amount = n(1), from = CostObjects.Choose(filter = and(creature, you), count = n(1))),
            ),
            doing(Effect.Tap(objects = target("permanent"))),
            targets = listOf(slot("permanent", or(artifact, creature))),
        ),
    )

    @Test
    fun daystarGlowsmith() = card(
        name = "Daystar Glowsmith",
        cost = "{3}{W}",
        type = "Creature — Human Artificer",
        pt = "3/3",
        text = "Whenever you cast your second spell each turn, put a +1/+1 counter on this creature and draw a card.",
    ) parsesTo abilities(
        triggered(
            Trigger.On(events = listOf(EventPattern.Cast(spell = ObjectFilter.Any, by = PlayerFilter.You)), nthThisTurn = 2),
            doing(Effect.PutCounters(objects = Objects.Self, kind = p1p1, amount = n(1))),
            doing(Effect.Draw(players = Players.You, count = n(1))),
        ),
    )

    @Test
    fun ferventShowing() = card(
        name = "Fervent Showing",
        cost = "{2}{W}",
        type = "Instant",
        text = "Creatures you control get +2/+0 until end of turn. If it's not your turn, untap those creatures.",
    ) parsesTo abilities(
        spell(
            Step.Bind(objects = Objects.Each(filter = and(creature, you)), bind = "those"),
            untilEndOfTurn(bound("those"), pt(2, 0)),
            Step.If(condition = Condition.Not(condition = Condition.YourTurn), then = listOf(doing(Effect.Untap(objects = bound("those"))))),
        ),
    )
}
