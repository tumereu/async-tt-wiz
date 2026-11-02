package arcanum.parser.sets.blb

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Mechanic
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.PrintedNumber
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone

/*
 * Shared shapes of the Bloomburrow (BLB) parser tests. The test cards are invented (D15): new names, the real cards'
 * exact oracle text with the new name.
 */

val creature: ObjectFilter = ObjectFilter.Type(CardType.Creature)
val land: ObjectFilter = ObjectFilter.Type(CardType.Land)
val you: ObjectFilter = ObjectFilter.ControlledBy(PlayerFilter.You)
val opponents: ObjectFilter = ObjectFilter.ControlledBy(PlayerFilter.Opponent)
val creatureYouControl: ObjectFilter = ObjectFilter.And(listOf(creature, you))
val nonlandPermanent: ObjectFilter = ObjectFilter.And(listOf(ObjectFilter.Not(land), ObjectFilter.Category(ObjectCategory.Permanent)))
val anyCard: ObjectFilter = ObjectFilter.Category(ObjectCategory.Card)
val p1p1: arcanum.api.CounterKind = arcanum.api.CounterKind.PowerToughness(1, 1)
fun sub(name: String): ObjectFilter = ObjectFilter.Sub(Subtype(name))
fun one(n: Int = 1): Value = Value.Fixed(n)

/** "When this creature dies". */
val thisDies: Trigger = Trigger.On(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield, to = Zone.Graveyard))

/** "Whenever you attack": once per declaration of attackers with one or more of your creatures (CR 508.3d). */
val youAttack: Trigger = Trigger.On(EventPattern.Attack(ObjectFilter.ControlledBy(PlayerFilter.You)), oneOrMore = true)

/** "At the beginning of combat on your turn". */
val yourCombat: Trigger = Trigger.On(EventPattern.StepBegins(arcanum.api.TurnStep.BeginningOfCombat, PlayerFilter.You))

/** "At the beginning of your end step". */
val yourEndStep: Trigger = Trigger.On(EventPattern.StepBegins(arcanum.api.TurnStep.End, PlayerFilter.You))

/** "target [filter]" named [name]: exactly one. */
fun slot(name: String, filter: ObjectFilter, min: Int = 1, max: Int = 1) =
    TargetSlot(name, TargetKind.Objects(filter), min = Value.Fixed(min), max = Value.Fixed(max))

/** "target opponent" */
val targetOpponent = TargetSlot("opponent", TargetKind.Players(PlayerFilter.Opponent), min = Value.Fixed(1), max = Value.Fixed(1))

/** "target player" */
val targetPlayer = TargetSlot("player", TargetKind.Players(PlayerFilter.Any), min = Value.Fixed(1), max = Value.Fixed(1))

/** A creature token ("a 1/1 white Rabbit creature token", CR 111.4). */
fun creatureToken(subtype: String, colors: List<Color>, power: Int, toughness: Int, abilities: List<Ability> = emptyList()) =
    TokenSpec.Defined(FaceDefinition(
        name = "$subtype Token",
        colorIndicator = colors,
        types = listOf(CardType.Creature),
        subtypes = listOf(Subtype(subtype)),
        power = PrintedNumber.Fixed(power),
        toughness = PrintedNumber.Fixed(toughness),
        abilities = abilities,
    ))

val rabbitToken = creatureToken("Rabbit", listOf(Color.White), 1, 1)
val fishToken = creatureToken("Fish", listOf(Color.Blue), 1, 1)

/** "[objects] get(s) +[power]/+[toughness] until end of turn." */
fun pump(objects: Objects, power: Int, toughness: Int) = Step.Do(Effect.ApplyMechanics(
    listOf(Mechanic.ModifyPowerToughness(Value.Fixed(power), Value.Fixed(toughness))),
    arcanum.api.Affected.Objects(objects),
    Duration.EndOfTurn,
))

/** "[objects] gain(s) [keywords] until end of turn." */
fun gains(objects: Objects, vararg keywords: Ability) = Step.Do(Effect.ApplyMechanics(
    listOf(Mechanic.GainAbilities(keywords.toList())),
    arcanum.api.Affected.Objects(objects),
    Duration.EndOfTurn,
))

/** "Put [n] +1/+1 counter(s) on [objects]." */
fun counters(objects: Objects, n: Int = 1, kind: arcanum.api.CounterKind = p1p1) = Step.Do(Effect.PutCounters(objects, kind, Value.Fixed(n)))

/** "[player] loses [n] life unless they sacrifice a nonland permanent of their choice or discard a card." (CR 118.12a) */
fun losesUnlessSacrificesOrDiscards(player: Players, n: Int) = listOf(
    Step.Pay(
        Cost(listOf(CostPart.OneOf(listOf(
            Cost(listOf(CostPart.Sacrifice(CostObjects.Choose(nonlandPermanent, count = Value.Fixed(1))))),
            Cost(listOf(CostPart.Discard(CostObjects.Choose(anyCard, count = Value.Fixed(1))))),
        )))),
        payer = player,
        bind = "paid",
    ),
    Step.If(arcanum.api.Condition.Not(arcanum.api.Condition.Did("paid")), then = listOf(Step.Do(Effect.LoseLife(player, Value.Fixed(n))))),
)

/** Instructions of only [steps]. */
fun steps(vararg steps: Step) = Instructions(steps = steps.toList())
