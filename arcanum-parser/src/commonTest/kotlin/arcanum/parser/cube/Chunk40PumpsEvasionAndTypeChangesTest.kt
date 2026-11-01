package arcanum.parser.cube

import arcanum.api.Ability
import arcanum.api.AbilityKind
import arcanum.api.ActivationRules
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.PrintedNumber
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import kotlin.test.Test

/**
 * Chunk 40: creatures whose main text is an activated ability that changes creatures until end of turn: pumps ("once each
 * turn", +X/-X paid for by sacrificing X creatures), base power and toughness and creature types (layers 4 and 7b, CR
 * 613.1d, 613.4b), evasion and protection chosen as it resolves, tapping or untapping, changing a target; with them the
 * static and triggered abilities of the same cards (landwalk, token and counter replacements, "can't be countered").
 */
class Chunk40PumpsEvasionAndTypeChangesTest {
    // ---- Shared shapes

    private fun mana(text: String) = CostPart.Mana(ManaCost.parse(text)!!)
    private val creature = ObjectFilter.Type(CardType.Creature)
    private val youControl = ObjectFilter.ControlledBy(PlayerFilter.You)
    private val notSelf = ObjectFilter.Not(ObjectFilter.Is(Objects.Self))

    private fun activated(vararg cost: CostPart, steps: List<Step>, targets: List<TargetSlot> = emptyList(), rules: ActivationRules = ActivationRules()) =
        Ability.Activated(
            cost = Cost(cost.toList()),
            instructions = Instructions(targets = targets, steps = steps),
            rules = rules,
        )

    /** [mechanics] apply to [objects] until end of turn (CR 611.2c: a set of objects is fixed as it resolves). */
    private fun untilEndOfTurn(objects: Objects, vararg mechanics: Mechanic) = Step.Do(Effect.ApplyMechanics(
        mechanics = mechanics.toList(),
        affects = Affected.Objects(objects),
        duration = Duration.EndOfTurn,
    ))

    private fun pump(power: Int, toughness: Int) = Mechanic.ModifyPowerToughness(Value.Fixed(power), Value.Fixed(toughness))

    private fun slot(name: String, filter: ObjectFilter) = TargetSlot(name = name, what = TargetKind.Objects(filter))

    private fun creatureToken(subtypes: List<String>, power: Int, toughness: Int, color: Color) = TokenSpec.Defined(FaceDefinition(
        name = subtypes.joinToString(" ") + " Token",
        colorIndicator = listOf(color),
        types = listOf(CardType.Creature),
        subtypes = subtypes.map { Subtype(it) },
        power = PrintedNumber.Fixed(power),
        toughness = PrintedNumber.Fixed(toughness),
    ))

    // ---- Pumps

    // CR 602.5b: "Activate only once each turn"
    @Test
    fun sunningMudlizard() = card(
        name = "Sunning Mudlizard",
        cost = "{G}",
        type = "Creature — Lizard",
        pt = "1/1",
        text = """
            {1}{G}: This creature gets +2/+2 until end of turn. Activate only once each turn.
            Madness {0} (If you discard this card, discard it into exile. When you do, cast it for its madness cost or put it into your graveyard.)
        """,
    ) parsesTo abilities(
        activated(
            mana("{1}{G}"),
            steps = listOf(untilEndOfTurn(Objects.Self, pump(2, 2))),
            rules = ActivationRules(perTurn = 1),
        ),
        Keyword.Madness(manaCost("{0}")),
    )

    @Test
    fun scorchingMudlizard() = card(
        name = "Scorching Mudlizard",
        cost = "{R}",
        type = "Creature — Lizard",
        pt = "1/1",
        text = """
            {R}: This creature gets +2/+0 until end of turn. Activate only once each turn.
            Madness {0} (If you discard this card, discard it into exile. When you do, cast it for its madness cost or put it into your graveyard.)
        """,
    ) parsesTo abilities(
        activated(
            mana("{R}"),
            steps = listOf(untilEndOfTurn(Objects.Self, pump(2, 0))),
            rules = ActivationRules(perTurn = 1),
        ),
        Keyword.Madness(manaCost("{0}")),
    )

