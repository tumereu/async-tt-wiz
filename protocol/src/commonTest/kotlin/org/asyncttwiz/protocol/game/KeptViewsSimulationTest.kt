package org.asyncttwiz.protocol.game

import kotlin.test.Test

/**
 * Engine-responsiveness R2: the device simulation's whole games ([DeviceSimulationTest]: drafts, assumptions, branches
 * with their new lines' reorders, rewinds, reloads, the server's warm states) played with [GameModel.checkKept], so every
 * view and visible set a model serves from what it kept is checked against working it out afresh: none outlives a
 * change to its state.
 */
class KeptViewsSimulationTest {
    private fun checking(block: () -> Unit) {
        GameModel.checkKept = true
        try { block() } finally { GameModel.checkKept = false }
    }

    @Test fun liveGamesServeNoStaleView() = checking { DeviceSimulationTest().liveGames() }

    @Test fun asyncGamesServeNoStaleView() = checking { DeviceSimulationTest().asyncGames() }

    @Test fun mixedGamesServeNoStaleView() = checking { DeviceSimulationTest().mixedGames() }
}
