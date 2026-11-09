package arcanum.parser.sets.fra

import arcanum.api.Ability
import arcanum.api.ActivationRules
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.KeywordAction
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.ObjectMark
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.PrintedNumber
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Status
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Timing
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone

/*
 * Shared shapes of the Reality Fracture (FRA) parser tests. The test cards are invented (D15): new names, the real cards'
 * exact oracle text with the new name.
 */

val planeswalker: ObjectFilter = ObjectFilter.Type(CardType.Planeswalker)
val artifact: ObjectFilter = ObjectFilter.Type(CardType.Artifact)
val creatureCard: ObjectFilter = ObjectFilter.Type(CardType.Creature)
val nonland: ObjectFilter = ObjectFilter.Not(ObjectFilter.Type(CardType.Land))
val spellFilter: ObjectFilter = ObjectFilter.Category(ObjectCategory.Spell)
val permanentFilter: ObjectFilter = ObjectFilter.Category(ObjectCategory.Permanent)
val creatureOrPlaneswalker: ObjectFilter = ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Creature), planeswalker))
val anotherObject: ObjectFilter = ObjectFilter.Not(ObjectFilter.Is(Objects.Self))
fun and(vararg filters: ObjectFilter): ObjectFilter = ObjectFilter.And(filters.toList())
fun or(vararg filters: ObjectFilter): ObjectFilter = ObjectFilter.Or(filters.toList())
fun not(filter: ObjectFilter): ObjectFilter = ObjectFilter.Not(filter)
fun sub(name: String): ObjectFilter = ObjectFilter.Sub(Subtype(name))
fun controlledBy(players: PlayerFilter): ObjectFilter = ObjectFilter.ControlledBy(players)
fun manaValue(comparison: arcanum.api.Comparison, n: Int): ObjectFilter = ObjectFilter.ManaValue(comparison, Value.Fixed(n))
val yourGraveyard: ObjectFilter = ObjectFilter.And(listOf(ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)))
val cardsInYourGraveyard: Value = Value.Count(Objects.Each(yourGraveyard))
fun target(name: String): Objects = Objects.Target(name)

/** A target slot of objects [min]..[max]. */
fun slot(name: String, filter: ObjectFilter, min: Int = 1, max: Int = 1) =
    TargetSlot(name, TargetKind.Objects(filter), min = Value.Fixed(min), max = Value.Fixed(max))

// ---- Abilities

fun spell(vararg steps: Step, targets: List<TargetSlot> = emptyList()): Ability.Spell = Ability.Spell(Instructions(targets = targets, steps = steps.toList()))
fun triggered(trigger: Trigger, vararg steps: Step, targets: List<TargetSlot> = emptyList(), interveningIf: Condition? = null, perTurn: Int? = null,
              abilityWord: String? = null) =
    Ability.Triggered(trigger, Instructions(targets = targets, steps = steps.toList()), interveningIf = interveningIf, perTurn = perTurn, abilityWord = abilityWord)
fun activated(cost: Cost, vararg steps: Step, targets: List<TargetSlot> = emptyList(), rules: ActivationRules = anyTime, announced: List<String> = emptyList()) =
    Ability.Activated(cost, Instructions(targets = targets, steps = steps.toList(), announced = announced), rules)
fun static(vararg mechanics: Mechanic, affects: Objects = Objects.Self, condition: Condition? = null, abilityWord: String? = null) =
    Ability.Static(mechanics.toList(), affects = Affected.Objects(affects), condition = condition, abilityWord = abilityWord)
/** A loyalty ability (CR 606): "[±n]: …". */
fun loyalty(amount: Int, vararg steps: Step, targets: List<TargetSlot> = emptyList()) =
    Ability.Activated(Cost(listOf(CostPart.Loyalty(Value.Fixed(amount)))), Instructions(targets = targets, steps = steps.toList()), anyTime)

val anyTime = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You)
val asSorcery = ActivationRules(timing = Timing.Sorcery, activatedBy = PlayerFilter.You)
fun cost(vararg parts: CostPart): Cost = Cost(parts.toList())
fun mana(text: String): CostPart = CostPart.Mana(arcanum.api.ManaCost.parse(text)!!)
val tap: CostPart = CostPart.TapSelf
val sacrificeSelf: CostPart = CostPart.Sacrifice(CostObjects.Self)
val exileSelfFromGraveyard: CostPart = CostPart.Exile(CostObjects.Self, from = Zone.Graveyard)
val discardACard: CostPart = CostPart.Discard(CostObjects.Choose(ObjectFilter.Category(ObjectCategory.Card), count = Value.Fixed(1)))

// ---- Triggers

fun on(event: EventPattern): Trigger = Trigger.On(event)
val entersSelf: Trigger = on(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield))
val diesSelf: Trigger = on(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield, to = Zone.Graveyard))
val attacksSelf: Trigger = on(EventPattern.Attack(ObjectFilter.Is(Objects.Self)))
fun stepBegins(step: TurnStep, whose: PlayerFilter): Trigger = on(EventPattern.StepBegins(step, whose))
val youGainLife: Trigger = on(EventPattern.GainLife(PlayerFilter.You))
val youScryOrSurveil: Trigger = Trigger.On(listOf(EventPattern.KeywordActionDone(arcanum.api.KeywordActionKind.Scry, PlayerFilter.You), EventPattern.KeywordActionDone(arcanum.api.KeywordActionKind.Surveil, PlayerFilter.You)))
fun youCast(spells: ObjectFilter): Trigger = on(EventPattern.Cast(spells, PlayerFilter.You))
val youActivateLoyalty: Trigger = on(EventPattern.Activate(ObjectFilter.Any, PlayerFilter.You, kind = arcanum.api.AbilityKind.Loyalty))

// ---- Effects

