package arcanum.parser.sets.gtc

import arcanum.api.Ability
import arcanum.api.ActivationRules
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Cost
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
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

/*
 * Shared shapes of the Gatecrash (GTC) and Dragon's Maze (DGM) parser tests. The test cards are invented (D15): new
 * names, the real cards' exact oracle text with the new name.
 */

val creature: ObjectFilter = ObjectFilter.Type(CardType.Creature)
val land: ObjectFilter = ObjectFilter.Type(CardType.Land)
val you: ObjectFilter = ObjectFilter.ControlledBy(PlayerFilter.You)
val opponents: ObjectFilter = ObjectFilter.ControlledBy(PlayerFilter.Opponent)
val creatureYouControl: ObjectFilter = ObjectFilter.And(listOf(creature, you))
val creatureOpponentControls: ObjectFilter = ObjectFilter.And(listOf(creature, opponents))
val token: ObjectFilter = ObjectFilter.Category(ObjectCategory.Token)
val p1p1: CounterKind = CounterKind.PowerToughness(1, 1)
fun n(value: Int): Value = Value.Fixed(value)
fun mana(text: String): Cost = Cost.mana(ManaCost.parse(text)!!)
fun costOf(vararg parts: CostPart): Cost = Cost(parts.toList())
fun manaPart(text: String): CostPart = CostPart.Mana(ManaCost.parse(text)!!)
fun steps(vararg steps: Step): Instructions = Instructions(steps = steps.toList())
fun power(of: Objects): Value = Value.Of(NumericProperty.Power, of, Combine.Single)
fun toughness(of: Objects): Value = Value.Of(NumericProperty.Toughness, of, Combine.Single)

/** "Activate only as a sorcery" or any time, by its controller (CR 602.5). */
val anyTime = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You)
val sorcerySpeed = ActivationRules(timing = Timing.Sorcery, activatedBy = PlayerFilter.You)

/** "target [filter]" named [name]: exactly one, or [min]..[max]. */
fun slot(name: String, filter: ObjectFilter, min: Int = 1, max: Int = 1) =
    TargetSlot(name, TargetKind.Objects(filter), min = Value.Fixed(min), max = Value.Fixed(max))

/** "target player" / "target opponent" */
val targetPlayer = TargetSlot("player", TargetKind.Players(PlayerFilter.Any), min = Value.Fixed(1), max = Value.Fixed(1))
val targetOpponent = TargetSlot("opponent", TargetKind.Players(PlayerFilter.Opponent), min = Value.Fixed(1), max = Value.Fixed(1))

/** "any target" (CR 115.4): a creature, planeswalker or battle, or a player. */
val anyTarget = TargetSlot("any", TargetKind.Either(ObjectFilter.Or(listOf(creature, ObjectFilter.Type(CardType.Planeswalker),
    ObjectFilter.Type(CardType.Battle))), PlayerFilter.Any), min = Value.Fixed(1), max = Value.Fixed(1))

/** "target player or planeswalker" */
val playerOrPlaneswalker = TargetSlot("target", TargetKind.Either(ObjectFilter.Type(CardType.Planeswalker), PlayerFilter.Any),
    min = Value.Fixed(1), max = Value.Fixed(1))

/** [mechanics] for [objects] until end of turn (CR 611.2). */
fun untilEndOfTurn(objects: Objects, vararg mechanics: Mechanic): Step =
    Step.Do(Effect.ApplyMechanics(mechanics.toList(), Affected.Objects(objects), Duration.EndOfTurn))

/** "[objects] get(s) +[p]/+[t] until end of turn" */
fun pump(objects: Objects, p: Int, t: Int, vararg also: Mechanic): Step =
    untilEndOfTurn(objects, Mechanic.ModifyPowerToughness(n(p), n(t)), *also)

/** "[objects] gain(s) [keywords] until end of turn" */
fun gains(objects: Objects, vararg keywords: Ability): Step = untilEndOfTurn(objects, Mechanic.GainAbilities(keywords.toList()))

/** "When this creature enters" (CR 603.6a). */
val entersSelf: Trigger = Trigger.On(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield))

/** "When this creature dies" (CR 700.4). */
val diesSelf: Trigger = Trigger.On(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield, to = Zone.Graveyard))

/** "Whenever this creature attacks" */
val attacksSelf: Trigger = Trigger.On(EventPattern.Attack(ObjectFilter.Is(Objects.Self)))

/** "Whenever this creature deals combat damage to a player" */
val combatDamageToPlayer: Trigger = Trigger.On(EventPattern.Damage(ObjectFilter.Is(Objects.Self),
    arcanum.api.RecipientFilter(players = PlayerFilter.Any), combat = true))

/** "At the beginning of your upkeep" */
val yourUpkeep: Trigger = Trigger.On(EventPattern.StepBegins(arcanum.api.TurnStep.Upkeep, PlayerFilter.You))

/** A creature token ("a 1/1 white Soldier creature token", CR 111.4). */
fun creatureToken(subtypes: String, colors: List<Color>, power: Int, toughness: Int, abilities: List<Ability> = emptyList(),
                  types: List<CardType> = listOf(CardType.Creature)) =
    TokenSpec.Defined(FaceDefinition(
        name = "$subtypes Token",
        colorIndicator = colors,
        types = types,
        subtypes = subtypes.split(' ').map(::Subtype),
        power = PrintedNumber.Fixed(power),
        toughness = PrintedNumber.Fixed(toughness),
        abilities = abilities,
    ))
