package arcanum.parser.cube

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostChange
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.KeywordAction
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.SharedQuality
import arcanum.api.Status
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import kotlin.test.Test

/**
 * Chunk 46: creatures with static abilities. Lords ("other Elves you control get +1/+1"); P/T defined by a count
 * (a characteristic-defining ability, CR 604.3); conditional bonuses (delirium); creatures entering with additional
 * counters, a replacement effect (CR 614.1c), one of them linked to a creature type chosen as it enters (CR 614.12,
 * 607.2d); spell taxes and casting limits; preventing combat damage (CR 615); +1/+1 counters put by triggers, and
 * bolster (CR 701.39a).
 */
class Chunk46StaticCreaturesTest {
    // ---- Shared shapes

    private val creature = ObjectFilter.Type(CardType.Creature)
    private val artifact = ObjectFilter.Type(CardType.Artifact)
    private val another = ObjectFilter.Not(ObjectFilter.Is(Objects.Self))
    private val plusOne = CounterKind.PowerToughness(1, 1)
    private val youControl = ObjectFilter.ControlledBy(PlayerFilter.You)
    private fun all(vararg filters: ObjectFilter) = ObjectFilter.And(filters.toList())

    /** "Other [creatures] get +1/+1." */
    private fun othersGetPlusOne(vararg filter: ObjectFilter) = Ability.Static(
        mechanics = listOf(Mechanic.ModifyPowerToughness(Value.Fixed(1), Value.Fixed(1))),
        affects = Affected.Objects(Objects.Each(all(another, *filter))),
    )

    /** "[Creatures] enter with [amount] additional +1/+1 counters": a replacement of their entering (CR 614.1c). */
    private fun entersWithCounters(entering: ObjectFilter, amount: Value) = Ability.Static(listOf(Mechanic.Replacement(
        event = EventPattern.ZoneChange(entering, to = Zone.Battlefield),
        modify = listOf(Modification.EntersWithCounters(plusOne, amount)),
    )))

    // ---- Lords and counts

