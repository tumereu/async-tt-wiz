package arcanum.parser.sets.gtc

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.KeywordAction
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.PrintedNumber
import arcanum.api.RecipientFilter
import arcanum.api.Step
import arcanum.api.SubtypeRef
import arcanum.api.Subtype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Gatecrash chunk 04: evolve and +1/+1 counters. Evolve is a keyword (CR 702.100a) whose meaning comes from the shared
 * keyword table; "Whenever a +1/+1 counter is put on this creature" triggers once per counter (CR 603.2c). With them:
 * moving counters, removing counters as costs (one or more from a creature, from among creatures you control: the number
 * removed is X, CR 601.2f), mana for each counter, regeneration (CR 701.19a), creatures with counters gaining abilities,
 * a turn's creatures entering with an extra counter, and creatures entering with counters and as a Mutant (CR 614.1c).
 */
class GtcChunk04EvolveAndCountersTest {
    private val withCounter = ObjectFilter.And(listOf(creature, you, ObjectFilter.HasCounter(p1p1, atLeast = 1)))

    @Test
    fun clingingPolyps() = card(
        name = "Clinging Polyps",
        cost = "{3}{U}",
        type = "Creature — Jellyfish",
        pt = "1/4",
        text = """
            Defender
            Evolve (Whenever a creature you control enters, if that creature has greater power or toughness than this creature, put a +1/+1 counter on this creature.)
        """,
    ) parsesTo abilities(Keyword.Defender, Keyword.Evolve)

    @Test
    fun finnedRaptor() = card(
        name = "Finned Raptor",
        cost = "{U}",
        type = "Creature — Bird Mutant",
        pt = "0/1",
        text = """
            Flying
            Evolve (Whenever a creature you control enters, if that creature has greater power or toughness than this creature, put a +1/+1 counter on this creature.)
        """,
    ) parsesTo abilities(Keyword.Flying, Keyword.Evolve)

    @Test
    fun fluxmancerAdept() = card(
        name = "Fluxmancer Adept",
        cost = "{2}{U}",
        type = "Creature — Merfolk Wizard",
        pt = "1/2",
        text = """
            Evolve (Whenever a creature you control enters, if that creature has greater power or toughness than this creature, put a +1/+1 counter on this creature.)
            {1}{U}, {T}: Move a +1/+1 counter from this creature onto target creature.
        """,
    ) parsesTo abilities(
        Keyword.Evolve,
        Ability.Activated(
            cost = costOf(manaPart("{1}{U}"), CostPart.TapSelf),
            instructions = Instructions(
                targets = listOf(slot("creature", creature)),
                steps = listOf(Step.Do(Effect.MoveCounters(Objects.Self, Objects.Target("creature"), p1p1, n(1)))),
            ),
            rules = anyTime,
        ),
    )