fun n(value: Int): Value = Value.Fixed(value)
fun doing(effect: Effect): Step = Step.Do(effect)
/** "Empower Jace N" (CR 701.71a). */
fun empower(count: Value): Step = Step.Do(Effect.Action(KeywordAction.EmpowerJace(count)))
fun empower(count: Int): Step = empower(n(count))
fun surveil(count: Int): Step = Step.Do(Effect.Surveil(Players.You, n(count)))
fun scry(count: Int): Step = Step.Do(Effect.Scry(Players.You, n(count)))
fun mill(count: Int): Step = Step.Do(Effect.Mill(Players.You, n(count)))
fun draw(count: Int, players: Players = Players.You): Step = Step.Do(Effect.Draw(players, n(count)))
fun gainLife(amount: Int, players: Players = Players.You): Step = Step.Do(Effect.GainLife(players, n(amount)))
fun loseLife(amount: Int, players: Players = Players.You): Step = Step.Do(Effect.LoseLife(players, n(amount)))
fun damage(amount: Int, to: Recipients, source: Objects = Objects.Self): Step = Step.Do(Effect.DealDamage(to, n(amount), source))
val eachOpponent: Recipients = Recipients(players = Players.Each(PlayerFilter.Opponent))
fun counters(objects: Objects, amount: Int = 1, kind: arcanum.api.CounterKind = p1p1): Step = Step.Do(Effect.PutCounters(objects, kind, n(amount)))
val p1p1: arcanum.api.CounterKind = arcanum.api.CounterKind.PowerToughness(1, 1)
fun untilEndOfTurn(objects: Objects, vararg mechanics: Mechanic): Step =
    Step.Do(Effect.ApplyMechanics(mechanics.toList(), Affected.Objects(objects), Duration.EndOfTurn))
fun pump(objects: Objects, p: Int, t: Int, vararg also: Mechanic): Step =
    untilEndOfTurn(objects, Mechanic.ModifyPowerToughness(n(p), n(t)), *also)
fun gains(objects: Objects, vararg keywords: Ability): Step = untilEndOfTurn(objects, Mechanic.GainAbilities(keywords.toList()))
fun move(objects: Objects, zone: Zone, tapped: Boolean = false, position: Int? = null): Step =
    Step.Do(Effect.Move(objects, arcanum.api.Destination(zone, libraryPosition = position, tapped = tapped)))

// ---- Prepare (CR 722)

/** "This creature enters prepared." (CR 722.3a) */
val entersPrepared: Ability = Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
    modify = listOf(Modification.EntersMarked(ObjectMark.Prepared)))), affects = Affected.Objects(Objects.Self))
/** "[objects] become(s) prepared" / "unprepared" (CR 722.3a, b). */
fun becomesPrepared(objects: Objects): Step = Step.Do(Effect.Mark(objects, ObjectMark.Prepared))
fun becomesUnprepared(objects: Objects): Step = Step.Do(Effect.Mark(objects, ObjectMark.Prepared, remove = true))
val isPrepared: ObjectFilter = ObjectFilter.HasStatus(Status.Marked(ObjectMark.Prepared))
/** "At the beginning of your upkeep, if this creature isn't prepared, it becomes prepared." */
val preparesEachUpkeep: Ability = triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.You), becomesPrepared(Objects.Self),
    interveningIf = Condition.Not(Condition.ObjectMatches(Objects.Self, isPrepared)))

// ---- Tokens

/** "a 2/2 colorless Wizard Soldier creature token named Cadet" (CR 111.4). */
val cadet: TokenSpec.Defined = TokenSpec.Defined(FaceDefinition("Cadet", colorIndicator = emptyList(), types = listOf(CardType.Creature),
    subtypes = listOf(Subtype("Wizard"), Subtype("Soldier")), power = PrintedNumber.Fixed(2), toughness = PrintedNumber.Fixed(2)))
fun createCadets(count: Int): Step = Step.Do(Effect.CreateToken(cadet, n(count), Players.You, bind = "created"))
/** "Create a Heartwood token." (CR 111.10x) */
val createHeartwood: Step = Step.Do(Effect.CreateToken(TokenSpec.Predefined("Heartwood"), n(1), Players.You))
fun creatureToken(subtypes: String, colors: List<arcanum.api.Color>, power: Int, toughness: Int, abilities: List<Ability> = emptyList(),
                  types: List<CardType> = listOf(CardType.Creature), name: String = "$subtypes Token") =
    TokenSpec.Defined(FaceDefinition(name, colorIndicator = colors, types = types, subtypes = subtypes.split(' ').map(::Subtype),
        power = PrintedNumber.Fixed(power), toughness = PrintedNumber.Fixed(toughness), abilities = abilities))

// ---- Conditions and values

fun thisTurn(event: EventPattern, atLeast: Int = 1, measure: Measure = Measure.Events): Condition = Condition.HappenedThisTurn(event, atLeast, measure)
val scriedOrSurveilledThisTurn: Condition = Condition.Or(listOf(thisTurn(EventPattern.KeywordActionDone(arcanum.api.KeywordActionKind.Scry, PlayerFilter.You)), thisTurn(EventPattern.KeywordActionDone(arcanum.api.KeywordActionKind.Surveil, PlayerFilter.You))))
val opponentDealtNoncombatDamage: EventPattern = EventPattern.Damage(ObjectFilter.Any, RecipientFilter(players = PlayerFilter.Opponent), combat = false)
/** "Threshold — … as long as there are seven or more cards in your graveyard" (CR 207.2c) */
val threshold: Condition = Condition.Exists(yourGraveyard, atLeast = 7)
val lifeGainedThisTurn: Value = Value.EventsThisTurn(EventPattern.GainLife(PlayerFilter.You), Measure.Amount)
