package arcanum.parser.sets.tmt

import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.ColorRef
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CounterKind
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.SharedQuality
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.TargetKind
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Teenage Mutant Ninja Turtles chunk 09: blue cards. Card types among spells cast this turn (each as it was cast), drawing as much as the
 * combat damage dealt, "can't be blocked if an artifact entered", countering a spell that targets your artifact or creature, exchanging
 * control of two creatures controlled by different players (CR 701.12a), drawing up to four cards in hand, an artifact or creature
 * becoming a 4/5 artifact creature (layers 4 and 7b), a chosen colour with hexproof from it (CR 702.11d), the colours among your
 * permanents (CR 105.2), returning what entered this turn, an Aura making a 0/1 Turtle with no abilities (CR 205.1b), tap-or-untap, a
 * stun counter (CR 122.1d).
 */
class TmtChunk09BlueTest {
    private val enchanted: Objects = Objects.AttachedTo(Objects.Self)

    // each spell's card types as it was cast (CR 601.2i)
    @Test
    fun wrenFirewallHacktivist() = card(
        name = "Wren, Firewall Hacktivist",
        cost = "{3}{U}",
        type = "Legendary Creature — Human Scientist",
        text = "At the beginning of your end step, draw a card for each card type among spells you've cast this turn.",
        pt = "1/5",
    ) parsesTo abilities(
        triggered(stepEndOfYourTurn, doing(Effect.Draw(Players.You, Value.EventsThisTurn(EventPattern.Cast(ObjectFilter.Any, PlayerFilter.You),
            Measure.Distinct(SharedQuality.CardType))))),
    )

    // CR 120.3: as much damage as was dealt
    @Test
    fun wrenReporterOfTheStrange() = card(
        name = "Wren, Reporter of the Strange",
        cost = "{2}{U}",
        type = "Legendary Creature — Human Detective",
        text = "Whenever Wren deals combat damage to a player, draw that many cards, then discard a card.",
        pt = "2/2",
    ) parsesTo abilities(
        triggered(combatDamageToAPlayer, doing(Effect.Draw(Players.You, Value.EventAmount)), discard(1)),
    )

    @Test
    fun droneSwarmlings() = card(
        name = "Drone Swarmlings",
        cost = "{1}{U}",
        type = "Artifact Creature — Robot Insect",
        text = """
            Flying, vigilance
            When this creature dies, draw a card.
        """,
        pt = "1/1",
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Vigilance,
        triggered(diesSelf, draw(1)),
    )

    @Test
    fun gizmoShellTechie() = card(
        name = "Gizmo, Shell Techie",
        cost = "{3}{U}",
        type = "Legendary Creature — Mutant Ninja Turtle",
        text = "When Gizmo enters, if you control an artifact, draw a card.",
        pt = "3/4",
    ) parsesTo abilities(
        triggered(entersSelf, draw(1), interveningIf = Condition.Exists(and(artifact, you), atLeast = 1)),
    )

    @Test
    fun boltMachineWhisperer() = card(
        name = "Bolt, Machine Whisperer",
        cost = "{2}{U}",
        type = "Legendary Creature — Mutant Ninja Turtle",
        text = """
            Flying
            Whenever an artifact you control enters, put a +1/+1 counter on Bolt.
        """,
        pt = "1/2",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(enters(and(artifact, you)), counters(Objects.Self)),
    )

    // CR 509.1b: checked as blockers are declared; CR 115.7: the spell's targets
    @Test
    fun runawayDroid() = card(
        name = "Runaway Droid",
        cost = "{U}",
        type = "Artifact Creature — Robot Scientist",
        text = """
            This creature can't be blocked if an artifact entered the battlefield under your control this turn.
            {U}, Sacrifice this creature: Counter target spell that targets an artifact or creature you control.
        """,
        pt = "1/1",
    ) parsesTo abilities(
        static(Mechanic.CantBeBlocked, condition = Condition.HappenedThisTurn(EventPattern.ZoneChange(and(artifact, you), to = Zone.Battlefield),
            atLeast = 1, measure = Measure.Events)),
        activated(cost(mana("{U}"), sacrificeSelf), doing(Effect.Counter(target("spell"))),
            targets = listOf(slot("spell", and(spellFilter, ObjectFilter.Targets(objects = Objects.Each(and(or(artifact, creature), you))))))),
    )

