package arcanum.parser.sets.eoe

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Edge of Eternities chunk 10: more blue cards: artifact triggers once each turn, cost reductions during your turn and for the second spell, mana for spells not cast from the hand, token copies instead, -3/-0 until your next turn, a reflexive trigger, control Auras, mana spent compared with mana value, free first spells.
 */
class EoeChunk10BlueMoreTest {

    @Test
    fun automatonFabricator() = card(
        name = "Automaton Fabricator",
        cost = "{4}{U}",
        type = "Artifact Creature — Robot Artificer",
        pt = "4/4",
        text = "Whenever another artifact you control enters, create a 2/2 colorless Robot artifact creature token. This ability triggers only once each turn.",
    ) parsesTo abilities(
        triggered(
            on(EventPattern.ZoneChange(objects = and(not(isSelf), artifact, you), to = Zone.Battlefield)),
            doing(Effect.CreateToken(token = robot, count = n(1), controller = Players.You)),
            perTurn = 1,
        ),
    )

    @Test
    fun automatonHelmsman() = card(
        name = "Automaton Helmsman",
        cost = "{1}{U}",
        type = "Artifact Creature — Robot Pilot",
        pt = "2/1",
        text = "Whenever this creature becomes tapped, draw a card, then discard a card.",
    ) parsesTo abilities(
        triggered(
            on(EventPattern.Tap(objects = isSelf)),
            doing(Effect.Draw(players = Players.You, count = n(1))),
            doing(Effect.Discard(players = Players.You, count = n(1), filter = ObjectFilter.Any)),
        ),
    )

    @Test
    fun automatonBulwark() = card(
        name = "Automaton Bulwark",
        cost = "{1}{U}",
        type = "Artifact Creature — Robot Soldier",
        pt = "3/2",
        text = """
            Defender
            As long as an artifact entered the battlefield under your control this turn, this creature can attack as though it didn't have defender.
        """,
    ) parsesTo abilities(
        Keyword.Defender,
        static(
            Mechanic.CanAttackDespiteDefender,
            condition = Condition.HappenedThisTurn(
                event = EventPattern.ZoneChange(objects = and(artifact, you), to = Zone.Battlefield),
                atLeast = 1,
                measure = Measure.Events,
            ),
        ),
    )

    @Test
    fun mindTuning() = card(
        name = "Mind Tuning",
        cost = "{1}{U}",
        type = "Instant",
        text = """
            This spell costs {1} less to cast during your turn.
            Tap target artifact or creature.
            Draw a card.
        """,
    ) parsesTo abilities(
        static(
            Mechanic.CostChange(what = isSelf, change = CostChange.Less(mana = ManaCost.parse("{1}")!!, times = n(1))),
            condition = Condition.YourTurn,
        ),
        spell(
            doing(Effect.Tap(objects = target("permanent"))),
            doing(Effect.Draw(players = Players.You, count = n(1))),
            targets = listOf(slot("permanent", or(artifact, creature))),
        ),
    )

    @Test
    fun quellanTheTrustedHand() = card(
        name = "Qu'ellan, the Trusted Hand",
        cost = "{3}{U}{U}",
        type = "Legendary Creature — Jellyfish Advisor",
        pt = "3/4",
        text = """
            Flying
            You may look at the top card of your library any time.
            You may cast artifact spells from the top of your library.
            Artifacts you control have "{T}: Add {U}. Spend this mana only to cast a spell from anywhere other than your hand."
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        playerStatic(Mechanic.MayLookAt(objects = Objects.LibraryCards(of = Players.You, count = n(1), fromTop = true))),
        static(
            Mechanic.MayPlay(from = Zone.Library, how = PlayKind.Cast, topOnly = true, holder = PlayerFilter.You),
            affects = Objects.Each(filter = and(artifact, inZone(Zone.Library), ownedByYou)),
        ),
        static(
            has(
                activated(
                    cost(tap),
                    doing(
                        Effect.AddMana(
                            mana = ManaProduction(
                                kind = ManaKind.Fixed(mana = ManaCost.parse("{U}")!!),
                                restriction = and(spellFilter, not(ObjectFilter.WasCast(from = Zone.Hand))),
                            ),
                            players = Players.You,
                        ),
                    ),
                ),
            ),
            affects = Objects.Each(filter = and(artifact, you)),
        ),
    )

    @Test
    fun lunarContemplation() = card(
        name = "Lunar Contemplation",
        cost = "{2}{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant artifact or creature you control
            The first time you would create one or more tokens each turn, you may instead create that many tokens that are copies of enchanted permanent.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(what = TargetKind.Objects(filter = and(or(artifact, creature), you))),
        static(
            Mechanic.Replacement(
                event = EventPattern.CreateToken(tokens = you),
                modify = listOf(Modification.CreateInstead(token = TokenSpec.CopyOf(objects = Objects.AttachedTo(of = Objects.Self)))),
                optional = true,
            ),
            condition = Condition.Not(
                condition = Condition.HappenedThisTurn(
                    event = EventPattern.CreateToken(tokens = you),
                    atLeast = 1,
                    measure = Measure.Events,
                ),
            ),
        ),
    )

    @Test
    fun mawOfTheTempest() = card(
        name = "Maw of the Tempest",
        cost = "{6}{U}",
        type = "Creature — Elemental",
        pt = "6/6",
        text = """
            Flying
            Ward {2} (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays {2}.)
            When this creature enters, creatures your opponents control get -3/-0 until your next turn.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Ward(cost = cost(mana("{2}"))),
        triggered(
            entersSelf,
            doing(
                Effect.ApplyMechanics(
                    mechanics = listOf(pt(-3, 0)),
                    affects = Affected.Objects(objects = Objects.Each(filter = and(creature, opponents))),
                    duration = Duration.UntilYourNextTurn,
                ),
            ),
        ),
    )

