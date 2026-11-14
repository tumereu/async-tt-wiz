package arcanum.parser.sets.eoe

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Edge of Eternities chunk 09: blue cards: unblockable 1/1s, surveil X, kicked look-and-pick, triggers on becoming tapped or dealt damage, returning an attacker, greatest mana value among artifacts, spells cast this turn.
 */
class EoeChunk09BlueTest {

    @Test
    fun voidOut() = card(
        name = "Void Out",
        cost = "{U}",
        type = "Instant",
        text = "Counter target artifact or enchantment spell.",
    ) parsesTo abilities(
        spell(
            doing(Effect.Counter(objects = target("spell"))),
            targets = listOf(slot("spell", and(or(artifact, enchantment), spellFilter))),
        ),
    )

    @Test
    fun subatomicShrinker() = card(
        name = "Subatomic Shrinker",
        cost = "{U}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +1/+0.
            Whenever equipped creature attacks, choose up to one target creature. That creature can't be blocked this turn and has base power and toughness 1/1 until end of turn.
            Equip {2}
        """,
    ) parsesTo abilities(
        static(pt(1, 0), affects = Objects.AttachedTo(of = Objects.Self)),
        triggered(
            on(EventPattern.Attack(attacker = ObjectFilter.Is(objects = Objects.AttachedTo(of = Objects.Self)))),
            untilEndOfTurn(target("creature"), Mechanic.CantBeBlocked, Mechanic.SetPowerToughness(power = n(1), toughness = n(1))),
            targets = listOf(slot("creature", creature, min = 0)),
        ),
        Keyword.Equip(cost = cost(mana("{2}"))),
    )

    @Test
    fun neuralUpload() = card(
        name = "Neural Upload",
        cost = "{4}{U}",
        type = "Instant",
        text = "Surveil X, where X is the number of artifacts you control. Then draw three cards. (To surveil X, look at the top X cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)",
    ) parsesTo abilities(
        spell(
            doing(Effect.Surveil(players = Players.You, count = Value.Count(objects = Objects.Each(filter = and(artifact, you))))),
            doing(Effect.Draw(players = Players.You, count = n(3))),
        ),
    )

    @Test
    fun mistshaperMechanic() = card(
        name = "Mistshaper Mechanic",
        cost = "{2}{U}",
        type = "Creature — Jellyfish Artificer",
        pt = "1/4",
        text = """
            Flying
            As long as you control an artifact, this creature gets +1/+0.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        static(pt(1, 0), condition = Condition.Exists(filter = and(artifact, you), atLeast = 1)),
    )

    @Test
    fun readTheConstellations() = card(
        name = "Read the Constellations",
        cost = "{1}{U}",
        type = "Instant",
        text = """
            Kicker {1}{U} (You may pay an additional {1}{U} as you cast this spell.)
            Look at the top X cards of your library, where X is the number of lands you control. Put one of those cards into your hand. If this spell was kicked, put two of those cards into your hand instead. Put the rest on the bottom of your library in a random order.
        """,
    ) parsesTo abilities(
        Keyword.Kicker(costs = listOf(cost(mana("{1}{U}")))),
        spell(
            doing(
                Effect.LookAt(
                    objects = Objects.LibraryCards(
                        of = Players.You,
                        count = Value.Count(objects = Objects.Each(filter = and(land, you))),
                        fromTop = true,
                    ),
                    players = Players.You,
                    bind = "looked",
                ),
            ),
            Step.If(
                condition = Condition.Paid(keyword = Keyword.Kicker.Kind, times = 1, of = Objects.Self),
                then = listOf(
                    Step.Choose(
                        choice = Choice.Objects(filter = ObjectFilter.Any, min = n(2), max = n(2), from = bound("looked")),
                        bind = "chosen",
                        chooser = Players.You,
                    ),
                ),
                otherwise = listOf(
                    Step.Choose(
                        choice = Choice.Objects(filter = ObjectFilter.Any, min = n(1), max = n(1), from = bound("looked")),
                        bind = "chosen",
                        chooser = Players.You,
                    ),
                ),
            ),
            doing(Effect.Move(objects = bound("chosen"), to = Destination(zone = Zone.Hand))),
            doing(
                Effect.Move(
                    objects = Objects.Except(base = bound("looked"), minus = bound("chosen")),
                    to = Destination(zone = Zone.Library, libraryPosition = -1, randomOrder = true),
                ),
            ),
        ),
    )

