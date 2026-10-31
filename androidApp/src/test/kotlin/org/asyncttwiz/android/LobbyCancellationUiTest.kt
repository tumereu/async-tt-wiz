package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

/** Production personal-presentation/controller handlers; actual pointer/Back rendering is deferred to I12b2. */
class LobbyCancellationUiTest {
    @Test fun keepCloseAndControllerBackAreLocalAndPreservePreparationAndReady() = CancellationFixture().use { f ->
        val preparation = PreparationRules.empty(f.game).copy(players = PreparationRules.empty(f.game).players.map { it.copy(ready = true) })
        f.head(f.game, preparation)
        for (dismiss in listOf<() -> Unit>(f.lobby.setupCancellation::dismiss, f.lobby.setupCancellation::dismiss, f.lobby::back)) {
            f.lobby.requestCancelSetup(f.game); dismiss()
            assertNull(f.lobby.setupCancellation.confirmation)
            assertEquals(f.game.id, f.lobby.state.selected)
            assertEquals(preparation, f.lobby.state.preparation)
            assertTrue(f.commands().isEmpty())
        }
        f.lobby.back() // Leaving is a separate local navigation action, not Cancel/Withdraw Ready.
        assertNull(f.lobby.state.selected); assertTrue(f.commands().isEmpty())
        f.lobby.open(f.game.id); assertEquals(preparation, f.lobby.state.preparation)
    }

    // LOB-026
    @Test fun holdContextDoesNotOpenOrSendAndCancelChoiceCapturesSameGame() = CancellationFixture().use { f ->
        f.lobby.back(); assertNull(f.lobby.state.selected)
        f.lobby.showGameContext(f.game)
        assertEquals(f.game, f.lobby.setupCancellation.context); assertNull(f.lobby.state.selected)
        f.lobby.setupCancellation.dismissContext(); assertTrue(f.commands().isEmpty())
        f.lobby.showGameContext(f.game); f.lobby.requestCancelSetup(f.lobby.setupCancellation.context!!)
        assertNull(f.lobby.setupCancellation.context); assertEquals(f.game, f.lobby.setupCancellation.confirmation)
        f.lobby.confirmCancelSetup()
        assertEquals(GameScope(f.game.id, f.game.preparationRound), f.commands().single().scope)
        assertNull(f.lobby.state.selected)
    }

    // LOB-026, LOB-039
    @Test fun onlyUnstartedAdmittedRowsOfferCancelAndClearOrNavigationDropsPersonalIntent() = CancellationFixture().use { f ->
        assertTrue(f.lobby.offersSetupCancellation(f.game))
        assertFalse(f.lobby.offersSetupCancellation(f.game.copy(lifecycle = GameLifecycle.ACTIVE)))
        assertFalse(f.lobby.offersSetupCancellation(f.game.copy(lifecycle = GameLifecycle.CANCELLED)))
        assertFalse(setupCancellationAllowed(f.game, "another account"))
        f.lobby.requestCancelSetup(f.game); f.lobby.open(f.game.id)
        assertNull(f.lobby.setupCancellation.confirmation); assertTrue(f.commands().isEmpty())
        f.lobby.showGameContext(f.game); f.lobby.clear()
        assertNull(f.lobby.setupCancellation.context); assertNull(f.lobby.setupCancellation.confirmation)
        assertFalse(f.lobby.cancelSetup(f.game)); assertTrue(f.commands().isEmpty())
    }
}
