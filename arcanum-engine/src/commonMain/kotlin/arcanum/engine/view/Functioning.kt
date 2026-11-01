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
        // CR 113.6e, f: how and from where this object can be played or cast
        ability.mechanics.all { it is Mechanic.MayPlay || it is Mechanic.CastRestriction } && ability.selfOnly() -> EVERYWHERE
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
        // CR 113.6m: "return this card from your graveyard to the battlefield / to your hand" moves it out of the
        // graveyard, so the ability works only there (unless the trigger is about this object changing zones, which
        // looks back in time, CR 603.10a)
        val selfZoneChange = trigger.events.any { it is EventPattern.ZoneChange && it.objects.isSelf() }
        if (!selfZoneChange && firstSelfMove(ability.instructions.steps) in setOf(Zone.Battlefield, Zone.Hand)) return setOf(Zone.Graveyard)
        // CR 113.6k: a trigger condition that can't trigger from the battlefield works where it can trigger from
        val zones = trigger.events.map { event ->
            when {
                event is EventPattern.Cast && event.spell.isSelf() -> setOf(Zone.Stack)
                event is EventPattern.Discard && event.cards.isSelf() -> setOf(Zone.Hand)
                event is EventPattern.Cycle && event.cards.isSelf() -> setOf(Zone.Hand)
                event is EventPattern.ZoneChange && event.objects.isSelf() && event.from != null &&
                    event.from != Zone.Battlefield -> setOf(event.from!!)
                event is EventPattern.ZoneChange && event.objects.isSelf() && event.from == null &&
                    event.to != null && event.to != Zone.Battlefield -> EVERYWHERE
                else -> usual
            }
        }
        return zones.flatten().toSet()
    }

    /** The zone the first step that moves this object ([Objects.Self]) moves it to, looking inside nested steps. */
    private fun firstSelfMove(steps: List<Step>): Zone? {
        steps.forEach { step ->
            val found = when (step) {
                is Step.Do -> (step.effect as? Effect.Move)?.takeIf { it.objects == Objects.Self }?.to?.zone
                is Step.May -> firstSelfMove(step.steps)
                is Step.If -> firstSelfMove(step.then) ?: firstSelfMove(step.otherwise)
                is Step.Pay -> firstSelfMove(step.steps)
                is Step.Simultaneously -> firstSelfMove(step.steps)
                is Step.Repeat -> firstSelfMove(step.steps)
                is Step.ForEach -> firstSelfMove(step.steps)
                else -> null
            }
            if (found != null) return found
        }
        return null
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