    @Test
    fun boughCrownedOracle() = card(
        name = "Bough-Crowned Oracle",
        cost = "{G}{G}",
        type = "Creature — Elf Druid",
        pt = "1/1",
        text = """
            Other Elves you control get +1/+1.
            Whenever you cast an Elf spell, you may pay {G}. If you do, draw a card.
        """,
    ) parsesTo abilities(
        othersGetPlusOne(ObjectFilter.Sub(Subtype("Elf")), youControl),
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.Cast(ObjectFilter.Sub(Subtype("Elf")), by = PlayerFilter.You)),
            instructions = Instructions(
                steps = listOf(
                    Step.Pay(manaCost("{G}"), bind = "paid"),
                    Step.If(
                        condition = Condition.Did("paid"),
                        then = listOf(Step.Do(Effect.Draw(Players.You, count = Value.Fixed(1)))),
                    ),
                ),
            ),
        ),
    )

    /** CR 604.3: a characteristic-defining ability, applied in layer 7a and in every zone. */
    @Test
    fun lordOfTheAetherforge() = card(
        name = "Lord of the Aetherforge",
        cost = "{2}{U}",
        type = "Artifact Creature — Vedalken Wizard",
        pt = "*/*",
        text = """
            Lord of the Aetherforge's power and toughness are each equal to the number of artifacts you control.
            Other artifact creatures you control get +1/+1.
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(Mechanic.SetPowerToughness(
                power = Value.Count(Objects.Each(all(artifact, youControl))),
                toughness = Value.Count(Objects.Each(all(artifact, youControl))),
            )),
            characteristicDefining = true,
        ),
        othersGetPlusOne(artifact, creature, youControl),
    )

    @Test
    fun wyrmsFuryConduit() = card(
        name = "Wyrm's Fury Conduit",
        cost = "{R}",
        type = "Creature — Human Shaman",
        pt = "1/1",
        text = """
            Whenever you cast a noncreature spell, surveil 1. (Look at the top card of your library. You may put that card into your graveyard.)
            Delirium — As long as there are four or more card types among cards in your graveyard, this creature gets +2/+2, has flying, and attacks each combat if able.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.Cast(ObjectFilter.Not(creature), by = PlayerFilter.You)),
            instructions = Instructions(
                steps = listOf(Step.Do(Effect.Surveil(Players.You, Value.Fixed(1)))),
            ),
        ),
        Ability.Static(
            abilityWord = "Delirium",
            mechanics = listOf(
                Mechanic.ModifyPowerToughness(Value.Fixed(2), Value.Fixed(2)),
                Mechanic.GainAbilities(listOf(Keyword.Flying)),
                Mechanic.MustAttack(),
            ),
            condition = Condition.Compare(
                left = Value.CountDistinct(
                    Objects.Each(all(ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))),
                    SharedQuality.CardType,
                ),
                comparison = Comparison.AtLeast,
                right = Value.Fixed(4),
            ),
        ),
    )

    // ---- Entering with counters

    /** The number is read as each creature enters (CR 614.12). */
    @Test
    fun elenwynSpinnerOfDawn() = card(
        name = "Elenwyn, Spinner of Dawn",
        cost = "{1}{G}{G}",
        type = "Legendary Creature — Elf Noble",
        pt = "2/1",
        text = "Each other creature you control enters with a number of additional +1/+1 counters on it equal to Elenwyn's toughness.",
    ) parsesTo abilities(
        entersWithCounters(
            entering = all(another, creature, youControl),
            amount = Value.Of(NumericProperty.Toughness, Objects.Self, Combine.Single),
        ),
    )

    /** The type is chosen as it enters and kept on it; "the chosen type" refers to that choice (CR 614.12, 607.2d). */
    @Test
    fun gleamingCopycat() = card(
        name = "Gleaming Copycat",
        cost = "{2}",
        type = "Artifact Creature — Shapeshifter",
        pt = "2/1",
        text = """
            As this creature enters, choose a creature type.
            This creature is the chosen type in addition to its other types.
            Each other creature you control of the chosen type enters with an additional +1/+1 counter on it.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.Replacement(
            event = EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.AsEnters(listOf(Step.Choose(Choice.CreatureType, bind = "type", remember = true)))),
        ))),
        Ability.Static(listOf(Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Remembered("type"))))),
        entersWithCounters(
            entering = all(another, creature, youControl, ObjectFilter.Sub(SubtypeRef.Remembered("type"))),
            amount = Value.Fixed(1),
        ),
    )

    // ---- Counters from triggers

    /** Bolster 1: a +1/+1 counter on a creature you control with the least toughness (CR 701.39a). */
    @Test
    fun torvannaElderTreeSpirit() = card(
        name = "Torvanna, Elder-Tree Spirit",
        cost = "{W}{W}",
        type = "Legendary Creature — Spirit Soldier",
        pt = "2/2",
        text = "Whenever another nontoken creature you control enters, bolster 1. (Choose a creature with the least toughness among creatures you control and put a +1/+1 counter on it.)",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.ZoneChange(
                all(another, ObjectFilter.Not(ObjectFilter.Category(ObjectCategory.Token)), creature, youControl),
                to = Zone.Battlefield,
            )),
            instructions = Instructions(
                steps = listOf(Step.Do(Effect.Action(KeywordAction.Bolster(Value.Fixed(1))))),
            ),
        ),
    )

    @Test
    fun radiantAcolyte() = card(
        name = "Radiant Acolyte",
        cost = "{1}{W}",
        type = "Creature — Human Cleric",
        pt = "1/1",
        text = "At the beginning of combat on your turn, put a +1/+1 counter on target creature you control.",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.StepBegins(TurnStep.BeginningOfCombat, PlayerFilter.You)),
            instructions = Instructions(
                targets = listOf(TargetSlot("creature", TargetKind.Objects(all(creature, youControl)))),
                steps = listOf(Step.Do(Effect.PutCounters(Objects.Target("creature"), plusOne, amount = Value.Fixed(1)))),
            ),
        ),
    )

    // ---- Taxes, casting limits and prevention

    /** A cost increase for spells only, not abilities. */
    @Test
    fun magnetiteColossus() = card(
        name = "Magnetite Colossus",
        cost = "{4}",
        type = "Artifact Creature — Golem",
        pt = "5/3",
        text = "Nonartifact spells cost {1} more to cast.",
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.CostChange(
            what = all(ObjectFilter.Not(artifact), ObjectFilter.Category(ObjectCategory.Spell)),
            change = CostChange.More(ManaCost.parse("{1}")!!, times = Value.Fixed(1)),
        ))),
    )

    /** Each player in turn: the limit applies to a player once that player has cast a nonartifact spell this turn. */
    @Test
    fun aetherboundLawkeeper() = card(
        name = "Aetherbound Lawkeeper",
        cost = "{1}{W}",
        type = "Artifact Creature — Human Cleric",
        pt = "2/2",
        text = "Each player who has cast a nonartifact spell this turn can't cast additional nonartifact spells.",
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(Mechanic.CantCast(ObjectFilter.Not(artifact))),
            affects = Affected.Players(Players.Each(PlayerFilter.Any)),
            condition = Condition.HappenedThisTurn(EventPattern.Cast(ObjectFilter.Not(artifact), by = PlayerFilter.Is(Players.Affected))),
        ),
    )

    /** CR 615: a prevention effect, scoped by its damage event. */
    @Test
    fun brasselGearworkSage() = card(
        name = "Brassel, Gearwork Sage",
        cost = "{2}{W}",
        type = "Legendary Creature — Elephant Artificer",
        pt = "2/4",
        text = """
            Prevent all combat damage that would be dealt to attacking artifact creatures you control.
            Whenever one or more artifact creatures you control enter, draw a card. This ability triggers only once each turn.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.Prevention(EventPattern.Damage(
            to = RecipientFilter(objects = all(ObjectFilter.HasStatus(Status.Attacking), artifact, creature, youControl)),
            combat = true,
        )))),
        Ability.Triggered(
            trigger = Trigger.On(
                events = listOf(EventPattern.ZoneChange(all(artifact, creature, youControl), to = Zone.Battlefield)),
                oneOrMore = true,
            ),
            instructions = Instructions(
                steps = listOf(Step.Do(Effect.Draw(Players.You, count = Value.Fixed(1)))),
            ),
            perTurn = 1,
        ),
    )

    /** Only a player this creature dealt combat damage to this turn can be targeted; the effect lasts until your next
     * turn begins. */
    @Test
    fun wishOfAethervale() = card(
        name = "Wish of Aethervale",
        cost = "{1}",
        type = "Legendary Artifact Creature — Thopter",
        pt = "1/1",
        text = """
            Flying
            Sacrifice Wish of Aethervale: Until your next turn, target player who was dealt combat damage by Wish of Aethervale this turn can't cast noncreature spells.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Activated(
            cost = Cost(listOf(CostPart.Sacrifice(CostObjects.Self))),
            instructions = Instructions(
                targets = listOf(TargetSlot("player", TargetKind.Players(PlayerFilter.ThisTurn(EventPattern.Damage(
                    source = ObjectFilter.Is(Objects.Self),
                    to = RecipientFilter(players = PlayerFilter.Any),
                    combat = true,
                ))))),
                steps = listOf(Step.Do(Effect.ApplyMechanics(
                    mechanics = listOf(Mechanic.CantCast(ObjectFilter.Not(creature))),
                    affects = Affected.Players(Players.Target("player")),
                    duration = Duration.UntilYourNextTurn,
                ))),
            ),
        ),
    )
}
