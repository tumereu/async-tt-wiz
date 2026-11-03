package arcanum.parser.sets.sos

import arcanum.api.Choice
import arcanum.api.ColorRef
import arcanum.api.Condition
import arcanum.api.CopyException
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Doubled
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Keyword
import arcanum.api.KeywordAction
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.SharedQuality
import arcanum.api.Step
import arcanum.api.SubtypeRef
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.api.Color
import arcanum.api.Subtype
import arcanum.api.CardType
import arcanum.api.Comparison
import arcanum.parser.cube.abilities
import arcanum.parser.cube.anyOneColor
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Secrets of Strixhaven chunk 04: increment (CR 702.191, a keyword the shared table writes out), "whenever one or more +1/+1 counters are
 * put on" (one trigger per event, CR 122.6), paying {X} as a trigger resolves and a reflexive trigger moving X counters (CR 603.12), stun
 * counters (CR 122.1d), entering with three minus X stun counters, Fractal tokens with counters, a creature becoming a Fractal with base
 * P/T X plus 1 (layers 4, 5, 7b), doubling counters (CR 701.10e), a copy that is also a 0/0 Fractal creature (CR 707.9b), counters
 * moved to a token from a creature that died (CR 603.10a), and counters named by the cards (growth, CR 122.1).
 */
class SosChunk04IncrementStunAndFractalsTest {
    private val countersPutOnSelf: Trigger = Trigger.On(EventPattern.CountersPut(ObjectFilter.Is(Objects.Self), p1p1), oneOrMore = true)
    private fun tokenWithCounters(amount: Value) = listOf(create(fractal, bind = "token"), counters(Objects.Bound("token"), amount))
    private val growth = CounterKind.Named("growth")

    // CR 702.191a: increment; CR 122.6: one or more counters put at once is one event
    @Test
    fun ponderousDocent() = card(
        name = "Ponderous Docent",
        cost = "{1}{U}{U}",
        type = "Creature — Human Wizard",
        pt = "0/2",
        text = """
            Increment (Whenever you cast a spell, if the amount of mana you spent is greater than this creature's power or toughness, put a +1/+1 counter on this creature.)
            Whenever one or more +1/+1 counters are put on this creature, draw a card.
        """,
    ) parsesTo abilities(Keyword.Increment, triggered(countersPutOnSelf, draw(1)))

    // CR 603.12: "When you do" puts a reflexive trigger on the Stack, its target chosen then; X is chosen as {X} is paid
    @Test
    fun proverOfThePeripheral() = card(
        name = "Prover of the Peripheral",
        cost = "{1}{U}",
        type = "Creature — Djinn Wizard",
        pt = "1/1",
        text = """
            Increment (Whenever you cast a spell, if the amount of mana you spent is greater than this creature's power or toughness, put a +1/+1 counter on this creature.)
            At the beginning of combat on your turn, you may pay {X}. When you do, move X +1/+1 counters from this creature onto another target creature.
        """,
    ) parsesTo abilities(
        Keyword.Increment,
        triggered(
            beginningOfCombatOnYourTurn,
            Step.Choose(Choice.Number(n(0)), bind = "X", chooser = Players.You),
            Step.Pay(cost(CostPart.Mana(ManaCost.parse("{X}")!!, x = Value.Bound("X"))), payer = Players.You, bind = "paid"),
            ifThen(Condition.Did("paid"), reflexive(doing(Effect.MoveCounters(Objects.Self, target("creature"), p1p1, Value.Bound("X"))),
                targets = listOf(slot("creature", and(another, creature))))),
        ),
    )

    @Test
    fun primerTallykeeper() = card(
        name = "Primer Tallykeeper",
        cost = "{2}{U}",
        type = "Creature — Frog Wizard",
        pt = "0/3",
        text = """
            Increment (Whenever you cast a spell, if the amount of mana you spent is greater than this creature's power or toughness, put a +1/+1 counter on this creature.)
            When this creature enters, surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
        """,
    ) parsesTo abilities(Keyword.Increment, triggered(entersSelf, doing(Effect.Surveil(Players.You, n(2)))))

