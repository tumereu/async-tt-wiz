package arcanum.parser.cube

import arcanum.api.Ability
import arcanum.api.ActivationRules
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Mode
import arcanum.api.Modes
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.PrintedNumber
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Timing
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import kotlin.test.Test

/**
 * Chunk 55: the mana creatures left over from earlier chunks. Exiling a card from a graveyard for mana or a drain; Food
 * and Treasure makers (CR 111.10); a leveler whose level bands are static abilities (CR 711.2); a dies trigger with modes
 * (CR 700.2); a token with a quoted ability; tapping artifacts for mana and playing an exiled card for free.
 */
class Chunk55LeftoverManaCreaturesTest {
    // ---- Shared shapes

    private val creature = ObjectFilter.Type(CardType.Creature)
    private fun yourOwn(vararg filters: ObjectFilter) = ObjectFilter.And(filters.toList() + ObjectFilter.ControlledBy(PlayerFilter.You))
    private fun create(token: String) = Step.Do(Effect.CreateToken(
        TokenSpec.Predefined(token),
        count = Value.Fixed(1),
        controller = Players.You,
    ))
    private fun add(kind: ManaKind) = Step.Do(Effect.AddMana(ManaProduction(kind), players = Players.You))

    /** "{T}: Add {G}{G}." */
    private val tapForTwoGreen = Ability.Activated(
        cost = Cost(listOf(CostPart.TapSelf)),
        instructions = Instructions(
            steps = listOf(add(ManaKind.Fixed(ManaCost.parse("{G}{G}")!!))),
        ),
        rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You),
    )

    /** "[cost]: Exile target [kind] card from a graveyard. [then]" */
    private fun exileFromAGraveyard(cost: Cost, kind: ObjectFilter, vararg then: Step) = Ability.Activated(
        cost = cost,
        instructions = Instructions(
            targets = listOf(TargetSlot(
                name = "card",
                what = TargetKind.Objects(ObjectFilter.And(listOf(kind, ObjectFilter.InZone(Zone.Graveyard)))),
                min = Value.Fixed(1),
                max = Value.Fixed(1),
            )),
            steps = listOf(Step.Do(Effect.Move(Objects.Target("card"), to = Destination(Zone.Exile), postBind = "exiled"))) + then,
        ),
        rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You),
    )

    // ---- Graveyards and Food

    /** The first ability isn't a mana ability because it targets (CR 605.1a), so it is activated only with priority
     * (CR 602.2); its "Activate only as an instant" is reminder text. */
    @Test
    fun gravebloomAugur() = card(
        name = "Gravebloom Augur",
        cost = "{B/G}",
        type = "Creature — Elf Shaman",
        text = """
            {T}: Exile target land card from a graveyard. Add one mana of any color. (Activate only as an instant.)
            {B}, {T}: Exile target instant or sorcery card from a graveyard. Each opponent loses 2 life.
            {G}, {T}: Exile target creature card from a graveyard. You gain 2 life.
        """,
        pt = "1/2",
    ) parsesTo abilities(
        exileFromAGraveyard(
            Cost(listOf(CostPart.TapSelf)),
            ObjectFilter.Type(CardType.Land),
            add(anyOneColor(1)),
        ),
        exileFromAGraveyard(
            Cost(listOf(CostPart.Mana(ManaCost.parse("{B}")!!), CostPart.TapSelf)),
            ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Instant), ObjectFilter.Type(CardType.Sorcery))),
            Step.Do(Effect.LoseLife(Players.Each(PlayerFilter.Opponent), amount = Value.Fixed(2))),
        ),
        exileFromAGraveyard(
            Cost(listOf(CostPart.Mana(ManaCost.parse("{G}")!!), CostPart.TapSelf)),
            creature,
            Step.Do(Effect.GainLife(Players.You, amount = Value.Fixed(2))),
        ),
    )

    @Test
    fun giltFeatherGander() = card(
        name = "Gilt-Feather Gander",
        cost = "{G}",
        type = "Creature — Bird",
        text = """
            Flying
            When this creature enters, create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
            {1}{G}, {T}: Create a Food token.
            {T}, Sacrifice a Food: Add one mana of any color.
        """,
        pt = "0/2",
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)),
            instructions = Instructions(steps = listOf(create("Food"))),
        ),
        Ability.Activated(
            cost = Cost(listOf(CostPart.Mana(ManaCost.parse("{1}{G}")!!), CostPart.TapSelf)),
            instructions = Instructions(steps = listOf(create("Food"))),
            rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You),
        ),
        Ability.Activated(
            cost = Cost(listOf(
                CostPart.TapSelf,
                CostPart.Sacrifice(CostObjects.Choose(ObjectFilter.Sub(Subtype("Food")), count = Value.Fixed(1))),
            )),
            instructions = Instructions(steps = listOf(add(anyOneColor(1)))),
            rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You),
        ),
    )

    // ---- Leveler

    /** CR 711.2a, b: each "LEVEL" band is one static ability: while the creature has that many level counters it has the
     * band's base P/T and abilities (here a granted ability that itself grants one). Below level 1 it is the printed 1/1
     * with only level up (CR 711.4, 711.5). Level counters are compared with `ClassLevelAtLeast`. */
    @Test
    fun canopyWhisperer() = card(
        name = "Canopy Whisperer",
        cost = "{G}",
        type = "Creature — Elf Druid",
        text = """
            Level up {1}{G} ({1}{G}: Put a level counter on this. Level up only as a sorcery.)
            LEVEL 1-4
            1/2
            {T}: Add {G}{G}.
            LEVEL 5+
            1/4
            Elves you control have "{T}: Add {G}{G}."
        """,
        pt = "1/1",
    ) parsesTo abilities(
        Keyword.LevelUp(manaCost("{1}{G}")),
        Ability.Static(
            mechanics = listOf(
                Mechanic.SetPowerToughness(Value.Fixed(1), Value.Fixed(2)),
                Mechanic.GainAbilities(listOf(tapForTwoGreen)),
            ),
            condition = Condition.And(listOf(
                Condition.ClassLevelAtLeast(1),
                Condition.Not(Condition.ClassLevelAtLeast(5)),
            )),
            affects = Affected.Objects(Objects.Self),
        ),
        Ability.Static(
            mechanics = listOf(
                Mechanic.SetPowerToughness(Value.Fixed(1), Value.Fixed(4)),
                Mechanic.GainAbilities(listOf(Ability.Static(
                    mechanics = listOf(Mechanic.GainAbilities(listOf(tapForTwoGreen))),
                    affects = Affected.Objects(Objects.Each(yourOwn(ObjectFilter.Sub(Subtype("Elf"))))),
                ))),
            ),
            condition = Condition.ClassLevelAtLeast(5),
            affects = Affected.Objects(Objects.Self),
        ),
    )

    // ---- Treasure from creatures dying or sacrificed

    @Test
    fun remorselessCorsair() = card(
        name = "Remorseless Corsair",
        cost = "{3}{B}",
        type = "Creature — Human Pirate",
        text = """Whenever another creature you control dies, create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")""",
        pt = "1/4",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.ZoneChange(
                ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), creature, ObjectFilter.ControlledBy(PlayerFilter.You))),
                from = Zone.Battlefield,
                to = Zone.Graveyard,
            )),
            instructions = Instructions(steps = listOf(create("Treasure"))),
        ),
    )

    /** CR 700.2: the mode is chosen as the trigger is put on the Stack; the first mode's target only then. */
    @Test
    fun shufflingWight() = card(
        name = "Shuffling Wight",
        cost = "{B}",
        type = "Creature — Zombie",
        text = """
            When this creature dies, choose one —
            • Target creature an opponent controls gets -1/-1 until end of turn.
            • Create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
        """,
        pt = "1/1",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield, to = Zone.Graveyard)),
            instructions = Instructions(modes = Modes(listOf(
                Mode(Instructions(
                    targets = listOf(TargetSlot(
                        name = "creature",
                        what = TargetKind.Objects(ObjectFilter.And(listOf(creature, ObjectFilter.ControlledBy(PlayerFilter.Opponent)))),
                        min = Value.Fixed(1),
                        max = Value.Fixed(1),
                    )),
                    steps = listOf(Step.Do(Effect.ApplyMechanics(
                        mechanics = listOf(Mechanic.ModifyPowerToughness(Value.Fixed(-1), Value.Fixed(-1))),
                        affects = Affected.Objects(Objects.Target("creature")),
                        duration = Duration.EndOfTurn,
                    ))),
                )),
                Mode(Instructions(steps = listOf(create("Treasure")))),
            ), min = 1, max = 1, chooser = Players.You)),
        ),
    )

    @Test
    fun burrowSoulbroker() = card(
        name = "Burrow Soulbroker",
        cost = "{2}{B}",
        type = "Creature — Zombie Goblin Wizard",
        text = """Pay 1 life, Sacrifice another creature: Create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")""",
        pt = "3/3",
    ) parsesTo abilities(
        Ability.Activated(
            cost = Cost(listOf(
                CostPart.PayLife(Value.Fixed(1)),
                CostPart.Sacrifice(CostObjects.Choose(
                    ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), creature)),
                    count = Value.Fixed(1),
                )),
            )),
            instructions = Instructions(steps = listOf(create("Treasure"))),
            rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You),
        ),
    )

    // ---- Artifacts

    /** "Tamsin" is the card's shortened name (CR 201.5c). The token's "+1/+1 for each artifact" applies in layer 7c and
     * isn't characteristic-defining (CR 604.3, 613.4c). Tapping as a cost names only the kind (CR 602.2b). The exiled card
     * may be played (a land too, CR 305.1) for the rest of the turn. */
    @Test
    fun tamsinGrandMechanist() = card(
        name = "Tamsin, Grand Mechanist",
        cost = "{2}{U}{U}",
        type = "Legendary Creature — Human Artificer",
        text = """
            When Tamsin enters, create a 0/0 colorless Construct artifact creature token with "This token gets +1/+1 for each artifact you control."
            Tap an untapped artifact you control: Add {U}.
            {5}: Shuffle your library, then exile the top card. Until end of turn, you may play that card without paying its mana cost.
        """,
        pt = "1/4",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)),
            instructions = Instructions(steps = listOf(Step.Do(Effect.CreateToken(TokenSpec.Defined(FaceDefinition(
                name = "Construct Token",
                colorIndicator = emptyList(),
                types = listOf(CardType.Artifact, CardType.Creature),
                subtypes = listOf(Subtype("Construct")),
                power = PrintedNumber.Fixed(0),
                toughness = PrintedNumber.Fixed(0),
                abilities = listOf(Ability.Static(listOf(Mechanic.ModifyPowerToughness(
                    Value.Count(Objects.Each(yourOwn(ObjectFilter.Type(CardType.Artifact)))),
                    Value.Count(Objects.Each(yourOwn(ObjectFilter.Type(CardType.Artifact)))),
                )), affects = Affected.Objects(Objects.Self))),
            )), count = Value.Fixed(1), controller = Players.You)))),
        ),
        Ability.Activated(
            cost = Cost(listOf(CostPart.TapObjects(CostObjects.Choose(
                ObjectFilter.Type(CardType.Artifact),
                count = Value.Fixed(1),
            )))),
            instructions = Instructions(steps = listOf(add(ManaKind.Fixed(ManaCost.parse("{U}")!!)))),
            rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You),
        ),
        Ability.Activated(
            cost = manaCost("{5}"),
            instructions = Instructions(steps = listOf(
                Step.Do(Effect.Shuffle(Players.You)),
                Step.Do(Effect.Move(
                    Objects.LibraryCards(Players.You, Value.Fixed(1), fromTop = true),
                    to = Destination(Zone.Exile),
                    postBind = "exiled",
                )),
                Step.Do(Effect.ApplyMechanics(
                    mechanics = listOf(Mechanic.MayPlay(
                        Zone.Exile,
                        how = PlayKind.Play,
                        withoutPaying = true,
                        holder = PlayerFilter.You,
                    )),
                    affects = Affected.Objects(Objects.Bound("exiled")),
                    duration = Duration.EndOfTurn,
                )),
            )),
            rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You),
        ),
    )
}