    @Test
    fun stasisRelic() = card(
        name = "Stasis Relic",
        cost = "{1}{U}",
        type = "Artifact",
        text = """
            When this artifact enters or leaves the battlefield, draw a card.
            {1}{U}, Sacrifice this artifact: Put a stun counter on up to one target tapped creature. (If a permanent with a stun counter would become untapped, remove one from it instead.)
        """,
    ) parsesTo abilities(
        triggered(
            Trigger.On(
                events = listOf(
                    EventPattern.ZoneChange(objects = isSelf, to = Zone.Battlefield),
                    EventPattern.ZoneChange(objects = isSelf, from = Zone.Battlefield),
                ),
            ),
            doing(Effect.Draw(players = Players.You, count = n(1))),
        ),
        activated(
            cost(mana("{1}{U}"), CostPart.Sacrifice(what = CostObjects.Self)),
            doing(Effect.PutCounters(objects = target("creature"), kind = CounterKind.Stun, amount = n(1))),
            targets = listOf(slot("creature", and(tapped, creature), min = 0)),
        ),
    )

    @Test
    fun frostfracture() = card(
        name = "Frostfracture",
        cost = "{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets -5/-0.
            When enchanted creature becomes tapped or is dealt damage, destroy it.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(what = TargetKind.Objects(filter = creature)),
        static(pt(-5, 0), affects = Objects.AttachedTo(of = Objects.Self)),
        triggered(
            Trigger.On(
                events = listOf(
                    EventPattern.Tap(objects = ObjectFilter.Is(objects = Objects.AttachedTo(of = Objects.Self))),
                    EventPattern.Damage(
                        source = ObjectFilter.Any,
                        to = RecipientFilter(objects = ObjectFilter.Is(objects = Objects.AttachedTo(of = Objects.Self))),
                        per = DamageOccurrence.Recipient,
                    ),
                ),
            ),
            doing(Effect.Destroy(objects = Objects.EventObject(role = EventRole.Object))),
        ),
    )

    @Test
    fun unmakingBurst() = card(
        name = "Unmaking Burst",
        cost = "{1}{U}",
        type = "Instant",
        text = "Return target nonland permanent to its owner's hand. If it was attacking, create a 1/1 colorless Drone artifact creature token with flying and \"This token can block only creatures with flying.\"",
    ) parsesTo abilities(
        spell(
            doing(Effect.Move(objects = target("permanent"), to = Destination(zone = Zone.Hand))),
            Step.If(
                condition = Condition.ObjectMatches(objects = target("permanent"), filter = attacking),
                then = listOf(doing(Effect.CreateToken(token = drone, count = n(1), controller = Players.You, bind = "token"))),
            ),
            targets = listOf(slot("permanent", and(not(land), permanentFilter))),
        ),
    )

    @Test
    fun envoyBodyguard() = card(
        name = "Envoy Bodyguard",
        cost = "{1}{U}",
        type = "Artifact Creature — Robot Soldier",
        pt = "0/4",
        text = "This creature gets +X/+0, where X is the greatest mana value among other artifacts you control.",
    ) parsesTo abilities(
        static(
            Mechanic.ModifyPowerToughness(
                power = Value.Of(
                    property = NumericProperty.ManaValue,
                    objects = Objects.Each(filter = and(not(isSelf), artifact, you)),
                    how = Combine.Greatest,
                ),
                toughness = n(0),
            ),
        ),
    )

    @Test
    fun megatempestColossus() = card(
        name = "Megatempest Colossus",
        cost = "{4}{U}",
        type = "Creature — Elemental",
        pt = "4/4",
        text = "This spell costs {3} less to cast if you've cast another spell this turn.",
    ) parsesTo abilities(
        static(
            Mechanic.CostChange(what = isSelf, change = CostChange.Less(mana = ManaCost.parse("{3}")!!, times = n(1))),
            condition = Condition.HappenedThisTurn(
                event = EventPattern.Cast(spell = not(isSelf), by = PlayerFilter.You),
                atLeast = 1,
                measure = Measure.Events,
            ),
        ),
    )

