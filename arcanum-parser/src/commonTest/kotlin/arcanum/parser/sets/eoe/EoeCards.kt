package arcanum.parser.sets.eoe

import arcanum.api.Ability
import arcanum.api.ActivationRules
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.KeywordExpansions
import arcanum.api.ManaCost
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.PrintedNumber
import arcanum.api.Recipients
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
 * Shared shapes of the Edge of Eternities (EOE) parser tests. The test cards are invented (D15): new names, the real cards' exact oracle
 * text with the new name.
 */

// ---- Filters

val creature: ObjectFilter = ObjectFilter.Type(CardType.Creature)
val land: ObjectFilter = ObjectFilter.Type(CardType.Land)
val artifact: ObjectFilter = ObjectFilter.Type(CardType.Artifact)
val enchantment: ObjectFilter = ObjectFilter.Type(CardType.Enchantment)
val instant: ObjectFilter = ObjectFilter.Type(CardType.Instant)
val sorcery: ObjectFilter = ObjectFilter.Type(CardType.Sorcery)
val spellFilter: ObjectFilter = ObjectFilter.Category(ObjectCategory.Spell)
val cardFilter: ObjectFilter = ObjectFilter.Category(ObjectCategory.Card)
val token: ObjectFilter = ObjectFilter.Category(ObjectCategory.Token)
val permanentFilter: ObjectFilter = ObjectFilter.Category(ObjectCategory.Permanent)
val you: ObjectFilter = ObjectFilter.ControlledBy(PlayerFilter.You)
val opponents: ObjectFilter = ObjectFilter.ControlledBy(PlayerFilter.Opponent)
val ownedByYou: ObjectFilter = ObjectFilter.OwnedBy(PlayerFilter.You)
val isSelf: ObjectFilter = ObjectFilter.Is(Objects.Self)
val tapped: ObjectFilter = ObjectFilter.HasStatus(arcanum.api.Status.Tapped)
val attacking: ObjectFilter = ObjectFilter.HasStatus(arcanum.api.Status.Attacking)
fun inZone(zone: Zone): ObjectFilter = ObjectFilter.InZone(zone)
/** All of [filters] (the parser's canonical form is flat). */
fun and(vararg filters: ObjectFilter): ObjectFilter = ObjectFilter.And(filters.toList())
fun or(vararg filters: ObjectFilter): ObjectFilter = ObjectFilter.Or(filters.toList())
fun not(filter: ObjectFilter): ObjectFilter = ObjectFilter.Not(filter)
fun sub(name: String): ObjectFilter = ObjectFilter.Sub(Subtype(name))

// ---- Targets and references

fun target(name: String): Objects = Objects.Target(name)
fun bound(name: String): Objects = Objects.Bound(name)
/** A target slot of objects [min]..[max] (null: any number). */
fun slot(name: String, filter: ObjectFilter, min: Int = 1, max: Int? = 1) =
    TargetSlot(name, TargetKind.Objects(filter), min = Value.Fixed(min), max = max?.let { Value.Fixed(it) })
/** "any target" (CR 115.4) */
val anyTarget = TargetSlot("any", TargetKind.Either(or(creature, ObjectFilter.Type(CardType.Planeswalker), ObjectFilter.Type(CardType.Battle)),
    PlayerFilter.Any), min = Value.Fixed(1), max = Value.Fixed(1))
val toAny = Recipients(target = "any")

// ---- Abilities

fun spell(vararg steps: Step, targets: List<TargetSlot> = emptyList(), replacements: List<Mechanic.Replacement> = emptyList(), announced: List<String> = emptyList()) =
    Ability.Spell(Instructions(targets = targets, steps = steps.toList(), replacements = replacements, announced = announced))
fun triggered(trigger: Trigger, vararg steps: Step, targets: List<TargetSlot> = emptyList(), interveningIf: Condition? = null, perTurn: Int? = null,
              abilityWord: String? = null, triggersOnlyIf: Condition? = null, functionsIn: List<Zone>? = null) =
    Ability.Triggered(trigger, Instructions(targets = targets, steps = steps.toList()), interveningIf = interveningIf, perTurn = perTurn,
        abilityWord = abilityWord, triggersOnlyIf = triggersOnlyIf, functionsIn = functionsIn)
fun activated(cost: Cost, vararg steps: Step, targets: List<TargetSlot> = emptyList(), rules: ActivationRules = anyTime) =
    Ability.Activated(cost, Instructions(targets = targets, steps = steps.toList()), rules)
fun static(vararg mechanics: Mechanic, affects: Objects = Objects.Self, condition: Condition? = null, characteristicDefining: Boolean = false) =
    Ability.Static(mechanics.toList(), affects = Affected.Objects(affects), condition = condition, characteristicDefining = characteristicDefining)
fun playerStatic(vararg mechanics: Mechanic, players: arcanum.api.Players = arcanum.api.Players.You, condition: Condition? = null) =
    Ability.Static(mechanics.toList(), affects = Affected.Players(players), condition = condition)

