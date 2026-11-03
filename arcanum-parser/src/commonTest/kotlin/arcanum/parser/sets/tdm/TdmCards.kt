package arcanum.parser.sets.tdm

import arcanum.api.ActivationRules
import arcanum.api.CardType
import arcanum.api.CardTypeRef
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.ManaCost
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Status
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.Timing
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone

/*
 * Shared shapes of the Tarkir: Dragonstorm (TDM) parser tests. The test cards are invented (D15): new names, the real cards' exact oracle
 * text with the new name.
 */

// ---- Filters

private fun type(t: CardType): ObjectFilter = ObjectFilter.Type(CardTypeRef.Literal(t))
val creature: ObjectFilter = type(CardType.Creature)
val land: ObjectFilter = type(CardType.Land)
val artifact: ObjectFilter = type(CardType.Artifact)
val enchantment: ObjectFilter = type(CardType.Enchantment)
val instant: ObjectFilter = type(CardType.Instant)
val sorcery: ObjectFilter = type(CardType.Sorcery)
val planeswalker: ObjectFilter = type(CardType.Planeswalker)
val you: ObjectFilter = ObjectFilter.ControlledBy(PlayerFilter.You)
val opponents: ObjectFilter = ObjectFilter.ControlledBy(PlayerFilter.Opponent)
val ownedByYou: ObjectFilter = ObjectFilter.OwnedBy(PlayerFilter.You)
val isSelf: ObjectFilter = ObjectFilter.Is(Objects.Self)
val token: ObjectFilter = ObjectFilter.Category(ObjectCategory.Token)
val spellFilter: ObjectFilter = ObjectFilter.Category(ObjectCategory.Spell)
val cardFilter: ObjectFilter = ObjectFilter.Category(ObjectCategory.Card)
val permanentFilter: ObjectFilter = ObjectFilter.Category(ObjectCategory.Permanent)
val attacking: ObjectFilter = ObjectFilter.HasStatus(Status.Attacking)
val tapped: ObjectFilter = ObjectFilter.HasStatus(Status.Tapped)
/** All of [filters], as written (the parser's canonical form is already flat). */
fun and(vararg filters: ObjectFilter): ObjectFilter = ObjectFilter.And(filters.toList())
fun or(vararg filters: ObjectFilter): ObjectFilter = ObjectFilter.Or(filters.toList())
fun not(filter: ObjectFilter): ObjectFilter = ObjectFilter.Not(filter)
fun sub(name: String): ObjectFilter = ObjectFilter.Sub(SubtypeRef.Literal(Subtype(name)))
fun inZone(zone: Zone): ObjectFilter = ObjectFilter.InZone(zone)

// ---- References, values, steps, costs

fun target(name: String): Objects = Objects.Target(name)
fun bound(name: String): Objects = Objects.Bound(name)
fun n(value: Int): Value = Value.Fixed(value)
fun doing(effect: Effect): Step = Step.Do(effect)
fun mana(text: String): CostPart = CostPart.Mana(ManaCost.parse(text)!!)
val tap: CostPart = CostPart.TapSelf
val p1p1: CounterKind = CounterKind.PowerToughness(1, 1)
val anyTime = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You)
val asSorcery = ActivationRules(timing = Timing.Sorcery, activatedBy = PlayerFilter.You)

// ---- Triggers

val entersSelf: Trigger = Trigger.On(EventPattern.ZoneChange(isSelf, to = Zone.Battlefield))
val diesSelf: Trigger = Trigger.On(EventPattern.ZoneChange(isSelf, from = Zone.Battlefield, to = Zone.Graveyard))
val attacksSelf: Trigger = Trigger.On(EventPattern.Attack(isSelf))