    // CR 701.12a: an exchange of control between two players' creatures
    @Test
    fun kumihoDragonsDaughter() = card(
        name = "Kumiho, Dragon's Daughter",
        cost = "{4}{U}{U}",
        type = "Legendary Creature — Fox Warlock Avatar",
        text = """
            Vigilance
            Whenever Kumiho enters or deals combat damage to a player, you may exchange control of two other target creatures controlled by different players.
        """,
        pt = "6/6",
    ) parsesTo abilities(
        Keyword.Vigilance,
        triggered(Trigger.On(listOf(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                EventPattern.Damage(ObjectFilter.Is(Objects.Self), RecipientFilter(players = PlayerFilter.Any), combat = true))),
            Step.May(listOf(doing(Effect.ExchangeControl(target("creature"), target("other creature")))), player = Players.You),
            targets = listOf(slot("creature", and(creature, another)),
                slot("other creature", and(creature, another, not(ObjectFilter.ControlledBy(PlayerFilter.Is(Players.ControllerOf(target("creature")))))))
                    .copy(distinctFrom = listOf("creature")))),
    )

    // CR 702.41a: affinity; CR 603.4: the hand size checked as it triggers and resolves
    @Test
    fun cerebrusMasterMind() = card(
        name = "Cerebrus, Master Mind",
        cost = "{6}{U}{U}",
        type = "Legendary Artifact Creature — Utrom Warrior",
        text = """
            Affinity for artifacts (This spell costs {1} less to cast for each artifact you control.)
            When Cerebrus enters, if you have fewer than four cards in hand, draw cards equal to the difference.
            Cerebrus gets +1/+0 for each other artifact you control.
        """,
        pt = "1/4",
    ) parsesTo abilities(
        Keyword.Affinity(artifact, "artifacts"),
        triggered(entersSelf, doing(Effect.Draw(Players.You, Value.Difference(n(4), Value.HandSize(Players.You)))),
            interveningIf = Condition.Compare(Value.HandSize(Players.You), Comparison.Less, n(4))),
        static(Mechanic.ModifyPowerToughness(count(and(another, artifact, you)), n(0))),
    )

    @Test
    fun chromeshell() = card(
        name = "Chromeshell",
        cost = "{4}{U}",
        type = "Legendary Artifact Creature — Robot Turtle",
        text = """
            When Chromeshell enters, return up to one other target artifact or creature to its owner's hand.
            {R}, Sacrifice another artifact: Put a +1/+1 counter on Chromeshell. He gains menace and haste until end of turn.
        """,
        pt = "4/4",
    ) parsesTo abilities(
        triggered(entersSelf, move(target("permanent"), Zone.Hand), targets = listOf(slot("permanent", and(another, or(artifact, creature)), min = 0))),
        activated(cost(mana("{R}"), sacrificeA(and(another, artifact))), counters(Objects.Self), gains(Objects.Self, Keyword.Menace, Keyword.Haste)),
    )

    // CR 613.1d, 613.4b: types in layer 4, base power and toughness in layer 7b
    @Test
    fun consciousnessUpload() = card(
        name = "Consciousness Upload",
        cost = "{2}{U}",
        type = "Instant",
        text = """
            Until end of turn, target artifact or creature becomes an artifact creature with base power and toughness 4/5.
            Draw a card.
        """,
    ) parsesTo abilities(
        spell(
            untilEndOfTurn(target("permanent"), Mechanic.SetTypes(add = listOf(CardType.Artifact, CardType.Creature)), Mechanic.SetPowerToughness(n(4), n(5))),
            draw(1),
            targets = listOf(slot("permanent", or(artifact, creature)))),
    )