    @Test
    fun drifterWindedge() = card(
        name = "Drifter Windedge",
        cost = "{U}",
        type = "Creature — Jellyfish Warrior",
        pt = "1/1",
        text = """
            Flash
            Flying
            {2}, Sacrifice this creature: Draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Flying,
        activated(cost(mana("{2}"), CostPart.Sacrifice(what = CostObjects.Self)), doing(Effect.Draw(players = Players.You, count = n(1)))),
    )

    @Test
    fun drifterSneak() = card(
        name = "Drifter Sneak",
        cost = "{2}{U}",
        type = "Creature — Jellyfish Rogue",
        pt = "1/3",
        text = """
            This creature can't be blocked if you've cast two or more spells this turn.
            Whenever this creature deals combat damage to a player, draw a card.
        """,
    ) parsesTo abilities(
        static(
            Mechanic.CantBeBlocked,
            condition = Condition.HappenedThisTurn(
                event = EventPattern.Cast(spell = ObjectFilter.Any, by = PlayerFilter.You),
                atLeast = 2,
                measure = Measure.Events,
            ),
        ),
        triggered(
            on(EventPattern.Damage(source = isSelf, to = RecipientFilter(players = PlayerFilter.Any), combat = true)),
            doing(Effect.Draw(players = Players.You, count = n(1))),
        ),
    )

    @Test
    fun drifterGlareScrambler() = card(
        name = "Drifter Glare Scrambler",
        cost = "{1}{U}",
        type = "Artifact — Equipment",
        text = """
            Flash
            When this Equipment enters, attach it to target creature you control. That creature gains hexproof until end of turn. (It can't be the target of spells or abilities your opponents control.)
            Equipped creature gets +1/+2.
            Equip {3}
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(
            entersSelf,
            doing(Effect.Attach(objects = Objects.Self, to = Recipients(objects = target("creature")))),
            untilEndOfTurn(target("creature"), has(Keyword.Hexproof)),
            targets = listOf(slot("creature", and(creature, you))),
        ),
        static(pt(1, 2), affects = Objects.AttachedTo(of = Objects.Self)),
        Keyword.Equip(cost = cost(mana("{3}"))),
    )

    @Test
    fun drifterAgent() = card(
        name = "Drifter Agent",
        cost = "{1}{U}",
        type = "Creature — Jellyfish Rogue",
        pt = "2/1",
        text = "Whenever you cast your second spell each turn, put a +1/+1 counter on this creature.",
    ) parsesTo abilities(
        triggered(
            Trigger.On(events = listOf(EventPattern.Cast(spell = ObjectFilter.Any, by = PlayerFilter.You)), nthThisTurn = 2),
            doing(Effect.PutCounters(objects = Objects.Self, kind = p1p1, amount = n(1))),
        ),
    )

    @Test
    fun adriftInTheDark() = card(
        name = "Adrift in the Dark",
        cost = "{3}{U}",
        type = "Instant",
        text = "Target artifact or creature's owner puts it on their choice of the top or bottom of their library. Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)",
    ) parsesTo abilities(
        spell(
            Step.Choose(
                choice = Choice.Option(options = listOf("top", "bottom")),
                bind = "position",
                chooser = Players.OwnerOf(objects = target("permanent")),
            ),
            Step.If(
                condition = Condition.Chose(name = "position", option = "top"),
                then = listOf(doing(Effect.Move(objects = target("permanent"), to = Destination(zone = Zone.Library, libraryPosition = 0)))),
                otherwise = listOf(
                    doing(Effect.Move(objects = target("permanent"), to = Destination(zone = Zone.Library, libraryPosition = -1))),
                ),
            ),
            doing(Effect.Surveil(players = Players.You, count = n(1))),
            targets = listOf(slot("permanent", or(artifact, creature))),
        ),
    )
}