    // CR 603.10a: the counters it had as it last existed
    @Test
    fun eagerAmplifier() = card(
        name = "Eager Amplifier",
        cost = "{G}",
        type = "Creature — Turtle Wizard",
        pt = "1/1",
        text = """
            Increment (Whenever you cast a spell, if the amount of mana you spent is greater than this creature's power or toughness, put a +1/+1 counter on this creature.)
            When this creature dies, if it had one or more counters on it, create a 0/0 green and blue Fractal creature token, then put this creature's counters on that token.
        """,
    ) parsesTo abilities(
        Keyword.Increment,
        triggered(
            diesSelf,
            create(fractal, bind = "token"),
            doing(Effect.PutCountersOf(Objects.Bound("token"), of = Objects.EventObject(EventRole.Before))),
            interveningIf = Condition.ObjectMatches(Objects.EventObject(EventRole.Before), ObjectFilter.HasCounter(null, atLeast = 1)),
        ),
    )

    @Test
    fun ravenousLongneck() = card(
        name = "Ravenous Longneck",
        cost = "{3}{G}",
        type = "Creature — Giraffe",
        pt = "3/4",
        text = """
            Reach
            Increment (Whenever you cast a spell, if the amount of mana you spent is greater than this creature's power or toughness, put a +1/+1 counter on this creature.)
        """,
    ) parsesTo abilities(Keyword.Reach, Keyword.Increment)

    // CR 605.1a: a mana ability; the amount is its power as it resolves
    @Test
    fun hedgeworkOrator() = card(
        name = "Hedgework Orator",
        cost = "{2}{G}",
        type = "Creature — Elf Druid",
        pt = "1/2",
        text = """
            Increment (Whenever you cast a spell, if the amount of mana you spent is greater than this creature's power or toughness, put a +1/+1 counter on this creature.)
            {T}: Add an amount of {G} equal to this creature's power.
        """,
    ) parsesTo abilities(
        Keyword.Increment,
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{G}")!!, times = power(Objects.Self))), Players.You))),
    )

    // CR 605.1b: triggered by counters, not by a mana ability, so it uses the Stack
    @Test
    fun wennaKeenProjector() = card(
        name = "Wenna, Keen Projector",
        cost = "{2}{G}{U}",
        type = "Legendary Creature — Frog Druid",
        pt = "1/4",
        text = """
            Increment (Whenever you cast a spell, if the amount of mana you spent is greater than this creature's power or toughness, put a +1/+1 counter on this creature.)
            Whenever one or more +1/+1 counters are put on Wenna, add one mana of any color.
            {X}, {T}: Create a 0/0 green and blue Fractal creature token and put X +1/+1 counters on it.
        """,
    ) parsesTo abilities(
        Keyword.Increment,
        triggered(countersPutOnSelf, doing(Effect.AddMana(ManaProduction(anyOneColor(1)), Players.You))),
        arcanum.api.Ability.Activated(cost(mana("{X}"), tap), arcanum.api.Instructions(steps = tokenWithCounters(x), announced = listOf("X")), anyTime),
    )

    @Test
    fun cubicSwarm() = card(
        name = "Cubic Swarm",
        cost = "{G}{U}",
        type = "Creature — Insect",
        pt = "1/1",
        text = """
            Flash
            Flying, trample
            Increment (Whenever you cast a spell, if the amount of mana you spent is greater than this creature's power or toughness, put a +1/+1 counter on this creature.)
        """,
    ) parsesTo abilities(Keyword.Flash, Keyword.Flying, Keyword.Trample, Keyword.Increment)

    // CR 603.4: checked as each end step begins and as it resolves; the counters put by your spells and abilities
    @Test
    fun fractalWarden() = card(
        name = "Fractal Warden",
        cost = "{3}{G}{U}",
        type = "Creature — Elf Wizard",
        pt = "3/3",
        text = """
            Ward {2}
            Increment (Whenever you cast a spell, if the amount of mana you spent is greater than this creature's power or toughness, put a +1/+1 counter on this creature.)
            At the beginning of each end step, if you put a counter on this creature this turn, create a 0/0 green and blue Fractal creature token and put three +1/+1 counters on it.
        """,
    ) parsesTo abilities(
        Keyword.Ward(manaCost("{2}")),
        Keyword.Increment,
        triggered(
            stepBegins(TurnStep.End, PlayerFilter.Any),
            *tokenWithCounters(n(3)).toTypedArray(),
            interveningIf = Condition.HappenedThisTurn(EventPattern.CountersPut(ObjectFilter.Is(Objects.Self), null, causedBy = byYou), atLeast = 1,
                measure = Measure.Events),
        ),
    )