    @Test
    fun microframeWatcher() = card(
        name = "Microframe Watcher",
        cost = "{2}{U}",
        type = "Artifact Creature — Robot",
        pt = "3/2",
        text = "Whenever this creature becomes tapped, untap another target permanent. This ability triggers only once each turn.",
    ) parsesTo abilities(
        triggered(
            on(EventPattern.Tap(objects = isSelf)),
            doing(Effect.Untap(objects = target("permanent"))),
            targets = listOf(slot("permanent", and(not(isSelf), permanentFilter))),
            perTurn = 1,
        ),
    )

    @Test
    fun siftForSalvage() = card(
        name = "Sift for Salvage",
        cost = "{3}{U}",
        type = "Instant",
        text = """
            Choose one or both —
            • Search your library for an artifact card, reveal it, put it into your hand, then shuffle.
            • Return target artifact card from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        Ability.Spell(
            instructions = Instructions(
                modes = Modes(
                    options = listOf(
                        Mode(
                            instructions = Instructions(
                                steps = listOf(
                                    doing(
                                        Effect.Search(
                                            library = Players.You,
                                            filter = artifact,
                                            max = n(1),
                                            reveal = true,
                                            zones = listOf(Zone.Library),
                                            bind = "found",
                                            searcher = Players.You,
                                        ),
                                    ),
                                    doing(Effect.Move(objects = bound("found"), to = Destination(zone = Zone.Hand))),
                                    doing(Effect.Shuffle(library = Players.You)),
                                ),
                            ),
                        ),
                        Mode(
                            instructions = Instructions(
                                targets = listOf(slot("card", and(artifact, inZone(Zone.Graveyard), ownedByYou))),
                                steps = listOf(doing(Effect.Move(objects = target("card"), to = Destination(zone = Zone.Hand)))),
                            ),
                        ),
                    ),
                    min = 1,
                    max = 2,
                    chooser = Players.You,
                ),
            ),
        ),
    )

    @Test
    fun selfbuiltAutomaton() = card(
        name = "Selfbuilt Automaton",
        cost = "{3}{U}",
        type = "Artifact Creature — Robot Artificer",
        pt = "3/4",
        text = "When this creature enters, you may sacrifice an artifact. When you do, put a +1/+1 counter on target creature and draw a card.",
    ) parsesTo abilities(
        triggered(
            entersSelf,
            Step.Pay(
                cost = cost(CostPart.Sacrifice(what = CostObjects.Choose(filter = artifact, count = n(1)))),
                payer = Players.You,
                bind = "paid",
            ),
            Step.If(
                condition = Condition.Did(name = "paid"),
                then = listOf(
                    doing(
                        Effect.ReflexiveTrigger(
                            instructions = Instructions(
                                targets = listOf(slot("creature", creature)),
                                steps = listOf(
                                    doing(Effect.PutCounters(objects = target("creature"), kind = p1p1, amount = n(1))),
                                    doing(Effect.Draw(players = Players.You, count = n(1))),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun ironflockController() = card(
        name = "Ironflock Controller",
        cost = "{1}{U}",
        type = "Artifact Creature — Robot Soldier",
        pt = "1/1",
        text = """
            Flying
            {T}: Add {U}. Spend this mana only to cast an artifact spell.
            {T}: Add {U}{U}. Spend this mana only to activate abilities of artifact sources.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        activated(
            cost(tap),
            doing(
                Effect.AddMana(
                    mana = ManaProduction(kind = ManaKind.Fixed(mana = ManaCost.parse("{U}")!!), restriction = and(spellFilter, artifact)),
                    players = Players.You,
                ),
            ),
        ),
        activated(
            cost(tap),
            doing(
                Effect.AddMana(
                    mana = ManaProduction(
                        kind = ManaKind.Fixed(mana = ManaCost.parse("{U}{U}")!!),
                        restriction = ObjectFilter.AbilityOf(kind = AbilityKind.Activated, source = artifact),
                    ),
                    players = Players.You,
                ),
            ),
        ),
    )

