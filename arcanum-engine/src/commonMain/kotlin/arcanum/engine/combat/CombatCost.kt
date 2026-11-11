package arcanum.engine.combat

import arcanum.api.Cost
import arcanum.api.Instructions
import arcanum.api.Mechanic
import arcanum.engine.state.Bindings
import arcanum.engine.state.ObjectId
import arcanum.engine.view.AbilityOrigin
import arcanum.engine.view.AppliedMechanic

/**
 * One cost to attack or block with [creature] that a declaration of attackers or blockers asks (CR 508.1h, 509.1d), as
 * [arcanum.engine.Engine.attackCosts] and [arcanum.engine.Engine.blockCosts] list them: [cost], asked by [ability], with
 * its X and repetitions of mana worked out into plain mana parts as the declaration locks them in ("{X} … where X is the
 * number of enchantments you control" with three is {3}).
 *
 * [ability] is a "can't attack … unless … pays" ([Mechanic.AttackCost]), a "can't block … unless … pays"
 * ([Mechanic.BlockCost]) or an optional "as this creature attacks" cost chosen for the creature ([Mechanic.AsAttacks],
 * CR 508.1g); its source and origin name the permanent and printed ability asking. A cost "for each creature attacking
 * you" is one cost for each such attacker, and a blocker blocking several attackers has one for each attacker an
 * ability asks it of.
 */
data class CombatCost(val creature: ObjectId, val ability: AppliedMechanic, val cost: Cost) {
    /** The names [ability] reads its cost with. */
    internal val names: Bindings get() = ability.names

    /** An optional "as it attacks" cost's "when you do" (CR 607.2h), triggering once it is paid. */
    internal val then: Instructions? get() = (ability.mechanic as? Mechanic.AsAttacks)?.then

    /** An optional "as it attacks" cost's origin on the creature, its linked trigger's origin. */
    internal val origin: AbilityOrigin? get() = if (ability.mechanic is Mechanic.AsAttacks) ability.origin else null
}
