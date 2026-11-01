package arcanum.engine.mana

import arcanum.api.ManaCost
import arcanum.engine.decision.ManaUse
import arcanum.engine.notYet
import arcanum.engine.state.GameState
import arcanum.engine.state.PlayerId
import arcanum.engine.view.View

/**
 * Mana (CR 106, 107, 601.2g–h, 605): the mana abilities a player can use, paying a cost with pool mana, mana abilities
 * and other ways (convoke, delve, Phyrexian life), and the suggested payment (§7.8) shown in
 * [arcanum.engine.decision.Decision.ManaPayment].
 */
internal object Mana {
    /** Ways [player] could pay [cost] now: pool mana, untapped sources' mana abilities, other ways. */
    fun options(state: GameState, view: View, player: PlayerId, cost: ManaCost): List<ManaUse> = notYet("mana payment options")

    /** A payment of [cost] that keeps the most options open, or empty when none is found (§7.8). */
    fun suggest(state: GameState, view: View, player: PlayerId, cost: ManaCost): List<ManaUse> = notYet("suggested mana payment")
}