    @Test
    fun gravityTether() = card(
        name = "Gravity Tether",
        cost = "{2}{U}{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature or Spacecraft
            When this Aura enters, tap enchanted permanent.
            You control enchanted permanent.
            Enchanted permanent doesn't untap during its controller's untap step.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(what = TargetKind.Objects(filter = or(creature, sub("Spacecraft")))),
        triggered(entersSelf, doing(Effect.Tap(objects = Objects.AttachedTo(of = Objects.Self)))),
        static(Mechanic.ControlledBy(players = Players.You), affects = Objects.AttachedTo(of = Objects.Self)),
        static(Mechanic.DoesntUntap, affects = Objects.AttachedTo(of = Objects.Self)),
    )

    @Test
    fun unspool() = card(
        name = "Unspool",
        cost = "{1}{U}{U}",
        type = "Instant",
        text = "Counter target spell. If the amount of mana spent to cast that spell was less than its mana value, you draw a card.",
    ) parsesTo abilities(
        spell(
            doing(Effect.Counter(objects = target("spell"))),
            Step.If(
                condition = Condition.Compare(
                    left = Value.ManaSpent(of = target("spell")),
                    comparison = Comparison.Less,
                    right = Value.Of(property = NumericProperty.ManaValue, objects = target("spell"), how = Combine.Single),
                ),
                then = listOf(doing(Effect.Draw(players = Players.You, count = n(1)))),
            ),
            targets = listOf(slot("spell", spellFilter)),
        ),
    )

    @Test
    fun maeltharMindbender() = card(
        name = "Maelthar Mindbender",
        cost = "{2}{U}",
        type = "Creature — Jellyfish Scientist",
        pt = "2/4",
        text = "The second spell you cast each turn costs {2} less to cast.",
    ) parsesTo abilities(
        static(
            Mechanic.CostChange(
                what = and(ObjectFilter.NthSpellThisTurn(spells = ObjectFilter.Any, n = 2), you),
                change = CostChange.Less(mana = ManaCost.parse("{2}")!!, times = n(1)),
            ),
        ),
    )

    @Test
    fun loomstriding() = card(
        name = "Loomstriding",
        cost = "{4}{U}{U}",
        type = "Enchantment",
        text = """
            When this enchantment enters, if you cast it, shuffle your hand and graveyard into your library, then draw seven cards.
            The first spell each player casts during each of their turns may be cast without paying its mana cost.
        """,
    ) parsesTo abilities(
        triggered(
            entersSelf,
            doing(
                Effect.Move(
                    objects = Objects.Each(filter = and(or(inZone(Zone.Hand), inZone(Zone.Graveyard)), ownedByYou)),
                    to = Destination(zone = Zone.Library),
                ),
            ),
            doing(Effect.Shuffle(library = Players.You)),
            doing(Effect.Draw(players = Players.You, count = n(7))),
            interveningIf = Condition.CastFrom(zone = null, of = Objects.Self),
        ),
        static(
            Mechanic.MayPlay(
                from = Zone.Hand,
                how = PlayKind.Cast,
                withoutPaying = true,
                holder = PlayerFilter.And(
                    all = listOf(
                        PlayerFilter.Is(players = Players.Active),
                        PlayerFilter.Not(
                            filter = PlayerFilter.ThisTurn(event = EventPattern.Cast(spell = ObjectFilter.Any, by = PlayerFilter.Any)),
                        ),
                    ),
                ),
            ),
            affects = Objects.Each(
                filter = and(inZone(Zone.Hand), ObjectFilter.OwnedBy(players = PlayerFilter.Is(players = Players.Holder))),
            ),
        ),
    )
}
