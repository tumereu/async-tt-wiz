package org.asyncttwiz.protocol.game

import arcanum.engine.Preferences
import arcanum.engine.state.GameState

/**
 * The preferences ATW performs every game with ([arcanum.engine.Engine.perform], [arcanum.engine.Engine.options] and the
 * engine's pass checks), the same on both devices and the server: every player's "Skip when I have no choice" off in the
 * shared engine (I-D56 §13), so the engine asks every question whose skip would depend on hidden cards. A device applies a
 * player's own setting itself, as [arcanum.engine.PassCheck.ownSkipOn] and its own answers.
 */
object EnginePreferences {
    fun of(state: GameState): Preferences = Preferences(state.players.mapTo(HashSet()) { it.id })
}
