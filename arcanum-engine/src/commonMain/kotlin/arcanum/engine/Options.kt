package arcanum.engine

import arcanum.engine.input.Input
import arcanum.engine.state.GameState
import arcanum.engine.state.PlayerId

/**
 * [Engine.options]: what a player may do now (CR 116, 117.1): the answer to the open decision, or with priority the
 * lands they may play, spells they may cast, abilities they may activate, and passing; declarations in the declare
 * steps (CR 508, 509). Filled in with casting, combat and priority.
 */
internal object Options {
    fun of(state: GameState, player: PlayerId): List<Input> = notYet("options for $player")
}