val anyTime = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You)
val asSorcery = ActivationRules(timing = Timing.Sorcery, activatedBy = PlayerFilter.You)
fun cost(vararg parts: CostPart): Cost = Cost(parts.toList())
fun mana(text: String): CostPart = CostPart.Mana(ManaCost.parse(text)!!)
val tap: CostPart = CostPart.TapSelf

// ---- Triggers

fun on(event: EventPattern): Trigger = Trigger.On(event)
val entersSelf: Trigger = on(EventPattern.ZoneChange(isSelf, to = Zone.Battlefield))
val diesSelf: Trigger = on(EventPattern.ZoneChange(isSelf, from = Zone.Battlefield, to = Zone.Graveyard))
val attacksSelf: Trigger = on(EventPattern.Attack(isSelf))

// ---- Effects

fun n(value: Int): Value = Value.Fixed(value)
fun doing(effect: Effect): Step = Step.Do(effect)
fun untilEndOfTurn(objects: Objects, vararg mechanics: Mechanic): Step =
    Step.Do(Effect.ApplyMechanics(mechanics.toList(), Affected.Objects(objects), Duration.EndOfTurn))
fun pt(p: Int, t: Int): Mechanic = Mechanic.ModifyPowerToughness(n(p), n(t))
fun has(vararg abilities: Ability): Mechanic = Mechanic.GainAbilities(abilities.toList())
val p1p1: CounterKind = CounterKind.PowerToughness(1, 1)

// ---- Tokens

/** A Lander token (CR 111.10u). */
val lander: TokenSpec = TokenSpec.Predefined("Lander")
/** "a 2/2 colorless Robot artifact creature token" */
val robot: TokenSpec = TokenSpec.Defined(FaceDefinition("Robot Token", colorIndicator = emptyList(), types = listOf(CardType.Artifact, CardType.Creature),
    subtypes = listOf(Subtype("Robot")), power = PrintedNumber.Fixed(2), toughness = PrintedNumber.Fixed(2)))
/** "a 1/1 white Human Soldier creature token" */
val soldier: TokenSpec = TokenSpec.Defined(FaceDefinition("Human Soldier Token", colorIndicator = listOf(Color.White), types = listOf(CardType.Creature),
    subtypes = listOf(Subtype("Human"), Subtype("Soldier")), power = PrintedNumber.Fixed(1), toughness = PrintedNumber.Fixed(1)))
/** 'a 1/1 colorless Drone artifact creature token with flying and "This token can block only creatures with flying."' */
val drone: TokenSpec = TokenSpec.Defined(FaceDefinition("Drone Token", colorIndicator = emptyList(), types = listOf(CardType.Artifact, CardType.Creature),
    subtypes = listOf(Subtype("Drone")), power = PrintedNumber.Fixed(1), toughness = PrintedNumber.Fixed(1), abilities = listOf(Keyword.Flying,
        static(Mechanic.CanBlockOnly(and(creature, ObjectFilter.HasKeyword(Keyword.Flying)))))))

// ---- Edge of Eternities mechanics

/** "Warp [cost]" (CR 702.185a) */
fun warp(manaCost: String): Keyword = Keyword.Warp(Cost.mana(ManaCost.parse(manaCost)!!))
/** Charge counters (CR 122.1), what station puts (CR 702.184a). */
val charge: CounterKind = KeywordExpansions.CHARGE
/** A station symbol "N+ | [abilities]" (CR 721.2a), with its P/T box "[P/T]" when it has one (CR 721.2b): "As long as this permanent has N or more
 * charge counters on it, it has [abilities] and is a creature with base power and toughness [P/T] in addition to its other types." */
fun station(threshold: Int, vararg abilities: Ability, pt: String? = null): Ability.Static {
    val creature = pt?.split('/')?.let { (p, t) -> listOf(Mechanic.SetTypes(add = listOf(CardType.Creature)), Mechanic.SetPowerToughness(n(p.toInt()), n(t.toInt()))) }
    return Ability.Static(creature.orEmpty() + Mechanic.GainAbilities(abilities.toList()), affects = Affected.Objects(Objects.Self),
        condition = Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(charge, atLeast = threshold)))
}
/** Void (an ability word, CR 207.2c): "a nonland permanent left the battlefield this turn or a spell was warped this turn" (a spell cast for its
 * warp cost, CR 702.185c). */
val void: Condition = Condition.Or(listOf(
    Condition.HappenedThisTurn(EventPattern.ZoneChange(and(not(land), permanentFilter), from = Zone.Battlefield), atLeast = 1, measure = Measure.Objects),
    Condition.HappenedThisTurn(EventPattern.Cast(ObjectFilter.WasPaid(Keyword.Warp.Kind, times = 1), PlayerFilter.Any), atLeast = 1, measure = Measure.Objects)))
