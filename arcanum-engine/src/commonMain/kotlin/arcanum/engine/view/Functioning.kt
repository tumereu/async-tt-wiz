package arcanum.engine.view

import arcanum.api.Ability
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.Step
import arcanum.api.Trigger
import arcanum.api.Zone

/**
 * Where an ability functions (CR 113.6): the zones it states ([Ability.Static.functionsIn] and the like), else the
 * default worked out from what it does (CR 113.6a–m). Abilities of instants and sorceries function on the Stack, those
 * of other objects on the battlefield.
 */
object Functioning {
    val EVERYWHERE: Set<Zone> = setOf(
        Zone.Battlefield, Zone.Hand, Zone.Library, Zone.Graveyard, Zone.Exile, Zone.Stack, Zone.Command, Zone.Outside,
    )

    fun zones(ability: Ability, instantOrSorcery: Boolean): Set<Zone> {
        val usual = if (instantOrSorcery) setOf(Zone.Stack) else setOf(Zone.Battlefield)
        return when (ability) {
            is Ability.Static -> ability.functionsIn?.toSet() ?: staticZones(ability, usual)
            is Ability.Activated -> ability.functionsIn?.toSet() ?: activatedZones(ability, usual)
            is Ability.Triggered -> ability.functionsIn?.toSet() ?: triggeredZones(ability, usual)
            is Ability.SpecialAction -> ability.functionsIn?.toSet() ?: usual
            is Ability.Spell -> setOf(Zone.Stack)
            else -> usual
        }
    }

    private fun staticZones(ability: Ability.Static, usual: Set<Zone>): Set<Zone> = when {
        // CR 113.6a
        ability.characteristicDefining -> EVERYWHERE
        ability.mechanics.isEmpty() -> usual
        // CR 113.6e, f: how and from where this object can be played or cast (tla, ecl: "you may cast this spell as though it had
        // flash [if …]" works where the card is cast from, its condition checked as the static applies)
        ability.mechanics.all { it is Mechanic.MayPlay || it is Mechanic.CastRestriction || it == Mechanic.InstantSpeed } && ability.selfOnly() -> EVERYWHERE
        // CR 113.6h: how it enters
        ability.mechanics.all { it is Mechanic.Replacement && it.event.isSelfEntering() } -> EVERYWHERE
        // CR 113.6d, g: what this spell costs, that it can't be countered
        ability.mechanics.all { it.isOwnCastingRule() } && ability.selfOnly() -> setOf(Zone.Stack)
        // "as it resolves" replacements of the spell itself (rebound, buyback): on the Stack
        ability.mechanics.all { it is Mechanic.Replacement && it.event.isSelfLeaving(Zone.Stack) } -> setOf(Zone.Stack)
        else -> usual
    }

    private fun activatedZones(ability: Ability.Activated, usual: Set<Zone>): Set<Zone> {
        // CR 113.6j, m: a cost that moves the object out of a zone works in that zone
        ability.cost.parts.forEach { part ->
            when {
                part is CostPart.Discard && part.what == CostObjects.Self -> return setOf(Zone.Hand)
                part is CostPart.Exile && part.what == CostObjects.Self -> return setOf(part.from)
                part is CostPart.Reveal && part.what == CostObjects.Self -> return setOf(Zone.Hand)
            }
        }
        // An effect that puts this object onto the battlefield works where it is not: the graveyard (unearth-like)
        val movesSelfOntoBattlefield = ability.instructions.steps.any {
            it is Step.Do && it.effect is Effect.Move && (it.effect as Effect.Move).objects == Objects.Self &&
                (it.effect as Effect.Move).to.zone == Zone.Battlefield
        }
        return if (movesSelfOntoBattlefield) setOf(Zone.Graveyard) else usual
    }

    private fun triggeredZones(ability: Ability.Triggered, usual: Set<Zone>): Set<Zone> {
        val trigger = ability.trigger as? Trigger.On ?: return usual
        // CR 113.6k: a trigger condition that can't trigger from the battlefield works where it can trigger from
        val zones = trigger.events.map { event ->
            when {
                event is EventPattern.Cast && event.spell.isSelf() -> setOf(Zone.Stack)
                event is EventPattern.Discard && event.cards.isSelf() -> setOf(Zone.Hand)
                // CR 702.29c: "when you cycle this card" triggers from the zone the card winds up in
                event is EventPattern.Cycle && event.cards.isSelf() -> EVERYWHERE
                // "enters from your hand" is an enters ability: it works on the battlefield (CR 603.6a)
                event is EventPattern.ZoneChange && event.objects.isSelf() && event.from != null &&
                    event.from != Zone.Battlefield && event.to != Zone.Battlefield -> setOf(event.from!!)
                event is EventPattern.ZoneChange && event.objects.isSelf() && event.from == null &&
                    event.to != null && event.to != Zone.Battlefield -> EVERYWHERE
                else -> usual
            }
        }
        return zones.flatten().toSet()
    }

    private fun Ability.Static.selfOnly(): Boolean =
        (affects as? arcanum.api.Affected.Objects)?.objects == Objects.Self

    private fun ObjectFilter.isSelf(): Boolean = this == ObjectFilter.Is(Objects.Self)

    private fun EventPattern.isSelfEntering(): Boolean =
        this is EventPattern.ZoneChange && objects.isSelf() && to == Zone.Battlefield

    private fun EventPattern.isSelfLeaving(zone: Zone): Boolean =
        this is EventPattern.ZoneChange && objects.isSelf() && from == zone

    private fun Mechanic.isOwnCastingRule(): Boolean = when (this) {
        is Mechanic.AlternativeCost, is Mechanic.AdditionalCost, is Mechanic.PayManaBy, Mechanic.CantSpendMana,
        is Mechanic.XAtLeast, Mechanic.CantBeCountered -> true
        is Mechanic.CostChange -> what.isSelf()
        else -> false
    }
}