    // CR 122.1d: a stun counter replaces the next untap
    @Test
    fun foilRiposte() = card(
        name = "Foil Riposte",
        cost = "{1}{W}",
        type = "Instant",
        text = """
            Tap target creature. If it's your turn, put a stun counter on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)
            Draw a card.
        """,
    ) parsesTo abilities(
        spell(tapIt(target("creature")), ifThen(Condition.YourTurn, stunCounters(target("creature"))), draw(1), targets = listOf(slot("creature", creature))),
    )

    @Test
    fun putItOffAgain() = card(
        name = "Put It Off Again",
        cost = "{X}{U}",
        type = "Sorcery",
        text = "Tap target creature. Put twice X stun counters on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)",
    ) parsesTo abilities(
        spell(tapIt(target("creature")), stunCounters(target("creature"), Value.Times(x, 2)), targets = listOf(slot("creature", creature)), announced = listOf("X")),
    )

    @Test
    fun longingForHearth() = card(
        name = "Longing for Hearth",
        cost = "{4}{U}{U}",
        type = "Instant",
        text = "Target player draws two cards. Tap up to two target creatures. Put a stun counter on each of them. (If a permanent with a stun counter would become untapped, remove one from it instead.)",
    ) parsesTo abilities(
        spell(
            draw(2, Players.Target("player")),
            tapIt(target("creatures")),
            stunCounters(target("creatures")),
            targets = listOf(targetPlayer, slot("creatures", creature, min = 0, max = 2)),
        ),
    )