    @Test
    fun elvishDrillmaster() = card(
        name = "Elvish Drillmaster",
        cost = "{1}{G}",
        type = "Creature — Elf Warrior",
        pt = "2/2",
        text = """
            Whenever one or more other Elves you control enter, create a 1/1 green Elf Warrior creature token. This ability triggers only once each turn.
            {5}{G}{G}: Elves you control get +2/+2 and gain deathtouch until end of turn.
        """,
    ) parsesTo abilities(
        // "One or more" triggers once for the batch
        Ability.Triggered(
            trigger = Trigger.On(
                EventPattern.ZoneChange(ObjectFilter.And(listOf(notSelf, ObjectFilter.Sub(Subtype("Elf")), youControl)), to = Zone.Battlefield),
                oneOrMore = true,
            ),
            instructions = Instructions(steps = listOf(
                Step.Do(Effect.CreateToken(creatureToken(listOf("Elf", "Warrior"), 1, 1, Color.Green))),
            )),
            perTurn = 1,
        ),
        activated(
            mana("{5}{G}{G}"),
            steps = listOf(untilEndOfTurn(
                Objects.Each(ObjectFilter.And(listOf(ObjectFilter.Sub(Subtype("Elf")), youControl))),
                pump(2, 2),
                Mechanic.GainAbilities(listOf(Keyword.Deathtouch)),
            )),
        ),
    )