    // CR 702.11d: hexproof from the chosen colour; CR 105.2: the colours among permanents
    @Test
    fun radicalGeckoSkateLegend() = card(
        name = "Radical Gecko, Skate Legend",
        cost = "{1}{U}{U}",
        type = "Legendary Creature — Lizard Mutant",
        text = """
            {1}, Discard a card: Until end of turn, Radical Gecko becomes the color of your choice and gains hexproof from that color.
            Whenever Radical Gecko deals combat damage to a player, draw a card for each color among permanents you control.
        """,
        pt = "2/3",
    ) parsesTo abilities(
        activated(cost(mana("{1}"), discardACard),
            Step.Choose(Choice.Color(), bind = "color", chooser = Players.You),
            untilEndOfTurn(Objects.Self, Mechanic.SetColors(listOf(ColorRef.Chosen("color"))),
                Mechanic.GainAbilities(listOf(Keyword.HexproofFrom(ObjectFilter.OfColor(ColorRef.Chosen("color"))))))),
        triggered(combatDamageToAPlayer, doing(Effect.Draw(Players.You, Value.CountDistinct(Objects.Each(and(permanentFilter, you)), SharedQuality.Color)))),
    )

    @Test
    fun chroniaTemporalApprentice() = card(
        name = "Chronia, Temporal Apprentice",
        cost = "{3}{U}{U}",
        type = "Legendary Creature — Human Wizard",
        text = """
            Flash
            When Chronia enters, return each other nonland permanent that entered this turn to its owner's hand.
        """,
        pt = "4/3",
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(entersSelf, move(Objects.Each(and(another, nonland, permanentFilter,
            ObjectFilter.ThisTurn(EventPattern.ZoneChange(ObjectFilter.Any, to = Zone.Battlefield)))), Zone.Hand)),
    )

    // CR 205.1b: a new creature type replaces the others; layers 4, 6 and 7b
    @Test
    fun devolutionRay() = card(
        name = "Devolution Ray",
        cost = "{2}{U}",
        type = "Enchantment — Aura",
        text = """
            Flash
            Enchant creature
            Enchanted creature is a Turtle with base power and toughness 0/1. It can't attack and loses all abilities. (It also loses all other creature types.)
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Enchant(TargetKind.Objects(creature)),
        static(Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Turtle"))), replaceSubtypes = true),
            Mechanic.SetPowerToughness(n(0), n(1)), Mechanic.CantAttack(), Mechanic.LoseAbilities(), affects = enchanted),
    )

    @Test
    fun gutterSurveillanceCam() = card(
        name = "Gutter Surveillance Cam",
        cost = "{U}",
        type = "Artifact",
        text = """
            Flash
            When this artifact enters or leaves the battlefield, you may tap or untap target creature.
            {3}{U}, Sacrifice this artifact: Draw two cards.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(Trigger.On(listOf(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield))),
            Step.May(listOf(
                Step.Choose(Choice.Option(listOf("tap", "untap")), bind = "tapOrUntap", chooser = Players.You),
                Step.If(Condition.Chose("tapOrUntap", "tap"), then = listOf(tapIt(target("creature"))), otherwise = listOf(untapIt(target("creature"))))),
                player = Players.You),
            targets = listOf(slot("creature", creature))),
        activated(cost(mana("{3}{U}"), sacrificeSelf), draw(2)),
    )

    @Test
    fun buzzwingMadFlyentist() = card(
        name = "Buzzwing, Mad Flyentist",
        cost = "{4}{U}",
        type = "Legendary Creature — Insect Mutant Scientist",
        text = """
            Flying
            When Buzzwing enters, draw a card, then discard a card.
            Islandcycling {2} ({2}, Discard this card: Search your library for an Island card, reveal it, put it into your hand, then shuffle.)
        """,
        pt = "3/4",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, draw(1), discard(1)),
        landcycling("Island"),
    )

    // CR 122.1d: a stun counter
    @Test
    fun brainpodScientists() = card(
        name = "Brainpod Scientists",
        cost = "{2}{U}",
        type = "Artifact Creature — Utrom Robot Scientist",
        text = "When this creature enters, tap up to one target creature and put a stun counter on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)",
        pt = "2/2",
    ) parsesTo abilities(
        triggered(entersSelf, tapIt(target("creature")), counters(target("creature"), 1, CounterKind.Stun), targets = listOf(slot("creature", creature, min = 0))),
    )
}