    // CR 601.2f, 602.2b: how many counters are removed is announced as X, and the target's power is compared with it
    @Test
    fun mindgraftManipulator() = card(
        name = "Mindgraft Manipulator",
        cost = "{1}{U}{U}",
        type = "Creature — Mutant Wizard",
        pt = "0/1",
        text = """
            Evolve (Whenever a creature you control enters, if that creature has greater power or toughness than this creature, put a +1/+1 counter on this creature.)
            {T}, Remove one or more +1/+1 counters from this creature: Gain control of target creature with power less than or equal to the number of +1/+1 counters removed this way.
        """,
    ) parsesTo abilities(
        Keyword.Evolve,
        Ability.Activated(
            cost = costOf(CostPart.TapSelf, CostPart.RemoveCounters(p1p1, Value.Announced("X"), CostObjects.Self)),
            instructions = Instructions(
                targets = listOf(slot("creature", ObjectFilter.And(listOf(creature, ObjectFilter.Power(arcanum.api.Comparison.AtMost, Value.Announced("X")))))),
                steps = listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.ControlledBy(Players.You)),
                    Affected.Objects(Objects.Target("creature")), Duration.Indefinitely))),
                announced = listOf("X"),
            ),
            rules = anyTime,
        ),
    )

    @Test
    fun snappingMutant() = card(
        name = "Snapping Mutant",
        cost = "{4}{G}",
        type = "Creature — Lizard Beast",
        pt = "6/2",
        text = "Evolve (Whenever a creature you control enters, if that creature has greater power or toughness than this creature, put a +1/+1 counter on this creature.)",
    ) parsesTo abilities(Keyword.Evolve)

    @Test
    fun reedmawCroc() = card(
        name = "Reedmaw Croc",
        cost = "{2}{G}",
        type = "Creature — Crocodile Frog",
        pt = "1/3",
        text = """
            Reach (This creature can block creatures with flying.)
            Evolve (Whenever a creature you control enters, if that creature has greater power or toughness than this creature, put a +1/+1 counter on this creature.)
        """,
    ) parsesTo abilities(Keyword.Reach, Keyword.Evolve)

    // CR 701.19a: "Regenerate it": a shield for this creature
    @Test
    fun specimenZero() = card(
        name = "Specimen Zero",
        cost = "{G}",
        type = "Creature — Human Ooze",
        pt = "1/1",
        text = """
            Evolve (Whenever a creature you control enters, if that creature has greater power or toughness than this creature, put a +1/+1 counter on this creature.)
            Remove two +1/+1 counters from this creature: Regenerate it. (The next time this creature would be destroyed this turn, instead tap it, remove it from combat, and heal all damage on it.)
        """,
    ) parsesTo abilities(
        Keyword.Evolve,
        Ability.Activated(
            cost = costOf(CostPart.RemoveCounters(p1p1, n(2), CostObjects.Self)),
            instructions = steps(Step.Do(Effect.Action(KeywordAction.Regenerate(Objects.Self)))),
            rules = anyTime,
        ),
    )

    // CR 605.1a: a mana ability; the amount is counted as it resolves
    @Test
    fun spiralSage() = card(
        name = "Spiral Sage",
        cost = "{1}{G}",
        type = "Creature — Elf Druid",
        pt = "1/2",
        text = """
            Evolve (Whenever a creature you control enters, if that creature has greater power or toughness than this creature, put a +1/+1 counter on this creature.)
            {T}: Add {G} for each +1/+1 counter on this creature.
        """,
    ) parsesTo abilities(
        Keyword.Evolve,
        Ability.Activated(
            cost = costOf(CostPart.TapSelf),
            instructions = steps(Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{G}")!!,
                times = Value.Of(NumericProperty.Counters(p1p1), Objects.Self, Combine.Single))), Players.You))),
            rules = anyTime,
        ),
    )

    @Test
    fun slipstreamKrasis() = card(
        name = "Slipstream Krasis",
        cost = "{1}{G}{U}",
        type = "Creature — Fish Mutant",
        pt = "0/4",
        text = """
            This creature can't be blocked.
            Evolve (Whenever a creature you control enters, if that creature has greater power or toughness than this creature, put a +1/+1 counter on this creature.)
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.CantBeBlocked), affects = Affected.Objects(Objects.Self)),
        Keyword.Evolve,
    )

    // CR 603.2c: one trigger per counter put
    @Test
    fun depthsightMage() = card(
        name = "Depthsight Mage",
        cost = "{2}{G}{U}",
        type = "Creature — Human Wizard",
        pt = "1/1",
        text = """
            Evolve (Whenever a creature you control enters, if that creature has greater power or toughness than this creature, put a +1/+1 counter on this creature.)
            Whenever a +1/+1 counter is put on this creature, you may draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Evolve,
        Ability.Triggered(
            Trigger.On(EventPattern.CountersPut(ObjectFilter.Is(Objects.Self), p1p1)),
            steps(Step.May(listOf(Step.Do(Effect.Draw(Players.You, n(1)))), Players.You)),
        ),
    )

    @Test
    fun lurkingSharkcrab() = card(
        name = "Lurking Sharkcrab",
        cost = "{G}{U}",
        type = "Creature — Shark Crab",
        pt = "2/1",
        text = """
            Flash (You may cast this spell any time you could cast an instant.)
            Evolve (Whenever a creature you control enters, if that creature has greater power or toughness than this creature, put a +1/+1 counter on this creature.)
        """,
    ) parsesTo abilities(Keyword.Flash, Keyword.Evolve)

    // CR 614.1c: an effect lasting the turn that makes creatures enter with a counter; a counter removed from any creature
    // you control as a cost
    @Test
    fun hybridGuildmage() = card(
        name = "Hybrid Guildmage",
        cost = "{G}{U}",
        type = "Creature — Elf Wizard",
        pt = "2/2",
        text = """
            {G}{U}: This turn, each creature you control enters with an additional +1/+1 counter on it.
            {G}{U}, Remove a +1/+1 counter from a creature you control: Draw a card.
        """,
    ) parsesTo abilities(
        Ability.Activated(
            cost = mana("{G}{U}"),
            instructions = steps(Step.Do(Effect.ApplyMechanics(
                listOf(Mechanic.Replacement(EventPattern.ZoneChange(creatureYouControl, to = Zone.Battlefield),
                    modify = listOf(Modification.EntersWithCounters(p1p1, n(1))))),
                affects = null,
                duration = Duration.EndOfTurn,
            ))),
            rules = anyTime,
        ),
        Ability.Activated(
            cost = costOf(manaPart("{G}{U}"), CostPart.RemoveCounters(p1p1, n(1), CostObjects.Choose(creatureYouControl, count = n(1)))),
            instructions = steps(Step.Do(Effect.Draw(Players.You, n(1)))),
            rules = anyTime,
        ),
    )

    // CR 601.2f: the counters removed from among creatures you control in total, announced as X; the token's power and
    // toughness are fixed as it is created (CR 707.2)
    @Test
    fun slimecraftVat() = card(
        name = "Slimecraft Vat",
        cost = "{3}{G}",
        type = "Enchantment",
        text = "{1}{G}, Remove one or more +1/+1 counters from among creatures you control: Create an X/X green Ooze creature token, where X is the number of +1/+1 counters removed this way.",
    ) parsesTo abilities(
        Ability.Activated(
            cost = costOf(manaPart("{1}{G}"), CostPart.RemoveCounters(p1p1, Value.Announced("X"), CostObjects.Choose(creatureYouControl, count = null), distributed = true)),
            instructions = Instructions(
                steps = listOf(Step.Do(Effect.CreateToken(
                    TokenSpec.Defined(
                        arcanum.api.FaceDefinition("Ooze Token", colorIndicator = listOf(Color.Green), types = listOf(arcanum.api.CardType.Creature),
                            subtypes = listOf(Subtype("Ooze")), power = PrintedNumber.X, toughness = PrintedNumber.X),
                        power = Value.Announced("X"), toughness = Value.Announced("X"),
                    ),
                    count = n(1), controller = Players.You,
                ))),
                announced = listOf("X"),
            ),
            rules = anyTime,
        ),
    )

    // CR 601.2c: the second target's controller must be the first target's; the number of counters is chosen as it resolves
    @Test
    fun adaptiveShift() = card(
        name = "Adaptive Shift",
        cost = "{G/U}",
        type = "Instant",
        text = "Move any number of +1/+1 counters from target creature onto another target creature with the same controller.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(
                slot("creature", creature),
                TargetSlot("other creature", TargetKind.Objects(ObjectFilter.And(listOf(creature,
                    ObjectFilter.ControlledBy(PlayerFilter.Is(Players.ControllerOf(Objects.Target("creature"))))))),
                    min = n(1), max = n(1), distinctFrom = listOf("creature")),
            ),
            steps = listOf(
                Step.Choose(Choice.Number(n(0), Value.Of(NumericProperty.Counters(p1p1), Objects.Target("creature"), Combine.Single)), bind = "moved", chooser = Players.You),
                Step.Do(Effect.MoveCounters(Objects.Target("creature"), Objects.Target("other creature"), p1p1, Value.Bound("moved"))),
            ),
        )),
    )

    @Test
    fun saphirDrake() = card(
        name = "Saphir Drake",
        cost = "{5}{U}",
        type = "Creature — Drake",
        pt = "4/4",
        text = """
            Flying
            Each creature you control with a +1/+1 counter on it has flying.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Static(listOf(Mechanic.GainAbilities(listOf(Keyword.Flying))), affects = Affected.Objects(Objects.Each(withCounter))),
    )

    @Test
    fun crestedCeratok() = card(
        name = "Crested Ceratok",
        cost = "{3}{G}",
        type = "Creature — Rhino",
        pt = "4/3",
        text = """
            Trample
            Each creature you control with a +1/+1 counter on it has trample.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        Ability.Static(listOf(Mechanic.GainAbilities(listOf(Keyword.Trample))), affects = Affected.Objects(Objects.Each(withCounter))),
    )

    @Test
    fun coercedGrowth() = card(
        name = "Coerced Growth",
        cost = "{G}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            At the beginning of your upkeep, put a +1/+1 counter on enchanted creature.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(creature)),
        Ability.Triggered(yourUpkeep, steps(Step.Do(Effect.PutCounters(Objects.AttachedTo(Objects.Self), p1p1, n(1))))),
    )

    @Test
    fun sproutingSurge() = card(
        name = "Sprouting Surge",
        cost = "{G}",
        type = "Instant",
        text = "Put a +1/+1 counter on target creature and untap it.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(slot("creature", creature)),
            steps = listOf(
                Step.Do(Effect.PutCounters(Objects.Target("creature"), p1p1, n(1))),
                Step.Do(Effect.Untap(Objects.Target("creature"))),
            ),
        )),
    )

    // CR 615: a prevention effect for the turn, applying to any creature without a counter when its damage would be dealt
    @Test
    fun thornvineWard() = card(
        name = "Thornvine Ward",
        cost = "{2}{G}",
        type = "Instant",
        text = "Prevent all combat damage that would be dealt this turn by creatures with no +1/+1 counters on them.",
    ) parsesTo abilities(
        Ability.Spell(steps(Step.Do(Effect.ApplyMechanics(
            listOf(Mechanic.Prevention(EventPattern.Damage(ObjectFilter.And(listOf(creature, ObjectFilter.Not(ObjectFilter.HasCounter(p1p1, atLeast = 1)))),
                RecipientFilter(), combat = true))),
            affects = null,
            duration = Duration.EndOfTurn,
        )))),
    )

    // CR 614.1c, 614.12: the counters and the creature type are part of how the other creature enters (its power then)
    @Test
    fun grandBiomancer() = card(
        name = "Grand Biomancer",
        cost = "{2}{G}{U}",
        type = "Creature — Elf Wizard",
        pt = "2/4",
        text = "Each other creature you control enters with a number of additional +1/+1 counters on it equal to this creature's power and as a Mutant in addition to its other types.",
    ) parsesTo abilities(
        Ability.Static(
            listOf(Mechanic.Replacement(
                EventPattern.ZoneChange(ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), creature, you)), to = Zone.Battlefield),
                modify = listOf(
                    Modification.EntersWithCounters(p1p1, power(Objects.Self)),
                    Modification.EntersAs(listOf(Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Mutant")))))),
                ),
            )),
            affects = Affected.Objects(Objects.Self),
        ),
    )
}