    // CR 107.3m: X of the spell it was; CR 122.1d: the counters; entering tapped isn't becoming tapped (CR 110.5b)
    @Test
    fun dozingPlodder() = card(
        name = "Dozing Plodder",
        cost = "{X}{G}",
        type = "Creature — Plant Beast",
        pt = "6/6",
        text = "This creature enters with a number of stun counters on it equal to three minus X. If X is 2 or less, it enters tapped. (If a permanent with a stun counter would become untapped, remove one from it instead.)",
    ) parsesTo abilities(
        entersWith(stun, Value.Max(listOf(n(0), Value.Difference(n(3), Value.CastX(Objects.Self))))),
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(Modification.EntersTapped)),
            condition = Condition.Compare(Value.CastX(Objects.Self), Comparison.AtMost, n(2))),
    )

    @Test
    fun fractalStandardBearer() = card(
        name = "Fractal Standard-Bearer",
        cost = "{4}{G}{U}",
        type = "Creature — Fractal Elk",
        pt = "6/6",
        text = """
            Trample
            When this creature enters, tap target creature an opponent controls. Put a stun counter on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(entersSelf, tapIt(target("creature")), stunCounters(target("creature")), targets = listOf(slot("creature", creatureOpponentControls))),
    )

    // CR 121.2: each card drawn is one draw
    @Test
    fun fractalOddity() = card(
        name = "Fractal Oddity",
        cost = "{U}",
        type = "Instant",
        text = "Create a 0/0 green and blue Fractal creature token and put X +1/+1 counters on it, where X is the number of cards you've drawn this turn.",
    ) parsesTo abilities(spell(*tokenWithCounters(Value.EventsThisTurn(EventPattern.Draw(PlayerFilter.You), Measure.Events)).toTypedArray()))

    // CR 613.1d, e, 613.4b: its colours and creature types are replaced; its base P/T set
    @Test
    fun geometrify() = card(
        name = "Geometrify",
        cost = "{X}{U}",
        type = "Instant",
        text = "Until end of turn, target creature becomes a green and blue Fractal with base power and toughness each equal to X plus 1. (It loses all other colors and creature types.)",
    ) parsesTo abilities(
        spell(
            untilEndOfTurn(target("creature"),
                Mechanic.SetColors(listOf(ColorRef.Literal(Color.Green), ColorRef.Literal(Color.Blue))),
                Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Fractal"))), replaceSubtypes = true),
                Mechanic.SetPowerToughness(Value.Sum(listOf(x, n(1))), Value.Sum(listOf(x, n(1))))),
            targets = listOf(slot("creature", creature)),
            announced = listOf("X"),
        ),
    )

    // CR 701.10e: doubling counters puts that many more
    @Test
    fun expansionGradient() = card(
        name = "Expansion Gradient",
        cost = "{G}{U}",
        type = "Sorcery",
        text = "Put a +1/+1 counter on target creature you control, then double the number of +1/+1 counters on that creature.",
    ) parsesTo abilities(
        spell(
            counters(target("creature")),
            doing(Effect.Action(KeywordAction.Double(Doubled.Counters(target("creature"), p1p1)))),
            targets = listOf(slot("creature", creatureYouControl)),
        ),
    )

    // CR 707.9b: the exceptions are part of the copy's copiable values
    @Test
    fun practicalTopology() = card(
        name = "Practical Topology",
        cost = "{2}{G}{U}",
        type = "Sorcery",
        text = "Create a token that's a copy of target non-Aura permanent you control, except it's a 0/0 Fractal creature in addition to its other types. Put six +1/+1 counters on it.",
    ) parsesTo abilities(
        spell(
            doing(Effect.CreateToken(TokenSpec.CopyOf(target("permanent"), except = listOf(CopyException.PowerToughness(0, 0),
                CopyException.Types(add = listOf(CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Fractal")))))), n(1), Players.You, bind = "token")),
            counters(Objects.Bound("token"), 6),
            targets = listOf(slot("permanent", and(not(sub("Aura")), permanentFilter, you))),
        ),
    )

    @Test
    fun untamedConjecture() = card(
        name = "Untamed Conjecture",
        cost = "{X}{G}",
        type = "Sorcery",
        text = """
            Create a 0/0 green and blue Fractal creature token. Put X +1/+1 counters on it.
            Surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
        """,
    ) parsesTo abilities(spell(*tokenWithCounters(x).toTypedArray(), doing(Effect.Surveil(Players.You, n(2))), announced = listOf("X")))

    // CR 201.2: lands with different names are counted once each
    @Test
    fun ostraWildlandsRambler() = card(
        name = "Ostra, Wildlands Rambler",
        cost = "{2}{G}",
        type = "Legendary Creature — Elf Druid",
        pt = "3/3",
        text = """
            Creatures you control with +1/+1 counters on them have trample.
            {4}{G}, {T}: Create a 0/0 green and blue Fractal creature token. Put X +1/+1 counters on it, where X is the number of differently named lands you control.
        """,
    ) parsesTo abilities(
        static(has(Keyword.Trample), affects = Objects.Each(and(creature, you, ObjectFilter.HasCounter(p1p1, atLeast = 1)))),
        activated(cost(mana("{4}{G}"), tap), *tokenWithCounters(Value.CountDistinct(Objects.Each(and(land, you)), SharedQuality.Name)).toTypedArray()),
    )

    // CR 107.3m: the X it was cast with
    @Test
    fun pteroctahedron() = card(
        name = "Pteroctahedron",
        cost = "{X}{G}{U}",
        type = "Creature — Dinosaur Fractal",
        pt = "1/0",
        text = """
            Flying
            This creature enters with X +1/+1 counters on it.
            When this creature enters, you gain 2 life.
        """,
    ) parsesTo abilities(Keyword.Flying, entersWith(p1p1, Value.CastX(Objects.Self)), triggered(entersSelf, gainLife(2)))

    @Test
    fun cumulativeProgression() = card(
        name = "Cumulative Progression",
        cost = "{3}{G}{G}",
        type = "Enchantment",
        text = """
            When this enchantment enters, create a 0/0 green and blue Fractal creature token. Put three +1/+1 counters on it.
            At the beginning of combat on your turn, put a +1/+1 counter on target creature you control. It gains vigilance until end of turn.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, *tokenWithCounters(n(3)).toTypedArray()),
        triggered(beginningOfCombatOnYourTurn, counters(target("creature")), gains(target("creature"), Keyword.Vigilance),
            targets = listOf(slot("creature", creatureYouControl))),
    )

    // CR 122.1: a growth counter has no rules meaning of its own
    @Test
    fun soothingAdvisement() = card(
        name = "Soothing Advisement",
        cost = "{1}{G}",
        type = "Enchantment",
        text = """
            Whenever you gain life, put a growth counter on this enchantment.
            As long as there are five or more growth counters on this enchantment, creatures you control get +3/+3.
        """,
    ) parsesTo abilities(
        triggered(on(EventPattern.GainLife(PlayerFilter.You)), counters(Objects.Self, 1, growth)),
        static(pt(3, 3), affects = Objects.Each(creatureYouControl), condition = Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(growth, atLeast = 5))),
    )

    // CR 105.2b: a multicoloured spell
    @Test
    fun spireDuelAdjudicator() = card(
        name = "Spire Duel Adjudicator",
        cost = "{2}",
        type = "Artifact Creature — Construct",
        pt = "2/1",
        text = "Whenever you cast a multicolored spell, put a +1/+1 counter on this creature.",
    ) parsesTo abilities(triggered(youCast(ObjectFilter.Multicolored), counters(Objects.Self)))
}
