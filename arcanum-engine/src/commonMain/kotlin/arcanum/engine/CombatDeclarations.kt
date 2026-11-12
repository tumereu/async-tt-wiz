package arcanum.engine

import arcanum.engine.state.GameState
import arcanum.engine.view.View

/**
 * For a host's combat declarations: the costs a declaration of attackers or blockers asks (CR 508.1h, 509.1d).
 * Stateless, like [Engine]: it reads the [GameState] it is given and changes nothing.
 */
object CombatDeclarations {
    /**
     * The costs declaring [attacks] asks (CR 508.1h), each attacker's own ([arcanum.engine.combat.CombatCost]): every "can't
     * attack … unless … pays" that applies to an attacker and what it attacks, and every optional "as it attacks" cost chosen
     * for it ([arcanum.engine.input.Attack.paying], CR 508.1g), with X and repetitions of mana worked out as the declaration
     * locks them in. Empty when the declaration costs nothing. [view]: [state]'s view when the caller has it.
     */
    fun attackCosts(state: GameState, attacks: List<arcanum.engine.input.Attack>, view: View? = null): List<arcanum.engine.combat.CombatCost> =
        arcanum.engine.combat.CombatRules.attackCosts(snapshot(state, view), attacks)

    /**
     * The costs declaring [blocks] asks (CR 509.1d), which the defending player determines and pays: every "can't block …
     * unless … pays" that applies to a blocker and what it blocks, once for each attacker it blocks, with X and repetitions
     * of mana worked out as the declaration locks them in. Empty when the declaration costs nothing. [view]: [state]'s view
     * when the caller has it.
     */
    fun blockCosts(state: GameState, blocks: List<arcanum.engine.input.Block>, view: View? = null): List<arcanum.engine.combat.CombatCost> =
        arcanum.engine.combat.CombatRules.blockCosts(snapshot(state, view), blocks)

    private fun snapshot(state: GameState, view: View?): arcanum.engine.casting.Snapshot =
        if (view != null) arcanum.engine.casting.Snapshot(state) { view } else arcanum.engine.casting.Snapshot(state)
}