    // CR 107.3a: X in an activation cost is announced as the ability is activated
    @Test
    fun nutcrackerSquirrelMarshal() = card(
        name = "Nutcracker, Squirrel Marshal",
        cost = "{2}{G}",
        type = "Legendary Creature — Squirrel Warrior",
        pt = "3/3",
        text = """
            Forestwalk (This creature can't be blocked as long as defending player controls a Forest.)
            If one or more tokens would be created under your control, those tokens plus that many 1/1 green Squirrel creature tokens are created instead.
            {B}, Sacrifice X Squirrels: Target creature gets +X/-X until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Landwalk(land = ObjectFilter.Sub(Subtype("Forest")), printed = "Forestwalk"),
        // CR 614.1a: one creation event, with as many extra Squirrels as tokens it would create
        Ability.Static(listOf(Mechanic.Replacement(
            event = EventPattern.CreateToken(youControl),
            modify = listOf(Modification.AlsoCreate(creatureToken(listOf("Squirrel"), 1, 1, Color.Green), count = Value.EventAmount)),
        ))),
        Ability.Activated(
            cost = Cost(listOf(
                mana("{B}"),
                CostPart.Sacrifice(CostObjects.Choose(ObjectFilter.Sub(Subtype("Squirrel")), count = Value.Announced())),
            )),
            instructions = Instructions(
                targets = listOf(slot("creature", creature)),
                steps = listOf(untilEndOfTurn(
                    Objects.Target("creature"),
                    Mechanic.ModifyPowerToughness(Value.Announced(), Value.Times(Value.Announced(), -1)),
                )),
                announced = listOf("X"),
            ),
        ),
    )

    // ---- Base power and toughness, creature types

    // CR 205.1b: "in addition to its other creature types" keeps the old ones
    @Test
    fun saurianHerder() = card(
        name = "Saurian Herder",
        cost = "{G}",
        type = "Creature — Elf Shaman",
        pt = "1/1",
        text = """
            This spell can't be countered.
            Green spells you control can't be countered.
            {4}{G}{G}: Until end of turn, each Elf creature you control has base power and toughness 5/5 and becomes a Dinosaur in addition to its other creature types.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.CantBeCountered)),
        Ability.Static(
            mechanics = listOf(Mechanic.CantBeCountered),
            affects = Affected.Objects(Objects.Each(ObjectFilter.And(listOf(
                ObjectFilter.OfColor(Color.Green),
                ObjectFilter.Category(ObjectCategory.Spell),
                youControl,
            )))),
        ),
        activated(
            mana("{4}{G}{G}"),
            steps = listOf(untilEndOfTurn(
                Objects.Each(ObjectFilter.And(listOf(ObjectFilter.Sub(Subtype("Elf")), creature, youControl))),
                Mechanic.SetPowerToughness(Value.Fixed(5), Value.Fixed(5)),
                Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Dinosaur")))),
            )),
        ),
    )

    @Test
    fun glassPolymorph() = card(
        name = "Glass Polymorph",
        cost = "{2}{W}",
        type = "Creature — Shapeshifter",
        pt = "1/1",
        text = """
            Changeling (This card is every creature type.)
            {X}: Until end of turn, creatures you control have base power and toughness X/X and gain all creature types.
        """,
    ) parsesTo abilities(
        Keyword.Changeling,
        Ability.Activated(
            cost = Cost(listOf(mana("{X}"))),
            instructions = Instructions(
                steps = listOf(untilEndOfTurn(
                    Objects.Each(ObjectFilter.And(listOf(creature, youControl))),
                    Mechanic.SetPowerToughness(Value.Announced(), Value.Announced()),
                    Mechanic.AllCreatureTypes,
                )),
                announced = listOf("X"),
            ),
        ),
    )

    // ---- Evasion and protection

    @Test
    fun cookieBrawler() = card(
        name = "Cookie Brawler",
        cost = "{1}",
        type = "Artifact Creature — Food Golem",
        pt = "1/1",
        text = """
            Haste (This creature can attack and {T} as soon as it comes under your control.)
            {1}: This creature can't be blocked this turn except by creatures with haste.
            {2}, {T}, Sacrifice this creature: You gain 3 life.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        // CR 509.1a: only creatures block, so the filter names only the keyword (as flying's does)
        activated(
            mana("{1}"),
            steps = listOf(untilEndOfTurn(Objects.Self, Mechanic.CanBeBlockedOnlyBy(ObjectFilter.HasKeyword(Keyword.Haste)))),
        ),
        activated(
            mana("{2}"), CostPart.TapSelf, CostPart.Sacrifice(CostObjects.Self),
            steps = listOf(Step.Do(Effect.GainLife(arcanum.api.Players.You, Value.Fixed(3)))),
        ),
    )

    // CR 608.2d: the colour (or colorless) is chosen as the ability resolves
    @Test
    fun bestowerOfSigils() = card(
        name = "Bestower of Sigils",
        cost = "{W}",
        type = "Creature — Kor Cleric",
        pt = "1/2",
        text = "{T}: Another target creature you control gains protection from colorless or from the color of your choice until end of turn.",
    ) parsesTo abilities(
        activated(
            CostPart.TapSelf,
            targets = listOf(slot("creature", ObjectFilter.And(listOf(notSelf, creature, youControl)))),
            steps = listOf(
                Step.Choose(Choice.Option(listOf("colorless", "a color")), bind = "quality"),
                Step.If(
                    condition = Condition.Chose("quality", "colorless"),
                    then = listOf(untilEndOfTurn(
                        Objects.Target("creature"),
                        Mechanic.GainAbilities(listOf(Keyword.Protection(ObjectFilter.Colorless))),
                    )),
                    otherwise = listOf(
                        Step.Choose(Choice.Color(), bind = "color"),
                        untilEndOfTurn(
                            Objects.Target("creature"),
                            Mechanic.GainAbilities(listOf(Keyword.Protection(ObjectFilter.OfColor(ColorRef.Chosen("color"))))),
                        ),
                    ),
                ),
            ),
        ),
    )

    // CR 702.43a: modular's counters are put by its triggered ability; this replacement adds one more (CR 614.1a)
    @Test
    fun vizzikTheGlintwasp() = card(
        name = "Vizzik, the Glintwasp",
        cost = "{1}",
        type = "Legendary Artifact Creature — Insect",
        pt = "0/0",
        text = """
            Modular 1
            If a modular triggered ability would put one or more +1/+1 counters on a creature you control, that many plus one +1/+1 counters are put on it instead.
            {R}: Destroy target artifact you control.
            {W}: Vizzik gains flying until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Modular(1),
        Ability.Static(listOf(Mechanic.Replacement(
            event = EventPattern.CountersPut(
                objects = ObjectFilter.And(listOf(creature, youControl)),
                kind = CounterKind.PowerToughness(1, 1),
                causedBy = ObjectFilter.AbilityOf(kind = AbilityKind.Triggered, keyword = Keyword.Modular.Kind),
            ),
            modify = listOf(Modification.AddAmount(Value.Fixed(1))),
        ))),
        activated(
            mana("{R}"),
            targets = listOf(slot("artifact", ObjectFilter.And(listOf(ObjectFilter.Type(CardType.Artifact), youControl)))),
            steps = listOf(Step.Do(Effect.Destroy(Objects.Target("artifact")))),
        ),
        activated(
            mana("{W}"),
            steps = listOf(untilEndOfTurn(Objects.Self, Mechanic.GainAbilities(listOf(Keyword.Flying)))),
        ),
    )

    // ---- Tapping, untapping, targets

    @Test
    fun canopyTender() = card(
        name = "Canopy Tender",
        cost = "{G}",
        type = "Creature — Elf Druid",
        pt = "1/1",
        text = "{T}: Untap target Forest.",
    ) parsesTo abilities(
        activated(
            CostPart.TapSelf,
            targets = listOf(slot("Forest", ObjectFilter.Sub(Subtype("Forest")))),
            steps = listOf(Step.Do(Effect.Untap(Objects.Target("Forest")))),
        ),
    )

    // CR 608.2d: tap or untap is chosen as it resolves
    @Test
    fun fateknitter() = card(
        name = "Fateknitter",
        cost = "{3}{U}",
        type = "Creature — Zombie Wizard",
        pt = "1/2",
        text = """
            {T}: You may tap or untap another target permanent.
            Unearth {U} ({U}: Return this card from your graveyard to the battlefield. It gains haste. Exile it at the beginning of the next end step or if it would leave the battlefield. Unearth only as a sorcery.)
        """,
    ) parsesTo abilities(
        activated(
            CostPart.TapSelf,
            targets = listOf(slot("permanent", ObjectFilter.And(listOf(notSelf, ObjectFilter.Category(ObjectCategory.Permanent))))),
            steps = listOf(Step.May(listOf(
                Step.Choose(Choice.Option(listOf("tap", "untap")), bind = "tapOrUntap"),
                Step.If(
                    condition = Condition.Chose("tapOrUntap", "tap"),
                    then = listOf(Step.Do(Effect.Tap(Objects.Target("permanent")))),
                    otherwise = listOf(Step.Do(Effect.Untap(Objects.Target("permanent")))),
                ),
            ))),
        ),
        Keyword.Unearth(manaCost("{U}")),
    )

    // CR 115.7b: only one target is changed, and only to this creature if that is legal
    @Test
    fun cursewebMite() = card(
        name = "Curseweb Mite",
        cost = "{2}",
        type = "Artifact Creature — Phyrexian Horror",
        pt = "0/4",
        text = "{U/P}: Change a target of target spell or ability to this creature. ({U/P} can be paid with either {U} or 2 life.)",
    ) parsesTo abilities(
        activated(
            mana("{U/P}"),
            targets = listOf(slot("spell", ObjectFilter.Or(listOf(
                ObjectFilter.Category(ObjectCategory.Spell),
                ObjectFilter.Category(ObjectCategory.StackAbility),
            )))),
            steps = listOf(Step.Do(Effect.ChangeTargets(Objects.Target("spell"), optional = false, to = Objects.Self))),
        ),
    )
}
